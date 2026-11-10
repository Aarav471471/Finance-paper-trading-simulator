package com.papertrader.model;

/**
 * ABSTRACTION: Asset is the abstract parent of everything that can be traded.
 * It cannot be created directly ("new Asset()" is not allowed) because an asset
 * must always be a concrete kind: Stock, Crypto or ETF.
 *
 * INHERITANCE: Stock, Crypto and ETF extend this class and reuse its fields/methods.
 *
 * ENCAPSULATION: all fields are private. The price can only be changed through
 * setPrice(), which rejects invalid values.
 */
public abstract class Asset {

    private final String symbol;
    private final String name;
    private double price;
    private double previousClose;   // yesterday's closing price, used for "change %"

    protected Asset(String symbol, String name, double price) {
        if (price <= 0) {
            throw new IllegalArgumentException("Price must be positive");
        }
        this.symbol = symbol;
        this.name = name;
        this.price = price;
        this.previousClose = price;
    }

    // ---- Abstract methods: every subclass MUST give its own answer (runtime polymorphism) ----

    /** e.g. "Stock", "Crypto", "ETF" */
    public abstract String getType();

    /** How risky this kind of asset is. */
    public abstract RiskLevel getRiskLevel();

    /** How much the price moves on each tick of the simulator (0.01 = about 1%). */
    public abstract double getVolatility();

    // ---- Getters / setters (encapsulation) ----

    public String getSymbol() {
        return symbol;
    }

    public String getName() {
        return name;
    }

    public double getPrice() {
        return price;
    }

    public double getPreviousClose() {
        return previousClose;
    }

    /** Set by the live price feed when the real previous close is known. */
    public void setPreviousClose(double previousClose) {
        if (previousClose > 0) {
            this.previousClose = previousClose;
        }
    }

    public void setPrice(double newPrice) {
        if (newPrice <= 0) {
            throw new IllegalArgumentException("Price must be positive");
        }
        this.price = newPrice;
    }

    /** Percentage change since the previous close (like every trading app shows). */
    public double getChangePercent() {
        return (price - previousClose) / previousClose * 100.0;
    }

    @Override
    public String toString() {
        return symbol + " - " + name;
    }
}
