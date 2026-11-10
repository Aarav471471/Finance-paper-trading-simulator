package com.papertrader.model;

/** INHERITANCE: a Crypto IS-AN Asset. Crypto is the most volatile and the riskiest. */
public class Crypto extends Asset {

    private final String blockchain;

    public Crypto(String symbol, String name, double price, String blockchain) {
        super(symbol, name, price);
        this.blockchain = blockchain;
    }

    public String getBlockchain() {
        return blockchain;
    }

    @Override
    public String getType() {
        return "Crypto";
    }

    @Override
    public RiskLevel getRiskLevel() {
        return RiskLevel.HIGH;
    }

    @Override
    public double getVolatility() {
        return 0.012;   // about 1.2% per tick
    }
}
