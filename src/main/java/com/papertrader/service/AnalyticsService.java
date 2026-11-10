package com.papertrader.service;

import com.papertrader.analytics.EquityPoint;
import com.papertrader.analytics.PerformanceAnalyzer;
import com.papertrader.analytics.PerformanceReport;
import com.papertrader.interfaces.EquityRepository;
import com.papertrader.model.OrderSide;
import com.papertrader.model.Transaction;
import com.papertrader.model.User;

import java.util.ArrayList;
import java.util.List;

/**
 * SINGLE RESPONSIBILITY: risk and performance analytics for a user's real paper account.
 * It records the account value every few seconds (the equity curve) and hands that,
 * plus every closed trade's profit, to PerformanceAnalyzer.
 */
public class AnalyticsService {

    private final PortfolioService portfolioService;
    private final TradingService tradingService;
    private final EquityRepository equityRepository;
    private final double startingCash;

    public AnalyticsService(PortfolioService portfolioService, TradingService tradingService,
                            EquityRepository equityRepository, double startingCash) {
        this.portfolioService = portfolioService;
        this.tradingService = tradingService;
        this.equityRepository = equityRepository;
        this.startingCash = startingCash;
    }

    /** Saves "net worth right now" as one point on the equity curve. */
    public void recordSnapshot(User user) {
        equityRepository.add(user.getUsername(),
                new EquityPoint(System.currentTimeMillis(), portfolioService.getNetWorth(user)));
    }

    /** The equity curve, starting from the starting cash. */
    public List<EquityPoint> getEquityCurve(User user) {
        List<EquityPoint> points = equityRepository.findByUsername(user.getUsername());
        List<EquityPoint> curve = new ArrayList<>();
        long start = points.isEmpty() ? System.currentTimeMillis() - 1000 : points.get(0).time() - 1000;
        curve.add(new EquityPoint(start, startingCash));
        curve.addAll(points);
        return curve;
    }

    /** Profit of every SELL (= every closed trade). */
    public List<Double> getClosedTradeProfits(User user) {
        List<Double> profits = new ArrayList<>();
        for (Transaction t : tradingService.getTransactionHistory(user)) {
            if (t.getSide() == OrderSide.SELL) {
                profits.add(t.getRealizedPnl());
            }
        }
        return profits;
    }

    public PerformanceReport getReport(User user) {
        return PerformanceAnalyzer.analyze(getEquityCurve(user), getClosedTradeProfits(user));
    }

    /** Wipes the equity history (part of "Reset account"). */
    public void clearHistory(User user) {
        equityRepository.deleteByUsername(user.getUsername());
    }
}
