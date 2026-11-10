package com.papertrader.strategy;

import com.papertrader.model.Candle;

import java.util.List;

/** Mean reversion: buy when the asset is oversold, sell when it is overbought. */
public class RsiStrategy implements TradingStrategy {

    private final int period;
    private final double oversold;
    private final double overbought;

    public RsiStrategy(int period, double oversold, double overbought) {
        this.period = period;
        this.oversold = oversold;
        this.overbought = overbought;
    }

    @Override
    public String getName() {
        return "RSI (" + period + ", " + (int) oversold + "/" + (int) overbought + ")";
    }

    @Override
    public String getDescription() {
        return "Buy when RSI drops below " + (int) oversold + " (oversold), sell when it rises above "
                + (int) overbought + " (overbought).";
    }

    @Override
    public Signal decide(List<Candle> candles, boolean holding) {
        if (candles.size() <= period) {
            return Signal.HOLD;
        }
        double rsi = Indicators.rsi(candles, period);
        if (rsi < oversold && !holding) {
            return Signal.BUY;
        }
        if (rsi > overbought && holding) {
            return Signal.SELL;
        }
        return Signal.HOLD;
    }
}
