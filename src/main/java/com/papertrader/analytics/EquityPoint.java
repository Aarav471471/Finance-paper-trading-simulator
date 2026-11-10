package com.papertrader.analytics;

/** The total account value at one moment (time in ms since 1970). */
public record EquityPoint(long time, double value) {
}
