package com.papertrader.feed;

import com.papertrader.interfaces.QuoteProvider;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * ABSTRACT parent for providers that download JSON over HTTP.
 * The download code is written once here (code reuse through INHERITANCE);
 * subclasses only build the URL and read the JSON.
 */
public abstract class HttpQuoteProvider implements QuoteProvider {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** Downloads a URL and returns the body text, or throws IOException on any problem. */
    protected String get(String url) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "Mozilla/5.0 (PaperTrader3D student project)")
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }
}
