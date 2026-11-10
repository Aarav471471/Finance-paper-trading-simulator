package com.papertrader.feed;

import com.papertrader.model.Asset;
import com.papertrader.model.Crypto;

import java.util.Set;

/**
 * Yahoo Finance writes some symbols differently from our app:
 * Indian NSE shares end in ".NS" (RELIANCE.NS) and coins end in "-USD" (BTC-USD).
 * Kept in ONE place so every Yahoo class agrees.
 */
public final class YahooSymbols {

    private static final Set<String> INDIAN = Set.of("RELIANCE", "TCS", "HDFCBANK", "INFY", "ICICIBANK",
            "BHARTIARTL", "SBIN", "ITC", "LT", "TATAMOTORS", "TRENT", "ETERNAL");

    private YahooSymbols() {
    }

    public static boolean isIndian(String symbol) {
        return symbol != null && INDIAN.contains(symbol.toUpperCase());
    }

    public static String of(String symbol) {
        return isIndian(symbol) ? symbol + ".NS" : symbol;
    }

    public static String of(Asset asset) {
        return asset instanceof Crypto ? asset.getSymbol() + "-USD" : of(asset.getSymbol());
    }
}
