package com.papertrader.interfaces;

import com.papertrader.model.Asset;
import com.papertrader.model.Candle;
import com.papertrader.model.Timeframe;

import java.io.IOException;
import java.util.List;

/**
 * A source of past prices (candles), used for candlestick charts and backtesting.
 * BinanceQuoteProvider and YahooQuoteProvider implement BOTH QuoteProvider and HistoryProvider:
 * a Java class can implement many interfaces. FinnhubQuoteProvider does not, because Finnhub's
 * free plan has no history (Interface Segregation: nobody is forced to implement what it can't do).
 */
public interface HistoryProvider {

    String getName();

    boolean supports(Asset asset);

    /** The most recent candles, oldest first. */
    List<Candle> fetchCandles(Asset asset, Timeframe timeframe, int count) throws IOException;
}
