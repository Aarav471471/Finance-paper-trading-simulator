package com.papertrader.feed;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** The one place that downloads JSON over HTTP (used by all quote providers and CoinGecko). */
public final class Http {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private Http() {
    }

    /** Downloads a URL and returns the body text, or throws IOException on any problem. */
    public static String get(String url) throws IOException {
        return get(url, Map.of());
    }

    /** GET with extra headers, e.g. "Authorization: Bearer ..." for APIs that need a key (Groww). */
    public static String get(String url, Map<String, String> headers) throws IOException {
        return send(builder(url, headers).GET().build());
    }

    /** POST a JSON body and return the answer text (used to log in to Groww). */
    public static String postJson(String url, Map<String, String> headers, String json) throws IOException {
        return send(builder(url, headers)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build());
    }

    private static HttpRequest.Builder builder(String url, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "Mozilla/5.0 (PaperTrader3D student project)")
                .header("Accept", "application/json");
        headers.forEach(builder::header);
        return builder;
    }

    private static String send(HttpRequest request) throws IOException {
        try {
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new HttpStatusException(response.statusCode());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }

    /** A non-200 answer. Keeps the status code so callers can react to 401 (expired login). */
    public static final class HttpStatusException extends IOException {
        private final int statusCode;

        public HttpStatusException(int statusCode) {
            super("HTTP " + statusCode);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }
}
