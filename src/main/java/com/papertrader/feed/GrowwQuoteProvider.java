package com.papertrader.feed;

import com.papertrader.model.Asset;
import com.papertrader.model.Stock;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Real NSE prices for Indian shares (RELIANCE, TCS, INFY ...) from the official Groww Trade API.
 * Needs your own Groww API key (see GrowwSession). Only READS prices: this app never places
 * real orders, every trade stays paper trading.
 *
 * GET https://api.groww.in/v1/live-data/quote?exchange=NSE&segment=CASH&trading_symbol=RELIANCE
 * Answer looks like:
 *   {"status":"SUCCESS","payload":{"last_price":2945.5,"day_change":12.3,"day_change_perc":0.42,
 *    "ohlc":"{open: 2930.00,high: 2950.00,low: 2925.00,close: 2933.20}", ...}}
 * previous close = last_price - day_change.
 */
public class GrowwQuoteProvider extends HttpQuoteProvider {

    static final String QUOTE_URL = "https://api.groww.in/v1/live-data/quote?exchange=NSE&segment=CASH&trading_symbol=";
    private static final Pattern CLOSE = Pattern.compile("close\\s*:\\s*(-?[0-9.]+)");

    private final GrowwSession session;

    public GrowwQuoteProvider(GrowwSession session) {
        this.session = session;
    }

    @Override
    public String getName() {
        return "Groww";
    }

    @Override
    public boolean supports(Asset asset) {
        return asset instanceof Stock && YahooSymbols.isIndian(asset.getSymbol());
    }

    @Override
    public Map<String, Quote> fetchQuotes(List<Asset> assets) throws IOException {
        Map<String, Quote> quotes = new HashMap<>();
        IOException lastError = null;
        for (Asset asset : assets) {   // one symbol per request; 12 shares every 10 s is well under 300/min
            try {
                Quote quote = parse(download(asset.getSymbol()));
                if (quote != null) {
                    quotes.put(asset.getSymbol(), quote);
                }
            } catch (IOException e) {
                lastError = e;
            }
        }
        if (quotes.isEmpty() && !assets.isEmpty()) {
            throw lastError != null ? lastError : new IOException("Groww gave no prices");
        }
        return quotes;
    }

    /** Asks for one quote. On 401 (token expired) logs in once more and tries again. */
    private String download(String symbol) throws IOException {
        try {
            return Http.get(QUOTE_URL + symbol, headers());
        } catch (Http.HttpStatusException e) {
            if (e.getStatusCode() != 401 || !session.canRenew()) {
                throw e.getStatusCode() == 401 ? new IOException("Groww token expired or not approved") : e;
            }
            session.invalidate();
            return Http.get(QUOTE_URL + symbol, headers());
        }
    }

    private Map<String, String> headers() throws IOException {
        return Map.of("Authorization", "Bearer " + session.accessToken(), "X-API-VERSION", "1.0");
    }

    @Override
    public int getRefreshSeconds() {
        return 10;
    }

    /** Reads Groww's quote JSON. Returns null when the answer has no usable price. */
    static Quote parse(String json) {
        JSONObject root = new JSONObject(json);
        if (!"SUCCESS".equalsIgnoreCase(root.optString("status", "SUCCESS"))) {
            return null;
        }
        JSONObject payload = root.optJSONObject("payload");
        if (payload == null) {
            return null;
        }
        double price = payload.optDouble("last_price", 0);
        if (!(price > 0)) {
            return null;
        }
        double change = payload.optDouble("day_change", Double.NaN);
        double previousClose = Double.isNaN(change) ? closeFromOhlc(payload.opt("ohlc")) : price - change;
        if (!(previousClose > 0)) {
            previousClose = price;
        }
        return new Quote(price, previousClose);
    }

    /** "ohlc" comes as a string "{open: 1,high: 2,low: 0.5,close: 1.5}" or as a JSON object. */
    private static double closeFromOhlc(Object ohlc) {
        if (ohlc instanceof JSONObject object) {
            return object.optDouble("close", Double.NaN);
        }
        if (ohlc instanceof String text) {
            Matcher m = CLOSE.matcher(text);
            if (m.find()) {
                return Double.parseDouble(m.group(1));
            }
        }
        return Double.NaN;
    }
}
