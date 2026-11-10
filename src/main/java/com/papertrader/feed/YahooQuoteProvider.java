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
            String sym = asset.getSymbol();
            String querySym = isIndianSymbol(sym) ? sym + ".NS" : sym;
            try {
                String json = get("https://query1.finance.yahoo.com/v8/finance/chart/" + querySym
                        + "?interval=1d&range=1d");
                Quote quote = parse(json);
                if (quote != null) {
                    quotes.put(asset.getSymbol(), quote);
                }
            } catch (Exception ignored) {
                // Keep existing price for assets not found on Yahoo Finance
            }
        }
        return quotes;
    }

    private boolean isIndianSymbol(String s) {
        return s.equals("RELIANCE") || s.equals("TCS") || s.equals("HDFCBANK") || s.equals("INFY")
                || s.equals("ICICIBANK") || s.equals("BHARTIARTL") || s.equals("SBIN") || s.equals("ITC")
                || s.equals("LT") || s.equals("TATAMOTORS") || s.equals("TRENT");
    }

    @Override
    public int getRefreshSeconds() {
        return 15;
    }

    static Quote parse(String json) {
        try {
            JSONObject meta = new JSONObject(json)
                    .getJSONObject("chart")
                    .getJSONArray("result")
                    .getJSONObject(0)
                    .getJSONObject("meta");
            double price = meta.getDouble("regularMarketPrice");
            double prev = meta.optDouble("chartPreviousClose", price);
            return new Quote(price, prev);
        } catch (Exception e) {
            return null;
        }
    }
}
