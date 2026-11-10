package com.papertrader;

import com.papertrader.analytics.TradeStats;
import com.papertrader.model.OrderSide;
import com.papertrader.model.Transaction;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeStatsTest {

    private static Transaction buy(int id, String sym, int qty, double price, LocalDateTime t) {
        return new Transaction(id, "u", t, sym, OrderSide.BUY, "MARKET", qty, price, 1.0);
    }

    private static Transaction sell(int id, String sym, int qty, double price, double pnl, LocalDateTime t) {
        return new Transaction(id, "u", t, sym, OrderSide.SELL, "MARKET", qty, price, 1.0, pnl);
    }

    @Test
    void countsWinsLossesStreaksAndFees() {
        LocalDateTime d = LocalDateTime.of(2026, 1, 5, 10, 0);
        TradeStats s = TradeStats.of(List.of(
                buy(1, "AAPL", 10, 100, d),
                sell(2, "AAPL", 2, 110, 20, d.plusDays(1)),
                sell(3, "AAPL", 2, 115, 30, d.plusDays(2)),
                sell(4, "AAPL", 2, 95, -10, d.plusDays(2))));

        assertEquals(4, s.trades);
        assertEquals(1, s.buys);
        assertEquals(3, s.sells);
        assertEquals(4.0, s.fees, 1e-9);
        assertEquals(40.0, s.realized, 1e-9);
        assertEquals(2, s.wins);
        assertEquals(1, s.losses);
        assertEquals(2, s.maxWinStreak);
        assertEquals(1, s.maxLossStreak);
        assertEquals(5.0, s.profitFactor(), 1e-9);          // 50 won / 10 lost
        assertEquals(2, s.dailyPnl.size());                // two different days with sells
        assertEquals(List.of(20.0, 50.0, 40.0), s.cumulativePnl);
    }

    @Test
    void fifoHoldingTimeAndLongTermSplit() {
        LocalDateTime d = LocalDateTime.of(2024, 1, 1, 10, 0);
        TradeStats s = TradeStats.of(List.of(
                buy(1, "TCS", 5, 100, d),                         // old lot
                buy(2, "TCS", 5, 100, d.plusDays(400)),           // new lot
                sell(3, "TCS", 10, 120, 200, d.plusDays(410))));   // sells both lots

        // 5 shares held 410 days + 5 shares held 10 days = 210 days on average
        assertEquals(210.0, s.avgHoldingDays, 1e-6);
        assertEquals(100.0, s.longTermGains, 1e-9);   // half the shares were long-term
        assertEquals(100.0, s.shortTermGains, 1e-9);
        assertTrue(Double.isInfinite(s.profitFactor()));
    }
}
