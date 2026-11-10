package com.papertrader.model;

/**
 * One candlestick: the open, high, low and close price during a time period, plus volume.
 * time = start of the period, in milliseconds since 1970 (UTC).
 * A record is immutable, so to update a candle we create a new one (see withPrice).
 */
public record Candle(long time, double open, double high, double low, double close, double volume) {

    /** The same candle after a new trade at this price. */
    public Candle withPrice(double price) {
        return new Candle(time, open, Math.max(high, price), Math.min(low, price), price, volume);
    }

    public boolean isGreen() {
        return close >= open;
    }
}
