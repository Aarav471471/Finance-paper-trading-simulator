package com.papertrader.service;

import com.papertrader.interfaces.HistoryProvider;
import com.papertrader.interfaces.PriceListener;
import com.papertrader.model.Asset;
import com.papertrader.model.Candle;
import com.papertrader.model.Timeframe;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * SINGLE RESPONSIBILITY: candlestick data for charts and backtests.
 *  1. Loads past candles from the first HistoryProvider that works (Binance / Yahoo / Simulated),
 *     on a background thread so the UI never freezes.
 *  2. Keeps the newest candle up to date with every live price (it is a PriceListener).
 * All the cached data is only touched on the Swing UI thread, so no locks are needed.
 */
public class ChartService implements PriceListener {

    private static final int MAX_CANDLES = 1000;

    private final List<HistoryProvider> providers;
    private final Map<String, List<Candle>> cache = new HashMap<>();
    private final Map<String, String> sourceNames = new HashMap<>();
    /** Everyone waiting for a download that is still running, by key. */
    private final Map<String, List<Consumer<List<Candle>>>> waiting = new HashMap<>();
    /** Keys whose download a page is actively waiting for (it called loadAsync). */
    private final Set<String> requested = new HashSet<>();
    /** Keys that finished downloading; read from the download threads to skip stale queued work. */
    private final Set<String> finished = ConcurrentHashMap.newKeySet();
    /** Background pre-fetching (getCandles): many requests, one at a time, so we don't flood the APIs. */
    private static final long PREFETCH_GAP_MS = 400;
    private final ExecutorService prefetchLoader = Executors.newSingleThreadExecutor(r -> daemon(r, "history-prefetch"));
    /** Downloads a page is waiting on (charts, Backtest): never queued behind the pre-fetching above. */
    private final ExecutorService requestLoader = Executors.newFixedThreadPool(2, r -> daemon(r, "history-download"));

    private static Thread daemon(Runnable r, String name) {
        Thread t = new Thread(r, name);
        t.setDaemon(true);
        return t;
    }

    /** @param providers tried in order; put SimulatedHistoryProvider last as the fallback */
    public ChartService(List<HistoryProvider> providers) {
        this.providers = providers;
    }

    private static String key(Asset asset, Timeframe timeframe) {
        return asset.getSymbol() + "|" + timeframe;
    }

    /** Candles if already loaded (a copy), otherwise an empty list and loading starts in the background. */
    public List<Candle> getCandles(Asset asset, Timeframe timeframe) {
        List<Candle> cached = cache.get(key(asset, timeframe));
        if (cached == null) {
            String key = key(asset, timeframe);
            if (!waiting.containsKey(key)) {
                waiting.put(key, new ArrayList<>());
                prefetchLoader.submit(() -> {
                    boolean needed = !finished.contains(key);
                    download(asset, timeframe, key);
                    if (needed) {
                        pauseBetweenPrefetches();
                    }
                });
            }
            return new ArrayList<>();
        }
        return new ArrayList<>(cached);
    }

    /** Where the candles came from, e.g. "Binance". */
    public String getSourceName(Asset asset, Timeframe timeframe) {
        return sourceNames.getOrDefault(key(asset, timeframe), "loading...");
    }

    /** Loads in the background, then calls onLoaded on the UI thread. */
    public void loadAsync(Asset asset, Timeframe timeframe, Consumer<List<Candle>> onLoaded) {
        String key = key(asset, timeframe);
        if (cache.containsKey(key)) {
            onLoaded.accept(new ArrayList<>(cache.get(key)));
            return;
        }
        // If a download for the same candles is already running (e.g. a chart on another page
        // asked first), wait for it as well. Dropping this caller left Backtest stuck forever.
        waiting.computeIfAbsent(key, k -> new ArrayList<>()).add(onLoaded);
        // A page is waiting, so download now on the request threads instead of waiting behind
        // the background pre-fetch queue (which can hold dozens of slow downloads).
        if (requested.add(key)) {
            requestLoader.submit(() -> download(asset, timeframe, key));
        }
    }

    /** Runs on a download thread; hands the result to the UI thread. */
    /**
     * The market list asks for the history of every share (150+), so background downloads are
     * spaced out to stay well under the data sources' rate limits. Pages that wait for a chart
     * use the other queue and are not slowed down.
     */
    private static void pauseBetweenPrefetches() {
        try {
            Thread.sleep(PREFETCH_GAP_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void download(Asset asset, Timeframe timeframe, String key) {
        if (finished.contains(key)) {
            return;   // already downloaded by the other queue
        }
        Result result = loadBlocking(asset, timeframe);
        SwingUtilities.invokeLater(() -> {
            if (cache.containsKey(key)) {
                return;   // the other queue finished first
            }
            finished.add(key);
            cache.put(key, result.candles);
            sourceNames.put(key, result.source);
            requested.remove(key);
            List<Consumer<List<Candle>>> callbacks = waiting.remove(key);
            if (callbacks != null) {
                for (Consumer<List<Candle>> callback : callbacks) {
                    callback.accept(new ArrayList<>(result.candles));
                }
            }
        });
    }

    /** Tries each provider in turn and returns the first non-empty answer. */
    Result loadBlocking(Asset asset, Timeframe timeframe) {
        int count = timeframe.getDefaultCount();
        for (HistoryProvider provider : providers) {
            if (!provider.supports(asset)) {
                continue;
            }
            try {
                List<Candle> candles = provider.fetchCandles(asset, timeframe, count);
                if (!candles.isEmpty()) {
                    return new Result(new ArrayList<>(candles), provider.getName());
                }
            } catch (Exception e) {
                // this source failed: try the next one
            }
        }
        return new Result(new ArrayList<>(), "unavailable");
    }

    /** Small holder for "the candles + where they came from". */
    record Result(List<Candle> candles, String source) {
    }

    /** Every live price updates the newest candle, or starts a new one when its period is over. */
    @Override
    public void onPriceUpdate(Asset asset, double oldPrice, double newPrice) {
        long now = System.currentTimeMillis();
        for (Timeframe timeframe : Timeframe.values()) {
            List<Candle> candles = cache.get(key(asset, timeframe));
            if (candles == null || candles.isEmpty()) {
                continue;
            }
            Candle last = candles.get(candles.size() - 1);
            long period = timeframe.getMillis();
            if (now < last.time() + period) {
                candles.set(candles.size() - 1, last.withPrice(newPrice));
            } else {
                long start = last.time() + (now - last.time()) / period * period;
                candles.add(new Candle(start, last.close(), Math.max(last.close(), newPrice),
                        Math.min(last.close(), newPrice), newPrice, 0));
                if (candles.size() > MAX_CANDLES) {
                    candles.remove(0);
                }
            }
        }
    }
}
