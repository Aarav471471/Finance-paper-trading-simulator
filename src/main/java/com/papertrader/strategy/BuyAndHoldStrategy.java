package com.papertrader.strategy;

import com.papertrader.model.Candle;

import java.util.List;

/** The simplest benchmark: buy at the start and never sell. */
public class BuyAndHoldStrategy implements TradingStrategy {

    @Override
    public String getName() {
        return "Buy & Hold";
    }

    @Override
    public String getDescription() {
        return "Buy once at the start and keep it. Every other strategy should try to beat this.";
    }

    @Override
    public Signal decide(List<Candle> candles, boolean holding) {
        return holding ? Signal.HOLD : Signal.BUY;
    }
}
