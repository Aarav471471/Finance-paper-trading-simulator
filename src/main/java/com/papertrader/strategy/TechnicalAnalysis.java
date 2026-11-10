package com.papertrader.strategy;

import com.papertrader.model.Candle;

import java.util.List;

/**
 * Technical "consensus" like trading websites show it: several well-known indicators each vote
 * BUY, SELL or NEUTRAL, and the votes are counted. Everything is calculated from real candles.
 *
 * Votes: price above/below SMA and EMA (10, 20, 50, 100, 200), RSI(14), MACD vs its signal line,
 * Bollinger Bands(20, 2) and 10-day momentum.
 */
public final class TechnicalAnalysis {

    /** The result: indicator values plus the vote count. NaN means "not enough candles". */
    public record Report(double lastPrice, double rsi, double macd, double macdSignal,
                         double ema20, double ema50, double ema200,
                         double bollingerUpper, double bollingerLower,
                         int buys, int neutrals, int sells) {

        /** 0 = everything says sell, 1 = everything says buy. */
        public double score() {
            int total = buys + neutrals + sells;
            return total == 0 ? 0.5 : (buys + 0.5 * neutrals) / total;
        }

        public String verdict() {
            double s = score();
            if (s >= 0.75) return "STRONG BUY";
            if (s >= 0.58) return "BUY";
            if (s > 0.42) return "NEUTRAL";
            if (s > 0.25) return "SELL";
            return "STRONG SELL";
        }
    }

    private TechnicalAnalysis() {
    }

    public static Report analyze(List<Candle> candles) {
        int n = candles.size();
        if (n < 2) {
            return null;
        }
        double[] closes = new double[n];
        for (int i = 0; i < n; i++) {
            closes[i] = candles.get(i).close();
        }
        double last = closes[n - 1];
        int buys = 0;
        int sells = 0;
        int neutrals = 0;

        // 1. Moving averages: price above the average = uptrend = BUY
        for (int period : new int[]{10, 20, 50, 100, 200}) {
            if (n >= period) {
                double sma = sma(closes, period);
                double ema = ema(closes, period);
                if (last > sma) buys++; else sells++;
                if (last > ema) buys++; else sells++;
            }
        }

        // 2. RSI: below 30 oversold (BUY), above 70 overbought (SELL)
        double rsi = n > 14 ? Indicators.rsi(candles, 14) : Double.NaN;
        if (!Double.isNaN(rsi)) {
            if (rsi < 30) buys++; else if (rsi > 70) sells++; else neutrals++;
        }

        // 3. MACD = EMA12 - EMA26, signal = 9-period EMA of MACD
        double macd = Double.NaN;
        double signal = Double.NaN;
        if (n >= 35) {
            double[] macdLine = new double[n];
            double e12 = closes[0];
            double e26 = closes[0];
            for (int i = 0; i < n; i++) {
                e12 = closes[i] * (2.0 / 13) + e12 * (1 - 2.0 / 13);
                e26 = closes[i] * (2.0 / 27) + e26 * (1 - 2.0 / 27);
                macdLine[i] = e12 - e26;
            }
            macd = macdLine[n - 1];
            signal = ema(macdLine, 9);
            if (macd > signal) buys++; else sells++;
        }

        // 4. Bollinger Bands: average +/- 2 standard deviations of the last 20 closes
        double upper = Double.NaN;
        double lower = Double.NaN;
        if (n >= 20) {
            double mid = sma(closes, 20);
            double var = 0;
            for (int i = n - 20; i < n; i++) {
                var += (closes[i] - mid) * (closes[i] - mid);
            }
            double sd = Math.sqrt(var / 20);
            upper = mid + 2 * sd;
            lower = mid - 2 * sd;
            if (last < lower) buys++; else if (last > upper) sells++; else neutrals++;
        }

        // 5. Momentum: higher than 10 candles ago = BUY
        if (n > 10) {
            double momentum = last - closes[n - 11];
            if (momentum > 0) buys++; else if (momentum < 0) sells++; else neutrals++;
        }

        return new Report(last, rsi, macd, signal,
                n >= 20 ? ema(closes, 20) : Double.NaN,
                n >= 50 ? ema(closes, 50) : Double.NaN,
                n >= 200 ? ema(closes, 200) : Double.NaN,
                upper, lower, buys, neutrals, sells);
    }

    static double sma(double[] values, int period) {
        double sum = 0;
        for (int i = values.length - period; i < values.length; i++) {
            sum += values[i];
        }
        return sum / period;
    }

    /** Exponential moving average: newer values count more (weight 2 / (period + 1)). */
    static double ema(double[] values, int period) {
        double k = 2.0 / (period + 1);
        double ema = values[0];
        for (double v : values) {
            ema = v * k + ema * (1 - k);
        }
        return ema;
    }
}
