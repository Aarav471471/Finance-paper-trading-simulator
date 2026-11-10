package com.papertrader.interfaces;

import com.papertrader.model.Transaction;

import java.util.List;

/** Storage for transactions. Extends the generic Repository with two extra queries. */
public interface TransactionRepository extends Repository<Transaction, Integer> {
    List<Transaction> findByUsername(String username);

    int nextId();

    /** Used by "Reset account". */
    void deleteByUsername(String username);
}
