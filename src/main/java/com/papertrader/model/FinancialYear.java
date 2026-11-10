package com.papertrader.model;

/** One year of a company's income statement. Missing numbers are NaN. */
public record FinancialYear(String year, double revenue, double costOfRevenue, double grossProfit,
                            double operatingIncome, double ebit, double interestExpense, double netIncome) {
}
