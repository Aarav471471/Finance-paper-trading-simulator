package com.papertrader.service;

import com.papertrader.feed.CoinGeckoGlobalProvider;
import com.papertrader.feed.YahooQuoteProvider;
import com.papertrader.interfaces.FundamentalsProvider;
import com.papertrader.interfaces.SnapshotProvider;
import com.papertrader.model.Asset;
import com.papertrader.model.Candle;
import com.papertrader.model.Fundamentals;
import com.papertrader.model.MarketSnapshot;
import com.papertrader.model.NewsItem;
import com.papertrader.model.Timeframe;
import com.papertrader.strategy.TechnicalAnalysis;

import javax.swing.SwingUtilities;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SINGLE RESPONSIBILITY: the real numbers the screens show around a price
 * (day high/low/volume, 52-week range, P/E, market cap, news, index levels, technical rating).
 *
 * How it works: a screen asks, e.g. snapshot(asset). The service answers immediately from its
 * cache and, if the cached value is old, downloads a fresh one in the background. Screens refresh
 * every second anyway, so they pick the new value up on their own. Without internet (or when a
 * source is down) the day / 52-week numbers are calculated from the candles in ChartService.
 * All caches are only touched on the Swing UI thread, so no locks are needed.
 */
public class MarketDataService {

    private static final long SNAPSHOT_MAX_AGE = 60_000;          // 1 minute
    private static final long FUNDAMENTALS_MAX_AGE = 6 * 3_600_000; // 6 hours
    private static final long NEWS_MAX_AGE = 10 * 60_000;         // 10 minutes

    private final List<SnapshotProvider> snapshotProviders;
    private final FundamentalsProvider fundamentalsProvider;   // null = offline mode
    private final YahooQuoteProvider indexProvider;            // null = offline mode
    private final CoinGeckoGlobalProvider cryptoProvider;      // null = offline mode
    private final ChartService charts;

    private final Map<String, Object> cache = new HashMap<>();
    private final Map<String, Long> lastAttempt = new HashMap<>();
    private final ExecutorService worker = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "market-data");
        t.setDaemon(true);
        return t;
    });

    public MarketDataService(List<SnapshotProvider> snapshotProviders, FundamentalsProvider fundamentalsProvider,
                             YahooQuoteProvider indexProvider, CoinGeckoGlobalProvider cryptoProvider,
                             ChartService charts) {
        this.snapshotProviders = snapshotProviders;
        this.fundamentalsProvider = fundamentalsProvider;
        this.indexProvider = indexProvider;
        this.cryptoProvider = cryptoProvider;
        this.charts = charts;
    }

    /** True when real (internet) sources are used. */
    public boolean isOnline() {
        return fundamentalsProvider != null;
    }

    // ---------------------------------------------------------------- day numbers

    /** Today's open / high / low / volume and the 52-week range. Never null. */
    public MarketSnapshot snapshot(Asset asset) {
        MarketSnapshot fromCandles = fromCandles(asset);
        for (SnapshotProvider provider : snapshotProviders) {
            if (provider.supports(asset)) {
                refresh("snap:" + asset.getSymbol(), SNAPSHOT_MAX_AGE, () -> provider.fetchSnapshot(asset));
                break;
            }
        }
        MarketSnapshot online = (MarketSnapshot) cache.get("snap:" + asset.getSymbol());
        if (online == null) {
            return fromCandles;
        }
        // keep the live price, fill anything the source did not give from the candles
        MarketSnapshot merged = online.fillFrom(fromCandles);
        return new MarketSnapshot(asset.getPrice(), merged.previousClose(), merged.open(),
                Math.max(merged.dayHigh(), asset.getPrice()), Math.min(merged.dayLow(), asset.getPrice()),
                merged.volume(), merged.week52High(), merged.week52Low(), merged.source());
    }

    /** Day numbers calculated from 5-minute candles of the latest session, 52-week from daily candles. */
    MarketSnapshot fromCandles(Asset asset) {
        List<Candle> intraday = charts.getCandles(asset, Timeframe.FIVE_MINUTES);
        List<Candle> daily = charts.getCandles(asset, Timeframe.ONE_DAY);
        double open = Double.NaN;
        double high = Double.NaN;
        double low = Double.NaN;
        double volume = Double.NaN;
        if (!intraday.isEmpty()) {
            LocalDate session = dateOf(intraday.get(intraday.size() - 1));
            high = Double.NEGATIVE_INFINITY;
            low = Double.POSITIVE_INFINITY;
            volume = 0;
            for (Candle c : intraday) {
                if (dateOf(c).equals(session)) {
                    if (Double.isNaN(open)) {
                        open = c.open();
                    }
                    high = Math.max(high, c.high());
                    low = Math.min(low, c.low());
                    volume += c.volume();
                }
            }
        }
        double high52 = Double.NaN;
        double low52 = Double.NaN;
        if (!daily.isEmpty()) {
            high52 = Double.NEGATIVE_INFINITY;
            low52 = Double.POSITIVE_INFINITY;
            for (Candle c : daily) {
                high52 = Math.max(high52, c.high());
                low52 = Math.min(low52, c.low());
            }
        }
        double price = asset.getPrice();
        return new MarketSnapshot(price, asset.getPreviousClose(), open,
                Double.isNaN(high) ? Double.NaN : Math.max(high, price),
                Double.isNaN(low) ? Double.NaN : Math.min(low, price),
                volume, high52, low52, "candles");
    }

    private static LocalDate dateOf(Candle c) {
        return Instant.ofEpochMilli(c.time()).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    /** Volume-weighted average price of today's session (from 5-minute candles). NaN if unknown. */
    public double vwap(Asset asset) {
        List<Candle> intraday = charts.getCandles(asset, Timeframe.FIVE_MINUTES);
        if (intraday.isEmpty()) {
            return Double.NaN;
        }
        LocalDate session = dateOf(intraday.get(intraday.size() - 1));
        double pv = 0;
        double v = 0;
        for (Candle c : intraday) {
            if (dateOf(c).equals(session)) {
                double typical = (c.high() + c.low() + c.close()) / 3;
                pv += typical * c.volume();
                v += c.volume();
            }
        }
        return v > 0 ? pv / v : Double.NaN;
    }

    /** Average daily volume of the last `days` daily candles. NaN if unknown. */
    public double averageVolume(Asset asset, int days) {
        List<Candle> daily = charts.getCandles(asset, Timeframe.ONE_DAY);
        if (daily.size() < 2) {
            return Double.NaN;
        }
        double sum = 0;
        int count = 0;
        for (int i = Math.max(0, daily.size() - 1 - days); i < daily.size() - 1; i++) {   // skip today
            sum += daily.get(i).volume();
            count++;
        }
        return count == 0 ? Double.NaN : sum / count;
    }

    /** Indicator votes from one year of daily candles, or null while they are still loading. */
    public TechnicalAnalysis.Report technicals(Asset asset) {
        List<Candle> daily = charts.getCandles(asset, Timeframe.ONE_DAY);
        return daily.size() < 2 ? null : TechnicalAnalysis.analyze(daily);
    }

    // ---------------------------------------------------------------- company data

    /** P/E, market cap, profile... Empty while loading, offline, or when Yahoo has no data. */
    public Optional<Fundamentals> fundamentals(Asset asset) {
        if (fundamentalsProvider != null) {
            refresh("fund:" + asset.getSymbol(), FUNDAMENTALS_MAX_AGE, () -> fundamentalsProvider.fetchFundamentals(asset));
        }
        return Optional.ofNullable((Fundamentals) cache.get("fund:" + asset.getSymbol()));
    }

    @SuppressWarnings("unchecked")
    public List<NewsItem> news(Asset asset) {
        if (fundamentalsProvider != null) {
            refresh("news:" + asset.getSymbol(), NEWS_MAX_AGE, () -> fundamentalsProvider.fetchNews(asset, 5));
        }
        return (List<NewsItem>) cache.getOrDefault("news:" + asset.getSymbol(), List.of());
    }

    // ---------------------------------------------------------------- market overview

    /** An index such as ^NSEI (NIFTY 50). Empty while loading or offline. */
    public Optional<MarketSnapshot> index(String yahooSymbol) {
        if (indexProvider != null) {
            refresh("index:" + yahooSymbol, SNAPSHOT_MAX_AGE, () -> indexProvider.fetchSnapshot(yahooSymbol));
        }
        return Optional.ofNullable((MarketSnapshot) cache.get("index:" + yahooSymbol));
    }

    /** {total crypto market cap in USD, 24h change %}. Empty while loading or offline. */
    public Optional<double[]> cryptoMarketCap() {
        if (cryptoProvider != null) {
            refresh("crypto:global", SNAPSHOT_MAX_AGE, cryptoProvider::fetchTotalMarketCap);
        }
        return Optional.ofNullable((double[]) cache.get("crypto:global"));
    }

    // ---------------------------------------------------------------- background loading

    /** Starts a background download if the key was not tried within maxAge. Failures simply retry later. */
    private void refresh(String key, long maxAge, Callable<Object> download) {
        long now = System.currentTimeMillis();
        Long last = lastAttempt.get(key);
        if (last != null && now - last < maxAge) {
            return;
        }
        lastAttempt.put(key, now);
        worker.submit(() -> {
            try {
                Object value = download.call();
                SwingUtilities.invokeLater(() -> cache.put(key, value));
            } catch (Exception e) {
                // source not reachable: keep the old value (or the candle fallback) and retry after maxAge
            }
        });
    }
}
