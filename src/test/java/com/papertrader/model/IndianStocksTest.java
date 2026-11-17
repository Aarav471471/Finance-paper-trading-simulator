package com.papertrader.model;

import com.papertrader.feed.YahooSymbols;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndianStocksTest {

    @Test
    void catalogHasTheNifty100AndPopularListingsWithoutRepeats() {
        List<IndianStocks.Entry> catalog = IndianStocks.catalog();
        assertTrue(catalog.size() >= 150, "only " + catalog.size());
        Set<String> seen = new HashSet<>();
        for (IndianStocks.Entry e : catalog) {
            assertTrue(seen.add(e.symbol()), "listed twice: " + e.symbol());
            assertTrue(e.price() > 0, e.symbol());
        }
        for (String s : List.of("RELIANCE", "JIOFIN", "SWIGGY", "LICI", "PAYTM", "NYKAA", "IRFC", "TATATECH", "HYUNDAI", "ETERNAL")) {
            assertTrue(IndianStocks.isIndian(s), s);
        }
    }

    @Test
    void everyCatalogShareIsInTheDefaultAssets() {
        Set<String> symbols = new HashSet<>();
        for (Asset a : AssetCatalog.defaultAssets()) {
            assertTrue(symbols.add(a.getSymbol()), "listed twice: " + a.getSymbol());
        }
        for (IndianStocks.Entry e : IndianStocks.catalog()) {
            assertTrue(symbols.contains(e.symbol()), e.symbol());
        }
        assertFalse(IndianStocks.isIndian("AAPL"));
        assertFalse(IndianStocks.isIndian("BTC"));
    }

    @Test
    void yahooSymbolsUseTheNseSymbol() {
        assertEquals("JIOFIN.NS", YahooSymbols.of("JIOFIN"));
        assertEquals("M&M.NS", YahooSymbols.of("M&M"));
        assertEquals("TMPV.NS", YahooSymbols.of("TATAMOTORS"));   // renamed on NSE after the 2025 demerger
        assertEquals("M%26M.NS", YahooSymbols.encoded(new Stock("M&M", "Mahindra", 1, "Automobile")));
    }

    @Test
    void normalizesWhatTheUserTyped() {
        assertEquals("JIOFIN", IndianStocks.normalize(" jiofin "));
        assertEquals("TCS", IndianStocks.normalize("tcs.ns"));
        assertEquals("BAJAJ-AUTO", IndianStocks.normalize("bajaj-auto"));
        assertNull(IndianStocks.normalize("Jio Financial"));
        assertNull(IndianStocks.normalize(""));
    }

    @Test
    void registeredSharesCountAsIndian() {
        assertFalse(IndianStocks.isIndian("IRCONTEST"));
        IndianStocks.register("ircontest");
        assertTrue(IndianStocks.isIndian("IRCONTEST"));
        assertEquals("IRCONTEST.NS", YahooSymbols.of("IRCONTEST"));
    }
}
