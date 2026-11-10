package com.papertrader.ui.components;

import com.papertrader.model.Timeframe;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Each chart range button must ask for a sensible candle size. */
class CandlestickRangeTest {

    @Test
    void rangesMapToCandleSizes() {
        assertEquals(Timeframe.FIVE_MINUTES, CandlestickChartPanel.candleSizeFor(CandlestickChartPanel.Timeframe.ONE_DAY));
        assertEquals(Timeframe.ONE_HOUR, CandlestickChartPanel.candleSizeFor(CandlestickChartPanel.Timeframe.ONE_WEEK));
        assertEquals(Timeframe.ONE_DAY, CandlestickChartPanel.candleSizeFor(CandlestickChartPanel.Timeframe.ONE_YEAR));
        assertEquals(Timeframe.ONE_WEEK, CandlestickChartPanel.candleSizeFor(CandlestickChartPanel.Timeframe.FIVE_YEARS));
    }
}
