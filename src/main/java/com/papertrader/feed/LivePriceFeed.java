package com.papertrader.feed;

import com.papertrader.interfaces.PriceFeed;
import com.papertrader.interfaces.PriceListener;
import com.papertrader.interfaces.QuoteProvider;
import com.papertrader.model.Asset;

import javax.swing.SwingUtilities;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Real market prices from the internet.
 *
 * It implements the SAME PriceFeed interface as SimulatedPriceFeed, so MarketService,
 * TradingService and the UI did not change at all when live data was added.
 * That is the Open/Closed and Dependency Inversion principles in action.
 *
 * How it works:
 *  1. Each asset is given to the first QuoteProvider that supports it
 *     (e.g. BTC -> Binance, AAPL -> Finnhub). Assets no provider supports
 *     keep using a SimulatedPriceFeed (COMPOSITION: this class HAS-A SimulatedPriceFeed).
 *  2. Each provider is asked for prices on a background thread, so the window never freezes
 *     while waiting for the internet.
 *  3. New prices are handed to the Swing thread with SwingUtilities.invokeLater, so listeners
 *     still run on the UI thread exactly like with the simulator.
 *  4. If the internet fails, the last known price is kept and the status shows "offline".
 */
public class LivePriceFeed implements PriceFeed {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Map<QuoteProvider, List<Asset>> assignments = new LinkedHashMap<>();
    private final SimulatedPriceFeed fallback;
    private final List<PriceListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, String> status = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler;

    public LivePriceFeed(List<Asset> assets, List<QuoteProvider> providers) {
        List<Asset> notCovered = new ArrayList<>();
        for (Asset asset : assets) {
            QuoteProvider chosen = null;
            for (QuoteProvider provider : providers) {
                if (provider.supports(asset)) {
                    chosen = provider;
                    break;
                }
            }
            if (chosen == null) {
                notCovered.add(asset);
            } else {
                assignments.computeIfAbsent(chosen, p -> new ArrayList<>()).add(asset);
            }
        }
        this.fallback = new SimulatedPriceFeed(notCovered, 1000);
        for (QuoteProvider provider : assignments.keySet()) {
            status.put(provider.getName(), provider.getName() + ": connecting...");
        }
    }

    /**
     * Downloads every price once, right now, before the app opens.
     * This way the market starts at real prices instead of the placeholder ones.
     * @return true if at least one provider answered (false = probably no internet)
     */
    public boolean loadInitialPrices() {
        boolean anySuccess = false;
        for (Map.Entry<QuoteProvider, List<Asset>> entry : assignments.entrySet()) {
            try {
                Map<String, Quote> quotes = entry.getKey().fetchQuotes(entry.getValue());
                applyQuotes(entry.getValue(), quotes, false);
                markOk(entry.getKey());
                anySuccess = true;
            } catch (Exception e) {
                markFailed(entry.getKey(), e);
            }
        }
        return anySuccess;
    }

    @Override
    public void addListener(PriceListener listener) {
        listeners.add(listener);
        fallback.addListener(listener);
    }

    @Override
    public void removeListener(PriceListener listener) {
        listeners.remove(listener);
        fallback.removeListener(listener);
    }

    @Override
    public void start() {
        scheduler = Executors.newScheduledThreadPool(Math.max(1, assignments.size()), runnable -> {
            Thread thread = new Thread(runnable, "price-download");
            thread.setDaemon(true);   // do not keep the app alive after the window closes
            return thread;
        });
        for (Map.Entry<QuoteProvider, List<Asset>> entry : assignments.entrySet()) {
            QuoteProvider provider = entry.getKey();
            int seconds = provider.getRefreshSeconds();
            scheduler.scheduleWithFixedDelay(() -> poll(provider, entry.getValue()), seconds, seconds, TimeUnit.SECONDS);
        }
        fallback.start();
    }

    @Override
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        fallback.stop();
    }

    @Override
    public String getStatusText() {
        List<String> parts = new ArrayList<>(status.values());
        if (fallback.hasAssets()) {
            parts.add("Simulated: other assets");
        }
        return String.join("  |  ", parts);
    }

    /** Runs on a background thread. */
    private void poll(QuoteProvider provider, List<Asset> assets) {
        try {
            Map<String, Quote> quotes = provider.fetchQuotes(assets);
            SwingUtilities.invokeLater(() -> applyQuotes(assets, quotes, true));   // back to the UI thread
            markOk(provider);
        } catch (Exception e) {
            markFailed(provider, e);   // keep the last prices; try again next time
        }
    }

    private void applyQuotes(List<Asset> assets, Map<String, Quote> quotes, boolean notify) {
        for (Asset asset : assets) {
            Quote quote = quotes.get(asset.getSymbol());
            if (quote == null) {
                continue;
            }
            double oldPrice = asset.getPrice();
            asset.setPrice(quote.price());
            asset.setPreviousClose(quote.previousClose());
            if (notify) {
                for (PriceListener listener : listeners) {
                    listener.onPriceUpdate(asset, oldPrice, quote.price());
                }
            }
        }
    }

    private void markOk(QuoteProvider provider) {
        status.put(provider.getName(), provider.getName() + ": live " + LocalTime.now().format(TIME));
    }

    private void markFailed(QuoteProvider provider, Exception e) {
        status.put(provider.getName(), provider.getName() + ": offline (" + e.getMessage() + ")");
    }
}
