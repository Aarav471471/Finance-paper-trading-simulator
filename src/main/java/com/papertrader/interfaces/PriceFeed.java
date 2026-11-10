package com.papertrader.interfaces;

/**
 * ABSTRACTION of "a source of live prices".
 * Today the app uses SimulatedPriceFeed (random prices). A real stock API feed could be
 * written later by implementing this same interface, and nothing else would need to change.
 */
public interface PriceFeed {
    void addListener(PriceListener listener);

    void removeListener(PriceListener listener);

    void start();

    void stop();
}
