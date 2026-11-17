package com.papertrader.feed;

import com.papertrader.interfaces.HistoryProvider;
import com.papertrader.interfaces.SnapshotProvider;
import com.papertrader.model.MarketSnapshot;
import com.papertrader.model.Asset;
import com.papertrader.model.Candle;
import com.papertrader.model.ETF;
import com.papertrader.model.Stock;
import com.papertrader.model.Timeframe;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Stock and ETF prices from Yahoo Finance. Needs no API key, so the app shows real
 * prices even without setup. It is an unofficial endpoint, so Finnhub is preferred
 * when a key is available (see App.java).
 *
 * Answer looks like: {"chart":{"result":[{"meta":{"regularMarketPrice":271.4,"chartPreviousClose":270.2,...}}]}}
 */
public class YahooQuoteProvider extends HttpQuoteProvider implements HistoryProvider, SnapshotProvider {

    /** Shares asked for per refresh when every share needs its own request. */
    static final int ONE_BY_ONE_PER_REFRESH = 30;
    private static final int SPARK_BATCH = 20;
    private static final long SPARK_RETRY_MS = 10 * 60_000;
    private static final long SNAPSHOT_REUSE_MS = 90_000;

    /** When the batch endpoint failed, we wait until this time before trying it again. */
    private static volatile long sparkRetryAt = 0;
    /** Day numbers seen in recent quote answers, by Yahoo symbol (shared by all instances). */
    private static final Map<String, TimedSnapshot> RECENT_SNAPSHOTS = new ConcurrentHashMap<>();
    /** A few requests at a time, so 150 shares load in seconds instead of a minute. */
    private static final ExecutorService ONE_BY_ONE = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "yahoo-quotes");
        t.setDaemon(true);
        return t;
    });

    @Override
    public String getName() {
        return "Yahoo Finance";
    }

    @Override
    public boolean supports(Asset asset) {
        return asset instanceof Stock || asset instanceof ETF;
    }

    @Override
    public Map<String, Quote> fetchQuotes(List<Asset> assets) throws IOException {
        Map<String, Asset> byYahoo = new LinkedHashMap<>();
        for (Asset asset : assets) {
            byYahoo.put(YahooSymbols.of(asset.getSymbol()), asset);
        }
        Map<String, Quote> quotes = new HashMap<>();
        if (sparkWorks()) {
            fetchWithSpark(byYahoo, quotes);
        }
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, Asset> e : byYahoo.entrySet()) {
            if (!quotes.containsKey(e.getValue().getSymbol())) {
                missing.add(e.getKey());
            }
        }
        fetchOneByOne(missing, byYahoo, quotes);
        if (quotes.isEmpty() && !assets.isEmpty()) {
            // nothing worked at all: report it, so the sidebar shows "offline" instead of a fake "Connected"
            throw new IOException("Yahoo Finance did not answer");
        }
        return quotes;
    }

    /**
     * With the batch endpoint every share is refreshed each time. Without it (one request per
     * share) LivePriceFeed refreshes the shares in turns, so 150+ NSE shares do not get us blocked.
     */
    @Override
    public int maxSymbolsPerRefresh() {
        return sparkWorks() ? Integer.MAX_VALUE : ONE_BY_ONE_PER_REFRESH;
    }

    @Override
    public int getRefreshSeconds() {
        return 15;
    }

    // ---------------------------------------------------------------- batch quotes ("spark")

    private static boolean sparkWorks() {
        return System.currentTimeMillis() >= sparkRetryAt;
    }

    /** Asks for up to 20 symbols per request. A failed batch switches to one-by-one for 10 minutes. */
    private void fetchWithSpark(Map<String, Asset> byYahoo, Map<String, Quote> quotes) {
        List<String> symbols = new ArrayList<>(byYahoo.keySet());
        for (int i = 0; i < symbols.size(); i += SPARK_BATCH) {
            List<String> batch = symbols.subList(i, Math.min(symbols.size(), i + SPARK_BATCH));
            String joined = batch.stream()
                    .map(sym -> URLEncoder.encode(sym, StandardCharsets.UTF_8))
                    .reduce((x, y) -> x + "," + y).orElse("");
            Map<String, Quote> found;
            try {
                found = parseSpark(get("https://query1.finance.yahoo.com/v7/finance/spark?symbols=" + joined
                        + "&range=1d&interval=1d"));
            } catch (Exception e) {
                found = Map.of();
            }
            if (found.isEmpty()) {
                sparkRetryAt = System.currentTimeMillis() + SPARK_RETRY_MS;
                return;
            }
            for (Map.Entry<String, Quote> e : found.entrySet()) {
                Asset asset = byYahoo.get(e.getKey());
                if (asset != null) {
                    quotes.put(asset.getSymbol(), e.getValue());
                }
            }
        }
    }

    /**
     * Reads the batch answer. Two shapes are in use:
     * {"spark":{"result":[{"symbol":"AAPL","response":[{"meta":{"regularMarketPrice":..}}]}]}} and
     * {"AAPL":{"symbol":"AAPL","close":[..],"chartPreviousClose":..}}. Keys are Yahoo symbols.
     */
    static Map<String, Quote> parseSpark(String json) {
        Map<String, Quote> quotes = new HashMap<>();
        JSONObject root = new JSONObject(json);
        JSONObject spark = root.optJSONObject("spark");
        if (spark != null) {
            JSONArray results = spark.optJSONArray("result");
            for (int i = 0; results != null && i < results.length(); i++) {
                JSONObject item = results.getJSONObject(i);
                JSONArray response = item.optJSONArray("response");
                if (response == null || response.isEmpty()) {
                    continue;
                }
                JSONObject result = response.getJSONObject(0);
                Quote quote = quoteFromResult(result);
                if (quote != null) {
                    String symbol = item.optString("symbol", result.optJSONObject("meta") == null ? ""
                            : result.getJSONObject("meta").optString("symbol", ""));
                    quotes.put(symbol, quote);
                    rememberSnapshot(symbol, result);
                }
            }
            return quotes;
        }
        for (String key : root.keySet()) {
            JSONObject item = root.optJSONObject(key);
            if (item == null) {
                continue;
            }
            JSONArray close = item.optJSONArray("close");
            double price = Double.NaN;
            for (int i = close == null ? -1 : close.length() - 1; i >= 0; i--) {
                if (!close.isNull(i)) {
                    price = close.getDouble(i);
                    break;
                }
            }
            if (price > 0) {
                double prev = item.optDouble("chartPreviousClose", item.optDouble("previousClose", price));
                quotes.put(item.optString("symbol", key), new Quote(price, prev > 0 ? prev : price));
            }
        }
        return quotes;
    }

    // ---------------------------------------------------------------- one request per share

    private void fetchOneByOne(List<String> yahooSymbols, Map<String, Asset> byYahoo, Map<String, Quote> quotes) {
        if (yahooSymbols.isEmpty()) {
            return;
        }
        List<Future<?>> jobs = new ArrayList<>();
        for (String yahoo : yahooSymbols) {
            jobs.add(ONE_BY_ONE.submit(() -> {
                try {
                    String json = get("https://query1.finance.yahoo.com/v8/finance/chart/"
                            + URLEncoder.encode(yahoo, StandardCharsets.UTF_8) + "?interval=1d&range=1d");
                    Quote quote = parse(json);
                    if (quote != null) {
                        synchronized (quotes) {
                            quotes.put(byYahoo.get(yahoo).getSymbol(), quote);
                        }
                        rememberSnapshot(json, yahoo);
                    }
                } catch (Exception ignored) {
                    // Keep existing price for assets not found on Yahoo Finance
                }
            }));
        }
        for (Future<?> job : jobs) {
            try {
                job.get();
            } catch (Exception ignored) {
                // already handled inside the job
            }
        }
    }

    // ---------------------------------------------------------------- day numbers from the same answers

    /** The quote answers already hold today's high / low / volume, so the detail screens reuse them. */
    private static void rememberSnapshot(String json, String yahooSymbol) {
        try {
            RECENT_SNAPSHOTS.put(yahooSymbol, new TimedSnapshot(parseSnapshot(json), System.currentTimeMillis()));
        } catch (IOException ignored) {
            // not a full answer: the screen downloads its own snapshot
        }
    }

    private static void rememberSnapshot(String yahooSymbol, JSONObject result) {
        JSONObject meta = result.optJSONObject("meta");
        if (meta != null && meta.has("regularMarketDayHigh") && meta.has("fiftyTwoWeekHigh")) {
            try {
                RECENT_SNAPSHOTS.put(yahooSymbol, new TimedSnapshot(snapshotFromResult(result), System.currentTimeMillis()));
            } catch (Exception ignored) {
                // incomplete: the screen downloads its own snapshot
            }
        }
    }

    private record TimedSnapshot(MarketSnapshot snapshot, long time) {
    }

    /**
     * Past candles from the same chart endpoint:
     * result[0].timestamp[] (seconds) and result[0].indicators.quote[0].open[] / high[] / low[] / close[] / volume[]
     */
    @Override
    public List<Candle> fetchCandles(Asset asset, Timeframe timeframe, int count) throws IOException {
        String query = switch (timeframe) {
            case ONE_MINUTE -> "interval=1m&range=1d";
            case FIVE_MINUTES -> "interval=5m&range=5d";
            case ONE_HOUR -> "interval=60m&range=1mo";
            case ONE_DAY -> "interval=1d&range=1y";
            case ONE_WEEK -> "interval=1wk&range=5y";
        };
        List<Candle> all = parseCandles(get("https://query1.finance.yahoo.com/v8/finance/chart/"
                + YahooSymbols.encoded(asset) + "?" + query));
        return all.subList(Math.max(0, all.size() - count), all.size());
    }

    static List<Candle> parseCandles(String json) {
        JSONObject result = new JSONObject(json).getJSONObject("chart").getJSONArray("result").getJSONObject(0);
        List<Candle> candles = new ArrayList<>();
        if (!result.has("timestamp")) {
            return candles;
        }
        JSONArray times = result.getJSONArray("timestamp");
        JSONObject q = result.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0);
        JSONArray open = q.getJSONArray("open");
        JSONArray high = q.getJSONArray("high");
        JSONArray low = q.getJSONArray("low");
        JSONArray close = q.getJSONArray("close");
        JSONArray volume = q.getJSONArray("volume");
        for (int i = 0; i < times.length(); i++) {
            if (open.isNull(i) || close.isNull(i) || high.isNull(i) || low.isNull(i)) {
                continue;   // Yahoo leaves gaps (null) for minutes with no trades
            }
            candles.add(new Candle(times.getLong(i) * 1000, open.getDouble(i), high.getDouble(i),
                    low.getDouble(i), close.getDouble(i), volume.isNull(i) ? 0 : volume.getDouble(i)));
        }
        return candles;
    }

    /** Today's open / high / low / volume and the 52-week range (same chart endpoint, no key). */
    @Override
    public MarketSnapshot fetchSnapshot(Asset asset) throws IOException {
        return fetchSnapshot(YahooSymbols.of(asset));
    }

    /** Works for any Yahoo symbol, including indices such as ^NSEI (NIFTY 50) or ^GSPC (S&P 500). */
    public MarketSnapshot fetchSnapshot(String yahooSymbol) throws IOException {
        TimedSnapshot recent = RECENT_SNAPSHOTS.get(yahooSymbol);
        if (recent != null && System.currentTimeMillis() - recent.time() < SNAPSHOT_REUSE_MS) {
            return recent.snapshot();   // fetched with the last price update, no need to ask again
        }
        String json = get("https://query1.finance.yahoo.com/v8/finance/chart/"
                + java.net.URLEncoder.encode(yahooSymbol, java.nio.charset.StandardCharsets.UTF_8)
                + "?interval=1d&range=1d");
        return parseSnapshot(json);
    }

    static MarketSnapshot parseSnapshot(String json) throws IOException {
        try {
            return snapshotFromResult(new JSONObject(json).getJSONObject("chart").getJSONArray("result").getJSONObject(0));
        } catch (Exception e) {
            throw new IOException("Unexpected Yahoo answer", e);
        }
    }

    private static MarketSnapshot snapshotFromResult(JSONObject result) {
        JSONObject meta = result.getJSONObject("meta");
        double open = Double.NaN;
        JSONObject indicators = result.optJSONObject("indicators");
        if (indicators != null && indicators.has("quote")) {
            JSONArray opens = indicators.getJSONArray("quote").getJSONObject(0).optJSONArray("open");
            if (opens != null && opens.length() > 0 && !opens.isNull(0)) {
                open = opens.getDouble(0);
            }
        }
        return new MarketSnapshot(
                meta.getDouble("regularMarketPrice"),
                meta.optDouble("chartPreviousClose", meta.optDouble("previousClose", Double.NaN)),
                open,
                meta.optDouble("regularMarketDayHigh", Double.NaN),
                meta.optDouble("regularMarketDayLow", Double.NaN),
                meta.optDouble("regularMarketVolume", Double.NaN),
                meta.optDouble("fiftyTwoWeekHigh", Double.NaN),
                meta.optDouble("fiftyTwoWeekLow", Double.NaN),
                "Yahoo Finance");
    }

    private static Quote quoteFromResult(JSONObject result) {
        JSONObject meta = result.optJSONObject("meta");
        if (meta == null || !meta.has("regularMarketPrice")) {
            return null;
        }
        double price = meta.optDouble("regularMarketPrice", Double.NaN);
        if (!(price > 0)) {
            return null;
        }
        double prev = meta.optDouble("chartPreviousClose", meta.optDouble("previousClose", price));
        return new Quote(price, prev > 0 ? prev : price);
    }

    /** What Yahoo knows about a symbol: its name and today's quote. */
    public record Listing(String yahooSymbol, String name, String exchange, Quote quote) {
    }

    /**
     * Checks that a Yahoo symbol exists and trades, e.g. "JIOFIN.NS".
     * @return the listing, or null if Yahoo has no prices for it
     */
    public Listing lookUp(String yahooSymbol) throws IOException {
        String json = get("https://query1.finance.yahoo.com/v8/finance/chart/"
                + URLEncoder.encode(yahooSymbol, StandardCharsets.UTF_8) + "?interval=1d&range=1d");
        return parseListing(json);
    }

    static Listing parseListing(String json) {
        try {
            JSONObject result = new JSONObject(json).getJSONObject("chart").getJSONArray("result").getJSONObject(0);
            Quote quote = quoteFromResult(result);
            if (quote == null) {
                return null;
            }
            JSONObject meta = result.getJSONObject("meta");
            String symbol = meta.optString("symbol", "");
            String name = meta.optString("longName", "");
            if (name.isBlank()) {
                name = meta.optString("shortName", symbol);
            }
            return new Listing(symbol, name, meta.optString("fullExchangeName", meta.optString("exchangeName", "")), quote);
        } catch (Exception e) {
            return null;
        }
    }

    static Quote parse(String json) {
        try {
            return quoteFromResult(new JSONObject(json).getJSONObject("chart").getJSONArray("result").getJSONObject(0));
        } catch (Exception e) {
            return null;
        }
    }
}
