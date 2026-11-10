package com.papertrader.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * All the holdings of one user, stored by symbol.
 * HIGH COHESION: this class only tracks units and average cost. It does not know
 * prices or money; PortfolioService does the value / profit calculations.
 */
public class Portfolio {

    private final Map<String, Holding> holdings = new LinkedHashMap<>();

    public void addUnits(String symbol, int units, double price) {
        Holding holding = holdings.get(symbol);
        if (holding == null) {
            holdings.put(symbol, new Holding(symbol, units, price));
        } else {
            holding.addUnits(units, price);
        }
    }

    public void removeUnits(String symbol, int units) {
        Holding holding = holdings.get(symbol);
        if (holding == null) {
            throw new IllegalArgumentException("You do not own " + symbol);
        }
        holding.removeUnits(units);
        if (holding.getQuantity() == 0) {
            holdings.remove(symbol);
        }
    }

    public int getQuantity(String symbol) {
        Holding holding = holdings.get(symbol);
        return holding == null ? 0 : holding.getQuantity();
    }

    /** Returns a copy, so callers cannot change the internal map (encapsulation). */
    public List<Holding> getHoldings() {
        return new ArrayList<>(holdings.values());
    }

    /** Removes every holding (used by "Reset account"). */
    public void clear() {
        holdings.clear();
    }

    public boolean isEmpty() {
        return holdings.isEmpty();
    }

    /** Used by the repository when loading saved data from disk. */
    public void restoreHolding(Holding holding) {
        holdings.put(holding.getSymbol(), holding);
    }
}
