package com.papertrader.service;

import com.papertrader.feed.SimulatedPriceFeed;
import com.papertrader.model.AssetCatalog;
import com.papertrader.model.IndianStocks;
import com.papertrader.model.Stock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StockLookupServiceTest {

    @Test
    void savedLookupsComeBackOnTheNextStart(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("looked-up-stocks.txt"),
                "SAVEDONE|Saved One Ltd|181.50\nRELIANCE|dup of the catalog|1\n\nSAVEDONE|repeat|1\n");
        List<Stock> saved = StockLookupService.loadSaved(dir);
        assertEquals(1, saved.size());
        assertEquals("Saved One Ltd", saved.get(0).getName());
        assertEquals(181.50, saved.get(0).getPrice(), 0.001);
        assertTrue(IndianStocks.isIndian("SAVEDONE"));

        MarketService market = new MarketService(new ArrayList<>(saved));
        new StockLookupService(market, new SimulatedPriceFeed(new ArrayList<>(), 1000), null, dir).save();
        assertTrue(Files.readString(dir.resolve("looked-up-stocks.txt")).contains("SAVEDONE|Saved One Ltd|181.50"));
    }

    @Test
    void offlineLookupExplainsWhy() {
        MarketService market = new MarketService(AssetCatalog.defaultAssets());
        StockLookupService lookup = new StockLookupService(market, new SimulatedPriceFeed(new ArrayList<>(), 1000), null, null);
        AtomicReference<StockLookupService.Result> result = new AtomicReference<>();

        lookup.lookUpIndian("jiofin", result::set);
        assertTrue(result.get().found(), "already in the list");

        lookup.lookUpIndian("not a symbol!", result::set);
        assertFalse(result.get().found());

        lookup.lookUpIndian("NEWONE", result::set);
        assertFalse(result.get().found());
        assertTrue(result.get().message().contains("offline"));
    }
}
