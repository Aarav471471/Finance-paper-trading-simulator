package com.papertrader.feed;

import com.papertrader.interfaces.HistoryProvider;
import com.papertrader.interfaces.SnapshotProvider;
import com.papertrader.model.MarketSnapshot;
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
public class YahooQuoteProvider extends HttpQuoteProvider implements HistoryProvider, SnapshotProvider {

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
            String querySym = YahooSymbols.of(sym);
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
                + YahooSymbols.of(asset.getSymbol()) + "?" + query));
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

    /** Today's open / high / low / volume and the 52-week range (same chart endpoint, no key). */
    @Override
    public MarketSnapshot fetchSnapshot(Asset asset) throws IOException {
        return fetchSnapshot(YahooSymbols.of(asset.getSymbol()));
    }

    /** Works for any Yahoo symbol, including indices such as ^NSEI (NIFTY 50) or ^GSPC (S&P 500). */
    public MarketSnapshot fetchSnapshot(String yahooSymbol) throws IOException {
        String json = get("https://query1.finance.yahoo.com/v8/finance/chart/"
                + java.net.URLEncoder.encode(yahooSymbol, java.nio.charset.StandardCharsets.UTF_8)
                + "?interval=1d&range=1d");
        return parseSnapshot(json);
    }

    static MarketSnapshot parseSnapshot(String json) throws IOException {
        try {
            JSONObject result = new JSONObject(json).getJSONObject("chart").getJSONArray("result").getJSONObject(0);
            JSONObject meta = result.getJSONObject("meta");
            double open = Double.NaN;
            JSONObject indicators = result.optJSONObject("indicators");
            if (indicators != null && indicators.has("quote")) {
                JSONArray opens = indicators.getJSONArray("quote").getJSONObject(0).optJSONArray("open");
                if (opens != null && opens.length() > 0 && !opens.isNull(0)) {
                    open = opens.getDouble(0);
                }
            }
            return new MarketSnapshot(
                    meta.getDouble("regularMarketPrice"),
                    meta.optDouble("chartPreviousClose", meta.optDouble("previousClose", Double.NaN)),
                    open,
                    meta.optDouble("regularMarketDayHigh", Double.NaN),
                    meta.optDouble("regularMarketDayLow", Double.NaN),
                    meta.optDouble("regularMarketVolume", Double.NaN),
                    meta.optDouble("fiftyTwoWeekHigh", Double.NaN),
                    meta.optDouble("fiftyTwoWeekLow", Double.NaN),
                    "Yahoo Finance");
        } catch (Exception e) {
            throw new IOException("Unexpected Yahoo answer", e);
        }
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
