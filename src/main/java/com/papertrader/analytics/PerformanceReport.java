package com.papertrader.analytics;

/**
 * All risk / performance numbers for one equity curve.
 * Double.NaN means "not enough data yet"; Double.POSITIVE_INFINITY means "no losses at all".
 */
public record PerformanceReport(
        double totalReturnPercent,
        double sharpeRatio,
        double sortinoRatio,
        double maxDrawdownPercent,
        double winRatePercent,
        double profitFactor,
        int closedTrades) {
}
