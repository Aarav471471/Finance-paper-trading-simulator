package com.papertrader.feed;

import com.papertrader.interfaces.QuoteProvider;
import com.papertrader.model.Asset;
import com.papertrader.model.Crypto;
import com.papertrader.model.ETF;
import com.papertrader.model.Stock;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests LivePriceFeed WITHOUT the internet by giving it a fake provider.
 * Possible only because LivePriceFeed depends on the QuoteProvider interface (Dependency Inversion).
 */
class LivePriceFeedTest {

    /** A pretend API that says every stock costs 123.45 and closed yesterday at 120. */
    private static class FakeStockProvider implements QuoteProvider {
        @Override
        public String getName() {
            return "Fake";
        }

        @Override
        public boolean supports(Asset asset) {
            return asset instanceof Stock;
        }

        @Override
        public Map<String, Quote> fetchQuotes(List<Asset> assets) throws IOException {
            Map<String, Quote> quotes = new HashMap<>();
            for (Asset a : assets) {
                quotes.put(a.getSymbol(), new Quote(123.45, 120));
            }
            return quotes;
        }

        @Override
        public int getRefreshSeconds() {
            return 60;
        }
    }

    private static class BrokenProvider extends FakeStockProvider {
        @Override
        public Map<String, Quote> fetchQuotes(List<Asset> assets) throws IOException {
            throw new IOException("no internet");
        }
    }

    @Test
    void initialLoadSetsRealPriceAndPreviousClose() {
        Stock stock = new Stock("AAPL", "Apple", 190, "Tech");
        Crypto coin = new Crypto("BTC", "Bitcoin", 68000, "Bitcoin");
        LivePriceFeed feed = new LivePriceFeed(List.of(stock, coin), List.of(new FakeStockProvider()));

        assertTrue(feed.loadInitialPrices());
        assertEquals(123.45, stock.getPrice(), 0.001);
        assertEquals(120, stock.getPreviousClose(), 0.001);
        assertEquals(68000, coin.getPrice(), 0.001);   // no provider for crypto -> unchanged (simulated)
        assertTrue(feed.getStatusText().contains("Simulated"));
    }

    @Test
    void offlineKeepsOldPricesAndReportsIt() {
        ETF etf = new ETF("SPY", "S&P 500", 520, "S&P 500");
        Stock stock = new Stock("MSFT", "Microsoft", 420, "Tech");
        LivePriceFeed feed = new LivePriceFeed(List.of(etf, stock), List.of(new BrokenProvider()));

        assertFalse(feed.loadInitialPrices());
        assertEquals(420, stock.getPrice(), 0.001);
        assertTrue(feed.getStatusText().contains("offline"));
    }
}
