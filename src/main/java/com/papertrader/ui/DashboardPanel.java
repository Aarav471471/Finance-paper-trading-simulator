package com.papertrader.ui;

import com.papertrader.model.Holding;
import com.papertrader.model.Transaction;
import com.papertrader.model.User;
import com.papertrader.service.AuthService;
import com.papertrader.service.MarketService;
import com.papertrader.service.PortfolioService;
import com.papertrader.service.TradingService;
import com.papertrader.ui.components.StatCard;
import com.papertrader.ui.components.TickerPanel;
import com.papertrader.ui.three.Portfolio3DView;
import com.papertrader.util.Formatter;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;

/** Home page: money summary cards, live ticker, 3D holdings chart and recent trades. */
public class DashboardPanel extends BasePanel {

    private final AuthService authService;
    private final PortfolioService portfolioService;
    private final TradingService tradingService;

    private final StatCard cashCard = new StatCard("Cash available");
    private final StatCard investedCard = new StatCard("Holdings value");
    private final StatCard netWorthCard = new StatCard("Net worth");
    private final StatCard profitCard = new StatCard("Total P&L");
    private final Portfolio3DView portfolio3D = new Portfolio3DView();
    private final DefaultListModel<String> recentTrades = new DefaultListModel<>();
    private final double startingCash;

    public DashboardPanel(AuthService authService, MarketService marketService, PortfolioService portfolioService,
                          TradingService tradingService, double startingCash) {
        super("Dashboard", "Your account at a glance. Prices update every second.");
        this.authService = authService;
        this.portfolioService = portfolioService;
        this.tradingService = tradingService;
        this.startingCash = startingCash;

        JPanel cards = new JPanel(new GridLayout(1, 4, 14, 0));
        cards.setOpaque(false);
        cards.add(cashCard);
        cards.add(investedCard);
        cards.add(netWorthCard);
        cards.add(profitCard);

        JPanel top = new JPanel(new BorderLayout(0, 14));
        top.setOpaque(false);
        top.add(cards, BorderLayout.NORTH);
        top.add(new TickerPanel(marketService), BorderLayout.SOUTH);

        JPanel chartCard = card("Your portfolio in 3D  (drag to rotate, scroll to zoom)", portfolio3D);

        JList<String> list = new JList<>(recentTrades);
        list.setBackground(Theme.CARD);
        list.setFont(Theme.SMALL);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(null);
        JPanel recentCard = card("Recent trades", scroll);
        recentCard.setPreferredSize(new Dimension(330, 100));

        JPanel middle = new JPanel(new BorderLayout(14, 0));
        middle.setOpaque(false);
        middle.add(chartCard, BorderLayout.CENTER);
        middle.add(recentCard, BorderLayout.EAST);

        JPanel content = new JPanel(new BorderLayout(0, 14));
        content.setOpaque(false);
        content.add(top, BorderLayout.NORTH);
        content.add(middle, BorderLayout.CENTER);
        add(content, BorderLayout.CENTER);
    }

    private JPanel card(String title, java.awt.Component body) {
        JPanel card = new JPanel(new BorderLayout(0, 10));
        card.setBackground(Theme.CARD);
        card.setBorder(Theme.cardBorder());
        JLabel label = new JLabel(title);
        label.setFont(Theme.HEADING);
        label.setForeground(Theme.TEXT);
        card.add(label, BorderLayout.NORTH);
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    @Override
    public void refresh() {
        User user = authService.getCurrentUser();
        if (user == null) {
            return;
        }
        double cash = user.getAccount().getBalance();
        double invested = portfolioService.getTotalMarketValue(user);
        double netWorth = portfolioService.getNetWorth(user);
        double pnl = portfolioService.getTotalProfitLoss(user);
        double growth = (netWorth - startingCash) / startingCash * 100;

        cashCard.setValue(Formatter.money(cash), Theme.TEXT);
        cashCard.setNote("Fee: " + tradingService.getFeeDescription(), Theme.MUTED);
        investedCard.setValue(Formatter.money(invested), Theme.TEXT);
        investedCard.setNote(user.getPortfolio().getHoldings().size() + " asset(s) held", Theme.MUTED);
        netWorthCard.setValue(Formatter.money(netWorth), Theme.TEXT);
        netWorthCard.setNote(Formatter.percent(growth) + " since start", Theme.colorFor(growth));
        profitCard.setValue(Formatter.signedMoney(pnl), Theme.colorFor(pnl));
        profitCard.setNote("Unrealised, on open holdings", Theme.MUTED);

        List<Portfolio3DView.Bar> bars = new ArrayList<>();
        for (Holding h : user.getPortfolio().getHoldings()) {
            bars.add(new Portfolio3DView.Bar(h.getSymbol(), Math.round(portfolioService.getMarketValue(h)),
                    Math.signum(portfolioService.getProfitLoss(h))));
        }
        portfolio3D.update(bars);

        List<Transaction> history = tradingService.getTransactionHistory(user);
        recentTrades.clear();
        for (int i = history.size() - 1; i >= 0 && i >= history.size() - 12; i--) {
            Transaction t = history.get(i);
            recentTrades.addElement(" " + t.getSide() + "  " + t.getQuantity() + " " + t.getSymbol()
                    + " @ " + Formatter.money(t.getPrice()));
        }
        if (recentTrades.isEmpty()) {
            recentTrades.addElement(" No trades yet. Go to Trade to place one.");
        }
    }
}
