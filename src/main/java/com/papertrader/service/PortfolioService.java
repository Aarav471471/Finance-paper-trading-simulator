package com.papertrader.service;

import com.papertrader.model.Asset;
import com.papertrader.model.Holding;
import com.papertrader.model.User;

/**
 * SINGLE RESPONSIBILITY: all money calculations about a portfolio
 * (current value, profit/loss, net worth). It uses MarketService for live prices.
 */
public class PortfolioService {

    private final MarketService marketService;

    public PortfolioService(MarketService marketService) {
        this.marketService = marketService;
    }

    public double getCurrentPrice(Holding holding) {
        return marketService.findBySymbol(holding.getSymbol())
                .map(Asset::getPrice)
                .orElse(holding.getAverageCost());   // unknown asset: fall back to cost
    }

    public double getMarketValue(Holding holding) {
        return getCurrentPrice(holding) * holding.getQuantity();
    }

    public double getProfitLoss(Holding holding) {
        return getMarketValue(holding) - holding.getCostBasis();
    }

    public double getProfitLossPercent(Holding holding) {
        double cost = holding.getCostBasis();
        return cost == 0 ? 0 : getProfitLoss(holding) / cost * 100.0;
    }

    public double getTotalMarketValue(User user) {
        double total = 0;
        for (Holding holding : user.getPortfolio().getHoldings()) {
            total += getMarketValue(holding);
        }
        return total;
    }

    public double getTotalCostBasis(User user) {
        double total = 0;
        for (Holding holding : user.getPortfolio().getHoldings()) {
            total += holding.getCostBasis();
        }
        return total;
    }

    public double getTotalProfitLoss(User user) {
        return getTotalMarketValue(user) - getTotalCostBasis(user);
    }

    /** Cash + value of everything owned. */
    public double getNetWorth(User user) {
        return user.getAccount().getBalance() + getTotalMarketValue(user);
    }
}
