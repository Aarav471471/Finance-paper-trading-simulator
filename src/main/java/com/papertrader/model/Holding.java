package com.papertrader.model;

import com.papertrader.interfaces.Exportable;

/** How many units of one asset the user owns, and the average price paid. */
public class Holding implements Exportable {

    private final String symbol;
    private int quantity;
    private double averageCost;

    public Holding(String symbol, int quantity, double averageCost) {
        this.symbol = symbol;
        this.quantity = quantity;
        this.averageCost = averageCost;
    }

    /** Buying more updates the average cost: (old cost + new cost) / total units. */
    public void addUnits(int units, double price) {
        double totalCost = averageCost * quantity + price * units;
        quantity += units;
        averageCost = totalCost / quantity;
    }

    public void removeUnits(int units) {
        if (units > quantity) {
            throw new IllegalArgumentException("Cannot remove more units than owned");
        }
        quantity -= units;
    }

    public String getSymbol() {
        return symbol;
    }

    public int getQuantity() {
        return quantity;
    }

    public double getAverageCost() {
        return averageCost;
    }

    /** Total amount paid for the units still held. */
    public double getCostBasis() {
        return averageCost * quantity;
    }

    @Override
    public String toCsvRow() {
        return symbol + "," + quantity + "," + averageCost;
    }
}
