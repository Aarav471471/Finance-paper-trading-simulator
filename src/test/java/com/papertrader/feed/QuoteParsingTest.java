package com.papertrader.feed;

import org.junit.jupiter.api.Test;

import com.papertrader.model.Candle;

import java.util.List;
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

    @Test
    void readsBinanceKlines() {
        String json = "[[1731196800000,\"76000.0\",\"77000.5\",\"75500.0\",\"76800.2\",\"1234.5\",1731283199999,\"0\",1,\"0\",\"0\",\"0\"]]";
        List<Candle> candles = BinanceQuoteProvider.parseKlines(json);
        assertEquals(1, candles.size());
        assertEquals(77000.5, candles.get(0).high(), 0.001);
        assertEquals(76800.2, candles.get(0).close(), 0.001);
    }

    @Test
    void readsYahooCandlesAndSkipsGaps() {
        String json = "{\"chart\":{\"result\":[{\"meta\":{},\"timestamp\":[1000,2000,3000],"
                + "\"indicators\":{\"quote\":[{\"open\":[1,null,3],\"high\":[2,null,4],\"low\":[0.5,null,2.5],"
                + "\"close\":[1.5,null,3.5],\"volume\":[10,null,30]}]}}],\"error\":null}}";
        List<Candle> candles = YahooQuoteProvider.parseCandles(json);
        assertEquals(2, candles.size());
        assertEquals(3_000_000L, candles.get(1).time());
        assertEquals(3.5, candles.get(1).close(), 0.001);
    }
}
