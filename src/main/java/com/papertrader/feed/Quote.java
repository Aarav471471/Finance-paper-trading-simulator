package com.papertrader.feed;

/**
 * One price answer from an API: the current price and the previous close.
 * (A Java "record" is a short way to write a small read-only data class.)
 */
public record Quote(double price, double previousClose) {
}
