package com.papertrader.model;

/**
 * Today's trading numbers for one asset or index. Values that a source does not give are NaN.
 *
 * @param source where the numbers came from, e.g. "Yahoo Finance" or "candles"
 */
public record MarketSnapshot(double price, double previousClose, double open, double dayHigh, double dayLow,
                             double volume, double week52High, double week52Low, String source) {

    public double change() {
        return price - previousClose;
    }

    public double changePercent() {
        return previousClose > 0 ? (price - previousClose) / previousClose * 100 : 0;
    }

    /** Copies missing (NaN) values from another snapshot. */
    public MarketSnapshot fillFrom(MarketSnapshot other) {
        if (other == null) {
            return this;
        }
        return new MarketSnapshot(pick(price, other.price), pick(previousClose, other.previousClose),
                pick(open, other.open), pick(dayHigh, other.dayHigh), pick(dayLow, other.dayLow),
                pick(volume, other.volume), pick(week52High, other.week52High), pick(week52Low, other.week52Low), source);
    }

    private static double pick(double mine, double theirs) {
        return Double.isNaN(mine) || mine <= 0 ? theirs : mine;
    }
}
