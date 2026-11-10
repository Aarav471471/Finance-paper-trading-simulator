package com.papertrader.ui;

import com.papertrader.model.Asset;
import com.papertrader.model.Holding;
import com.papertrader.model.User;
import com.papertrader.service.AuthService;
import com.papertrader.service.MarketService;
import com.papertrader.service.PortfolioService;
import com.papertrader.service.TradingService;
import com.papertrader.ui.components.ExecutionLedgerPanel;
import com.papertrader.ui.components.OrderTerminalPanel;
import com.papertrader.ui.components.PortfolioPerformanceChart;
import com.papertrader.ui.components.StatCard;
import com.papertrader.ui.components.TerminalFooter;
import com.papertrader.ui.components.TerminalHeader;
import com.papertrader.ui.components.TickerPanel;
import com.papertrader.ui.components.WatchlistPanel;
import com.papertrader.ui.three.Portfolio3DView;
import com.papertrader.util.Formatter;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * PaperTrader 3D - Production Simulation Terminal Dashboard.
 * Reconstructed strictly according to the full terminal HTML specification:
 * - Compact top header with search (⌘K), live UTC clock, status badges
 * - 4 Financial summary cards with sparklines & Net Worth blue ring
 * - Compact horizontal ticker marquee with leading "Ticker" badge
 * - 3-Column Workspace:
 *     1. Left (22%): Market Watchlist [Watchlist, Gainers, Losers] + Ratio Bar
 *     2. Center (53%): 3D Volumetric Matrix + Portfolio Performance Line Chart
 *     3. Right (25%): Order Execution Terminal + Execution Ledger
 * - Compact bottom information footer strip with allocation breakdown and telemetry
 */
public class DashboardPanel extends BasePanel {

    private final AuthService authService;
    private final MarketService marketService;
    private final PortfolioService portfolioService;
    private final TradingService tradingService;
    private final double startingCash;
    private final Consumer<String> onNavigate;

    // Header & Ticker
    private final TerminalHeader terminalHeader;
    private final TickerPanel tickerPanel;

    // 4 Financial Summary Cards
    private final StatCard cashCard = new StatCard("Available Cash", StatCard.IconType.CASH);
    private final StatCard investedCard = new StatCard("Holdings Value", StatCard.IconType.HOLDINGS);
    private final StatCard netWorthCard = new StatCard("Net Worth", StatCard.IconType.NET_WORTH);
    private final StatCard profitCard = new StatCard("Total Profit & Loss", StatCard.IconType.PROFIT_LOSS);

    // 3-Column Components
    private final WatchlistPanel watchlistPanel;
    private final Portfolio3DView portfolio3D = new Portfolio3DView();
    private final PortfolioPerformanceChart performanceChart = new PortfolioPerformanceChart();
    private final OrderTerminalPanel orderTerminal;
    private final ExecutionLedgerPanel ledgerPanel;
    private final TerminalFooter terminalFooter = new TerminalFooter();

    public DashboardPanel(AuthService authService, MarketService marketService, PortfolioService portfolioService,
                          TradingService tradingService, double startingCash) {
        this(authService, marketService, portfolioService, tradingService, startingCash, null);
    }

    public DashboardPanel(AuthService authService, MarketService marketService, PortfolioService portfolioService,
                          TradingService tradingService, double startingCash, Consumer<String> onNavigate) {
        super("", ""); // Header handled by TerminalHeader
        this.authService = authService;
        this.marketService = marketService;
        this.portfolioService = portfolioService;
        this.tradingService = tradingService;
        this.startingCash = startingCash;
        this.onNavigate = onNavigate;

        setLayout(new BorderLayout());
        setBackground(Theme.APP_BG);
        setBorder(null);

        // Column 3 Panels
        this.orderTerminal = new OrderTerminalPanel(authService, marketService, tradingService, this::refresh);
        this.ledgerPanel = new ExecutionLedgerPanel(tradingService, () -> {
            if (onNavigate != null) onNavigate.accept("History");
        });

        // Column 1 Watchlist
        this.watchlistPanel = new WatchlistPanel(marketService, asset -> {
            orderTerminal.setTargetAsset(asset);
        });

        // Header & Ticker
        this.terminalHeader = new TerminalHeader(query -> watchlistPanel.selectSymbol(query));
        this.tickerPanel = new TickerPanel(marketService);

        // 3D Symbol Selection Link
        this.portfolio3D.setOnSelectSymbol(symbol -> {
            marketService.findBySymbol(symbol).ifPresent(orderTerminal::setTargetAsset);
        });

        // Assemble Top Section (Header + Cards + Ticker)
        JPanel topWrapper = new JPanel(new BorderLayout(0, 5));
        topWrapper.setOpaque(false);
        topWrapper.add(terminalHeader, BorderLayout.NORTH);

        JPanel cardsAndTicker = new JPanel(new BorderLayout(0, 5));
        cardsAndTicker.setOpaque(false);
        cardsAndTicker.setBorder(BorderFactory.createEmptyBorder(6, 8, 2, 8));

        JPanel cardsGrid = new JPanel(new GridLayout(1, 4, 8, 0));
        cardsGrid.setOpaque(false);
        cardsGrid.add(cashCard);
        cardsGrid.add(investedCard);
        cardsGrid.add(netWorthCard);
        cardsGrid.add(profitCard);

        cardsAndTicker.add(cardsGrid, BorderLayout.NORTH);
        cardsAndTicker.add(tickerPanel, BorderLayout.SOUTH);
        topWrapper.add(cardsAndTicker, BorderLayout.SOUTH);

        add(topWrapper, BorderLayout.NORTH);

        // Assemble 3-Column Workspace (Left 22%, Center 53%, Right 25%)
        JPanel workspace = buildThreeColumnWorkspace();
        add(workspace, BorderLayout.CENTER);

        // Bottom Footer
        add(terminalFooter, BorderLayout.SOUTH);
    }

    private JPanel buildThreeColumnWorkspace() {
        JPanel workspace = new JPanel(new GridBagLayout());
        workspace.setOpaque(false);
        workspace.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(0, 0, 0, 4);

        // 1. LEFT COLUMN: Watchlist (22%)
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.22;
        gbc.weighty = 1.0;
        workspace.add(watchlistPanel, gbc);

        // 2. CENTER COLUMN: 3D Matrix + Performance Chart (53%)
        JPanel centerCol = new JPanel(new GridLayout(2, 1, 0, 6));
        centerCol.setOpaque(false);

        JPanel matrixCard = buildMatrixContainer();
        centerCol.add(matrixCard);
        centerCol.add(performanceChart);

        gbc.gridx = 1;
        gbc.weightx = 0.53;
        gbc.insets = new Insets(0, 4, 0, 4);
        workspace.add(centerCol, gbc);

        // 3. RIGHT COLUMN: Order Execution + Execution Ledger (25%)
        JPanel rightCol = new JPanel(new GridLayout(2, 1, 0, 6));
        rightCol.setOpaque(false);
        rightCol.add(orderTerminal);
        rightCol.add(ledgerPanel);

        gbc.gridx = 2;
        gbc.weightx = 0.25;
        gbc.insets = new Insets(0, 4, 0, 0);
        workspace.add(rightCol, gbc);

        return workspace;
    }

    private JPanel buildMatrixContainer() {
        JPanel card = new JPanel(new BorderLayout(0, 2));
        card.setBackground(Theme.CARD_BG);
        card.setBorder(Theme.terminalCardBorder());

        // Header matching HTML
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_CUSTOM));
        header.setPreferredSize(new Dimension(100, 24));

        JPanel titles = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        titles.setOpaque(false);

        JLabel glyph = new JLabel("◫");
        glyph.setFont(new Font("SansSerif", Font.BOLD, 12));
        glyph.setForeground(Theme.ACCENT);

        JLabel title = new JLabel("3D PORTFOLIO VOLUMETRIC MATRIX");
        title.setFont(Theme.TINY.deriveFont(Font.BOLD, 10f));
        title.setForeground(Color.WHITE);

        JLabel webgl = new JLabel("WEBGL V2");
        webgl.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 7f));
        webgl.setForeground(Theme.ACCENT);
        webgl.setOpaque(true);
        webgl.setBackground(Theme.ACCENT_BG);
        webgl.setBorder(BorderFactory.createEmptyBorder(1, 3, 1, 3));

        titles.add(glyph);
        titles.add(title);
        titles.add(webgl);
        header.add(titles, BorderLayout.WEST);

        JPanel tools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 1));
        tools.setOpaque(false);

        JButton isoBtn = new JButton("Iso");
        JButton topBtn = new JButton("Top");
        JButton rotBtn = new JButton("↻ Rotate");
        JButton zoomIn = new JButton("+");
        JButton zoomOut = new JButton("-");
        JButton resetBtn = new JButton("Reset");

        JButton[] allBtns = {isoBtn, topBtn, rotBtn, zoomIn, zoomOut, resetBtn};
        for (JButton b : allBtns) {
            b.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 8.5f));
            b.setForeground(Theme.TXT_SECONDARY);
            b.setBackground(Theme.SURFACE_SUB);
            b.setBorder(BorderFactory.createCompoundBorder(
                    new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 3, 1),
                    BorderFactory.createEmptyBorder(1, 5, 1, 5)));
            b.setFocusPainted(false);
            b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        }

        // Active state for Iso
        isoBtn.setBackground(Theme.ACCENT);
        isoBtn.setForeground(Color.WHITE);

        isoBtn.addActionListener(e -> {
            portfolio3D.setIsoView();
            isoBtn.setBackground(Theme.ACCENT);
            isoBtn.setForeground(Color.WHITE);
            topBtn.setBackground(Theme.SURFACE_SUB);
            topBtn.setForeground(Theme.TXT_SECONDARY);
        });

        topBtn.addActionListener(e -> {
            portfolio3D.setTopView();
            topBtn.setBackground(Theme.ACCENT);
            topBtn.setForeground(Color.WHITE);
            isoBtn.setBackground(Theme.SURFACE_SUB);
            isoBtn.setForeground(Theme.TXT_SECONDARY);
        });

        rotBtn.addActionListener(e -> portfolio3D.toggleAutoRotate());
        zoomIn.addActionListener(e -> portfolio3D.zoomIn());
        zoomOut.addActionListener(e -> portfolio3D.zoomOut());
        resetBtn.addActionListener(e -> {
            portfolio3D.resetView();
            isoBtn.setBackground(Theme.ACCENT);
            isoBtn.setForeground(Color.WHITE);
            topBtn.setBackground(Theme.SURFACE_SUB);
            topBtn.setForeground(Theme.TXT_SECONDARY);
        });

        tools.add(isoBtn);
        tools.add(topBtn);
        tools.add(rotBtn);
        tools.add(zoomIn);
        tools.add(zoomOut);
        tools.add(resetBtn);

        header.add(tools, BorderLayout.EAST);

        card.add(header, BorderLayout.NORTH);
        card.add(portfolio3D, BorderLayout.CENTER);
        return card;
    }

    @Override
    public void refresh() {
        User user = authService.getCurrentUser();
        if (user == null) return;

        terminalHeader.setUsername(user.getUsername());

        double cash = user.getAccount().getBalance();
        double invested = portfolioService.getTotalMarketValue(user);
        double netWorth = portfolioService.getNetWorth(user);
        double pnl = portfolioService.getTotalProfitLoss(user);
        double growth = startingCash > 0 ? ((netWorth - startingCash) / startingCash * 100.0) : 0.0;
        double costBasis = portfolioService.getTotalCostBasis(user);
        double pnlPct = costBasis > 0 ? (pnl / costBasis * 100.0) : 0.0;

        // 1. Stat Cards
        cashCard.setValue(Formatter.money(cash), Color.WHITE);
        cashCard.setChangePill("+1.8%", 1.8);
        cashCard.setNote("Available trading balance", Theme.TXT_MUTED);

        investedCard.setValue(Formatter.money(invested), Color.WHITE);
        investedCard.setChangePill("+3.42%", 3.42);
        investedCard.setNote("Current market value", Theme.TXT_MUTED);

        netWorthCard.setValue(Formatter.money(netWorth), Color.WHITE);
        netWorthCard.setChangePill(String.format("%s%.2f%%", growth >= 0 ? "+" : "", growth), growth);
        netWorthCard.setNote("Total account value", Theme.TXT_SECONDARY);

        profitCard.setValue(Formatter.signedMoney(pnl), Theme.colorFor(pnl));
        profitCard.setChangePill(String.format("%s%.2f%%", pnl >= 0 ? "+" : "", pnlPct), pnl);
        profitCard.setNote("Overall simulated return", Theme.TXT_MUTED);

        // 2. 3D Matrix
        List<Portfolio3DView.Bar> bars = new ArrayList<>();
        for (Holding h : user.getPortfolio().getHoldings()) {
            double mktVal = portfolioService.getMarketValue(h);
            double holdingPnl = portfolioService.getProfitLoss(h);
            double holdingPnlPct = portfolioService.getProfitLossPercent(h);
            String name = marketService.findBySymbol(h.getSymbol()).map(Asset::getName).orElse(h.getSymbol());
            bars.add(new Portfolio3DView.Bar(h.getSymbol(), name, mktVal, holdingPnl, h.getAverageCost(), h.getQuantity(), holdingPnlPct));
        }
        portfolio3D.update(bars);

        // 3. Performance Chart
        performanceChart.updateData(user, portfolioService, marketService, tradingService, startingCash);

        // 4. Watchlist
        watchlistPanel.refreshList();

        // 5. Order Terminal
        orderTerminal.recalculate();

        // 6. Execution Ledger
        ledgerPanel.updateData(user);

        // 7. Footer Strip
        terminalFooter.updateData(user, portfolioService, marketService);
    }
}
