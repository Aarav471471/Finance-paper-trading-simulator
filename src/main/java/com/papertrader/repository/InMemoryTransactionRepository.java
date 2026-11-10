package com.papertrader.repository;

import com.papertrader.interfaces.TransactionRepository;
import com.papertrader.model.Transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Keeps transactions in a list in memory. Used in tests and as the parent of the file version. */
public class InMemoryTransactionRepository implements TransactionRepository {

    private final List<Transaction> transactions = new ArrayList<>();

    @Override
    public void save(Transaction transaction) {
        transactions.add(transaction);
    }

    @Override
    public Optional<Transaction> findById(Integer id) {
        for (Transaction t : transactions) {
            if (t.getId() == id) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }

    @Override
    public List<Transaction> findAll() {
        return new ArrayList<>(transactions);
    }

    @Override
    public List<Transaction> findByUsername(String username) {
        List<Transaction> result = new ArrayList<>();
        for (Transaction t : transactions) {
            if (t.getUsername().equalsIgnoreCase(username)) {
                result.add(t);
            }
        }
        return result;
    }

    @Override
    public int nextId() {
        return transactions.size() + 1;
    }
}
