package com.papertrader.strategy;

import com.papertrader.model.Candle;

import java.util.List;

/** Classic technical indicators, written out in plain loops so they are easy to explain. */
public final class Indicators {

    private Indicators() {
    }

    /** Simple Moving Average: the average close of the last `period` candles. */
    public static double sma(List<Candle> candles, int period) {
        double sum = 0;
        for (int i = candles.size() - period; i < candles.size(); i++) {
            sum += candles.get(i).close();
        }
        return sum / period;
    }

    /**
     * Relative Strength Index (0-100). Above 70 = "overbought", below 30 = "oversold".
     * RSI = 100 - 100 / (1 + averageGain / averageLoss) over the last `period` changes.
     */
    public static double rsi(List<Candle> candles, int period) {
        double gains = 0;
        double losses = 0;
        for (int i = candles.size() - period; i < candles.size(); i++) {
            double change = candles.get(i).close() - candles.get(i - 1).close();
            if (change > 0) {
                gains += change;
            } else {
                losses -= change;
            }
        }
        if (losses == 0) {
            return 100;
        }
        double rs = (gains / period) / (losses / period);
        return 100 - 100 / (1 + rs);
    }
}
