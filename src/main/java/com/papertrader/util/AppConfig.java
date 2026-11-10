package com.papertrader.util;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Reads settings from "config.properties" (next to pom.xml) or from environment variables.
 * Environment variables win, so a key never has to be written into a file.
 *
 *   data.mode       / PAPERTRADER_DATA_MODE : live (default) or simulated
 *   finnhub.apiKey  / FINNHUB_API_KEY       : free key from finnhub.io
 */
public final class AppConfig {

    private final Properties properties = new Properties();

    private AppConfig() {
    }

    public static AppConfig load() {
        AppConfig config = new AppConfig();
        Path file = Paths.get("config.properties");
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                config.properties.load(reader);
            } catch (IOException e) {
                System.err.println("Could not read config.properties: " + e.getMessage());
            }
        }
        return config;
    }

    private String get(String propertyName, String envName, String defaultValue) {
        String fromEnv = System.getenv(envName);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        String fromFile = properties.getProperty(propertyName);
        if (fromFile != null && !fromFile.isBlank()) {
            return fromFile.trim();
        }
        return defaultValue;
    }

    public boolean useLiveData() {
        return !"simulated".equalsIgnoreCase(get("data.mode", "PAPERTRADER_DATA_MODE", "live"));
    }

    /** Empty string when no key is set. */
    public String finnhubApiKey() {
        return get("finnhub.apiKey", "FINNHUB_API_KEY", "");
    }
}
