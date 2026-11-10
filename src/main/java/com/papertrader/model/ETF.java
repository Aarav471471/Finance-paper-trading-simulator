package com.papertrader.model;

/** INHERITANCE: an ETF IS-AN Asset. An ETF tracks an index, so it is the least risky. */
public class ETF extends Asset {

    private final String trackedIndex;

    public ETF(String symbol, String name, double price, String trackedIndex) {
        super(symbol, name, price);
        this.trackedIndex = trackedIndex;
    }

    public String getTrackedIndex() {
        return trackedIndex;
    }

    @Override
    public String getType() {
        return "ETF";
    }

    @Override
    public RiskLevel getRiskLevel() {
        return RiskLevel.LOW;
    }

    @Override
    public double getSpreadPercent() {
        return 0.01;
    }

    @Override
    public double getVolatility() {
        return 0.002;   // about 0.2% per tick
    }
}
