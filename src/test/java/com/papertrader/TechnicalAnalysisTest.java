package com.papertrader;

import com.papertrader.model.Candle;
import com.papertrader.strategy.TechnicalAnalysis;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TechnicalAnalysisTest {

    private static List<Candle> trend(double start, double step, int count) {
        List<Candle> candles = new ArrayList<>();
        double p = start;
        for (int i = 0; i < count; i++) {
            candles.add(new Candle(i * 86_400_000L, p, p + 1, p - 1, p, 1000));
            p += step;
        }
        return candles;
    }

    @Test
    void steadyUptrendIsABuy() {
        TechnicalAnalysis.Report r = TechnicalAnalysis.analyze(trend(100, 1, 250));
        assertTrue(r.buys() > r.sells());
        assertTrue(r.verdict().contains("BUY"));
        assertEquals(100.0, r.rsi(), 1e-9);   // only gains -> RSI 100
    }

    @Test
    void steadyDowntrendIsASell() {
        TechnicalAnalysis.Report r = TechnicalAnalysis.analyze(trend(400, -1, 250));
        assertTrue(r.sells() > r.buys());
        assertTrue(r.verdict().contains("SELL"));
    }
}
