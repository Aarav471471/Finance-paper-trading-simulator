package com.papertrader.service;

import com.papertrader.feed.Quote;
import com.papertrader.feed.YahooQuoteProvider;
import com.papertrader.interfaces.PriceFeed;
import com.papertrader.model.Asset;
import com.papertrader.model.IndianStocks;
import com.papertrader.model.Stock;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Finds any NSE share by its symbol (for example "IRCON" or "SUZLON") when it is not in the
 * built-in list, and adds it to the app with live prices and charts.
 *
 * The check is a Yahoo Finance quote for SYMBOL.NS. Shares found are saved to a small file, so
 * they are still there (with any holdings in them) the next time the app starts.
 */
public class StockLookupService {

    /** Sector given to shares that were looked up (Yahoo's chart answer has no sector). */
    public static final String LOOKED_UP_SECTOR = "NSE Listed";
    private static final String FILE_NAME = "looked-up-stocks.txt";

    /** What a lookup found: the asset, or a message saying why there is none. */
    public record Result(Asset asset, String message) {
        public boolean found() {
            return asset != null;
        }
    }

    private final MarketService market;
    private final PriceFeed feed;
    private final YahooQuoteProvider yahoo;   // null = offline
    private final Path saveFile;               // null = do not save
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "stock-lookup");
        t.setDaemon(true);
        return t;
    });

    public StockLookupService(MarketService market, PriceFeed feed, YahooQuoteProvider yahoo, Path dataDir) {
        this.market = market;
        this.feed = feed;
        this.yahoo = yahoo;
        this.saveFile = dataDir == null ? null : dataDir.resolve(FILE_NAME);
    }

    public boolean isOnline() {
        return yahoo != null;
    }

    /**
     * Looks a typed symbol up on NSE in the background. {@code done} runs on the Swing thread
     * with the asset (already added to the market and priced live) or a message.
     */
    public void lookUpIndian(String typed, Consumer<Result> done) {
        String symbol = IndianStocks.normalize(typed);
        if (symbol == null) {
            done.accept(new Result(null, "\"" + typed.trim() + "\" is not an NSE symbol (letters and digits, like JIOFIN)."));
            return;
        }
        Optional<Asset> known = market.findBySymbol(symbol);
        if (known.isPresent()) {
            done.accept(new Result(known.get(), symbol + " is already in the list."));
            return;
        }
        if (yahoo == null) {
            done.accept(new Result(null, "Looking up new shares needs live data (the app is offline)."));
            return;
        }
        worker.submit(() -> {
            Result result;
            try {
                YahooQuoteProvider.Listing listing = yahoo.lookUp(symbol + ".NS");
                result = listing == null
                        ? new Result(null, "No NSE share called " + symbol + " was found.")
                        : new Result(toStock(symbol, listing.name(), listing.quote()), "");
            } catch (IOException e) {
                result = new Result(null, "No NSE share called " + symbol + " was found (or Yahoo Finance did not answer).");
            }
            Result finalResult = result;
            SwingUtilities.invokeLater(() -> {
                if (finalResult.found()) {
                    add(finalResult.asset());
                    save();
                }
                done.accept(finalResult);
            });
        });
    }

    private static Stock toStock(String symbol, String name, Quote quote) {
        Stock stock = new Stock(symbol, name == null || name.isBlank() ? symbol : name, quote.price(), LOOKED_UP_SECTOR);
        stock.setPreviousClose(quote.previousClose());
        return stock;
    }

    /** Makes the asset Indian everywhere, lists it in the market and starts its live price. Swing thread. */
    private void add(Asset asset) {
        IndianStocks.register(asset.getSymbol());
        market.addAsset(asset);
        feed.track(asset);
    }

    // ---------------------------------------------------------------- saved lookups

    /**
     * Shares looked up in earlier runs, as stocks at their last saved price. Call before the
     * price feed is created, and add them to the starting asset list.
     */
    public static List<Stock> loadSaved(Path dataDir) {
        List<Stock> stocks = new ArrayList<>();
        Path file = dataDir.resolve(FILE_NAME);
        if (!Files.exists(file)) {
            return stocks;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String[] parts = line.split("\\|", -1);
                String symbol = parts.length == 0 ? null : IndianStocks.normalize(parts[0]);
                if (symbol == null || IndianStocks.isIndian(symbol)) {
                    continue;   // empty line, a repeat, or a share the built-in list now has
                }
                String name = parts.length > 1 && !parts[1].isBlank() ? parts[1] : symbol;
                double price = parts.length > 2 ? parsePrice(parts[2]) : 0;
                IndianStocks.register(symbol);
                stocks.add(new Stock(symbol, name, price > 0 ? price : 100, LOOKED_UP_SECTOR));
            }
        } catch (IOException e) {
            System.err.println("Could not read " + file + ": " + e.getMessage());
        }
        return stocks;
    }

    private static double parsePrice(String text) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Writes every looked-up share with its latest price. Swing thread. */
    public void save() {
        if (saveFile == null) {
            return;
        }
        List<String> lines = new ArrayList<>();
        for (String symbol : IndianStocks.registered()) {
            market.findBySymbol(symbol).ifPresent(a -> lines.add(a.getSymbol() + "|" + a.getName().replace('|', ' ')
                    + "|" + String.format(Locale.US, "%.2f", a.getPrice())));
        }
        try {
            Files.createDirectories(saveFile.getParent());
            Files.write(saveFile, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Could not save " + saveFile + ": " + e.getMessage());
        }
    }
}
