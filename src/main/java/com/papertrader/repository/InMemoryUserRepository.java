package com.papertrader.repository;

import com.papertrader.interfaces.Repository;
import com.papertrader.model.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Keeps users only in memory (lost when the app closes). Used in unit tests.
 * LISKOV SUBSTITUTION: it can replace FileUserRepository anywhere a
 * Repository<User, String> is expected, and everything still works.
 */
public class InMemoryUserRepository implements Repository<User, String> {

    private final Map<String, User> users = new HashMap<>();

    @Override
    public void save(User user) {
        users.put(user.getUsername().toLowerCase(), user);
    }

    @Override
    public Optional<User> findById(String username) {
        return Optional.ofNullable(users.get(username.toLowerCase()));
    }

    @Override
    public List<User> findAll() {
        return new ArrayList<>(users.values());
    }
}
