package com.papertrader.model;

/** One news headline about an asset. */
public record NewsItem(String title, String publisher, long timeMillis, String link) {
}
