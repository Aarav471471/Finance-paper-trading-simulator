package com.papertrader.interfaces;

/**
 * STRATEGY pattern: different ways to charge a brokerage fee.
 * TradingService depends on this interface, not on a concrete class (Dependency Inversion),
 * so the fee rule can be swapped in App.java without touching TradingService.
 */
public interface FeeCalculator {
    double calculateFee(double tradeValue);

    String getDescription();
}
