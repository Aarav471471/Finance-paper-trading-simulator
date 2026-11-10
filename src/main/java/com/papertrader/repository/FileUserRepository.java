package com.papertrader.repository;

import com.papertrader.model.Holding;
import com.papertrader.model.User;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Saves users to two simple CSV files so data survives restarting the app:
 *   data/users.csv     -> username,passwordHash,cashBalance
 *   data/holdings.csv  -> username,symbol,quantity,averageCost
 *
 * INHERITANCE: it extends InMemoryUserRepository and only ADDS file saving/loading.
 */
public class FileUserRepository extends InMemoryUserRepository {

    private final Path usersFile;
    private final Path holdingsFile;

    public FileUserRepository(Path dataDirectory) {
        try {
            Files.createDirectories(dataDirectory);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        this.usersFile = dataDirectory.resolve("users.csv");
        this.holdingsFile = dataDirectory.resolve("holdings.csv");
        loadFromDisk();
    }

    @Override
    public void save(User user) {
        super.save(user);     // update the in-memory map (parent behaviour)
        writeToDisk();        // then write everything to the files (extra behaviour)
    }

    private void loadFromDisk() {
        if (!Files.exists(usersFile)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(usersFile, StandardCharsets.UTF_8)) {
                String[] p = line.split(",");
                if (p.length == 3) {
                    super.save(new User(p[0], p[1], Double.parseDouble(p[2])));
                }
            }
            if (Files.exists(holdingsFile)) {
                for (String line : Files.readAllLines(holdingsFile, StandardCharsets.UTF_8)) {
                    String[] p = line.split(",");
                    if (p.length == 4) {
                        findById(p[0]).ifPresent(user -> user.getPortfolio().restoreHolding(
                                new Holding(p[1], Integer.parseInt(p[2]), Double.parseDouble(p[3]))));
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read saved users", e);
        }
    }

    private void writeToDisk() {
        List<String> userLines = new ArrayList<>();
        List<String> holdingLines = new ArrayList<>();
        for (User user : findAll()) {
            userLines.add(user.getUsername() + "," + user.getPasswordHash() + "," + user.getAccount().getBalance());
            for (Holding holding : user.getPortfolio().getHoldings()) {
                holdingLines.add(user.getUsername() + "," + holding.toCsvRow());
            }
        }
        try {
            Files.write(usersFile, userLines, StandardCharsets.UTF_8);
            Files.write(holdingsFile, holdingLines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save users", e);
        }
    }
}
