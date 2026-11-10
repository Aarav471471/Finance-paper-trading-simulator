package com.papertrader.feed;

import com.papertrader.interfaces.PriceFeed;
import com.papertrader.interfaces.PriceListener;
import com.papertrader.model.Asset;

import javax.swing.Timer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates fake but realistic-looking prices using a "random walk":
 * every tick, each price moves up or down by a small random percentage.
 * How big the move is depends on asset.getVolatility(), which is different for
 * Stock, Crypto and ETF (runtime polymorphism).
 *
 * A Swing Timer is used so ticks run on the Swing UI thread. That means the UI and
 * the trading engine never run at the same time, so no locking is needed.
 */
public class SimulatedPriceFeed implements PriceFeed {

    private final List<Asset> assets;
    private final List<PriceListener> listeners = new ArrayList<>();
    private final Random random;
    private final Timer timer;

    public SimulatedPriceFeed(List<Asset> assets, int intervalMillis) {
        this(assets, intervalMillis, new Random());
    }

    /** Constructor that accepts a Random, so tests can use a fixed seed. */
    public SimulatedPriceFeed(List<Asset> assets, int intervalMillis, Random random) {
        this.assets = assets;
        this.random = random;
        this.timer = new Timer(intervalMillis, event -> tick());
    }

    @Override
    public void addListener(PriceListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(PriceListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void start() {
        timer.start();
    }

    @Override
    public void stop() {
        timer.stop();
    }

    /** One step of the simulation: move every price and tell all listeners. */
    public void tick() {
        for (Asset asset : assets) {
            double oldPrice = asset.getPrice();
            double percentMove = random.nextGaussian() * asset.getVolatility();
            double newPrice = Math.max(0.01, oldPrice * (1 + percentMove));
            newPrice = Math.round(newPrice * 100) / 100.0;   // keep 2 decimal places
            asset.setPrice(newPrice);
            notifyListeners(asset, oldPrice, newPrice);
        }
    }

    private void notifyListeners(Asset asset, double oldPrice, double newPrice) {
        // copy the list so a listener may safely add/remove listeners while we loop
        for (PriceListener listener : new ArrayList<>(listeners)) {
            listener.onPriceUpdate(asset, oldPrice, newPrice);
        }
    }
}
