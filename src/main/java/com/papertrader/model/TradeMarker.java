package com.papertrader.model;

/** A buy or sell that happened at the candle starting at this time (drawn as an arrow on charts). */
public record TradeMarker(long time, OrderSide side) {
}
