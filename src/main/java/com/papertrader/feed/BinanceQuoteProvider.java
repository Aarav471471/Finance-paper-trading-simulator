package com.papertrader.feed;

import com.papertrader.interfaces.HistoryProvider;
import com.papertrader.interfaces.SnapshotProvider;
import com.papertrader.model.MarketSnapshot;
import com.papertrader.model.Asset;
import com.papertrader.model.Candle;
import com.papertrader.model.Crypto;
import com.papertrader.model.Timeframe;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
public class BinanceQuoteProvider extends HttpQuoteProvider implements HistoryProvider, SnapshotProvider {

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

    /** 24-hour open / high / low / volume of one coin. The 52-week range comes from daily candles instead. */
    @Override
    public MarketSnapshot fetchSnapshot(Asset asset) throws IOException {
        return parseSnapshot(get("https://api.binance.com/api/v3/ticker/24hr?symbol=" + toBinanceSymbol(asset)));
    }

    static MarketSnapshot parseSnapshot(String json) {
        JSONObject t = new JSONObject(json);
        double open = t.getDouble("openPrice");
        return new MarketSnapshot(t.getDouble("lastPrice"), open, open,
                t.getDouble("highPrice"), t.getDouble("lowPrice"), t.getDouble("volume"),
                Double.NaN, Double.NaN, "Binance");
    }

    @Override
    public int getRefreshSeconds() {
        return 3;   // one request for all coins, Binance allows far more than this
    }

    /**
     * Past candles from Binance "klines":
     * [[openTime, "open", "high", "low", "close", "volume", closeTime, ...], ...]
     */
    @Override
    public List<Candle> fetchCandles(Asset asset, Timeframe timeframe, int count) throws IOException {
        String interval = switch (timeframe) {
            case ONE_MINUTE -> "1m";
            case FIVE_MINUTES -> "5m";
            case ONE_HOUR -> "1h";
            case ONE_DAY -> "1d";
            case ONE_WEEK -> "1w";
        };
        String json = get("https://api.binance.com/api/v3/klines?symbol=" + toBinanceSymbol(asset)
                + "&interval=" + interval + "&limit=" + Math.min(count, 1000));
        return parseKlines(json);
    }

    static List<Candle> parseKlines(String json) {
        List<Candle> candles = new ArrayList<>();
        JSONArray rows = new JSONArray(json);
        for (int i = 0; i < rows.length(); i++) {
            JSONArray k = rows.getJSONArray(i);
            candles.add(new Candle(k.getLong(0),
                    Double.parseDouble(k.getString(1)), Double.parseDouble(k.getString(2)),
                    Double.parseDouble(k.getString(3)), Double.parseDouble(k.getString(4)),
                    Double.parseDouble(k.getString(5))));
        }
        return candles;
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
