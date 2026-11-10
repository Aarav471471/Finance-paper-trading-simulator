package com.papertrader.model;

/** INHERITANCE: a Stock IS-AN Asset. It adds its own field (sector) and overrides the abstract methods. */
public class Stock extends Asset {

    private final String sector;

    public Stock(String symbol, String name, double price, String sector) {
        super(symbol, name, price);   // call the parent (Asset) constructor
        this.sector = sector;
    }

    public String getSector() {
        return sector;
    }

    @Override
    public String getType() {
        return "Stock";
    }

    @Override
    public RiskLevel getRiskLevel() {
        return RiskLevel.MEDIUM;
    }

    @Override
    public double getSpreadPercent() {
        return 0.02;
    }

    @Override
    public double getVolatility() {
        return 0.004;   // about 0.4% per tick
    }
}
