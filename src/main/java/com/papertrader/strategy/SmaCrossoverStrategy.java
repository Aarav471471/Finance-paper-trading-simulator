package com.papertrader.strategy;

import com.papertrader.model.Candle;

import java.util.List;

/** Trend following: be in the market while the short average is above the long average. */
public class SmaCrossoverStrategy implements TradingStrategy {

    private final int shortPeriod;
    private final int longPeriod;

    public SmaCrossoverStrategy(int shortPeriod, int longPeriod) {
        if (shortPeriod >= longPeriod) {
            throw new IllegalArgumentException("Short period must be smaller than long period");
        }
        this.shortPeriod = shortPeriod;
        this.longPeriod = longPeriod;
    }

    @Override
    public String getName() {
        return "SMA Crossover (" + shortPeriod + "/" + longPeriod + ")";
    }

    @Override
    public String getDescription() {
        return "Buy when the " + shortPeriod + "-candle average rises above the " + longPeriod
                + "-candle average (uptrend), sell when it falls below.";
    }

    @Override
    public Signal decide(List<Candle> candles, boolean holding) {
        if (candles.size() < longPeriod) {
            return Signal.HOLD;   // not enough data yet
        }
        double fast = Indicators.sma(candles, shortPeriod);
        double slow = Indicators.sma(candles, longPeriod);
        if (fast > slow && !holding) {
            return Signal.BUY;
        }
        if (fast < slow && holding) {
            return Signal.SELL;
        }
        return Signal.HOLD;
    }
}
