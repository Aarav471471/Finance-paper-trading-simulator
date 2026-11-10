package com.papertrader.repository;

import com.papertrader.analytics.EquityPoint;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Saves equity snapshots to data/equity.csv as: username,timeMillis,value */
public class FileEquityRepository extends InMemoryEquityRepository {

    private final Path file;

    public FileEquityRepository(Path dataDirectory) {
        this.file = dataDirectory.resolve("equity.csv");
        try {
            Files.createDirectories(dataDirectory);
            if (Files.exists(file)) {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    String[] p = line.split(",");
                    if (p.length == 3) {
                        super.add(p[0], new EquityPoint(Long.parseLong(p[1]), Double.parseDouble(p[2])));
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read equity history", e);
        }
    }

    @Override
    public void add(String username, EquityPoint point) {
        super.add(username, point);
        try {
            Files.write(file, List.of(username.toLowerCase() + "," + point.time() + "," + point.value()),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save equity point", e);
        }
    }

    @Override
    public void deleteByUsername(String username) {
        super.deleteByUsername(username);
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, List<EquityPoint>> entry : all().entrySet()) {
            for (EquityPoint p : entry.getValue()) {
                lines.add(entry.getKey() + "," + p.time() + "," + p.value());
            }
        }
        try {
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not rewrite equity history", e);
        }
    }
}
