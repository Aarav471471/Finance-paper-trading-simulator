package com.papertrader.ui;

import com.papertrader.analytics.EquityPoint;
import com.papertrader.analytics.PerformanceAnalyzer;
import com.papertrader.analytics.PerformanceReport;
import com.papertrader.model.User;
import com.papertrader.service.AnalyticsService;
import com.papertrader.service.AuthService;
import com.papertrader.service.PortfolioService;
import com.papertrader.service.TradingService;
import com.papertrader.ui.components.LineChartPanel;
import com.papertrader.ui.components.StatCard;
import com.papertrader.util.Formatter;

import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;

/** Performance & risk analytics of the user's own paper account. */
public class AnalyticsPanel extends BasePanel {

    private final AuthService authService;
    private final PortfolioService portfolioService;
    private final TradingService tradingService;
    private final AnalyticsService analyticsService;
    private final double startingCash;

    private final StatCard returnCard = new StatCard("Total return");
    private final StatCard realizedCard = new StatCard("Realized P&L");
    private final StatCard unrealizedCard = new StatCard("Unrealized P&L");
    private final StatCard drawdownCard = new StatCard("Max drawdown");
    private final StatCard sharpeCard = new StatCard("Sharpe ratio");
    private final StatCard sortinoCard = new StatCard("Sortino ratio");
    private final StatCard winRateCard = new StatCard("Win rate");
    private final StatCard profitFactorCard = new StatCard("Profit factor");
    private final LineChartPanel equityChart = new LineChartPanel();

    public AnalyticsPanel(AuthService authService, PortfolioService portfolioService, TradingService tradingService,
                          AnalyticsService analyticsService, double startingCash) {
        super("Analytics", "Risk-adjusted performance of your account. Net worth is recorded every 15 seconds.");
        this.authService = authService;
        this.portfolioService = portfolioService;
        this.tradingService = tradingService;
        this.analyticsService = analyticsService;
        this.startingCash = startingCash;

        JPanel cards = new JPanel(new GridLayout(2, 4, 14, 14));
        cards.setOpaque(false);
        for (StatCard card : new StatCard[]{returnCard, realizedCard, unrealizedCard, drawdownCard,
                sharpeCard, sortinoCard, winRateCard, profitFactorCard}) {
            cards.add(card);
        }
        equityChart.setBorder(Theme.cardBorder());

        JPanel content = new JPanel(new BorderLayout(0, 14));
        content.setOpaque(false);
        content.add(cards, BorderLayout.NORTH);
        content.add(equityChart, BorderLayout.CENTER);
        add(content, BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        User user = authService.getCurrentUser();
        if (user == null) {
            return;
        }
        PerformanceReport report = analyticsService.getReport(user);
        double netWorth = portfolioService.getNetWorth(user);
        double totalReturn = (netWorth / startingCash - 1) * 100;
        double realized = tradingService.getRealizedProfitLoss(user);
        double unrealized = portfolioService.getTotalProfitLoss(user);

        returnCard.setValue(Formatter.percent(totalReturn), Theme.colorFor(totalReturn));
        returnCard.setNote("Net worth " + Formatter.money(netWorth), Theme.MUTED);
        realizedCard.setValue(Formatter.signedMoney(realized), Theme.colorFor(realized));
        realizedCard.setNote("Locked in by your sells", Theme.MUTED);
        unrealizedCard.setValue(Formatter.signedMoney(unrealized), Theme.colorFor(unrealized));
        unrealizedCard.setNote("On holdings you still own", Theme.MUTED);
        drawdownCard.setValue("-" + PerformanceAnalyzer.format(report.maxDrawdownPercent(), "%"),
                report.maxDrawdownPercent() > 0 ? Theme.RED : Theme.TEXT);
        drawdownCard.setNote("Biggest fall from a peak", Theme.MUTED);
        sharpeCard.setValue(PerformanceAnalyzer.format(report.sharpeRatio(), ""), Theme.TEXT);
        sharpeCard.setNote(Double.isNaN(report.sharpeRatio()) ? "Needs 3+ days of history"
                : "Return per unit of risk (daily, annualised)", Theme.MUTED);
        sortinoCard.setValue(PerformanceAnalyzer.format(report.sortinoRatio(), ""), Theme.TEXT);
        sortinoCard.setNote(Double.isNaN(report.sortinoRatio()) ? "Needs 3+ days of history"
                : "Like Sharpe, only counts downside", Theme.MUTED);
        winRateCard.setValue(PerformanceAnalyzer.format(report.winRatePercent(), "%"), Theme.TEXT);
        winRateCard.setNote(report.closedTrades() + " closed trade(s)", Theme.MUTED);
        profitFactorCard.setValue(PerformanceAnalyzer.format(report.profitFactor(), ""), Theme.TEXT);
        profitFactorCard.setNote("Money won / money lost", Theme.MUTED);

        List<Double> values = new ArrayList<>();
        for (EquityPoint p : analyticsService.getEquityCurve(user)) {
            values.add(p.value());
        }
        values.add(netWorth);
        equityChart.setData("Equity curve (account value over time)", values);
    }
}
