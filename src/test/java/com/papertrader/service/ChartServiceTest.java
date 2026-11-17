package com.papertrader.service;

import com.papertrader.feed.SimulatedHistoryProvider;
import com.papertrader.model.Asset;
import com.papertrader.model.Candle;
import com.papertrader.model.Stock;
import com.papertrader.model.Timeframe;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChartServiceTest {

    @Test
    void fallsBackToSimulatedHistoryEndingAtTheCurrentPrice() {
        Asset stock = new Stock("AAPL", "Apple", 200, "Tech");
        ChartService charts = new ChartService(List.of(new SimulatedHistoryProvider()));
        ChartService.Result result = charts.loadBlocking(stock, Timeframe.ONE_DAY);
        assertEquals("Simulated", result.source());
        assertEquals(365, result.candles().size());
        Candle last = result.candles().get(result.candles().size() - 1);
        assertEquals(200, last.close(), 1e-9);
        for (Candle c : result.candles()) {
            assertTrue(c.high() >= Math.max(c.open(), c.close()) - 1e-9);
            assertTrue(c.low() <= Math.min(c.open(), c.close()) + 1e-9);
        }
    }

    @Test
    void secondRequestWhileDownloadingStillGetsTheCandles() throws Exception {
        // Regression: Backtest asked for daily candles another page was already downloading,
        // its callback was dropped, and the page sat on "Downloading..." forever.
        Asset stock = new Stock("AAPL", "Apple", 200, "Tech");
        ChartService charts = new ChartService(List.of(new SimulatedHistoryProvider()));
        List<Integer> first = new CopyOnWriteArrayList<>();
        List<Integer> second = new CopyOnWriteArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            charts.loadAsync(stock, Timeframe.ONE_DAY, candles -> first.add(candles.size()));
            charts.loadAsync(stock, Timeframe.ONE_DAY, candles -> second.add(candles.size()));
        });
        for (int i = 0; i < 100 && (first.isEmpty() || second.isEmpty()); i++) {
            Thread.sleep(50);
            SwingUtilities.invokeAndWait(() -> { });
        }
        assertEquals(List.of(365), first);
        assertEquals(List.of(365), second);
    }

    @Test
    void liveTickUpdatesTheNewestCandle() {
        Candle c = new Candle(0, 100, 101, 99, 100, 10).withPrice(105);
        assertEquals(105, c.high(), 1e-9);
        assertEquals(105, c.close(), 1e-9);
        assertEquals(100, c.open(), 1e-9);
    }
}
