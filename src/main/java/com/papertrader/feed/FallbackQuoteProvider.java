package com.papertrader.feed;

import com.papertrader.interfaces.QuoteProvider;
import com.papertrader.model.Asset;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Uses a main provider and, for any asset it could not price, a backup provider
 * (DECORATOR / COMPOSITION: it HAS-A QuoteProvider and is itself a QuoteProvider).
 *
 * Example: Groww first for Indian shares; if the Groww key is not approved today or Groww is
 * down, the same shares come from Yahoo Finance instead of freezing at old prices.
 */
public class FallbackQuoteProvider implements QuoteProvider {

    private final QuoteProvider main;
    private final QuoteProvider backup;
    private volatile String note = "";

    public FallbackQuoteProvider(QuoteProvider main, QuoteProvider backup) {
        this.main = main;
        this.backup = backup;
    }

    @Override
    public String getName() {
        return main.getName();
    }

    @Override
    public boolean supports(Asset asset) {
        return main.supports(asset);
    }

    @Override
    public Map<String, Quote> fetchQuotes(List<Asset> assets) throws IOException {
        Map<String, Quote> quotes = new HashMap<>();
        String mainError = null;
        try {
            quotes.putAll(main.fetchQuotes(assets));
        } catch (IOException e) {
            mainError = e.getMessage();
        }
        List<Asset> missing = new ArrayList<>();
        for (Asset asset : assets) {
            if (!quotes.containsKey(asset.getSymbol()) && backup.supports(asset)) {
                missing.add(asset);
            }
        }
        if (missing.isEmpty()) {
            note = "";
            return quotes;
        }
        try {
            quotes.putAll(backup.fetchQuotes(missing));
        } catch (IOException e) {
            if (quotes.isEmpty()) {
                throw new IOException((mainError != null ? mainError + "; " : "") + backup.getName() + ": " + e.getMessage());
            }
        }
        note = "via " + backup.getName() + (mainError != null ? " (" + mainError + ")" : "");
        return quotes;
    }

    @Override
    public int getRefreshSeconds() {
        return main.getRefreshSeconds();
    }

    @Override
    public String getStatusNote() {
        return note;
    }
}
