package com.papertrader.repository;

import com.papertrader.analytics.EquityPoint;
import com.papertrader.interfaces.EquityRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Equity history kept in memory only. Used by tests and as the parent of the file version. */
public class InMemoryEquityRepository implements EquityRepository {

    private final Map<String, List<EquityPoint>> history = new HashMap<>();

    @Override
    public void add(String username, EquityPoint point) {
        history.computeIfAbsent(username.toLowerCase(), u -> new ArrayList<>()).add(point);
    }

    @Override
    public List<EquityPoint> findByUsername(String username) {
        return new ArrayList<>(history.getOrDefault(username.toLowerCase(), new ArrayList<>()));
    }

    @Override
    public void deleteByUsername(String username) {
        history.remove(username.toLowerCase());
    }

    protected Map<String, List<EquityPoint>> all() {
        return history;
    }
}
