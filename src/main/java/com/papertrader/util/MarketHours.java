package com.papertrader.util;

import com.papertrader.feed.YahooSymbols;
import com.papertrader.model.Asset;
import com.papertrader.model.Crypto;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Is the exchange open right now? Uses the real trading hours in each exchange's own time zone:
 * NSE 09:15-15:30 IST, US 09:30-16:00 New York time, crypto 24/7. (Public holidays are not included.)
 */
public final class MarketHours {

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    private MarketHours() {
    }

    public static boolean isOpen(Asset asset) {
        if (asset instanceof Crypto) {
            return true;
        }
        return YahooSymbols.isIndian(asset.getSymbol())
                ? isOpen(ZonedDateTime.now(INDIA), LocalTime.of(9, 15), LocalTime.of(15, 30))
                : isOpen(ZonedDateTime.now(NEW_YORK), LocalTime.of(9, 30), LocalTime.of(16, 0));
    }

    static boolean isOpen(ZonedDateTime now, LocalTime open, LocalTime close) {
        DayOfWeek day = now.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        LocalTime t = now.toLocalTime();
        return !t.isBefore(open) && t.isBefore(close);
    }

    /** Short text for badges, e.g. "MARKET OPEN 09:15-15:30 IST" or "US MARKET CLOSED". */
    public static String status(Asset asset) {
        if (asset instanceof Crypto) {
            return "24/7 MARKET OPEN";
        }
        boolean open = isOpen(asset);
        if (YahooSymbols.isIndian(asset.getSymbol())) {
            return open ? "MARKET OPEN 09:15-15:30 IST" : "NSE CLOSED · OPENS 09:15 IST";
        }
        return open ? "US MARKET OPEN 09:30-16:00 ET" : "US MARKET CLOSED · OPENS 09:30 ET";
    }
}
