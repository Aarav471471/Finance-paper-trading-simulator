package com.papertrader.interfaces;

import com.papertrader.feed.Quote;
import com.papertrader.model.Asset;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * A website/API that can give real prices for some kinds of assets.
 * Implemented by FinnhubQuoteProvider and YahooQuoteProvider (stocks + ETFs)
 * and BinanceQuoteProvider (crypto). LivePriceFeed only knows this interface.
 */
public interface QuoteProvider {

    /** Name shown in the UI, e.g. "Binance". */
    String getName();

    /** Can this provider price this asset? */
    boolean supports(Asset asset);

    /** Downloads the latest quotes. The map key is the asset symbol, e.g. "AAPL". */
    Map<String, Quote> fetchQuotes(List<Asset> assets) throws IOException;

    /** How often to ask, in seconds (free plans have rate limits). */
    int getRefreshSeconds();
}
