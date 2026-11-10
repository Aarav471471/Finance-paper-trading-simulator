package com.papertrader.model;

import java.util.List;

/**
 * Company facts and ratios from Yahoo Finance. Any number Yahoo does not report is NaN,
 * and the screen shows "N/A" for it instead of inventing a value.
 * Money values are in the asset's own currency (INR for NSE shares, USD otherwise).
 */
public record Fundamentals(
        double marketCap, double peRatio, double eps, double bookValue,
        double returnOnEquity, double returnOnAssets, double debtToEquity,
        double enterpriseValue, double evToEbitda, double freeCashFlow, double profitMargin,
        double targetMedianPrice, int analystCount, String recommendation,
        String description, String sector, String industry, int employees, String city, String country,
        String ceo, double insidersPercent, double institutionsPercent, int institutionsCount,
        List<FinancialYear> years) {

    /** EBIT / interest paid, from the newest year that has both numbers. */
    public double interestCoverage() {
        for (FinancialYear y : years) {
            if (!Double.isNaN(y.ebit()) && !Double.isNaN(y.interestExpense()) && y.interestExpense() != 0) {
                return y.ebit() / Math.abs(y.interestExpense());
            }
        }
        return Double.NaN;
    }
}
