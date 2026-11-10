package com.papertrader.feed;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Checks that each provider reads its API's JSON format correctly (sample answers from the API docs). */
class QuoteParsingTest {

    @Test
    void readsFinnhubQuote() {
        Quote q = FinnhubQuoteProvider.parse("{\"c\":271.4,\"d\":1.2,\"dp\":0.44,\"h\":272,\"l\":268,\"o\":269,\"pc\":270.2,\"t\":1731250000}");
        assertEquals(271.4, q.price(), 0.001);
        assertEquals(270.2, q.previousClose(), 0.001);
    }

    @Test
    void finnhubUnknownSymbolGivesNull() {
        assertNull(FinnhubQuoteProvider.parse("{\"c\":0,\"d\":null,\"dp\":null,\"h\":0,\"l\":0,\"o\":0,\"pc\":0,\"t\":0}"));
    }

    @Test
    void readsYahooChartMeta() {
        String json = "{\"chart\":{\"result\":[{\"meta\":{\"currency\":\"USD\",\"symbol\":\"AAPL\","
                + "\"regularMarketPrice\":271.4,\"chartPreviousClose\":270.2}}],\"error\":null}}";
        Quote q = YahooQuoteProvider.parse(json);
        assertEquals(271.4, q.price(), 0.001);
        assertEquals(270.2, q.previousClose(), 0.001);
    }

    @Test
    void readsBinanceTickers() {
        String json = "[{\"symbol\":\"BTCUSDT\",\"lastPrice\":\"68012.10\",\"openPrice\":\"67100.00\"},"
                + "{\"symbol\":\"ETHUSDT\",\"lastPrice\":\"3501.5\",\"openPrice\":\"3450.0\"}]";
        Map<String, Quote> quotes = BinanceQuoteProvider.parse(json);
        assertEquals(68012.10, quotes.get("BTCUSDT").price(), 0.001);
        assertEquals(3450.0, quotes.get("ETHUSDT").previousClose(), 0.001);
    }
}
