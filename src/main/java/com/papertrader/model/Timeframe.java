package com.papertrader.model;

/** How long one candle lasts, and how many candles we download for it. */
public enum Timeframe {
    ONE_MINUTE("1m", 60, 120),
    FIVE_MINUTES("5m", 300, 288),       // 288 x 5 minutes = one day
    ONE_HOUR("1h", 3_600, 168),         // 168 hours = one week
    ONE_DAY("1D", 86_400, 365),         // one year
    ONE_WEEK("1W", 604_800, 260);       // five years

    private final String label;
    private final long seconds;
    private final int defaultCount;

    Timeframe(String label, long seconds, int defaultCount) {
        this.label = label;
        this.seconds = seconds;
        this.defaultCount = defaultCount;
    }

    /** How many candles ChartService downloads for this timeframe. */
    public int getDefaultCount() {
        return defaultCount;
    }

    public String getLabel() {
        return label;
    }

    public long getMillis() {
        return seconds * 1000;
    }

    @Override
    public String toString() {
        return label;
    }
}
