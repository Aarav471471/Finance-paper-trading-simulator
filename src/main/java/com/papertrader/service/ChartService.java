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
    private final Set<String> loading = new HashSet<>();
    private final ExecutorService loader = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "history-download");
        t.setDaemon(true);
        return t;
    });

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
            loadAsync(asset, timeframe, candles -> { });
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
        if (!loading.add(key)) {
            return;   // already downloading
        }
        loader.submit(() -> {
            Result result = loadBlocking(asset, timeframe);
            SwingUtilities.invokeLater(() -> {
                cache.put(key, result.candles);
                sourceNames.put(key, result.source);
                loading.remove(key);
                onLoaded.accept(new ArrayList<>(result.candles));
            });
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
