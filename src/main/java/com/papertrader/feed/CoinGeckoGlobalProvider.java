package com.papertrader.feed;

import org.json.JSONObject;

import java.io.IOException;

/**
 * Size of the whole crypto market from CoinGecko's free /global endpoint (no key).
 * Answer: {"data":{"total_market_cap":{"usd":2.4E12},"market_cap_change_percentage_24h_usd":-0.85}}
 */
public class CoinGeckoGlobalProvider {

    /** @return {total market cap in USD, 24h change in %} */
    public double[] fetchTotalMarketCap() throws IOException {
        return parse(Http.get("https://api.coingecko.com/api/v3/global"));
    }

    static double[] parse(String json) {
        JSONObject data = new JSONObject(json).getJSONObject("data");
        return new double[]{
                data.getJSONObject("total_market_cap").getDouble("usd"),
                data.optDouble("market_cap_change_percentage_24h_usd", 0)};
    }
}
