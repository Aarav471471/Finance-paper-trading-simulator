package com.papertrader.feed;

import com.papertrader.interfaces.HistoryProvider;
import com.papertrader.model.Asset;
import com.papertrader.model.Candle;
import com.papertrader.model.ETF;
import com.papertrader.model.Stock;
import com.papertrader.model.Timeframe;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
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
public class YahooQuoteProvider extends HttpQuoteProvider implements HistoryProvider {

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
            String querySym = yahooSymbol(sym);
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
        if (quotes.isEmpty() && !assets.isEmpty()) {
            // nothing worked at all: report it, so the sidebar shows "offline" instead of a fake "Connected"
            throw new IOException("Yahoo Finance did not answer");
        }
        return quotes;
    }

    private boolean isIndianSymbol(String s) {
        return s.equals("RELIANCE") || s.equals("TCS") || s.equals("HDFCBANK") || s.equals("INFY")
                || s.equals("ICICIBANK") || s.equals("BHARTIARTL") || s.equals("SBIN") || s.equals("ITC")
                || s.equals("LT") || s.equals("TATAMOTORS") || s.equals("TRENT");
    }

    /** Indian NSE stocks are listed on Yahoo with a ".NS" ending, e.g. RELIANCE.NS. */
    private String yahooSymbol(String symbol) {
        return isIndianSymbol(symbol) || symbol.equals("ETERNAL") ? symbol + ".NS" : symbol;
    }

    @Override
    public int getRefreshSeconds() {
        return 15;
    }

    /**
     * Past candles from the same chart endpoint:
     * result[0].timestamp[] (seconds) and result[0].indicators.quote[0].open[] / high[] / low[] / close[] / volume[]
     */
    @Override
    public List<Candle> fetchCandles(Asset asset, Timeframe timeframe, int count) throws IOException {
        String query = switch (timeframe) {
            case ONE_MINUTE -> "interval=1m&range=1d";
            case FIVE_MINUTES -> "interval=5m&range=5d";
            case ONE_HOUR -> "interval=60m&range=1mo";
            case ONE_DAY -> "interval=1d&range=1y";
            case ONE_WEEK -> "interval=1wk&range=5y";
        };
        List<Candle> all = parseCandles(get("https://query1.finance.yahoo.com/v8/finance/chart/"
                + yahooSymbol(asset.getSymbol()) + "?" + query));
        return all.subList(Math.max(0, all.size() - count), all.size());
    }

    static List<Candle> parseCandles(String json) {
        JSONObject result = new JSONObject(json).getJSONObject("chart").getJSONArray("result").getJSONObject(0);
        List<Candle> candles = new ArrayList<>();
        if (!result.has("timestamp")) {
            return candles;
        }
        JSONArray times = result.getJSONArray("timestamp");
        JSONObject q = result.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0);
        JSONArray open = q.getJSONArray("open");
        JSONArray high = q.getJSONArray("high");
        JSONArray low = q.getJSONArray("low");
        JSONArray close = q.getJSONArray("close");
        JSONArray volume = q.getJSONArray("volume");
        for (int i = 0; i < times.length(); i++) {
            if (open.isNull(i) || close.isNull(i) || high.isNull(i) || low.isNull(i)) {
                continue;   // Yahoo leaves gaps (null) for minutes with no trades
            }
            candles.add(new Candle(times.getLong(i) * 1000, open.getDouble(i), high.getDouble(i),
                    low.getDouble(i), close.getDouble(i), volume.isNull(i) ? 0 : volume.getDouble(i)));
        }
        return candles;
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
