package com.papertrader;

import com.papertrader.analytics.PerformanceReport;
import com.papertrader.backtest.BacktestEngine;
import com.papertrader.fees.FlatFeeCalculator;
import com.papertrader.model.Candle;
import com.papertrader.strategy.BuyAndHoldStrategy;
import com.papertrader.strategy.Indicators;
import com.papertrader.strategy.PriceRuleStrategy;
import com.papertrader.strategy.RsiStrategy;
import com.papertrader.strategy.Signal;
import com.papertrader.strategy.SmaCrossoverStrategy;
import com.papertrader.strategy.TradingStrategy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyAndBacktestTest {

    private static final long DAY = 86_400_000L;

    /** Daily candles whose closes are exactly the given prices. */
    private static List<Candle> candles(double... closes) {
        List<Candle> list = new ArrayList<>();
        for (int i = 0; i < closes.length; i++) {
            double c = closes[i];
            list.add(new Candle(i * DAY, c, c, c, c, 1000));
        }
        return list;
    }

    private static double[] ramp(double from, double step, int n) {
        double[] values = new double[n];
        for (int i = 0; i < n; i++) {
            values[i] = from + step * i;
        }
        return values;
    }

    @Test
    void smaIsTheAverageOfTheLastCloses() {
        assertEquals(4.0, Indicators.sma(candles(1, 2, 3, 4, 5), 3), 1e-9);
    }

    @Test
    void rsiIsHighWhenPricesOnlyRise() {
        assertEquals(100, Indicators.rsi(candles(ramp(10, 1, 20)), 14), 1e-9);
    }

    @Test
    void smaCrossoverBuysInAnUptrendAndSellsInADowntrend() {
        TradingStrategy sma = new SmaCrossoverStrategy(3, 6);
        assertEquals(Signal.BUY, sma.decide(candles(ramp(10, 1, 10)), false));
        assertEquals(Signal.SELL, sma.decide(candles(ramp(20, -1, 10)), true));
        assertEquals(Signal.HOLD, sma.decide(candles(1, 2), false));   // not enough data
    }

    @Test
    void rsiBuysWhenOversold() {
        assertEquals(Signal.BUY, new RsiStrategy(14, 30, 70).decide(candles(ramp(100, -2, 20)), false));
    }

    @Test
    void priceRuleFollowsTheUsersNumbers() {
        TradingStrategy rule = new PriceRuleStrategy(90, 110);
        assertEquals(Signal.BUY, rule.decide(candles(89), false));
        assertEquals(Signal.HOLD, rule.decide(candles(100), false));
        assertEquals(Signal.SELL, rule.decide(candles(111), true));
        assertThrows(IllegalArgumentException.class, () -> new PriceRuleStrategy(110, 90));
    }

    @Test
    void buyAndHoldBacktestMatchesTheMarketMove() {
        BacktestEngine engine = new BacktestEngine(candles(ramp(100, 1, 101)), new BuyAndHoldStrategy(),
                10_000, new FlatFeeCalculator(0), 0);
        engine.runToEnd();
        PerformanceReport report = engine.getReport();
        assertEquals(100.0, report.totalReturnPercent(), 0.01);       // 100 -> 200 = +100%
        assertEquals(100.0, engine.getBuyAndHoldReturnPercent(), 0.01);
        assertEquals(0, report.maxDrawdownPercent(), 1e-9);           // only went up
        assertEquals(1, engine.getMarkers().size());                  // bought once
    }

    @Test
    void replayCanBeSteppedOneCandleAtATime() {
        BacktestEngine engine = new BacktestEngine(candles(ramp(100, 1, 5)), new BuyAndHoldStrategy(),
                1000, new FlatFeeCalculator(0), 0);
        engine.step();
        engine.step();
        assertEquals(2, engine.getProgress());
        assertEquals(2, engine.getCandlesSoFar().size());
        assertTrue(engine.hasNext());
    }

    @Test
    void priceRuleBacktestRecordsAClosedWinningTrade() {
        BacktestEngine engine = new BacktestEngine(candles(100, 95, 89, 100, 111, 105), new PriceRuleStrategy(90, 110),
                1000, new FlatFeeCalculator(0), 0);
        engine.runToEnd();
        assertEquals(1, engine.getTrades().size());
        assertTrue(engine.getTrades().get(0).profit() > 0);
        assertEquals(100, engine.getReport().winRatePercent(), 1e-9);
    }
}
