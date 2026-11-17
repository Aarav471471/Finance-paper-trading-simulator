package com.papertrader.feed;

import com.papertrader.model.Asset;
import com.papertrader.model.Crypto;
import com.papertrader.model.IndianStocks;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Yahoo Finance writes some symbols differently from our app:
 * Indian NSE shares end in ".NS" (RELIANCE.NS) and coins end in "-USD" (BTC-USD).
 * Kept in ONE place so every Yahoo class agrees. Which shares are Indian is decided by
 * {@link IndianStocks}.
 */
public final class YahooSymbols {

    private YahooSymbols() {
    }

    public static boolean isIndian(String symbol) {
        return IndianStocks.isIndian(symbol);
    }

    public static String of(String symbol) {
        return isIndian(symbol) ? IndianStocks.nseSymbol(symbol) + ".NS" : symbol;
    }

    public static String of(Asset asset) {
        return asset instanceof Crypto ? asset.getSymbol() + "-USD" : of(asset.getSymbol());
    }

    /** The Yahoo symbol, ready to put in a URL ("M&M.NS" becomes "M%26M.NS"). */
    public static String encoded(Asset asset) {
        return URLEncoder.encode(of(asset), StandardCharsets.UTF_8);
    }
}
