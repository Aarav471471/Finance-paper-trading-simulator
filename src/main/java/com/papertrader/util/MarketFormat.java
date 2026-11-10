package com.papertrader.util;

import com.papertrader.feed.YahooSymbols;

import java.util.Locale;

/**
 * Formats market numbers for the screens. A value that is not known (NaN) is shown as "N/A":
 * the app never invents a number.
 */
public final class MarketFormat {

    public static final String NA = "N/A";

    private MarketFormat() {
    }

    public static boolean known(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v);
    }

    /** "₹" for NSE shares (priced in rupees), "$" for everything else. */
    public static String currency(String symbol) {
        return YahooSymbols.isIndian(symbol) ? "₹" : "$";
    }

    public static String price(String cur, double v) {
        return known(v) ? String.format(Locale.US, "%s%,.2f", cur, v) : NA;
    }

    /** Large amounts: "₹19.92 Lakh Cr" / "₹4,250 Cr" in India, "$2.84T" / "$310.5B" / "$12.3M" otherwise. */
    public static String big(String cur, double v) {
        if (!known(v)) {
            return NA;
        }
        String sign = v < 0 ? "-" : "";
        double a = Math.abs(v);
        if ("₹".equals(cur)) {
            if (a >= 1e12) return String.format(Locale.US, "%s₹%.2f Lakh Cr", sign, a / 1e12);
            if (a >= 1e7) return String.format(Locale.US, "%s₹%,.0f Cr", sign, a / 1e7);
            return String.format(Locale.US, "%s₹%,.0f", sign, a);
        }
        if (a >= 1e12) return String.format(Locale.US, "%s$%.2fT", sign, a / 1e12);
        if (a >= 1e9) return String.format(Locale.US, "%s$%.1fB", sign, a / 1e9);
        if (a >= 1e6) return String.format(Locale.US, "%s$%.1fM", sign, a / 1e6);
        return String.format(Locale.US, "%s$%,.0f", sign, a);
    }

    /** Share / coin counts: 6.42M, 512.0K. */
    public static String volume(double v) {
        if (!known(v)) return NA;
        if (v >= 1e9) return String.format(Locale.US, "%.2fB", v / 1e9);
        if (v >= 1e6) return String.format(Locale.US, "%.2fM", v / 1e6);
        if (v >= 1e3) return String.format(Locale.US, "%.1fK", v / 1e3);
        return String.format(Locale.US, "%.2f", v);
    }

    /** A fraction as percent: 0.118 -> "11.8%". */
    public static String pct(double fraction) {
        return known(fraction) ? String.format(Locale.US, "%.1f%%", fraction * 100) : NA;
    }

    public static String signedPct(double percent) {
        return known(percent) ? String.format(Locale.US, "%+.2f%%", percent) : NA;
    }

    public static String number(double v, int decimals) {
        return known(v) ? String.format(Locale.US, "%,." + decimals + "f", v) : NA;
    }

    /** "18m ago", "3h ago", "2d ago". */
    public static String timeAgo(long millis) {
        long minutes = Math.max(0, (System.currentTimeMillis() - millis) / 60_000);
        if (minutes < 60) return minutes + "m ago";
        if (minutes < 60 * 24) return (minutes / 60) + "h ago";
        return (minutes / (60 * 24)) + "d ago";
    }
}
