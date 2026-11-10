package com.papertrader.analytics;

import com.papertrader.model.OrderSide;
import com.papertrader.model.Transaction;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Statistics calculated from a user's real transaction list (used by the History page).
 *
 * <p>Buys and sells are matched FIFO (first in, first out) per symbol to know how long each
 * share was held. A SELL's realized P&L is already saved on the transaction.</p>
 */
public final class TradeStats {

    /** Shares held longer than this count as long-term (Indian equity rule: 12 months). */
    public static final int LONG_TERM_DAYS = 365;

    public int trades;
    public int todayTrades;
    public int buys;
    public int sells;
    public double buyGross;
    public double sellGross;
    public double fees;
    public double realized;

    public int wins;
    public int losses;
    public int even;
    public double grossWins;    // sum of winning sells (positive)
    public double grossLosses;  // sum of losing sells (negative)
    public double bestTrade;
    public double worstTrade;
    public int maxWinStreak;
    public int maxLossStreak;

    /** Average holding period of the sold shares in days (NaN if nothing sold). */
    public double avgHoldingDays = Double.NaN;
    public double shortTermGains;
    public double longTermGains;

    /** Realized P&L per calendar day (oldest first). */
    public final TreeMap<LocalDate, Double> dailyPnl = new TreeMap<>();
    /** Realized P&L per symbol. */
    public final Map<String, Double> pnlBySymbol = new LinkedHashMap<>();
    /** Running total of realized P&L after each SELL. */
    public final List<Double> cumulativePnl = new ArrayList<>();

    private TradeStats() {
    }

    public static TradeStats of(List<Transaction> transactions) {
        TradeStats s = new TradeStats();
        LocalDate today = LocalDate.now();
        Map<String, Deque<double[]>> lots = new HashMap<>();   // symbol -> {qty, buy time in epoch seconds}
        double heldShareDays = 0;
        int soldShares = 0;
        int winRun = 0;
        int lossRun = 0;
        double running = 0;

        List<Transaction> sorted = new ArrayList<>(transactions);
        sorted.sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));
        for (Transaction t : sorted) {
            s.trades++;
            s.fees += t.getFee();
            if (t.getTimestamp().toLocalDate().equals(today)) {
                s.todayTrades++;
            }
            double gross = t.getPrice() * t.getQuantity();
            long time = seconds(t.getTimestamp());
            if (t.getSide() == OrderSide.BUY) {
                s.buys++;
                s.buyGross += gross;
                lots.computeIfAbsent(t.getSymbol(), k -> new ArrayDeque<>()).add(new double[]{t.getQuantity(), time});
                continue;
            }

            // ---- SELL
            s.sells++;
            s.sellGross += gross;
            double pnl = t.getRealizedPnl();
            s.realized += pnl;
            running += pnl;
            s.cumulativePnl.add(running);
            s.dailyPnl.merge(t.getTimestamp().toLocalDate(), pnl, Double::sum);
            s.pnlBySymbol.merge(t.getSymbol(), pnl, Double::sum);

            if (pnl > 0) {
                s.wins++;
                s.grossWins += pnl;
                winRun++;
                lossRun = 0;
            } else if (pnl < 0) {
                s.losses++;
                s.grossLosses += pnl;
                lossRun++;
                winRun = 0;
            } else {
                s.even++;
            }
            s.maxWinStreak = Math.max(s.maxWinStreak, winRun);
            s.maxLossStreak = Math.max(s.maxLossStreak, lossRun);
            s.bestTrade = Math.max(s.bestTrade, pnl);
            s.worstTrade = Math.min(s.worstTrade, pnl);

            // FIFO: take the oldest shares first and measure how long they were held
            int left = t.getQuantity();
            int longQty = 0;
            Deque<double[]> queue = lots.getOrDefault(t.getSymbol(), new ArrayDeque<>());
            while (left > 0 && !queue.isEmpty()) {
                double[] lot = queue.peekFirst();
                int used = (int) Math.min(left, lot[0]);
                double days = (time - lot[1]) / 86_400.0;
                heldShareDays += used * days;
                soldShares += used;
                if (days >= LONG_TERM_DAYS) {
                    longQty += used;
                }
                lot[0] -= used;
                left -= used;
                if (lot[0] <= 0) {
                    queue.pollFirst();
                }
            }
            double longShare = t.getQuantity() > 0 ? longQty / (double) t.getQuantity() : 0;
            s.longTermGains += pnl * longShare;
            s.shortTermGains += pnl * (1 - longShare);
        }
        if (soldShares > 0) {
            s.avgHoldingDays = heldShareDays / soldShares;
        }
        return s;
    }

    private static long seconds(LocalDateTime t) {
        return Duration.between(LocalDateTime.of(2000, 1, 1, 0, 0), t).getSeconds();
    }

    /** Wins / (wins + losses) in %, NaN if there are no closed trades with a result. */
    public double winRate() {
        int n = wins + losses;
        return n == 0 ? Double.NaN : wins * 100.0 / n;
    }

    /** Total won / total lost; infinity when nothing was lost, NaN when there are no closed trades. */
    public double profitFactor() {
        if (grossLosses == 0) {
            return grossWins > 0 ? Double.POSITIVE_INFINITY : Double.NaN;
        }
        return grossWins / -grossLosses;
    }

    public double avgWin() {
        return wins == 0 ? Double.NaN : grossWins / wins;
    }

    public double avgLoss() {
        return losses == 0 ? Double.NaN : grossLosses / losses;
    }

    /** Average win divided by average loss (the "reward" for each unit of "risk"). */
    public double rewardToRisk() {
        double w = avgWin();
        double l = avgLoss();
        return Double.isNaN(w) || Double.isNaN(l) || l == 0 ? Double.NaN : w / -l;
    }
}
