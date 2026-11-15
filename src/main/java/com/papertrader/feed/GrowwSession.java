package com.papertrader.feed;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

/**
 * Logs in to the Groww Trade API and keeps the access token (SINGLE RESPONSIBILITY:
 * GrowwQuoteProvider only reads prices, this class only deals with the login).
 *
 * Two ways to log in, both read from environment variables / config.properties (see AppConfig):
 *  1. GROWW_ACCESS_TOKEN: a token you generated yourself on the Groww Cloud API Keys page.
 *  2. GROWW_API_KEY + GROWW_API_SECRET: the app makes a token itself with
 *     POST /v1/token/api/access, body {"key_type":"approval","checksum":SHA256(secret+timestamp),"timestamp":...}.
 *     Groww asks you to approve the key once a day on the Groww Cloud page.
 *
 * The key and token are never printed or written to a file.
 */
public class GrowwSession {

    static final String TOKEN_URL = "https://api.groww.in/v1/token/api/access";

    private final String apiKey;
    private final String apiSecret;
    private final String fixedToken;
    private volatile String token;

    public GrowwSession(String apiKey, String apiSecret, String accessToken) {
        this.apiKey = clean(apiKey);
        this.apiSecret = clean(apiSecret);
        this.fixedToken = clean(accessToken);
    }

    /** True when there is enough to log in (a token, or a key and its secret). */
    public boolean isConfigured() {
        return !fixedToken.isEmpty() || (!apiKey.isEmpty() && !apiSecret.isEmpty());
    }

    /** True when the app can make a new token by itself after the old one expires. */
    public boolean canRenew() {
        return !apiKey.isEmpty() && !apiSecret.isEmpty();
    }

    /** The token to send as "Authorization: Bearer ...". Logs in the first time it is needed. */
    public synchronized String accessToken() throws IOException {
        if (token == null) {
            token = canRenew() ? requestToken() : fixedToken;
        }
        if (token.isEmpty()) {
            throw new IOException("no Groww key set");
        }
        return token;
    }

    /** Forget the token (Groww said 401), so the next call logs in again. */
    public synchronized void invalidate() {
        token = canRenew() ? null : fixedToken;
    }

    private String requestToken() throws IOException {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        JSONObject body = new JSONObject()
                .put("key_type", "approval")
                .put("checksum", checksum(apiSecret, timestamp))
                .put("timestamp", timestamp);
        String json = Http.postJson(TOKEN_URL, Map.of("Authorization", "Bearer " + apiKey), body.toString());
        String newToken = parseToken(json);
        if (newToken == null) {
            throw new IOException("Groww login gave no token (approve the key on Groww Cloud today)");
        }
        return newToken;
    }

    /** SHA-256 of secret + timestamp, as lowercase hex (Groww's checksum rule). */
    static String checksum(String secret, String timestamp) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha.digest((secret + timestamp).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is missing from this Java", e);
        }
    }

    /** Reads {"token":"...","expiry":"...","isActive":true}. Returns null if there is no token. */
    static String parseToken(String json) {
        String value = new JSONObject(json).optString("token", "");
        return value.isBlank() ? null : value;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
