package com.papertrader;

import com.papertrader.analytics.EquityPoint;
import com.papertrader.analytics.PerformanceAnalyzer;
import com.papertrader.analytics.PerformanceReport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerformanceAnalyzerTest {

    private static final long DAY = 86_400_000L;

    @Test
    void maxDrawdownIsTheBiggestFallFromAPeak() {
        List<EquityPoint> curve = List.of(new EquityPoint(0, 100), new EquityPoint(DAY, 120),
                new EquityPoint(2 * DAY, 90), new EquityPoint(3 * DAY, 130));
        assertEquals(25.0, PerformanceAnalyzer.maxDrawdown(curve), 1e-9);   // 120 -> 90
    }

    @Test
    void winRateAndProfitFactor() {
        PerformanceReport r = PerformanceAnalyzer.analyze(List.of(new EquityPoint(0, 100), new EquityPoint(DAY, 110)),
                List.of(30.0, -10.0, 20.0, -10.0));
        assertEquals(50.0, r.winRatePercent(), 1e-9);
        assertEquals(2.5, r.profitFactor(), 1e-9);   // won 50, lost 20
        assertEquals(4, r.closedTrades());
    }

    @Test
    void steadyGainsGiveAPositiveSharpeAndNoLossesGiveInfiniteProfitFactor() {
        List<EquityPoint> curve = List.of(new EquityPoint(0, 100), new EquityPoint(DAY, 101),
                new EquityPoint(2 * DAY, 103), new EquityPoint(3 * DAY, 104), new EquityPoint(4 * DAY, 106));
        PerformanceReport r = PerformanceAnalyzer.analyze(curve, List.of(5.0));
        assertTrue(r.sharpeRatio() > 0);
        assertTrue(Double.isInfinite(r.profitFactor()));
        assertEquals("n/a", PerformanceAnalyzer.format(Double.NaN, ""));
    }
}
