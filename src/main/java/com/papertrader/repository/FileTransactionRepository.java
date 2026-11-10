package com.papertrader.repository;

import com.papertrader.model.OrderSide;
import com.papertrader.model.Transaction;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.List;

/** Saves every transaction as one line in data/transactions.csv (append-only, like a ledger). */
public class FileTransactionRepository extends InMemoryTransactionRepository {

    private final Path file;

    public FileTransactionRepository(Path dataDirectory) {
        this.file = dataDirectory.resolve("transactions.csv");
        try {
            Files.createDirectories(dataDirectory);
            if (Files.exists(file)) {
                loadFromDisk();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read saved transactions", e);
        }
    }

    @Override
    public void save(Transaction transaction) {
        super.save(transaction);
        try {
            Files.write(file, List.of(transaction.toCsvRow()), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save transaction", e);
        }
    }

    private void loadFromDisk() throws IOException {
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String[] p = line.split(",");
            if (p.length == 9) {
                super.save(new Transaction(Integer.parseInt(p[0]), p[1], LocalDateTime.parse(p[2]), p[3],
                        OrderSide.valueOf(p[4]), p[5], Integer.parseInt(p[6]),
                        Double.parseDouble(p[7]), Double.parseDouble(p[8])));
            }
        }
    }
}
