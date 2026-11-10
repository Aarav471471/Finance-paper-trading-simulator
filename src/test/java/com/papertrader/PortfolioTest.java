package com.papertrader;

import com.papertrader.model.Portfolio;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioTest {

    @Test
    void averageCostIsWeighted() {
        Portfolio portfolio = new Portfolio();
        portfolio.addUnits("AAPL", 10, 100);   // cost 1000
        portfolio.addUnits("AAPL", 10, 200);   // cost 2000
        assertEquals(20, portfolio.getQuantity("AAPL"));
        assertEquals(150, portfolio.getHoldings().get(0).getAverageCost(), 0.001);
    }

    @Test
    void sellingEverythingRemovesTheHolding() {
        Portfolio portfolio = new Portfolio();
        portfolio.addUnits("BTC", 2, 50_000);
        portfolio.removeUnits("BTC", 2);
        assertTrue(portfolio.isEmpty());
    }
}
