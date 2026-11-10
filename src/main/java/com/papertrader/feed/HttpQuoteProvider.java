package com.papertrader.feed;

import com.papertrader.interfaces.QuoteProvider;

import java.io.IOException;

/**
 * ABSTRACT parent for providers that download JSON over HTTP.
 * The download code is written once here (code reuse through INHERITANCE);
 * subclasses only build the URL and read the JSON.
 */
public abstract class HttpQuoteProvider implements QuoteProvider {

    /** Downloads a URL and returns the body text, or throws IOException on any problem. */
    protected String get(String url) throws IOException {
        return Http.get(url);
    }
}
