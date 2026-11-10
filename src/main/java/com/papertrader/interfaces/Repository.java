package com.papertrader.interfaces;

import java.util.List;
import java.util.Optional;

/**
 * Generic storage interface. T is the type stored, ID is the type of its key.
 * Services talk to this interface, so they do not care whether data is stored
 * in memory, in files or in a database (Dependency Inversion + low coupling).
 */
public interface Repository<T, ID> {
    void save(T item);

    Optional<T> findById(ID id);

    List<T> findAll();
}
