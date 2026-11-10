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
 * Real-time US stock and ETF prices from Finnhub (https://finnhub.io).
 * Official API with exchange data; the free plan needs a key and allows 60 calls per minute.
 *
 * Answer looks like: {"c":271.4,"d":1.2,"dp":0.44,"h":272,"l":268,"o":269,"pc":270.2,"t":1731250000}
 *   c  = current price, pc = previous close
 */
public class FinnhubQuoteProvider extends HttpQuoteProvider {

    private final String apiKey;

    public FinnhubQuoteProvider(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public String getName() {
        return "Finnhub";
    }

    @Override
    public boolean supports(Asset asset) {
        return asset instanceof Stock || asset instanceof ETF;
    }

    @Override
    public Map<String, Quote> fetchQuotes(List<Asset> assets) throws IOException {
        Map<String, Quote> quotes = new HashMap<>();
        for (Asset asset : assets) {   // Finnhub gives one symbol per request
            String json = get("https://finnhub.io/api/v1/quote?symbol=" + asset.getSymbol() + "&token=" + apiKey);
            Quote quote = parse(json);
            if (quote != null) {
                quotes.put(asset.getSymbol(), quote);
            }
        }
        return quotes;
    }

    @Override
    public int getRefreshSeconds() {
        return 15;   // 7 symbols every 15 s = 28 calls/min, well under the 60/min limit
    }

    /** Reads Finnhub's JSON. Returns null for an unknown symbol (Finnhub then sends c = 0). */
    static Quote parse(String json) {
        JSONObject o = new JSONObject(json);
        double price = o.optDouble("c", 0);
        if (price <= 0) {
            return null;
        }
        return new Quote(price, o.optDouble("pc", price));
    }
}
