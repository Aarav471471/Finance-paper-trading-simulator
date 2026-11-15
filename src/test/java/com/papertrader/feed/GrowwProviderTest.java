package com.papertrader.feed;

import com.papertrader.interfaces.QuoteProvider;
import com.papertrader.model.Asset;
import com.papertrader.model.Crypto;
import com.papertrader.model.Stock;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Groww quote parsing, login checksum, and the Groww -> Yahoo fallback (no internet needed). */
class GrowwProviderTest {

    @Test
    void readsGrowwQuoteUsingDayChange() {
        String json = "{\"status\":\"SUCCESS\",\"payload\":{\"last_price\":2945.5,\"day_change\":12.3,"
                + "\"day_change_perc\":0.42,\"ohlc\":\"{open: 2930.00,high: 2950.00,low: 2925.00,close: 2933.20}\"}}";
        Quote q = GrowwQuoteProvider.parse(json);
        assertEquals(2945.5, q.price(), 0.001);
        assertEquals(2933.2, q.previousClose(), 0.001);
    }

    @Test
    void usesOhlcCloseWhenDayChangeIsMissing() {
        String json = "{\"status\":\"SUCCESS\",\"payload\":{\"last_price\":149.5,"
                + "\"ohlc\":\"{open: 149.50,high: 150.50,low: 148.50,close: 150.00}\"}}";
        assertEquals(150.0, GrowwQuoteProvider.parse(json).previousClose(), 0.001);
    }

    @Test
    void failedOrEmptyAnswerGivesNull() {
        assertNull(GrowwQuoteProvider.parse("{\"status\":\"FAILURE\",\"error\":{\"code\":\"GA001\"}}"));
        assertNull(GrowwQuoteProvider.parse("{\"status\":\"SUCCESS\",\"payload\":{\"last_price\":0}}"));
    }

    @Test
    void growwOnlyHandlesIndianShares() {
        GrowwQuoteProvider groww = new GrowwQuoteProvider(new GrowwSession("", "", "token"));
        assertTrue(groww.supports(new Stock("RELIANCE", "Reliance", 1, "Energy")));
        assertFalse(groww.supports(new Stock("AAPL", "Apple", 1, "Tech")));
        assertFalse(groww.supports(new Crypto("BTC", "Bitcoin", 1, "Bitcoin")));
    }

    @Test
    void checksumIsSha256OfSecretPlusTimestamp() {
        // printf "%s%s" secret 1700000000 | sha256sum
        assertEquals("a09abffb61e9ed1074de76ba1133a9900ffb7cccde778fb92b31afb76f5390ff",
                GrowwSession.checksum("secret", "1700000000"));
    }

    @Test
    void sessionNeedsTokenOrKeyAndSecret() {
        assertFalse(new GrowwSession("", "", "").isConfigured());
        assertFalse(new GrowwSession("key", "", "").isConfigured());
        assertTrue(new GrowwSession("key", "secret", "").isConfigured());
        assertTrue(new GrowwSession(null, null, "token").isConfigured());
        assertEquals("tok", GrowwSession.parseToken("{\"token\":\"tok\",\"isActive\":true}"));
        assertNull(GrowwSession.parseToken("{\"isActive\":false}"));
    }

    @Test
    void fallsBackToBackupWhenMainFails() throws IOException {
        Asset reliance = new Stock("RELIANCE", "Reliance", 1, "Energy");
        QuoteProvider failing = new Fake("Groww", null);
        QuoteProvider yahoo = new Fake("Yahoo Finance", new Quote(2900, 2890));
        FallbackQuoteProvider provider = new FallbackQuoteProvider(failing, yahoo);

        Map<String, Quote> quotes = provider.fetchQuotes(List.of(reliance));
        assertEquals(2900, quotes.get("RELIANCE").price(), 0.001);
        assertEquals("Groww", provider.getName());
        assertTrue(provider.getStatusNote().startsWith("via Yahoo Finance"));
    }

    @Test
    void noNoteWhenMainWorks() throws IOException {
        FallbackQuoteProvider provider = new FallbackQuoteProvider(new Fake("Groww", new Quote(10, 9)),
                new Fake("Yahoo Finance", new Quote(1, 1)));
        assertEquals(10, provider.fetchQuotes(List.of(new Stock("TCS", "TCS", 1, "Tech"))).get("TCS").price(), 0.001);
        assertEquals("", provider.getStatusNote());
    }

    @Test
    void reportsErrorWhenBothFail() {
        FallbackQuoteProvider provider = new FallbackQuoteProvider(new Fake("Groww", null), new Fake("Yahoo Finance", null));
        assertThrows(IOException.class, () -> provider.fetchQuotes(List.of(new Stock("TCS", "TCS", 1, "Tech"))));
    }

    /** A provider that always answers the same quote, or always fails when quote is null. */
    private record Fake(String name, Quote quote) implements QuoteProvider {
        public String getName() { return name; }
        public boolean supports(Asset asset) { return true; }
        public int getRefreshSeconds() { return 10; }
        public Map<String, Quote> fetchQuotes(List<Asset> assets) throws IOException {
            if (quote == null) {
                throw new IOException(name + " down");
            }
            return Map.of(assets.get(0).getSymbol(), quote);
        }
    }
}
