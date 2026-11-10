package com.papertrader.analytics;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns an equity curve (account value over time) and a list of closed-trade profits
 * into the standard performance metrics used by fund managers.
 *
 *  Sharpe  = average return / standard deviation of returns         (reward per unit of risk)
 *  Sortino = average return / standard deviation of NEGATIVE returns (only "bad" risk counts)
 *            both use DAILY returns (the curve is first reduced to one value per day) and are
 *            annualised: multiplied by sqrt(days per year). Risk-free rate is taken as 0.
 *  Max drawdown = biggest fall from a peak to a later low, in %
 *  Win rate     = winning trades / all closed trades
 *  Profit factor = total money won / total money lost
 */
public final class PerformanceAnalyzer {

    private static final double MILLIS_PER_YEAR = 365.25 * 24 * 3600 * 1000;

    private PerformanceAnalyzer() {
    }

    public static PerformanceReport analyze(List<EquityPoint> equity, List<Double> tradeProfits) {
        double totalReturn = Double.NaN;
        double sharpe = Double.NaN;
        double sortino = Double.NaN;
        double maxDrawdown = 0;

        if (equity.size() >= 2) {
            double first = equity.get(0).value();
            double last = equity.get(equity.size() - 1).value();
            totalReturn = (last / first - 1) * 100;
            maxDrawdown = maxDrawdown(equity);
        }

        List<EquityPoint> daily = toDaily(equity);
        if (daily.size() >= 3) {   // needs at least 2 daily returns
            int n = daily.size() - 1;
            double[] returns = new double[n];
            double sum = 0;
            for (int i = 0; i < n; i++) {
                returns[i] = daily.get(i + 1).value() / daily.get(i).value() - 1;
                sum += returns[i];
            }
            double mean = sum / n;

            double squares = 0;
            double downsideSquares = 0;
            for (double r : returns) {
                squares += (r - mean) * (r - mean);
                downsideSquares += Math.min(r, 0) * Math.min(r, 0);
            }
            double stdDev = Math.sqrt(squares / (n - 1));
            double downsideDev = Math.sqrt(downsideSquares / n);

            // how many of these periods fit in a year (e.g. ~365 for daily data)
            double years = (daily.get(n).time() - daily.get(0).time()) / MILLIS_PER_YEAR;
            double periodsPerYear = years > 0 ? n / years : 252;
            double annualise = Math.sqrt(periodsPerYear);

            sharpe = stdDev > 0 ? mean / stdDev * annualise : Double.NaN;
            sortino = downsideDev > 0 ? mean / downsideDev * annualise : Double.NaN;
        }

        int wins = 0;
        double grossProfit = 0;
        double grossLoss = 0;
        for (double p : tradeProfits) {
            if (p > 0) {
                wins++;
                grossProfit += p;
            } else {
                grossLoss -= p;
            }
        }
        int trades = tradeProfits.size();
        double winRate = trades == 0 ? Double.NaN : 100.0 * wins / trades;
        double profitFactor;
        if (trades == 0) {
            profitFactor = Double.NaN;
        } else if (grossLoss == 0) {
            profitFactor = Double.POSITIVE_INFINITY;
        } else {
            profitFactor = grossProfit / grossLoss;
        }

        return new PerformanceReport(totalReturn, sharpe, sortino, maxDrawdown, winRate, profitFactor, trades);
    }

    /** Keeps only the last value of each day, so many snapshots in one day count as one daily close. */
    public static List<EquityPoint> toDaily(List<EquityPoint> equity) {
        List<EquityPoint> daily = new ArrayList<>();
        long dayMillis = 86_400_000L;
        for (EquityPoint p : equity) {
            if (!daily.isEmpty() && daily.get(daily.size() - 1).time() / dayMillis == p.time() / dayMillis) {
                daily.set(daily.size() - 1, p);   // same day: replace with the later value
            } else {
                daily.add(p);
            }
        }
        return daily;
    }

    /** Largest % drop from any peak to any later point. */
    public static double maxDrawdown(List<EquityPoint> equity) {
        double peak = equity.get(0).value();
        double worst = 0;
        for (EquityPoint p : equity) {
            peak = Math.max(peak, p.value());
            worst = Math.max(worst, (peak - p.value()) / peak * 100);
        }
        return worst;
    }

    /** Formats a ratio for the UI: "n/a" when there is not enough data, "∞" when there are no losses. */
    public static String format(double value, String suffix) {
        if (Double.isNaN(value)) {
            return "n/a";
        }
        if (Double.isInfinite(value)) {
            return "∞";
        }
        return String.format("%.2f", value) + suffix;
    }
}
