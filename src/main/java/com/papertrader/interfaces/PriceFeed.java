package com.papertrader.interfaces;

/**
 * ABSTRACTION of "a source of live prices".
 * Two implementations: LivePriceFeed (real prices from APIs) and SimulatedPriceFeed
 * (random prices, works offline). App.java picks one; nothing else in the app knows which.
 */
public interface PriceFeed {
    void addListener(PriceListener listener);

    void removeListener(PriceListener listener);

    void start();

    void stop();

    /** One line for the UI saying where prices come from, e.g. "Binance: live 16:30:05". */
    String getStatusText();
}
