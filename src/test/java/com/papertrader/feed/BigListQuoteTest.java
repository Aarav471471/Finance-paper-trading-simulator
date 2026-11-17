package com.papertrader.feed;

import com.papertrader.interfaces.QuoteProvider;
import com.papertrader.model.Asset;
import com.papertrader.model.Stock;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 150+ Indian shares: batch quotes from Yahoo, and refreshing in turns when one request per share is needed. */
class BigListQuoteTest {

    @Test
    void readsYahooBatchAnswerWithChartResults() {
        String json = "{\"spark\":{\"result\":[{\"symbol\":\"JIOFIN.NS\",\"response\":[{\"meta\":{\"symbol\":\"JIOFIN.NS\","
                + "\"regularMarketPrice\":312.5,\"chartPreviousClose\":305.0}}]},"
                + "{\"symbol\":\"M&M.NS\",\"response\":[{\"meta\":{\"regularMarketPrice\":3600,\"previousClose\":3550}}]}],\"error\":null}}";
        Map<String, Quote> quotes = YahooQuoteProvider.parseSpark(json);
        assertEquals(312.5, quotes.get("JIOFIN.NS").price(), 0.001);
        assertEquals(305.0, quotes.get("JIOFIN.NS").previousClose(), 0.001);
        assertEquals(3550, quotes.get("M&M.NS").previousClose(), 0.001);
    }

    @Test
    void readsYahooBatchAnswerWithCloseLists() {
        String json = "{\"SWIGGY.NS\":{\"symbol\":\"SWIGGY.NS\",\"close\":[410.0,null],\"chartPreviousClose\":400.0},"
                + "\"NOPE.NS\":{\"symbol\":\"NOPE.NS\",\"close\":[]}}";
        Map<String, Quote> quotes = YahooQuoteProvider.parseSpark(json);
        assertEquals(1, quotes.size());
        assertEquals(410.0, quotes.get("SWIGGY.NS").price(), 0.001);
    }

    @Test
    void readsALookedUpListing() {
        String json = "{\"chart\":{\"result\":[{\"meta\":{\"symbol\":\"IRCON.NS\",\"longName\":\"Ircon International Limited\","
                + "\"fullExchangeName\":\"NSE\",\"regularMarketPrice\":180.2,\"chartPreviousClose\":178.0}}],\"error\":null}}";
        YahooQuoteProvider.Listing listing = YahooQuoteProvider.parseListing(json);
        assertEquals("Ircon International Limited", listing.name());
        assertEquals(180.2, listing.quote().price(), 0.001);
        assertEquals(null, YahooQuoteProvider.parseListing("{\"chart\":{\"result\":null,\"error\":{\"code\":\"Not Found\"}}}"));
    }

    /** Answers any share, at most 10 per call, and remembers what it was asked. */
    private static class LimitedProvider implements QuoteProvider {
        final List<List<String>> calls = new ArrayList<>();

        @Override
        public String getName() {
            return "Limited";
        }

        @Override
        public boolean supports(Asset asset) {
            return asset instanceof Stock;
        }

        @Override
        public Map<String, Quote> fetchQuotes(List<Asset> assets) {
            List<String> asked = new ArrayList<>();
            Map<String, Quote> quotes = new HashMap<>();
            for (Asset a : assets) {
                asked.add(a.getSymbol());
                quotes.put(a.getSymbol(), new Quote(50, 49));
            }
            calls.add(asked);
            return quotes;
        }

        @Override
        public int getRefreshSeconds() {
            return 60;
        }

        @Override
        public int maxSymbolsPerRefresh() {
            return 10;
        }
    }

    @Test
    void limitedProviderRefreshesEveryShareInTurnsWithPriorityFirst() {
        List<Asset> stocks = new ArrayList<>();
        for (int i = 0; i < 35; i++) {
            stocks.add(new Stock("S" + i, "Share " + i, 10, "Test"));
        }
        LimitedProvider provider = new LimitedProvider();
        LivePriceFeed feed = new LivePriceFeed(stocks, List.of(provider));
        feed.setPrioritySymbols(Set.of("S30"));

        Set<String> covered = new HashSet<>();
        for (int round = 0; round < 4; round++) {
            List<Asset> batch = feed.nextBatch(provider, stocks);
            assertEquals(10, batch.size());
            assertTrue(batch.stream().anyMatch(a -> a.getSymbol().equals("S30")), "priority share in every round");
            batch.forEach(a -> covered.add(a.getSymbol()));
        }
        assertEquals(35, covered.size(), "every share refreshed within 4 rounds");
    }

    @Test
    void initialLoadOnlyAsksForOneRoundOfALimitedProvider() {
        List<Asset> stocks = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            stocks.add(new Stock("S" + i, "Share " + i, 10, "Test"));
        }
        LimitedProvider provider = new LimitedProvider();
        assertTrue(new LivePriceFeed(stocks, List.of(provider)).loadInitialPrices());
        assertEquals(1, provider.calls.size());
        assertEquals(10, provider.calls.get(0).size());
        assertEquals(50, stocks.get(0).getPrice(), 0.001);
    }
}
