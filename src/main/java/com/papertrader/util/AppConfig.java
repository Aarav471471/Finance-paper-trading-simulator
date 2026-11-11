package com.papertrader.util;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Reads settings, first match wins:
 *   1. environment variables
 *   2. JVM options "-Dpapertrader.<name>=..." (the installers pass the Finnhub key this way)
 *   3. "config.properties" in the working folder (next to pom.xml)
 *   4. "~/.papertrader/config.properties" (for the installed app)
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
        // Load the home-folder file first so the working-folder file overrides it.
        config.loadFile(Paths.get(System.getProperty("user.home"), ".papertrader", "config.properties"));
        config.loadFile(Paths.get("config.properties"));
        return config;
    }

    private void loadFile(Path file) {
        if (!Files.exists(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            System.err.println("Could not read " + file + ": " + e.getMessage());
        }
    }

    private String get(String propertyName, String envName, String defaultValue) {
        String fromEnv = System.getenv(envName);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        String fromJvm = System.getProperty("papertrader." + propertyName);
        if (fromJvm != null && !fromJvm.isBlank()) {
            return fromJvm.trim();
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

    /**
     * Folder where accounts, trades and equity history are saved.
     * Default "data" next to pom.xml. A leading "~" means the user's home folder,
     * which the installed app uses because its own folder may be read-only.
     */
    public Path dataDirectory() {
        String dir = System.getProperty("papertrader.dataDir");
        if (dir == null || dir.isBlank()) {
            dir = get("data.dir", "PAPERTRADER_DATA_DIR", "data");
        }
        dir = dir.trim();
        if (dir.equals("~") || dir.startsWith("~/") || dir.startsWith("~\\")) {
            return Paths.get(System.getProperty("user.home"), dir.substring(1).replaceFirst("^[/\\\\]", ""));
        }
        return Paths.get(dir);
    }

    /** Empty string when no key is set. */
    public String finnhubApiKey() {
        return get("finnhub.apiKey", "FINNHUB_API_KEY", "");
    }
}
