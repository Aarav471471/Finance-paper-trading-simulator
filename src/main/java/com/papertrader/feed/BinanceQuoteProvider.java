package com.papertrader.feed;

import com.papertrader.model.Asset;
import com.papertrader.model.Crypto;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Crypto prices straight from Binance, the largest crypto exchange. No key needed.
 * Prices are in USDT, a dollar-pegged coin, so 1 USDT is about $1.
 *
 * One request returns all coins:
 * [{"symbol":"BTCUSDT","lastPrice":"68012.10","openPrice":"67100.00",...}, ...]
 * openPrice = price 24 hours ago (crypto trades 24/7, so there is no daily "close").
 */
public class BinanceQuoteProvider extends HttpQuoteProvider {

    @Override
    public String getName() {
        return "Binance";
    }

    @Override
    public boolean supports(Asset asset) {
        return asset instanceof Crypto;
    }

    @Override
    public Map<String, Quote> fetchQuotes(List<Asset> assets) throws IOException {
        StringBuilder symbols = new StringBuilder("[");
        for (int i = 0; i < assets.size(); i++) {
            symbols.append(i == 0 ? "" : ",").append('"').append(toBinanceSymbol(assets.get(i))).append('"');
        }
        symbols.append("]");
        String json = get("https://api.binance.com/api/v3/ticker/24hr?symbols="
                + URLEncoder.encode(symbols.toString(), StandardCharsets.UTF_8));

        Map<String, Quote> bySymbol = parse(json);   // keyed by "BTCUSDT"
        Map<String, Quote> quotes = new HashMap<>();
        for (Asset asset : assets) {
            Quote quote = bySymbol.get(toBinanceSymbol(asset));
            if (quote != null) {
                quotes.put(asset.getSymbol(), quote);
            }
        }
        return quotes;
    }

    @Override
    public int getRefreshSeconds() {
        return 3;   // one request for all coins, Binance allows far more than this
    }

    private static String toBinanceSymbol(Asset asset) {
        return asset.getSymbol() + "USDT";
    }

    static Map<String, Quote> parse(String json) {
        Map<String, Quote> result = new HashMap<>();
        JSONArray array = new JSONArray(json);
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.getJSONObject(i);
            double price = Double.parseDouble(o.getString("lastPrice"));   // Binance sends numbers as text
            double open = Double.parseDouble(o.getString("openPrice"));
            if (price > 0) {
                result.put(o.getString("symbol"), new Quote(price, open > 0 ? open : price));
            }
        }
        return result;
    }
}
