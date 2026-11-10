package com.papertrader.feed;

import com.papertrader.model.Asset;
import com.papertrader.model.ETF;
import com.papertrader.model.Stock;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stock and ETF prices from Yahoo Finance. Needs no API key, so the app shows real
 * prices even without setup. It is an unofficial endpoint, so Finnhub is preferred
 * when a key is available (see App.java).
 *
 * Answer looks like: {"chart":{"result":[{"meta":{"regularMarketPrice":271.4,"chartPreviousClose":270.2,...}}]}}
 */
public class YahooQuoteProvider extends HttpQuoteProvider {

    @Override
    public String getName() {
        return "Yahoo Finance";
    }

    @Override
    public boolean supports(Asset asset) {
        return asset instanceof Stock || asset instanceof ETF;
    }

    @Override
    public Map<String, Quote> fetchQuotes(List<Asset> assets) throws IOException {
        Map<String, Quote> quotes = new HashMap<>();
        for (Asset asset : assets) {
            String json = get("https://query1.finance.yahoo.com/v8/finance/chart/" + asset.getSymbol()
                    + "?interval=1d&range=1d");
            Quote quote = parse(json);
            if (quote != null) {
                quotes.put(asset.getSymbol(), quote);
            }
        }
        return quotes;
    }

    @Override
    public int getRefreshSeconds() {
        return 15;
    }

    static Quote parse(String json) {
        JSONObject meta = new JSONObject(json)
                .getJSONObject("chart")
                .getJSONArray("result")
                .getJSONObject(0)
                .getJSONObject("meta");
        double price = meta.optDouble("regularMarketPrice", 0);
        if (price <= 0) {
            return null;
        }
        double previousClose = meta.optDouble("previousClose", meta.optDouble("chartPreviousClose", price));
        return new Quote(price, previousClose);
    }
}
