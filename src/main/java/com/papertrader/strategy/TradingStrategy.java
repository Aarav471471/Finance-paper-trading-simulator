package com.papertrader.strategy;

import com.papertrader.model.Candle;

import java.util.List;

/**
 * A trading rule. The SAME strategy objects are used in two places:
 *  - BacktestEngine replays history and asks the strategy what to do at each candle
 *  - TradingBot asks it on live candles and places real paper orders
 * STRATEGY pattern + RUNTIME POLYMORPHISM: callers only see this interface.
 */
public interface TradingStrategy {

    String getName();

    /** One sentence the UI shows to explain the rule. */
    String getDescription();

    /**
     * @param candles  all candles up to "now", oldest first
     * @param holding  true if we currently own the asset
     */
    Signal decide(List<Candle> candles, boolean holding);
}
