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

    /** Starts pricing an asset that was added after start (a share the user looked up). */
    default void track(com.papertrader.model.Asset asset) {
    }

    /**
     * Symbols to keep the freshest (the share on screen, the user's holdings). Feeds that can
     * only refresh some assets at a time refresh these every time. Ignored by default.
     */
    default void setPrioritySymbols(java.util.Collection<String> symbols) {
    }

    /** One line for the UI saying where prices come from, e.g. "Binance: live 16:30:05". */
    String getStatusText();
}
