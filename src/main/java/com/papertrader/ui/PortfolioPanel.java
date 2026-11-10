package com.papertrader.ui;

import com.papertrader.service.AnalyticsService;
import com.papertrader.service.MarketDataService;
import com.papertrader.util.MarketFormat;
import com.papertrader.util.MarketHours;
import com.papertrader.service.ChartService;
import java.util.HashMap;
import com.papertrader.interfaces.Exportable;
import com.papertrader.model.Asset;
import com.papertrader.model.Holding;
import com.papertrader.model.OrderSide;
import com.papertrader.model.Transaction;
import com.papertrader.model.User;
import com.papertrader.service.AuthService;
import com.papertrader.service.MarketService;
import com.papertrader.service.PortfolioService;
import com.papertrader.service.TradingService;
import com.papertrader.ui.components.PortfolioPerformanceChart;
import com.papertrader.util.CsvExporter;
import com.papertrader.util.Formatter;

import javax.swing.AbstractCellEditor;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * PaperTrader 3D - Production Portfolio Management Terminal.
 * 
 * Features:
 * 1. Top Command & Action Bar: Breadcrumbs, Live Status Pill, Dual IST/UTC Live Clock,
 *    Export CSV, Report (PDF) modal, and interactive Rebalance Simulator.
 * 2. Five Core Financial KPI Tiles: Total Portfolio Value (with dual INR/USD & area sparkline),
 *    Total Invested (with Capital Deployed bar), Total Returns, Today's P&L, Available Cash (with Margin bar).
 * 3. 65% / 35% Workspace: Interactive Performance Chart with benchmarks & timeframes +
 *    Asset Allocation Matrix Donut visualization (Indian Eq, US Eq, Crypto, Liquid Cash).
 * 4. Comprehensive Holdings Table ("My Investments") with category filter tabs (All, Indian, US Tech, Crypto),
 *    live search filtering, rich cell renderers, 7D micro trend sparklines, inline Action buttons (Analyze, Buy, Sell),
 *    and an interactive Expandable Drawer Strip showing First Purchased, Last Executed, Realized P&L, Weight, Sector & Alert.
 * 5. Sector Exposure Progress Breakdown (6 industry domains) + 4 Core Analytical Tiles (Top Alpha, Max Drag, Win Ratio,
 *    Beta/Volatility) + Health & Concentration Risk Intelligence Card.
 * 6. Periodic P&L Breakdown (Day, 1W, 1M, 1Y) with Tax-Loss Harvesting pill + Recent Portfolio Activity Mini-Ledger.
 */
public class PortfolioPanel extends BasePanel {

    /** USD -> INR rate. Updated from the live Yahoo "INR=X" quote; 83.07 is only the start value until it loads. */
    private static double USD_TO_INR = 83.07;
    private MarketDataService marketData;
    private final JLabel statusLbl = new JLabel("NSE");

    private final AuthService authService;
    private final MarketService marketService;
    private final PortfolioService portfolioService;
    private final TradingService tradingService;
    private final Consumer<String> navHandler;

    // Live clock label
    private final JLabel clockLabel = new JLabel("IST --:--:-- | UTC --:--:--");

    // 5 KPI Cards
    private final ValueKpiCard cardNetWorth = new ValueKpiCard("TOTAL PORTFOLIO VALUE", true);
    private final InvestedKpiCard cardInvested = new InvestedKpiCard("TOTAL INVESTED");
    private final SparkKpiCard cardReturns = new SparkKpiCard("TOTAL RETURNS", "All-Time");
    private final SparkKpiCard cardTodayPnl = new SparkKpiCard("TODAY'S P&L", "Intraday");
    private final CashKpiCard cardCash = new CashKpiCard("AVAILABLE CASH");

    // Row 2: Performance Chart & Asset Allocation Donut
    private PortfolioPerformanceChart performanceChart;
    private AnalyticsService analytics;
    private double startingCash = 100000.0;
    private final AssetAllocationCard allocationCard = new AssetAllocationCard();

    // Row 3: Holdings Table
    private final JLabel holdingsCountBadge = new JLabel("0 Positions");
    private final JTextField searchField = new JTextField();
    private String activeCategoryFilter = "ALL";
    private final JButton tabAll = createTabBtn("All", true);
    private final JButton tabIndian = createTabBtn("Indian Equities", false);
    private final JButton tabUs = createTabBtn("US Tech", false);
    private final JButton tabCrypto = createTabBtn("Crypto", false);
    private final HoldingsTableModel tableModel = new HoldingsTableModel();
    private final JTable holdingsTable = new JTable(tableModel);

    // Expandable Drawer row
    private final PositionDrawerPanel drawerPanel = new PositionDrawerPanel();

    // Table Summary Bar
    private final JLabel tableFooterSummaryLbl = new JLabel("Showing all 0 verified simulated market entries across 3 clearing channels");
    private final JLabel tableFooterTurnoverLbl = new JLabel("Portfolio Turnover (30d): -");
    private final JLabel tableFooterMarginLbl = new JLabel("Unrealized Margin: +$0.00");

    // Row 4: Sector Exposure & Risk
    private final SectorExposureCard sectorCard = new SectorExposureCard();
    private final AnalyticalGridCard analyticalCard = new AnalyticalGridCard();
    private final HealthScoreCard healthScoreCard = new HealthScoreCard();

    // Row 5: Periodic P&L & Recent Activity
    private final PeriodicPnlCard periodicPnlCard = new PeriodicPnlCard();
    private final RecentActivityCard recentActivityCard = new RecentActivityCard();

    /** 3-arg constructor for backwards compatibility. */
    public PortfolioPanel(AuthService authService, MarketService marketService, PortfolioService portfolioService) {
        this(authService, marketService, portfolioService, null, null);
    }

    /** Primary full terminal constructor. */
    public PortfolioPanel(AuthService authService, MarketService marketService, PortfolioService portfolioService,
                          TradingService tradingService, Consumer<String> navHandler) {
        super("Portfolio Management", "Comprehensive overview of active holdings, performance alpha, and risk intelligence.");
        this.authService = authService;
        this.marketService = marketService;
        this.portfolioService = portfolioService;
        this.tradingService = tradingService;
        this.navHandler = navHandler;

        setLayout(new BorderLayout());
        setBackground(Theme.APP_BG);

        // Build Scrollable Workspace
        JPanel workspace = new JPanel();
        workspace.setLayout(new BoxLayout(workspace, BoxLayout.Y_AXIS));
        workspace.setBackground(Theme.APP_BG);
        workspace.setBorder(BorderFactory.createEmptyBorder(12, 16, 20, 16));

        // 1. Top Command & Action Bar
        workspace.add(buildTopCommandBar());
        workspace.add(Box.createVerticalStrut(14));

        // 2. Row 1: Five Core KPI Tiles
        workspace.add(buildKpiGrid());
        workspace.add(Box.createVerticalStrut(14));

        // 3. Row 2: Performance Chart & Asset Allocation Workspace
        workspace.add(buildChartAndAllocationRow());
        workspace.add(Box.createVerticalStrut(14));

        // 4. Row 3: Comprehensive Holdings Table
        workspace.add(buildHoldingsSection());
        workspace.add(Box.createVerticalStrut(14));

        // 5. Row 4: Sector Exposure & Risk Intelligence
        workspace.add(buildSectorAndRiskRow());
        workspace.add(Box.createVerticalStrut(14));

        // 6. Row 5: Periodic P&L & Recent Activity Ledger
        workspace.add(buildPeriodicAndActivityRow());

        JScrollPane scrollPane = new JScrollPane(workspace);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setBackground(Theme.APP_BG);
        scrollPane.getViewport().setBackground(Theme.APP_BG);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        add(scrollPane, BorderLayout.CENTER);

        // Start Clock Timer (1 sec tick)
        Timer clockTimer = new Timer(1000, e -> updateClock());
        clockTimer.setRepeats(true);
        clockTimer.start();
        updateClock();
    }

    // =========================================================================
    // SECTION 1: TOP COMMAND & ACTION BAR
    // =========================================================================

    private JPanel buildTopCommandBar() {
        JPanel bar = new JPanel(new BorderLayout(12, 8));
        bar.setBackground(Theme.SURFACE_SUB);
        bar.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 10, 1),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)
        ));

        // Left Side: Breadcrumb & Title with Status Pill
        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setOpaque(false);

        // Breadcrumbs: Home / Portfolio Management
        JPanel breadcrumb = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        breadcrumb.setOpaque(false);
        JLabel bcHome = new JLabel("Home");
        bcHome.setFont(Theme.TINY);
        bcHome.setForeground(Theme.TXT_MUTED);
        JLabel bcSep = new JLabel("/");
        bcSep.setFont(Theme.TINY);
        bcSep.setForeground(Theme.TXT_MUTED);
        JLabel bcCurrent = new JLabel("Portfolio Management");
        bcCurrent.setFont(Theme.TINY);
        bcCurrent.setForeground(Theme.ACCENT);
        breadcrumb.add(bcHome);
        breadcrumb.add(bcSep);
        breadcrumb.add(bcCurrent);
        left.add(breadcrumb);
        left.add(Box.createVerticalStrut(3));

        // Title row: Portfolio Overview + Live Pill
        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        titleRow.setOpaque(false);
        JLabel title = new JLabel("Portfolio Overview");
        title.setFont(new Font("Inter", Font.BOLD, 19));
        title.setForeground(Theme.TXT_PRIMARY);
        titleRow.add(title);

        JPanel statusPill = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 2)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.GREEN_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(new Color(34, 197, 94, 90));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                // Glowing dot
                g2.setColor(Theme.GAIN);
                g2.fillOval(7, (getHeight() - 6) / 2, 6, 6);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        statusPill.setOpaque(false);
        statusPill.setBorder(BorderFactory.createEmptyBorder(2, 16, 2, 8));
        statusLbl.setFont(Theme.MICRO);
        statusLbl.setForeground(Theme.GAIN);
        statusPill.add(statusLbl);
        titleRow.add(statusPill);
        left.add(titleRow);

        bar.add(left, BorderLayout.WEST);

        // Right Side: Live Clock, Export CSV, Report (PDF), Rebalance Simulator
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        right.setOpaque(false);

        // Clock Pill
        JPanel clockPill = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 4)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.SIDEBAR_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(Theme.BORDER_CUSTOM);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                // Mini Clock Vector Icon
                g2.setColor(Theme.TXT_MUTED);
                int cy = getHeight() / 2;
                g2.drawOval(8, cy - 5, 10, 10);
                g2.drawLine(13, cy, 13, cy - 3);
                g2.drawLine(13, cy, 15, cy);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        clockPill.setOpaque(false);
        clockPill.setBorder(BorderFactory.createEmptyBorder(2, 22, 2, 8));
        clockLabel.setFont(Theme.MONO_SMALL);
        clockLabel.setForeground(Theme.TXT_SECONDARY);
        clockPill.add(clockLabel);
        right.add(clockPill);

        // Export CSV Button
        JButton btnExport = createVectorBtn("Export CSV", new Color(0x1E, 0x29, 0x3B), Theme.BORDER_CUSTOM, Theme.TXT_PRIMARY, 1);
        btnExport.addActionListener(e -> onExportCsv());
        right.add(btnExport);

        // Report (PDF) Button
        JButton btnReport = createVectorBtn("Report (PDF)", new Color(0x1E, 0x29, 0x3B), Theme.BORDER_CUSTOM, Theme.TXT_PRIMARY, 2);
        btnReport.addActionListener(e -> onShowReport());
        right.add(btnReport);

        // Rebalance Simulator Button
        JButton btnRebalance = createVectorBtn("Rebalance Simulator", Theme.ACCENT, Theme.ACCENT, Color.WHITE, 3);
        btnRebalance.addActionListener(e -> onShowRebalanceModal());
        right.add(btnRebalance);

        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private void updateClock() {
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Kolkata"));
        marketService.findBySymbol("RELIANCE").ifPresent(a -> {
            statusLbl.setText(MarketHours.status(a));
            statusLbl.setForeground(MarketHours.isOpen(a) ? Theme.GAIN : Theme.WARNING);
        });
        String ist = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        LocalDateTime utc = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).toLocalDateTime();
        String utcStr = utc.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        clockLabel.setText("IST " + ist + "  |  UTC " + utcStr);
    }

    // =========================================================================
    // SECTION 2: ROW 1 - FIVE CORE KPI TILES
    // =========================================================================

    private JPanel buildKpiGrid() {
        JPanel grid = new JPanel(new GridLayout(1, 5, 10, 0));
        grid.setOpaque(false);
        grid.add(cardNetWorth);
        grid.add(cardInvested);
        grid.add(cardReturns);
        grid.add(cardTodayPnl);
        grid.add(cardCash);
        return grid;
    }

    // =========================================================================
    // SECTION 3: ROW 2 - PERFORMANCE CHART & ASSET ALLOCATION WORKSPACE
    // =========================================================================

    private JPanel buildChartAndAllocationRow() {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);

        // Left 65%: Performance Chart Card
        JPanel chartCard = new JPanel(new BorderLayout());
        chartCard.setBackground(Theme.CARD_BG);
        chartCard.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 10, 1),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)
        ));

        performanceChart = new PortfolioPerformanceChart();
        performanceChart.setPreferredSize(new Dimension(650, 310));
        chartCard.add(performanceChart, BorderLayout.CENTER);

        // Right 35%: Asset Allocation Matrix
        allocationCard.setPreferredSize(new Dimension(380, 310));

        row.add(chartCard, BorderLayout.CENTER);
        row.add(allocationCard, BorderLayout.EAST);
        return row;
    }

    // =========================================================================
    // SECTION 4: ROW 3 - COMPREHENSIVE HOLDINGS TABLE ("MY INVESTMENTS")
    // =========================================================================

    private JPanel buildHoldingsSection() {
        JPanel section = new JPanel(new BorderLayout(0, 0));
        section.setBackground(Theme.CARD_BG);
        section.setBorder(new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 10, 1));

        // 1. Toolbar Header
        JPanel toolbar = new JPanel(new BorderLayout(10, 8));
        toolbar.setBackground(Theme.SURFACE_SUB);
        toolbar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_CUSTOM),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)
        ));

        // Title + Badge
        JPanel titleBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        titleBox.setOpaque(false);
        JLabel title = new JLabel("My Active Holdings");
        title.setFont(new Font("Inter", Font.BOLD, 15));
        title.setForeground(Theme.TXT_PRIMARY);
        titleBox.add(title);

        holdingsCountBadge.setFont(Theme.MICRO);
        holdingsCountBadge.setForeground(Theme.ACCENT);
        holdingsCountBadge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER_CUSTOM, 1),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)
        ));
        titleBox.add(holdingsCountBadge);
        toolbar.add(titleBox, BorderLayout.WEST);

        // Right Controls: Filter Category Tabs & Live Search
        JPanel rightControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightControls.setOpaque(false);

        // Tabs container
        JPanel tabsBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        tabsBox.setBackground(Theme.SIDEBAR_BG);
        tabsBox.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER_CUSTOM, 1),
                BorderFactory.createEmptyBorder(2, 2, 2, 2)
        ));
        tabsBox.add(tabAll);
        tabsBox.add(tabIndian);
        tabsBox.add(tabUs);
        tabsBox.add(tabCrypto);
        rightControls.add(tabsBox);

        tabAll.addActionListener(e -> setCategoryFilter("ALL"));
        tabIndian.addActionListener(e -> setCategoryFilter("INDIAN"));
        tabUs.addActionListener(e -> setCategoryFilter("US"));
        tabCrypto.addActionListener(e -> setCategoryFilter("CRYPTO"));

        // Search Input Field
        searchField.setPreferredSize(new Dimension(180, 26));
        searchField.setBackground(Theme.SIDEBAR_BG);
        searchField.setForeground(Theme.TXT_PRIMARY);
        searchField.setCaretColor(Theme.ACCENT);
        searchField.setFont(Theme.SMALL);
        searchField.putClientProperty("JTextField.placeholderText", "Filter positions...");
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER_CUSTOM, 1),
                BorderFactory.createEmptyBorder(2, 8, 2, 8)
        ));
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { applyFilters(); }
            @Override public void removeUpdate(DocumentEvent e) { applyFilters(); }
            @Override public void changedUpdate(DocumentEvent e) { applyFilters(); }
        });
        rightControls.add(searchField);

        toolbar.add(rightControls, BorderLayout.EAST);
        section.add(toolbar, BorderLayout.NORTH);

        // 2. Table Setup
        holdingsTable.setRowHeight(38);
        holdingsTable.setBackground(Theme.CARD_BG);
        holdingsTable.setForeground(Theme.TXT_PRIMARY);
        holdingsTable.setGridColor(Theme.BORDER_SUBTLE);
        holdingsTable.setSelectionBackground(Theme.SURFACE_SUB);
        holdingsTable.setSelectionForeground(Theme.TXT_PRIMARY);
        holdingsTable.getTableHeader().setBackground(Theme.SIDEBAR_BG);
        holdingsTable.getTableHeader().setForeground(Theme.TXT_MUTED);
        holdingsTable.getTableHeader().setFont(Theme.MONO_SMALL);
        holdingsTable.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_CUSTOM));
        holdingsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Custom Column Renderers
        holdingsTable.getColumnModel().getColumn(0).setPreferredWidth(170);
        holdingsTable.getColumnModel().getColumn(0).setCellRenderer(new AssetCellRenderer());

        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
        rightRenderer.setHorizontalAlignment(SwingConstants.RIGHT);

        for (int i = 1; i <= 5; i++) {
            holdingsTable.getColumnModel().getColumn(i).setCellRenderer(new MoneyCellRenderer(i));
        }
        holdingsTable.getColumnModel().getColumn(6).setPreferredWidth(140);
        holdingsTable.getColumnModel().getColumn(6).setCellRenderer(new PnlCellRenderer());

        holdingsTable.getColumnModel().getColumn(7).setPreferredWidth(80);
        holdingsTable.getColumnModel().getColumn(7).setCellRenderer(new SparklineCellRenderer());

        holdingsTable.getColumnModel().getColumn(8).setPreferredWidth(150);
        holdingsTable.getColumnModel().getColumn(8).setCellRenderer(new ActionsCellRenderer());
        holdingsTable.getColumnModel().getColumn(8).setCellEditor(new ActionsCellEditor());

        // Row Selection Listener -> updates Drawer Panel
        holdingsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = holdingsTable.getSelectedRow();
                if (row >= 0 && row < tableModel.filteredList.size()) {
                    HoldingRowData data = tableModel.filteredList.get(row);
                    drawerPanel.updateHolding(data, firstBuyText(data.symbol()), lastTradeText(data.symbol()));
                    drawerPanel.setVisible(true);
                } else {
                    drawerPanel.setVisible(false);
                }
            }
        });

        JScrollPane tableScroll = new JScrollPane(holdingsTable);
        tableScroll.setBorder(BorderFactory.createEmptyBorder());
        tableScroll.getViewport().setBackground(Theme.CARD_BG);
        tableScroll.setPreferredSize(new Dimension(800, 240));

        JPanel tableArea = new JPanel(new BorderLayout());
        tableArea.setOpaque(false);
        tableArea.add(tableScroll, BorderLayout.CENTER);
        tableArea.add(drawerPanel, BorderLayout.SOUTH);
        drawerPanel.setVisible(false);

        section.add(tableArea, BorderLayout.CENTER);

        // 3. Table Footer / Summary Bar
        JPanel footer = new JPanel(new BorderLayout(10, 0));
        footer.setBackground(Theme.SURFACE_SUB);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER_CUSTOM),
                BorderFactory.createEmptyBorder(8, 14, 8, 14)
        ));

        tableFooterSummaryLbl.setFont(Theme.MICRO);
        tableFooterSummaryLbl.setForeground(Theme.TXT_MUTED);
        footer.add(tableFooterSummaryLbl, BorderLayout.WEST);

        JPanel footerRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 0));
        footerRight.setOpaque(false);

        tableFooterTurnoverLbl.setFont(Theme.MICRO);
        tableFooterTurnoverLbl.setForeground(Theme.TXT_SECONDARY);
        footerRight.add(tableFooterTurnoverLbl);

        tableFooterMarginLbl.setFont(Theme.MICRO);
        tableFooterMarginLbl.setForeground(Theme.GAIN);
        footerRight.add(tableFooterMarginLbl);

        footer.add(footerRight, BorderLayout.EAST);
        section.add(footer, BorderLayout.SOUTH);

        return section;
    }

    private JButton createTabBtn(String label, boolean active) {
        JButton btn = new JButton(label);
        btn.setFont(Theme.MICRO);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        setTabBtnStyle(btn, active);
        return btn;
    }

    private void setTabBtnStyle(JButton btn, boolean active) {
        if (active) {
            btn.setBackground(Theme.SURFACE_SUB);
            btn.setForeground(Theme.TXT_PRIMARY);
        } else {
            btn.setBackground(Theme.SIDEBAR_BG);
            btn.setForeground(Theme.TXT_MUTED);
        }
    }

    private void setCategoryFilter(String filter) {
        this.activeCategoryFilter = filter;
        setTabBtnStyle(tabAll, "ALL".equals(filter));
        setTabBtnStyle(tabIndian, "INDIAN".equals(filter));
        setTabBtnStyle(tabUs, "US".equals(filter));
        setTabBtnStyle(tabCrypto, "CRYPTO".equals(filter));
        applyFilters();
    }

    private void applyFilters() {
        tableModel.applyFilter(activeCategoryFilter, searchField.getText());
        int count = tableModel.filteredList.size();
        holdingsCountBadge.setText(count + " Positions");
        if (count > 0 && holdingsTable.getSelectedRow() < 0) {
            holdingsTable.setRowSelectionInterval(0, 0);
        }
    }

    // =========================================================================
    // SECTION 5: ROW 4 - SECTOR EXPOSURE & RISK INTELLIGENCE (6 cols / 6 cols)
    // =========================================================================

    private JPanel buildSectorAndRiskRow() {
        JPanel row = new JPanel(new GridLayout(1, 2, 12, 0));
        row.setOpaque(false);

        // Left (6 cols): Sector Exposure Progress Bar Breakdown
        row.add(sectorCard);

        // Right (6 cols): 2x2 Analytical KPI Grid + Health & Concentration Score
        JPanel rightCol = new JPanel();
        rightCol.setLayout(new BoxLayout(rightCol, BoxLayout.Y_AXIS));
        rightCol.setOpaque(false);
        rightCol.add(analyticalCard);
        rightCol.add(Box.createVerticalStrut(10));
        rightCol.add(healthScoreCard);

        row.add(rightCol);
        return row;
    }

    // =========================================================================
    // SECTION 6: ROW 5 - PERIODIC P&L & RECENT ACTIVITY LEDGER (7 cols / 5 cols)
    // =========================================================================

    private JPanel buildPeriodicAndActivityRow() {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);

        // Left (7 cols): Periodic P&L Breakdown
        periodicPnlCard.setPreferredSize(new Dimension(640, 220));
        row.add(periodicPnlCard, BorderLayout.CENTER);

        // Right (5 cols): Recent Portfolio Activity Mini-Ledger
        recentActivityCard.setPreferredSize(new Dimension(420, 220));
        row.add(recentActivityCard, BorderLayout.EAST);

        return row;
    }

    // =========================================================================
    // DATA REFRESH LOGIC
    // =========================================================================

    /** Real history for the performance chart (account values + SPY benchmark). */
    public void setDataSources(AnalyticsService analytics, ChartService charts, double startingCash) {
        this.analytics = analytics;
        this.startingCash = startingCash;
        if (performanceChart != null) {
            performanceChart.setDataSources(analytics, charts, marketService);
        }
    }

    /** Live data used for the USD/INR rate. */
    public void setMarketData(MarketDataService marketData) {
        this.marketData = marketData;
    }

    /** Transactions of the logged-in user, loaded in refresh(). */
    private List<Transaction> txHistory = List.of();

    /** "First Purchased" text for a symbol: date of its oldest BUY. */
    private String firstBuyText(String symbol) {
        return txHistory.stream().filter(t -> t.getSymbol().equals(symbol) && t.getSide() == OrderSide.BUY)
                .map(Transaction::getTimestamp).min(Comparator.naturalOrder())
                .map(t -> t.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))).orElse("N/A");
    }

    /** "Last Executed" text for a symbol: how long ago its newest trade was. */
    private String lastTradeText(String symbol) {
        return txHistory.stream().filter(t -> t.getSymbol().equals(symbol))
                .map(Transaction::getTimestamp).max(Comparator.naturalOrder())
                .map(t -> MarketFormat.timeAgo(t.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()))
                .orElse("N/A");
    }

    /** Draws a real value series as a line (optionally filled) scaled to the box. Nothing if fewer than 2 values. */
    static void drawSeries(Graphics2D g2, List<Double> values, int w, int h, Color color, boolean fill) {
        if (values == null || values.size() < 2) {
            return;
        }
        double min = Collections.min(values);
        double max = Collections.max(values);
        double range = max - min < 1e-9 ? 1 : max - min;
        Path2D line = new Path2D.Double();
        for (int i = 0; i < values.size(); i++) {
            double x = (double) i / (values.size() - 1) * w;
            double y = h - 3 - (values.get(i) - min) / range * (h - 6);
            if (i == 0) line.moveTo(x, y);
            else line.lineTo(x, y);
        }
        if (fill) {
            Path2D area = new Path2D.Double(line);
            area.lineTo(w, h);
            area.lineTo(0, h);
            area.closePath();
            g2.setPaint(new GradientPaint(0, 0, new Color(color.getRed(), color.getGreen(), color.getBlue(), 60),
                    0, h, new Color(color.getRed(), color.getGreen(), color.getBlue(), 0)));
            g2.fill(area);
        }
        g2.setColor(color);
        g2.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(line);
    }

    @Override
    public void refresh() {
        User user = authService.getCurrentUser();
        if (user == null) {
            return;
        }

        if (marketData != null) {
            marketData.index("INR=X").ifPresent(fx -> {
                if (fx.price() > 1) {
                    USD_TO_INR = fx.price();
                }
            });
        }
        txHistory = tradingService != null ? tradingService.getTransactionHistory(user) : List.of();
        List<Double> equity = new ArrayList<>();
        List<Double> todayEquity = new ArrayList<>();
        List<com.papertrader.analytics.EquityPoint> curve = analytics != null
                ? analytics.getEquityCurve(user) : List.of();
        long midnight = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        for (com.papertrader.analytics.EquityPoint ep : curve) {
            equity.add(ep.value());
            if (ep.time() >= midnight) {
                todayEquity.add(ep.value());
            }
        }

        double cash = user.getAccount().getBalance();
        double cost = portfolioService.getTotalCostBasis(user);
        double value = portfolioService.getTotalMarketValue(user);
        double netWorth = cash + value;
        double pnl = value - cost;
        double pnlPct = cost == 0 ? 0 : (pnl / cost * 100.0);

        // Realized P&L = profit locked in by sales (saved on every SELL transaction)
        Map<String, Double> realizedBySymbol = new HashMap<>();
        for (Transaction t : txHistory) {
            if (t.getSide() == OrderSide.SELL) {
                realizedBySymbol.merge(t.getSymbol(), t.getRealizedPnl(), Double::sum);
            }
        }
        double realized = tradingService.getRealizedProfitLoss(user);

        // 1. Update 5 KPI Cards
        cardNetWorth.updateValues(netWorth, pnl, pnlPct);
        equity.add(netWorth);
        todayEquity.add(netWorth);
        cardNetWorth.setSeries(equity);
        cardReturns.setSeries(equity);
        cardTodayPnl.setSeries(todayEquity);
        cardInvested.updateValues(cost, netWorth, user.getPortfolio().getHoldings().size());
        // total return = realized (closed trades) + unrealized (open positions)
        cardReturns.updateValues(pnl + realized, cost == 0 ? 0 : ((pnl + realized) / cost * 100.0), "Over Cost Basis");

        // Intraday calculation
        double todayPnl = calculateTodayPnl(user);
        double todayPct = netWorth > 0 ? (todayPnl / netWorth * 100.0) : 0;
        cardTodayPnl.updateValues(todayPnl, todayPct, "of Net Worth");

        cardCash.updateValues(cash, netWorth);

        // 2. Update Performance Chart
        if (performanceChart != null) {
            performanceChart.updateData(user, portfolioService, marketService, tradingService, startingCash);
        }

        // 3. Build HoldingRowData List
        List<HoldingRowData> rows = new ArrayList<>();
        double totalMktVal = Math.max(1.0, value);
        for (Holding h : user.getPortfolio().getHoldings()) {
            Optional<Asset> opt = marketService.findBySymbol(h.getSymbol());
            Asset a = opt.orElse(null);
            double cmp = a != null ? a.getPrice() : h.getAverageCost();
            double prevClose = (a != null && a.getPreviousClose() > 0) ? a.getPreviousClose() : cmp;
            double mktVal = cmp * h.getQuantity();
            double cBasis = h.getCostBasis();
            double hPnl = mktVal - cBasis;
            double hPnlPct = cBasis > 0 ? (hPnl / cBasis * 100.0) : 0;
            double weight = (mktVal / totalMktVal) * 100.0;

            String name = a != null ? a.getName() : h.getSymbol();
            String ex = getExchange(h.getSymbol(), a != null ? a.getType() : "");
            String existingSector = (a instanceof com.papertrader.model.Stock s) ? s.getSector() : "";
            String sector = getSector(h.getSymbol(), existingSector);
            List<Double> hist = marketService.getPriceHistory(h.getSymbol());

            rows.add(new HoldingRowData(h, a, h.getSymbol(), name, ex, sector,
                    h.getQuantity(), h.getAverageCost(), cmp, prevClose, cBasis, mktVal,
                    hPnl, hPnlPct, weight, hist, realizedBySymbol.getOrDefault(h.getSymbol(), 0.0)));
        }

        // 4. Update Table Model
        tableModel.setAllHoldings(rows);
        applyFilters();

        // 5. Update Table Footer
        long exchanges = rows.stream().map(HoldingRowData::exchange).distinct().count();
        tableFooterSummaryLbl.setText(String.format("Showing all %d simulated positions across %d exchange%s",
                rows.size(), exchanges, exchanges == 1 ? "" : "s"));
        // turnover = value bought + sold in the last 30 days, compared with today's net worth
        LocalDateTime monthAgo = LocalDateTime.now().minusDays(30);
        double traded = txHistory.stream().filter(t -> t.getTimestamp().isAfter(monthAgo))
                .mapToDouble(t -> t.getPrice() * t.getQuantity()).sum();
        tableFooterTurnoverLbl.setText(String.format(Locale.US, "Portfolio Turnover (30d): %.1f%%",
                netWorth > 0 ? traded / netWorth * 100 : 0));
        tableFooterMarginLbl.setText(String.format("Unrealized: %s   ·   Realized: %s",
                Formatter.signedMoney(pnl), Formatter.signedMoney(realized)));

        // 6. Update Asset Allocation Donut
        allocationCard.updateData(user, rows, cash, netWorth);

        // 7. Update Sector Exposure Card
        sectorCard.updateData(rows, totalMktVal);

        // 8. Update Analytical Grid Card & Health Card
        analyticalCard.updateData(rows, analytics != null ? analytics.getReport(user) : null);
        healthScoreCard.updateData(rows, totalMktVal);

        // 9. Update Periodic P&L
        periodicPnlCard.updateData(todayPnl, netWorth, curve, startingCash, realized, rows);

        // 10. Update Recent Activity
        recentActivityCard.updateTransactions(txHistory);
    }

    private double calculateTodayPnl(User user) {
        double today = 0.0;
        for (Holding h : user.getPortfolio().getHoldings()) {
            Optional<Asset> a = marketService.findBySymbol(h.getSymbol());
            if (a.isPresent()) {
                Asset asset = a.get();
                double cmp = asset.getPrice();
                double prev = asset.getPreviousClose() > 0 ? asset.getPreviousClose() : cmp;
                today += (cmp - prev) * h.getQuantity();
            }
        }
        return today;
    }

    // =========================================================================
    // ACTION HANDLERS: EXPORT CSV, PDF REPORT, REBALANCE SIMULATOR
    // =========================================================================

    private void onExportCsv() {
        User user = authService.getCurrentUser();
        if (user == null) return;

        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("papertrader_portfolio_holdings.csv"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            try {
                CsvExporter.export(user.getPortfolio().getHoldings(), "symbol,quantity,averageCost", file);
                JOptionPane.showMessageDialog(this,
                        "Portfolio holdings successfully exported to:\n" + file.getAbsolutePath(),
                        "Export Completed", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void onShowReport() {
        User user = authService.getCurrentUser();
        if (user == null) return;

        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Portfolio Valuation Statement (PDF Preview)", JDialog.ModalityType.APPLICATION_MODAL);
        dialog.setSize(620, 520);
        dialog.setLocationRelativeTo(this);

        JPanel p = new JPanel(new BorderLayout(12, 12));
        p.setBackground(Theme.CARD_BG);
        p.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        // Header
        JPanel hdr = new JPanel(new BorderLayout());
        hdr.setOpaque(false);
        JLabel title = new JLabel("PAPERTRADER 3D - OFFICIAL VALUATION STATEMENT");
        title.setFont(new Font("Inter", Font.BOLD, 15));
        title.setForeground(Theme.TXT_PRIMARY);
        JLabel sub = new JLabel("Generated on " + Formatter.dateTime(LocalDateTime.now()) + " for trader @" + user.getUsername());
        sub.setFont(Theme.MICRO);
        sub.setForeground(Theme.TXT_MUTED);
        hdr.add(title, BorderLayout.NORTH);
        hdr.add(sub, BorderLayout.SOUTH);
        p.add(hdr, BorderLayout.NORTH);

        // Body Content
        double cash = user.getAccount().getBalance();
        double cost = portfolioService.getTotalCostBasis(user);
        double val = portfolioService.getTotalMarketValue(user);
        double pnl = val - cost;

        StringBuilder sb = new StringBuilder();
        sb.append("--------------------------------------------------------------------------------\n");
        sb.append(String.format("ACCOUNT VALUE:          $%,.2f  (₹%,.2f INR)\n", cash + val, (cash + val) * USD_TO_INR));
        sb.append(String.format("COST BASIS:             $%,.2f\n", cost));
        sb.append(String.format("MARKET VALUE:           $%,.2f\n", val));
        sb.append(String.format("ALL-TIME RETURN:        %s$%,.2f (%.2f%%)\n", pnl >= 0 ? "+" : "-", Math.abs(pnl), cost > 0 ? pnl / cost * 100 : 0));
        sb.append(String.format("LIQUID CASH:            $%,.2f\n", cash));
        sb.append("--------------------------------------------------------------------------------\n\n");
        sb.append(String.format("%-10s %-8s %-12s %-12s %-14s %-10s\n", "SYMBOL", "QTY", "AVG BUY", "CMP", "MKT VALUE", "P&L %"));
        sb.append("--------------------------------------------------------------------------------\n");
        for (Holding h : user.getPortfolio().getHoldings()) {
            double cmp = portfolioService.getCurrentPrice(h);
            double mv = cmp * h.getQuantity();
            double hpnl = mv - h.getCostBasis();
            double pct = h.getCostBasis() > 0 ? hpnl / h.getCostBasis() * 100 : 0;
            sb.append(String.format("%-10s %-8d $%-11.2f $%-11.2f $%-13.2f %+7.2f%%\n",
                    h.getSymbol(), h.getQuantity(), h.getAverageCost(), cmp, mv, pct));
        }
        sb.append("--------------------------------------------------------------------------------\n");
        sb.append("RISK ADVISORY: Simulated education trading account. Zero counterparty margin obligation.\n");

        javax.swing.JTextArea area = new javax.swing.JTextArea(sb.toString());
        area.setFont(new Font("JetBrains Mono", Font.PLAIN, 11));
        area.setBackground(Theme.SIDEBAR_BG);
        area.setForeground(Theme.TXT_PRIMARY);
        area.setEditable(false);
        area.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        p.add(new JScrollPane(area), BorderLayout.CENTER);

        // Buttons
        JPanel bRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        bRow.setOpaque(false);
        JButton btnSave = new JButton("Save Statement");
        btnSave.setBackground(Theme.ACCENT);
        btnSave.setForeground(Color.WHITE);
        btnSave.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setSelectedFile(new File("valuation_report.txt"));
            if (fc.showSaveDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                try (FileWriter fw = new FileWriter(fc.getSelectedFile())) {
                    fw.write(sb.toString());
                    JOptionPane.showMessageDialog(dialog, "Saved successfully!", "Saved", JOptionPane.INFORMATION_MESSAGE);
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(dialog, "Failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        JButton btnClose = new JButton("Close");
        btnClose.setBackground(Theme.SURFACE_SUB);
        btnClose.setForeground(Theme.TXT_PRIMARY);
        btnClose.addActionListener(e -> dialog.dispose());
        bRow.add(btnSave);
        bRow.add(btnClose);
        p.add(bRow, BorderLayout.SOUTH);

        dialog.setContentPane(p);
        dialog.setVisible(true);
    }

    private void onShowRebalanceModal() {
        User user = authService.getCurrentUser();
        if (user == null) return;

        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Portfolio Rebalance Simulator", JDialog.ModalityType.APPLICATION_MODAL);
        dialog.setSize(640, 520);
        dialog.setLocationRelativeTo(this);

        JPanel p = new JPanel(new BorderLayout(12, 14));
        p.setBackground(Theme.CARD_BG);
        p.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));

        // Header
        JPanel hdr = new JPanel(new BorderLayout());
        hdr.setOpaque(false);
        JLabel title = new JLabel("Interactive Portfolio Rebalance Simulator");
        title.setFont(new Font("Inter", Font.BOLD, 16));
        title.setForeground(Theme.TXT_PRIMARY);
        JLabel desc = new JLabel("Adjust target allocation models and calculate required rebalancing orders.");
        desc.setFont(Theme.MICRO);
        desc.setForeground(Theme.TXT_MUTED);
        hdr.add(title, BorderLayout.NORTH);
        hdr.add(desc, BorderLayout.SOUTH);
        p.add(hdr, BorderLayout.NORTH);

        // Sliders / Form
        JPanel slidersPanel = new JPanel(new GridLayout(4, 1, 6, 8));
        slidersPanel.setOpaque(false);

        // current allocation in USD: {Indian, US, Crypto, Cash}
        double[] now = currentAllocation();
        double total = Math.max(1, now[0] + now[1] + now[2] + now[3]);
        int[] pct = new int[4];
        for (int i = 0; i < 4; i++) {
            pct[i] = (int) Math.round(now[i] / total * 100);
        }
        pct[3] += 100 - (pct[0] + pct[1] + pct[2] + pct[3]);   // rounding fix so they add up to 100
        JSlider sIndian = createSlider(pct[0]);
        JSlider sUs = createSlider(pct[1]);
        JSlider sCrypto = createSlider(pct[2]);
        JSlider sCash = createSlider(pct[3]);

        slidersPanel.add(buildSliderRow("Indian Equities (NSE/BSE)", sIndian, pct[0] + "%"));
        slidersPanel.add(buildSliderRow("US Stocks & ETFs", sUs, pct[1] + "%"));
        slidersPanel.add(buildSliderRow("Cryptocurrency (Spot)", sCrypto, pct[2] + "%"));
        slidersPanel.add(buildSliderRow("Liquid Cash Buffer", sCash, pct[3] + "%"));
        p.add(slidersPanel, BorderLayout.CENTER);

        // Footer Actions
        JPanel ftr = new JPanel(new BorderLayout());
        ftr.setOpaque(false);
        JLabel note = new JLabel("Sliders start at your current allocation (live prices).");
        note.setFont(Theme.MICRO);
        note.setForeground(Theme.TXT_MUTED);
        ftr.add(note, BorderLayout.WEST);

        JPanel ftrBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        ftrBtns.setOpaque(false);
        JButton btnCalc = new JButton("Calculate Order Sheet");
        btnCalc.setBackground(Theme.ACCENT);
        btnCalc.setForeground(Color.WHITE);
        btnCalc.addActionListener(e -> {
            int tot = sIndian.getValue() + sUs.getValue() + sCrypto.getValue() + sCash.getValue();
            if (tot != 100) {
                JOptionPane.showMessageDialog(dialog, "Target allocation weights must sum to exactly 100% (currently " + tot + "%).",
                        "Weights Mismatch", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String[] names = {"Indian Equities", "US Stocks & ETFs", "Crypto", "Cash"};
            int[] target = {sIndian.getValue(), sUs.getValue(), sCrypto.getValue(), sCash.getValue()};
            StringBuilder sb = new StringBuilder("Orders needed to reach the target (net worth "
                    + Formatter.money(total) + "):\n\n");
            double maxDrift = 0;
            for (int i = 0; i < 4; i++) {
                double diff = total * target[i] / 100.0 - now[i];
                maxDrift = Math.max(maxDrift, Math.abs(now[i] / total * 100 - target[i]));
                String action = i == 3 ? (diff >= 0 ? "Raise cash by " : "Deploy cash ")
                        : (diff >= 0 ? "BUY " : "SELL ");
                sb.append(String.format(Locale.US, "• %s: now %.1f%% → target %d%%  ·  %s%s%n",
                        names[i], now[i] / total * 100, target[i], Math.abs(diff) < 1 ? "no change" : action,
                        Math.abs(diff) < 1 ? "" : Formatter.money(Math.abs(diff))));
            }
            sb.append(String.format(Locale.US, "%nLargest drift: %.1f%%%s", maxDrift,
                    maxDrift < 5 ? " (within a 5% tolerance)" : " (rebalancing recommended)"));
            JOptionPane.showMessageDialog(dialog, sb.toString(), "Rebalance Simulated", JOptionPane.INFORMATION_MESSAGE);
        });
        JButton btnClose = new JButton("Close");
        btnClose.setBackground(Theme.SURFACE_SUB);
        btnClose.setForeground(Theme.TXT_PRIMARY);
        btnClose.addActionListener(e -> dialog.dispose());
        ftrBtns.add(btnCalc);
        ftrBtns.add(btnClose);
        ftr.add(ftrBtns, BorderLayout.EAST);

        p.add(ftr, BorderLayout.SOUTH);
        dialog.setContentPane(p);
        dialog.setVisible(true);
    }

    /** Current value in USD of {Indian stocks, US stocks/ETFs, crypto, cash}. */
    private double[] currentAllocation() {
        double[] out = new double[4];
        User user = authService.getCurrentUser();
        if (user == null) {
            return out;
        }
        for (Holding h : user.getPortfolio().getHoldings()) {
            double price = marketService.findBySymbol(h.getSymbol()).map(Asset::getPrice).orElse(h.getAverageCost());
            int bucket = isIndianAsset(h.getSymbol()) ? 0 : isCryptoAsset(h.getSymbol()) ? 2 : 1;
            out[bucket] += price * h.getQuantity();
        }
        out[3] = user.getAccount().getBalance();
        return out;
    }

    private JSlider createSlider(int val) {
        JSlider s = new JSlider(0, 100, val);
        s.setBackground(Theme.CARD_BG);
        s.setForeground(Theme.ACCENT);
        return s;
    }

    private JPanel buildSliderRow(String label, JSlider slider, String val) {
        JPanel r = new JPanel(new BorderLayout(8, 0));
        r.setOpaque(false);
        JLabel l = new JLabel(label);
        l.setFont(Theme.SMALL);
        l.setForeground(Theme.TXT_PRIMARY);
        l.setPreferredSize(new Dimension(180, 20));
        JLabel v = new JLabel(val);
        v.setFont(Theme.MONO_SMALL);
        v.setForeground(Theme.ACCENT);
        v.setPreferredSize(new Dimension(40, 20));
        slider.addChangeListener(e -> v.setText(slider.getValue() + "%"));
        r.add(l, BorderLayout.WEST);
        r.add(slider, BorderLayout.CENTER);
        r.add(v, BorderLayout.EAST);
        return r;
    }

    // =========================================================================
    // HELPER METHODS: EXCHANGE, SECTOR & CLASSIFICATION
    // =========================================================================

    private static boolean isIndianAsset(String sym) {
        if (sym == null) return false;
        String s = sym.toUpperCase();
        return s.equals("RELIANCE") || s.equals("TCS") || s.equals("INFY") || s.equals("HDFCBANK")
                || s.equals("ICICIBANK") || s.equals("BHARTIARTL") || s.equals("SBIN") || s.equals("ITC")
                || s.equals("LT") || s.equals("TATAMOTORS") || s.equals("TRENT") || s.equals("ETERNAL");
    }

    private static boolean isCryptoAsset(String sym) {
        if (sym == null) return false;
        String s = sym.toUpperCase();
        return s.equals("BTC") || s.equals("ETH") || s.equals("SOL") || s.contains("BTC") || s.contains("ETH");
    }

    private static String getExchange(String sym, String type) {
        if (isIndianAsset(sym)) return "NSE";
        if (isCryptoAsset(sym) || "Crypto".equalsIgnoreCase(type)) return "CRYPTO";
        return "NASDAQ";
    }

    private static String getSector(String sym, String existing) {
        if (existing != null && !existing.isBlank() && !existing.equalsIgnoreCase("General")) {
            return existing;
        }
        String s = sym.toUpperCase();
        if (s.equals("TCS") || s.equals("INFY")) return "Information Technology";
        if (s.equals("NVDA") || s.equals("AAPL") || s.equals("MSFT") || s.equals("GOOGL") || s.equals("AMZN")) return "US Big Tech & AI";
        if (s.equals("RELIANCE")) return "Energy & Conglomerates";
        if (s.equals("HDFCBANK") || s.equals("ICICIBANK") || s.equals("SBIN")) return "Banking & Financials";
        if (isCryptoAsset(s)) return "Cryptocurrencies";
        if (s.equals("TSLA") || s.equals("TATAMOTORS")) return "Automotive";
        return "Diversified Equities";
    }

    // Vector Button Generator
    private JButton createVectorBtn(String text, Color bg, Color border, Color fg, int iconType) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(border);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);

                // Paint mini vector icon (1=download, 2=pdf, 3=tune)
                int cy = getHeight() / 2;
                g2.setColor(fg);
                if (iconType == 1) { // download
                    g2.drawLine(12, cy - 3, 12, cy + 3);
                    g2.drawLine(9, cy + 1, 12, cy + 4);
                    g2.drawLine(15, cy + 1, 12, cy + 4);
                    g2.drawLine(9, cy + 5, 15, cy + 5);
                } else if (iconType == 2) { // pdf doc
                    g2.drawRoundRect(8, cy - 5, 8, 10, 2, 2);
                    g2.drawLine(10, cy - 2, 14, cy - 2);
                    g2.drawLine(10, cy + 1, 14, cy + 1);
                } else if (iconType == 3) { // tune / sliders
                    g2.drawLine(8, cy - 3, 16, cy - 3);
                    g2.fillRect(10, cy - 5, 3, 4);
                    g2.drawLine(8, cy + 3, 16, cy + 3);
                    g2.fillRect(13, cy + 1, 3, 4);
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(Theme.MICRO);
        btn.setForeground(fg);
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(6, 22, 6, 12));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    // =========================================================================
    // HOLDING ROW DATA RECORD
    // =========================================================================

    public record HoldingRowData(
            Holding holding,
            Asset asset,
            String symbol,
            String name,
            String exchange,
            String sector,
            int quantity,
            double avgBuyPrice,
            double cmp,
            double prevClose,
            double investedVal,
            double currentVal,
            double pnlVal,
            double pnlPct,
            double portfolioWeight,
            List<Double> trendHistory,
            double realizedPnl          // profit already locked in by selling this symbol
    ) {}

    // =========================================================================
    // TABLE MODEL
    // =========================================================================

    private class HoldingsTableModel extends AbstractTableModel {
        private final String[] columns = {
                "Asset & Symbol", "Qty", "Avg Buy Price", "CMP",
                "Invested Value", "Current Value", "Unrealized P&L", "7D Trend", "Actions"
        };
        private final List<HoldingRowData> allList = new ArrayList<>();
        private final List<HoldingRowData> filteredList = new ArrayList<>();

        public void setAllHoldings(List<HoldingRowData> list) {
            allList.clear();
            allList.addAll(list);
        }

        public void applyFilter(String category, String query) {
            filteredList.clear();
            String q = query != null ? query.trim().toUpperCase() : "";

            for (HoldingRowData r : allList) {
                // Category check
                boolean catMatch = true;
                if ("INDIAN".equals(category)) {
                    catMatch = isIndianAsset(r.symbol());
                } else if ("US".equals(category)) {
                    catMatch = !isIndianAsset(r.symbol()) && !isCryptoAsset(r.symbol());
                } else if ("CRYPTO".equals(category)) {
                    catMatch = isCryptoAsset(r.symbol());
                }
                if (!catMatch) continue;

                // Query check
                if (!q.isEmpty()) {
                    boolean qMatch = r.symbol().toUpperCase().contains(q) || r.name().toUpperCase().contains(q);
                    if (!qMatch) continue;
                }

                filteredList.add(r);
            }
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return filteredList.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int col) { return columns[col]; }
        @Override public boolean isCellEditable(int row, int col) { return col == 8; }

        @Override
        public Object getValueAt(int row, int col) {
            if (row < 0 || row >= filteredList.size()) return null;
            HoldingRowData r = filteredList.get(row);
            switch (col) {
                case 0: return r;
                case 1: return r.quantity();
                case 2: return r.avgBuyPrice();
                case 3: return r.cmp();
                case 4: return r.investedVal();
                case 5: return r.currentVal();
                case 6: return r;
                case 7: return r.trendHistory();
                case 8: return r;
                default: return null;
            }
        }
    }

    // =========================================================================
    // TABLE CELL RENDERERS & EDITORS
    // =========================================================================

    /** Column 0: Asset avatar badge + Symbol + Name · Exchange pill */
    private static class AssetCellRenderer extends JPanel implements TableCellRenderer {
        private final JLabel avatarLbl = new JLabel("", SwingConstants.CENTER);
        private final JLabel symLbl = new JLabel();
        private final JLabel subLbl = new JLabel();

        public AssetCellRenderer() {
            setLayout(new FlowLayout(FlowLayout.LEFT, 8, 4));
            setOpaque(true);

            avatarLbl.setPreferredSize(new Dimension(28, 28));
            avatarLbl.setFont(new Font("JetBrains Mono", Font.BOLD, 10));
            avatarLbl.setOpaque(true);

            JPanel textCol = new JPanel();
            textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));
            textCol.setOpaque(false);

            symLbl.setFont(new Font("Inter", Font.BOLD, 12));
            subLbl.setFont(new Font("Inter", Font.PLAIN, 10));

            textCol.add(symLbl);
            textCol.add(subLbl);

            add(avatarLbl);
            add(textCol);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            setBackground(isSelected ? Theme.SURFACE_SUB : Theme.CARD_BG);
            if (value instanceof HoldingRowData r) {
                symLbl.setText(r.symbol());
                symLbl.setForeground(Theme.TXT_PRIMARY);

                String initial = r.symbol().length() >= 4 ? r.symbol().substring(0, 4) : r.symbol();
                avatarLbl.setText(initial);
                avatarLbl.setBackground(Theme.SIDEBAR_BG);

                if (isIndianAsset(r.symbol())) {
                    avatarLbl.setForeground(Theme.ACCENT);
                    subLbl.setText("<html><font color='#94A3B8'>" + r.name() + " · </font><font color='#3B82F6'><b>" + r.exchange() + "</b></font></html>");
                } else if (isCryptoAsset(r.symbol())) {
                    avatarLbl.setForeground(Theme.GAIN);
                    subLbl.setText("<html><font color='#94A3B8'>" + r.name() + " · </font><font color='#22C55E'><b>" + r.exchange() + "</b></font></html>");
                } else {
                    avatarLbl.setForeground(new Color(0x60, 0xA5, 0xFA));
                    subLbl.setText("<html><font color='#94A3B8'>" + r.name() + " · </font><font color='#60A5FA'><b>" + r.exchange() + "</b></font></html>");
                }
            }
            return this;
        }
    }

    /** Columns 1 to 5: Formatted numeric / currency renderer */
    private class MoneyCellRenderer extends DefaultTableCellRenderer {
        private final int colIndex;
        public MoneyCellRenderer(int col) {
            this.colIndex = col;
            setHorizontalAlignment(SwingConstants.RIGHT);
            setFont(Theme.MONO_BODY);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setBackground(isSelected ? Theme.SURFACE_SUB : Theme.CARD_BG);

            HoldingRowData r = (row >= 0 && row < tableModel.filteredList.size()) ? tableModel.filteredList.get(row) : null;
            boolean indian = r != null && isIndianAsset(r.symbol());
            String cur = indian ? "₹" : "$";

            if (value instanceof Number num) {
                if (colIndex == 1) { // Qty
                    setText(String.format(Locale.US, "%,d", num.intValue()));
                    setForeground(Theme.TXT_PRIMARY);
                } else if (colIndex == 3 || colIndex == 5) { // CMP or Current Value (bold)
                    setText(String.format(Locale.US, "%s%,.2f", cur, num.doubleValue()));
                    setForeground(Theme.TXT_PRIMARY);
                    setFont(new Font("JetBrains Mono", Font.BOLD, 11));
                } else { // Avg Buy or Invested
                    setText(String.format(Locale.US, "%s%,.2f", cur, num.doubleValue()));
                    setForeground(Theme.TXT_SECONDARY);
                    setFont(Theme.MONO_BODY);
                }
            }
            return this;
        }
    }

    /** Column 6: Unrealized P&L formatted signed money + percentage pill */
    private class PnlCellRenderer extends DefaultTableCellRenderer {
        public PnlCellRenderer() {
            setHorizontalAlignment(SwingConstants.RIGHT);
            setFont(Theme.MONO_BODY);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setBackground(isSelected ? Theme.SURFACE_SUB : Theme.CARD_BG);

            if (value instanceof HoldingRowData r) {
                boolean indian = isIndianAsset(r.symbol());
                String cur = indian ? "₹" : "$";
                String sign = r.pnlVal() >= 0 ? "+" : "-";
                Color c = r.pnlVal() >= 0 ? Theme.GAIN : Theme.LOSS;

                setText(String.format(Locale.US, "%s%s%,.2f (%+.2f%%)", sign, cur, Math.abs(r.pnlVal()), r.pnlPct()));
                setForeground(c);
            }
            return this;
        }
    }

    /** Column 7: 7D Micro Trend Sparkline */
    private static class SparklineCellRenderer extends JPanel implements TableCellRenderer {
        private final List<Double> data = new ArrayList<>();
        private boolean isGaining = true;

        public SparklineCellRenderer() {
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            setBackground(isSelected ? Theme.SURFACE_SUB : Theme.CARD_BG);
            data.clear();
            if (value instanceof List<?> list && !list.isEmpty()) {
                for (Object o : list) {
                    if (o instanceof Number n) data.add(n.doubleValue());
                }
            }
            if (data.size() >= 2) {
                isGaining = data.get(data.size() - 1) >= data.get(0);
            } else {
                isGaining = true;
            }
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (data.size() < 2) return;

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth() - 16;
            int h = getHeight() - 12;
            int ox = 8;
            int oy = 6;

            double min = Collections.min(data);
            double max = Collections.max(data);
            double range = max - min <= 0.0001 ? 1.0 : (max - min);

            Path2D path = new Path2D.Double();
            for (int i = 0; i < data.size(); i++) {
                double x = ox + (i / (double) (data.size() - 1)) * w;
                double y = oy + h - ((data.get(i) - min) / range) * h;
                if (i == 0) path.moveTo(x, y);
                else path.lineTo(x, y);
            }

            g2.setColor(isGaining ? Theme.GAIN : Theme.LOSS);
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(path);
            g2.dispose();
        }
    }

    /** Column 8: Action Buttons (Analyze, Buy +, Sell -) Renderer */
    private class ActionsCellRenderer extends JPanel implements TableCellRenderer {
        private final JButton btnAnalyze = createActionBtn("Analyze", Theme.SURFACE_SUB, Theme.ACCENT);
        private final JButton btnBuy = createActionBtn("Buy +", Theme.GREEN_BG, Theme.GAIN);
        private final JButton btnSell = createActionBtn("Sell -", Theme.RED_BG, Theme.LOSS);

        public ActionsCellRenderer() {
            setLayout(new FlowLayout(FlowLayout.RIGHT, 4, 5));
            setOpaque(true);
            add(btnAnalyze);
            add(btnBuy);
            add(btnSell);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            setBackground(isSelected ? Theme.SURFACE_SUB : Theme.CARD_BG);
            return this;
        }
    }

    /** Column 8: Action Buttons Cell Editor */
    private class ActionsCellEditor extends AbstractCellEditor implements TableCellEditor {
        private final JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 5));
        private final JButton btnAnalyze = createActionBtn("Analyze", Theme.SURFACE_SUB, Theme.ACCENT);
        private final JButton btnBuy = createActionBtn("Buy +", Theme.GREEN_BG, Theme.GAIN);
        private final JButton btnSell = createActionBtn("Sell -", Theme.RED_BG, Theme.LOSS);
        private HoldingRowData currentRowData;

        public ActionsCellEditor() {
            panel.setOpaque(true);
            panel.setBackground(Theme.SURFACE_SUB);

            btnAnalyze.addActionListener(e -> {
                fireEditingStopped();
                if (currentRowData != null && navHandler != null) {
                    navHandler.accept("StockDetails:" + currentRowData.symbol());
                }
            });

            btnBuy.addActionListener(e -> {
                fireEditingStopped();
                if (currentRowData != null && navHandler != null) {
                    navHandler.accept("Trade:" + currentRowData.symbol());
                }
            });

            btnSell.addActionListener(e -> {
                fireEditingStopped();
                if (currentRowData != null && navHandler != null) {
                    navHandler.accept("Trade:" + currentRowData.symbol());
                }
            });

            panel.add(btnAnalyze);
            panel.add(btnBuy);
            panel.add(btnSell);
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            if (value instanceof HoldingRowData r) {
                currentRowData = r;
            }
            return panel;
        }

        @Override public Object getCellEditorValue() { return currentRowData; }
    }

    private static JButton createActionBtn(String label, Color bg, Color fg) {
        JButton btn = new JButton(label);
        btn.setFont(new Font("Inter", Font.BOLD, 10));
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 60), 1),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)
        ));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    // =========================================================================
    // EXPANDABLE DRAWER ROW PANEL
    // =========================================================================

    private static class PositionDrawerPanel extends JPanel {
        private final JLabel purchasedLbl = new JLabel();
        private final JLabel executedLbl = new JLabel();
        private final JLabel realizedPnlLbl = new JLabel();
        private final JLabel weightLbl = new JLabel();
        private final JLabel sectorLbl = new JLabel();
        private final JLabel alertLbl = new JLabel();

        public PositionDrawerPanel() {
            setLayout(new BorderLayout(10, 0));
            setBackground(Theme.SIDEBAR_BG);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER_CUSTOM),
                    BorderFactory.createEmptyBorder(8, 14, 8, 14)
            ));

            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 0));
            left.setOpaque(false);

            purchasedLbl.setFont(Theme.MICRO);
            purchasedLbl.setForeground(Theme.TXT_SECONDARY);

            executedLbl.setFont(Theme.MICRO);
            executedLbl.setForeground(Theme.TXT_SECONDARY);

            realizedPnlLbl.setFont(Theme.MICRO);

            weightLbl.setFont(Theme.MICRO);
            weightLbl.setForeground(Theme.TXT_SECONDARY);

            sectorLbl.setFont(Theme.MICRO);

            left.add(purchasedLbl);
            left.add(executedLbl);
            left.add(realizedPnlLbl);
            left.add(weightLbl);
            left.add(sectorLbl);
            add(left, BorderLayout.WEST);

            alertLbl.setFont(new Font("Inter", Font.BOLD, 10));
            add(alertLbl, BorderLayout.EAST);
        }

        public void updateHolding(HoldingRowData r, String firstBuy, String lastTrade) {
            boolean indian = isIndianAsset(r.symbol());
            String cur = indian ? "₹" : "$";

            purchasedLbl.setText("<html><font color='#64748B'>First Purchased: </font><b>" + firstBuy + "</b></html>");
            executedLbl.setText("<html><font color='#64748B'>Last Executed: </font><b>" + lastTrade + "</b></html>");

            double realized = r.realizedPnl();
            realizedPnlLbl.setText(String.format(Locale.US, "<html><font color='#64748B'>Realized P&L: </font><font color='%s'><b>%s%s%,.2f</b></font></html>",
                    realized >= 0 ? "#22C55E" : "#EF4444", realized >= 0 ? "+" : "-", cur, Math.abs(realized)));
            weightLbl.setText(String.format(Locale.US, "<html><font color='#64748B'>Portfolio Weight: </font><b>%.2f%%</b></html>", r.portfolioWeight()));
            sectorLbl.setText("<html><font color='#64748B'>Sector: </font><font color='#3B82F6'><b>" + r.sector() + "</b></font></html>");

            // real signal: is the price above or below the average of its recent prices?
            List<Double> hist = r.trendHistory();
            if (hist == null || hist.size() < 5) {
                alertLbl.setForeground(Theme.TXT_MUTED);
                alertLbl.setText("Collecting price history...");
            } else {
                double avg = hist.stream().mapToDouble(Double::doubleValue).average().orElse(r.cmp());
                boolean above = r.cmp() >= avg;
                alertLbl.setForeground(above ? Theme.GAIN : Theme.LOSS);
                alertLbl.setText(String.format(Locale.US, "%s %.2f%% %s its %d-tick average", above ? "▲" : "▼",
                        Math.abs((r.cmp() - avg) / avg * 100), above ? "above" : "below", hist.size()));
            }
        }
    }

    // =========================================================================
    // SECTION 2 CUSTOM KPI CARDS IMPLEMENTATIONS
    // =========================================================================

    private static class ValueKpiCard extends JPanel {
        private final JLabel titleLbl = new JLabel();
        private final JLabel returnPill = new JLabel("+0.00%", SwingConstants.CENTER);
        private final JLabel mainValLbl = new JLabel("₹0.00");
        private final JLabel usdValLbl = new JLabel("≈ $0.00 USD");
        private final JLabel deltaLbl = new JLabel("▲ +₹0.00");
        private List<Double> series = List.of();

        void setSeries(List<Double> values) {
            series = values;
            repaint();
        }

        public ValueKpiCard(String title, boolean hasAreaSpark) {
            setLayout(new BorderLayout(0, 4));
            setBackground(Theme.CARD_BG);
            setBorder(BorderFactory.createCompoundBorder(
                    new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 10, 1),
                    BorderFactory.createEmptyBorder(10, 12, 10, 12)
            ));

            // Top Row
            JPanel top = new JPanel(new BorderLayout());
            top.setOpaque(false);
            titleLbl.setText(title);
            titleLbl.setFont(Theme.MICRO);
            titleLbl.setForeground(Theme.TXT_MUTED);
            top.add(titleLbl, BorderLayout.WEST);

            returnPill.setFont(new Font("JetBrains Mono", Font.BOLD, 10));
            returnPill.setForeground(Theme.GAIN);
            returnPill.setOpaque(true);
            returnPill.setBackground(Theme.GREEN_BG);
            returnPill.setBorder(BorderFactory.createEmptyBorder(1, 6, 1, 6));
            top.add(returnPill, BorderLayout.EAST);
            add(top, BorderLayout.NORTH);

            // Middle Values
            JPanel center = new JPanel();
            center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
            center.setOpaque(false);

            mainValLbl.setFont(Theme.BIG_NUMBER);
            mainValLbl.setForeground(Theme.TXT_PRIMARY);
            center.add(mainValLbl);

            JPanel sub = new JPanel(new BorderLayout());
            sub.setOpaque(false);
            usdValLbl.setFont(Theme.MICRO);
            usdValLbl.setForeground(Theme.TXT_SECONDARY);
            deltaLbl.setFont(new Font("Inter", Font.BOLD, 10));
            deltaLbl.setForeground(Theme.GAIN);
            sub.add(usdValLbl, BorderLayout.WEST);
            sub.add(deltaLbl, BorderLayout.EAST);
            center.add(sub);

            add(center, BorderLayout.CENTER);

            // Mini Java2D vector sparkline area at bottom
            if (hasAreaSpark) {
                JPanel spark = new JPanel() {
                    @Override
                    protected void paintComponent(Graphics g) {
                        super.paintComponent(g);
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        // real account values over time (from the saved equity history)
                        boolean up = series.size() < 2 || series.get(series.size() - 1) >= series.get(0);
                        drawSeries(g2, series, getWidth(), getHeight(), up ? Theme.GAIN : Theme.LOSS, true);
                        g2.dispose();
                    }
                };
                spark.setPreferredSize(new Dimension(100, 24));
                spark.setOpaque(false);
                add(spark, BorderLayout.SOUTH);
            }
        }

        public void updateValues(double totalInrUsd, double pnl, double pnlPct) {
            double usd = totalInrUsd;
            double inr = totalInrUsd * USD_TO_INR;
            mainValLbl.setText(String.format(Locale.US, "₹%,.2f", inr));
            usdValLbl.setText(String.format(Locale.US, "≈ $%,.2f USD", usd));

            String sign = pnl >= 0 ? "+" : "-";
            Color c = pnl >= 0 ? Theme.GAIN : Theme.LOSS;
            returnPill.setText(String.format(Locale.US, "%s%.2f%%", sign, Math.abs(pnlPct)));
            returnPill.setForeground(c);
            returnPill.setBackground(pnl >= 0 ? Theme.GREEN_BG : Theme.RED_BG);

            deltaLbl.setText(String.format(Locale.US, "%s %s₹%,.0f", pnl >= 0 ? "▲" : "▼", sign, Math.abs(pnl * USD_TO_INR)));
            deltaLbl.setForeground(c);
        }
    }

    private static class InvestedKpiCard extends JPanel {
        private final JLabel titleLbl = new JLabel();
        private final JLabel assetBadge = new JLabel("0 Assets", SwingConstants.CENTER);
        private final JLabel mainValLbl = new JLabel("₹0.00");
        private final JProgressBar deployedBar = new JProgressBar(0, 100);
        private final JLabel deployedLbl = new JLabel("58.4% Capital Deployed");

        public InvestedKpiCard(String title) {
            setLayout(new BorderLayout(0, 4));
            setBackground(Theme.CARD_BG);
            setBorder(BorderFactory.createCompoundBorder(
                    new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 10, 1),
                    BorderFactory.createEmptyBorder(10, 12, 10, 12)
            ));

            JPanel top = new JPanel(new BorderLayout());
            top.setOpaque(false);
            titleLbl.setText(title);
            titleLbl.setFont(Theme.MICRO);
            titleLbl.setForeground(Theme.TXT_MUTED);
            top.add(titleLbl, BorderLayout.WEST);

            assetBadge.setFont(Theme.MICRO);
            assetBadge.setForeground(Theme.ACCENT);
            assetBadge.setOpaque(true);
            assetBadge.setBackground(Theme.SIDEBAR_BG);
            assetBadge.setBorder(BorderFactory.createEmptyBorder(1, 6, 1, 6));
            top.add(assetBadge, BorderLayout.EAST);
            add(top, BorderLayout.NORTH);

            JPanel center = new JPanel();
            center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
            center.setOpaque(false);

            mainValLbl.setFont(Theme.BIG_NUMBER);
            mainValLbl.setForeground(Theme.TXT_PRIMARY);
            center.add(mainValLbl);

            JLabel sub = new JLabel("Cost basis across all books");
            sub.setFont(Theme.MICRO);
            sub.setForeground(Theme.TXT_MUTED);
            center.add(sub);
            add(center, BorderLayout.CENTER);

            JPanel bottom = new JPanel(new BorderLayout(0, 3));
            bottom.setOpaque(false);
            deployedBar.setPreferredSize(new Dimension(100, 5));
            deployedBar.setForeground(Theme.ACCENT);
            deployedBar.setBackground(Theme.SIDEBAR_BG);
            deployedBar.setBorderPainted(false);
            deployedLbl.setFont(Theme.MICRO);
            deployedLbl.setForeground(Theme.TXT_SECONDARY);
            bottom.add(deployedBar, BorderLayout.CENTER);
            bottom.add(deployedLbl, BorderLayout.SOUTH);
            add(bottom, BorderLayout.SOUTH);
        }

        public void updateValues(double costUsd, double netWorthUsd, int count) {
            double costInr = costUsd * USD_TO_INR;
            mainValLbl.setText(String.format(Locale.US, "₹%,.2f", costInr));
            assetBadge.setText(count + " Assets");
            int pct = netWorthUsd > 0 ? (int) Math.min(100, (costUsd / netWorthUsd * 100)) : 0;
            deployedBar.setValue(pct);
            deployedLbl.setText(pct + "% Capital Deployed");
        }
    }

    private static class SparkKpiCard extends JPanel {
        private final JLabel titleLbl = new JLabel();
        private final JLabel badgeLbl = new JLabel("", SwingConstants.CENTER);
        private final JLabel valLbl = new JLabel("+₹0.00");
        private final JLabel subLbl = new JLabel("+0.00%");
        private List<Double> series = List.of();

        void setSeries(List<Double> values) {
            series = values;
            repaint();
        }

        public SparkKpiCard(String title, String badge) {
            setLayout(new BorderLayout(0, 4));
            setBackground(Theme.CARD_BG);
            setBorder(BorderFactory.createCompoundBorder(
                    new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 10, 1),
                    BorderFactory.createEmptyBorder(10, 12, 10, 12)
            ));

            JPanel top = new JPanel(new BorderLayout());
            top.setOpaque(false);
            titleLbl.setText(title);
            titleLbl.setFont(Theme.MICRO);
            titleLbl.setForeground(Theme.TXT_MUTED);
            top.add(titleLbl, BorderLayout.WEST);

            badgeLbl.setText(badge);
            badgeLbl.setFont(Theme.MICRO);
            badgeLbl.setForeground(Theme.TXT_SECONDARY);
            badgeLbl.setOpaque(true);
            badgeLbl.setBackground(Theme.SIDEBAR_BG);
            badgeLbl.setBorder(BorderFactory.createEmptyBorder(1, 6, 1, 6));
            top.add(badgeLbl, BorderLayout.EAST);
            add(top, BorderLayout.NORTH);

            JPanel center = new JPanel();
            center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
            center.setOpaque(false);

            valLbl.setFont(Theme.BIG_NUMBER);
            center.add(valLbl);

            subLbl.setFont(Theme.MICRO);
            center.add(subLbl);
            add(center, BorderLayout.CENTER);

            // Vector Curve Sparkline
            JPanel spark = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    drawSeries(g2, series, getWidth(), getHeight(), valLbl.getForeground(), false);
                    g2.dispose();
                }
            };
            spark.setPreferredSize(new Dimension(100, 20));
            spark.setOpaque(false);
            add(spark, BorderLayout.SOUTH);
        }

        public void updateValues(double pnlUsd, double pct, String basis) {
            double pnlInr = pnlUsd * USD_TO_INR;
            String sign = pnlInr >= 0 ? "+" : "-";
            Color c = pnlInr >= 0 ? Theme.GAIN : Theme.LOSS;
            valLbl.setText(String.format(Locale.US, "%s₹%,.2f", sign, Math.abs(pnlInr)));
            valLbl.setForeground(c);
            subLbl.setText(String.format(Locale.US, "%s%.2f%% %s", sign, Math.abs(pct), basis));
            subLbl.setForeground(c);
            repaint();
        }
    }

    private static class CashKpiCard extends JPanel {
        private final JLabel titleLbl = new JLabel();
        private final JLabel liquidBadge = new JLabel("0.0% Liquid", SwingConstants.CENTER);
        private final JLabel valLbl = new JLabel("₹0.00");
        private final JProgressBar marginBar = new JProgressBar(0, 100);
        private final JLabel marginLbl = new JLabel("Collateral Margin: 2x Available");

        public CashKpiCard(String title) {
            setLayout(new BorderLayout(0, 4));
            setBackground(Theme.CARD_BG);
            setBorder(BorderFactory.createCompoundBorder(
                    new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 10, 1),
                    BorderFactory.createEmptyBorder(10, 12, 10, 12)
            ));

            JPanel top = new JPanel(new BorderLayout());
            top.setOpaque(false);
            titleLbl.setText(title);
            titleLbl.setFont(Theme.MICRO);
            titleLbl.setForeground(Theme.TXT_MUTED);
            top.add(titleLbl, BorderLayout.WEST);

            liquidBadge.setFont(Theme.MICRO);
            liquidBadge.setForeground(Theme.TXT_SECONDARY);
            liquidBadge.setOpaque(true);
            liquidBadge.setBackground(Theme.SIDEBAR_BG);
            liquidBadge.setBorder(BorderFactory.createEmptyBorder(1, 6, 1, 6));
            top.add(liquidBadge, BorderLayout.EAST);
            add(top, BorderLayout.NORTH);

            JPanel center = new JPanel();
            center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
            center.setOpaque(false);

            valLbl.setFont(Theme.BIG_NUMBER);
            valLbl.setForeground(Theme.TXT_PRIMARY);
            center.add(valLbl);

            JLabel sub = new JLabel("Instant Buying Power");
            sub.setFont(Theme.MICRO);
            sub.setForeground(Theme.TXT_MUTED);
            center.add(sub);
            add(center, BorderLayout.CENTER);

            JPanel bottom = new JPanel(new BorderLayout(0, 3));
            bottom.setOpaque(false);
            marginBar.setPreferredSize(new Dimension(100, 5));
            marginBar.setForeground(new Color(0x60, 0xA5, 0xFA));
            marginBar.setBackground(Theme.SIDEBAR_BG);
            marginBar.setBorderPainted(false);
            marginLbl.setFont(Theme.MICRO);
            marginLbl.setForeground(Theme.TXT_SECONDARY);
            bottom.add(marginBar, BorderLayout.CENTER);
            bottom.add(marginLbl, BorderLayout.SOUTH);
            add(bottom, BorderLayout.SOUTH);
        }

        public void updateValues(double cashUsd, double netWorthUsd) {
            double cashInr = cashUsd * USD_TO_INR;
            valLbl.setText(String.format(Locale.US, "₹%,.2f", cashInr));
            double pct = netWorthUsd > 0 ? (cashUsd / netWorthUsd * 100.0) : 0;
            liquidBadge.setText(String.format(Locale.US, "%.1f%% Liquid", pct));
            marginBar.setValue((int) Math.min(100, pct * 2));
        }
    }

    // =========================================================================
    // ROW 2 RIGHT: ASSET ALLOCATION DONUT CARD
    // =========================================================================

    private static class AssetAllocationCard extends JPanel {
        private double indianPct = 62.0;
        private double usPct = 18.0;
        private double cryptoPct = 12.0;
        private double cashPct = 8.0;

        private final JLabel lblIndianVal = new JLabel("₹0.00");
        private final JLabel lblIndianPct = new JLabel("0.0%");
        private final JLabel lblUsVal = new JLabel("₹0.00");
        private final JLabel lblUsPct = new JLabel("0.0%");
        private final JLabel lblCryptoVal = new JLabel("₹0.00");
        private final JLabel lblCryptoPct = new JLabel("0.0%");
        private final JLabel lblCashVal = new JLabel("₹0.00");
        private final JLabel lblCashPct = new JLabel("0.0%");
        private final JLabel globalSpreadLbl = new JLabel("Global Spread: India 62% · USA 18% · Global Crypto 12%");

        private final DonutCanvas donutCanvas = new DonutCanvas();

        public AssetAllocationCard() {
            setLayout(new BorderLayout(0, 8));
            setBackground(Theme.CARD_BG);
            setBorder(BorderFactory.createCompoundBorder(
                    new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 10, 1),
                    BorderFactory.createEmptyBorder(10, 14, 10, 14)
            ));

            // Header: Title + Toggle buttons
            JPanel header = new JPanel(new BorderLayout());
            header.setOpaque(false);
            JLabel title = new JLabel("Asset Allocation Matrix");
            title.setFont(new Font("Inter", Font.BOLD, 15));
            title.setForeground(Theme.TXT_PRIMARY);
            header.add(title, BorderLayout.WEST);

            JPanel toggles = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
            toggles.setBackground(Theme.SIDEBAR_BG);
            toggles.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
            JButton b1 = new JButton("Asset Class");
            b1.setFont(Theme.MICRO);
            b1.setBackground(Theme.SURFACE_SUB);
            b1.setForeground(Theme.TXT_PRIMARY);
            b1.setFocusPainted(false);
            b1.setBorderPainted(false);
            JButton b2 = new JButton("Geographic");
            b2.setFont(Theme.MICRO);
            b2.setBackground(Theme.SIDEBAR_BG);
            b2.setForeground(Theme.TXT_MUTED);
            b2.setFocusPainted(false);
            b2.setBorderPainted(false);
            toggles.add(b1);
            toggles.add(b2);
            header.add(toggles, BorderLayout.EAST);
            add(header, BorderLayout.NORTH);

            // Center: Donut + Rows
            JPanel center = new JPanel(new BorderLayout(10, 8));
            center.setOpaque(false);

            donutCanvas.setPreferredSize(new Dimension(130, 130));
            center.add(donutCanvas, BorderLayout.WEST);

            JPanel rows = new JPanel(new GridLayout(4, 1, 0, 4));
            rows.setOpaque(false);
            rows.add(buildBreakdownRow("Indian Equities (NSE/BSE)", Theme.GAIN, lblIndianVal, lblIndianPct));
            rows.add(buildBreakdownRow("US Equities (NASDAQ/NYSE)", Theme.ACCENT, lblUsVal, lblUsPct));
            rows.add(buildBreakdownRow("Cryptocurrency (Spot)", Theme.LOSS, lblCryptoVal, lblCryptoPct));
            rows.add(buildBreakdownRow("Liquid Cash & Equiv.", new Color(0x60, 0xA5, 0xFA), lblCashVal, lblCashPct));
            center.add(rows, BorderLayout.CENTER);
            add(center, BorderLayout.CENTER);

            // Footer Global Spread
            JPanel ftr = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            ftr.setBackground(Theme.SIDEBAR_BG);
            ftr.setBorder(BorderFactory.createCompoundBorder(
                    new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 6, 1),
                    BorderFactory.createEmptyBorder(4, 8, 4, 8)
            ));
            globalSpreadLbl.setFont(Theme.MICRO);
            globalSpreadLbl.setForeground(Theme.TXT_MUTED);
            ftr.add(globalSpreadLbl);
            add(ftr, BorderLayout.SOUTH);
        }

        private JPanel buildBreakdownRow(String name, Color dotColor, JLabel valLbl, JLabel pctLbl) {
            JPanel r = new JPanel(new BorderLayout(8, 0));
            r.setBackground(Theme.SURFACE_SUB);
            r.setBorder(BorderFactory.createCompoundBorder(
                    new Theme.RoundedBorder(Theme.BORDER_SUBTLE, 6, 1),
                    BorderFactory.createEmptyBorder(3, 8, 3, 8)
            ));

            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            left.setOpaque(false);
            JPanel dot = new JPanel() {
                @Override protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(dotColor);
                    g2.fillOval(0, (getHeight() - 8) / 2, 8, 8);
                    g2.dispose();
                }
            };
            dot.setPreferredSize(new Dimension(8, 16));
            dot.setOpaque(false);

            JLabel n = new JLabel(name);
            n.setFont(Theme.MICRO);
            n.setForeground(Theme.TXT_PRIMARY);
            left.add(dot);
            left.add(n);
            r.add(left, BorderLayout.WEST);

            JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            right.setOpaque(false);
            valLbl.setFont(new Font("JetBrains Mono", Font.BOLD, 10));
            valLbl.setForeground(Theme.TXT_PRIMARY);
            pctLbl.setFont(new Font("JetBrains Mono", Font.PLAIN, 10));
            pctLbl.setForeground(dotColor);
            right.add(valLbl);
            right.add(pctLbl);
            r.add(right, BorderLayout.EAST);
            return r;
        }

        public void updateData(User user, List<HoldingRowData> rows, double cashUsd, double netWorthUsd) {
            double indianUsd = 0, usUsd = 0, cryptoUsd = 0;
            for (HoldingRowData r : rows) {
                if (isIndianAsset(r.symbol())) indianUsd += r.currentVal();
                else if (isCryptoAsset(r.symbol())) cryptoUsd += r.currentVal();
                else usUsd += r.currentVal();
            }

            double total = Math.max(1.0, indianUsd + usUsd + cryptoUsd + cashUsd);
            indianPct = (indianUsd / total) * 100.0;
            usPct = (usUsd / total) * 100.0;
            cryptoPct = (cryptoUsd / total) * 100.0;
            cashPct = (cashUsd / total) * 100.0;

            lblIndianVal.setText(String.format(Locale.US, "₹%,.0f", indianUsd * USD_TO_INR));
            lblIndianPct.setText(String.format(Locale.US, "%.1f%%", indianPct));

            lblUsVal.setText(String.format(Locale.US, "₹%,.0f", usUsd * USD_TO_INR));
            lblUsPct.setText(String.format(Locale.US, "%.1f%%", usPct));

            lblCryptoVal.setText(String.format(Locale.US, "₹%,.0f", cryptoUsd * USD_TO_INR));
            lblCryptoPct.setText(String.format(Locale.US, "%.1f%%", cryptoPct));

            lblCashVal.setText(String.format(Locale.US, "₹%,.0f", cashUsd * USD_TO_INR));
            lblCashPct.setText(String.format(Locale.US, "%.1f%%", cashPct));

            double totalEq = indianPct + usPct;
            donutCanvas.setEquityPct(totalEq);
            donutCanvas.repaint();

            globalSpreadLbl.setText(String.format(Locale.US,
                    "Global Spread: India %.0f%% · USA %.0f%% · Global Crypto %.0f%% · Cash %.0f%%",
                    indianPct, usPct, cryptoPct, cashPct));
        }

        private class DonutCanvas extends JPanel {
            private double totalEq = 80.0;

            public DonutCanvas() { setOpaque(false); }
            public void setEquityPct(double eq) { this.totalEq = eq; }

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int size = Math.min(getWidth(), getHeight()) - 16;
                int x = (getWidth() - size) / 2;
                int y = (getHeight() - size) / 2;

                int stroke = 12;
                g2.setStroke(new BasicStroke(stroke, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));

                double total = indianPct + usPct + cryptoPct + cashPct;
                if (total <= 0) total = 100.0;

                double angle1 = (indianPct / total) * 360.0;
                double angle2 = (usPct / total) * 360.0;
                double angle3 = (cryptoPct / total) * 360.0;
                double angle4 = 360.0 - (angle1 + angle2 + angle3);

                double start = 90;
                // Indian Equities (Green)
                g2.setColor(Theme.GAIN);
                g2.draw(new Arc2D.Double(x + stroke/2.0, y + stroke/2.0, size - stroke, size - stroke, start, -angle1, Arc2D.OPEN));
                start -= angle1;

                // US Equities (Blue)
                g2.setColor(Theme.ACCENT);
                g2.draw(new Arc2D.Double(x + stroke/2.0, y + stroke/2.0, size - stroke, size - stroke, start, -angle2, Arc2D.OPEN));
                start -= angle2;

                // Crypto (Loss / Coral)
                g2.setColor(Theme.LOSS);
                g2.draw(new Arc2D.Double(x + stroke/2.0, y + stroke/2.0, size - stroke, size - stroke, start, -angle3, Arc2D.OPEN));
                start -= angle3;

                // Cash (Light Blue)
                g2.setColor(new Color(0x60, 0xA5, 0xFA));
                g2.draw(new Arc2D.Double(x + stroke/2.0, y + stroke/2.0, size - stroke, size - stroke, start, -angle4, Arc2D.OPEN));

                // Center Label
                int cx = getWidth() / 2;
                int cy = getHeight() / 2;
                g2.setColor(Theme.TXT_MUTED);
                g2.setFont(new Font("Inter", Font.PLAIN, 9));
                String sub = "EQUITIES LED";
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(sub, cx - fm.stringWidth(sub)/2, cy - 8);

                g2.setColor(Theme.TXT_PRIMARY);
                g2.setFont(new Font("JetBrains Mono", Font.BOLD, 15));
                fm = g2.getFontMetrics();
                String eqStr = String.format(Locale.US, "%.1f%%", totalEq);
                g2.drawString(eqStr, cx - fm.stringWidth(eqStr)/2, cy + 6);

                g2.setColor(Theme.GAIN);
                g2.setFont(new Font("Inter", Font.BOLD, 9));
                fm = g2.getFontMetrics();
                String div = "Diversified";
                g2.drawString(div, cx - fm.stringWidth(div)/2, cy + 18);

                g2.dispose();
            }
        }
    }

    // =========================================================================
    // ROW 4 LEFT: SECTOR EXPOSURE BAR BREAKDOWN
    // =========================================================================

    private static class SectorExposureCard extends JPanel {
        private final JPanel barsContainer = new JPanel(new GridLayout(6, 1, 0, 6));

        private final SectorBarRow barIt = new SectorBarRow("Information Technology", Theme.ACCENT, 31.4, 10.2);
        private final SectorBarRow barBigTech = new SectorBarRow("US Big Tech & AI", Theme.GAIN, 18.0, 14.8);
        private final SectorBarRow barEnergy = new SectorBarRow("Energy & Conglomerates", new Color(0x06, 0xB6, 0xD4), 17.7, 8.3);
        private final SectorBarRow barBanking = new SectorBarRow("Banking & Financials", new Color(0x8B, 0x5C, 0xF6), 16.5, 5.1);
        private final SectorBarRow barCrypto = new SectorBarRow("Cryptocurrencies", Theme.LOSS, 12.0, -2.4);
        private final SectorBarRow barAuto = new SectorBarRow("Automotive & Others", Theme.GOLD, 4.4, -4.9);

        public SectorExposureCard() {
            setLayout(new BorderLayout(0, 10));
            setBackground(Theme.CARD_BG);
            setBorder(BorderFactory.createCompoundBorder(
                    new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 10, 1),
                    BorderFactory.createEmptyBorder(12, 14, 12, 14)
            ));

            JPanel hdr = new JPanel(new BorderLayout());
            hdr.setOpaque(false);
            JPanel left = new JPanel();
            left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
            left.setOpaque(false);
            JLabel title = new JLabel("Sector Exposure");
            title.setFont(new Font("Inter", Font.BOLD, 15));
            title.setForeground(Theme.TXT_PRIMARY);
            JLabel sub = new JLabel("Macro concentration by industry domain");
            sub.setFont(Theme.MICRO);
            sub.setForeground(Theme.TXT_MUTED);
            left.add(title);
            left.add(sub);
            hdr.add(left, BorderLayout.WEST);

            JLabel pill = new JLabel("Weight Normalized");
            pill.setFont(Theme.MICRO);
            pill.setForeground(Theme.ACCENT);
            pill.setBackground(Theme.ACCENT_BG);
            pill.setOpaque(true);
            pill.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
            hdr.add(pill, BorderLayout.EAST);
            add(hdr, BorderLayout.NORTH);

            barsContainer.setOpaque(false);
            barsContainer.add(barIt);
            barsContainer.add(barBigTech);
            barsContainer.add(barEnergy);
            barsContainer.add(barBanking);
            barsContainer.add(barCrypto);
            barsContainer.add(barAuto);
            add(barsContainer, BorderLayout.CENTER);
        }

        public void updateData(List<HoldingRowData> rows, double totalMktVal) {
            Map<String, Double> sectorVals = new LinkedHashMap<>();
            sectorVals.put("Information Technology", 0.0);
            sectorVals.put("US Big Tech & AI", 0.0);
            sectorVals.put("Energy & Conglomerates", 0.0);
            sectorVals.put("Banking & Financials", 0.0);
            sectorVals.put("Cryptocurrencies", 0.0);
            sectorVals.put("Automotive & Others", 0.0);

            for (HoldingRowData r : rows) {
                String sec = r.sector();
                if (sec.contains("Tech") && isIndianAsset(r.symbol())) sec = "Information Technology";
                else if (sec.contains("Tech") || sec.contains("AI")) sec = "US Big Tech & AI";
                else if (sec.contains("Energy") || sec.contains("Oil")) sec = "Energy & Conglomerates";
                else if (sec.contains("Bank") || sec.contains("Fin")) sec = "Banking & Financials";
                else if (sec.contains("Crypto")) sec = "Cryptocurrencies";
                else sec = "Automotive & Others";

                sectorVals.put(sec, sectorVals.getOrDefault(sec, 0.0) + r.currentVal());
            }

            barIt.updateValues(sectorVals.get("Information Technology"), totalMktVal);
            barBigTech.updateValues(sectorVals.get("US Big Tech & AI"), totalMktVal);
            barEnergy.updateValues(sectorVals.get("Energy & Conglomerates"), totalMktVal);
            barBanking.updateValues(sectorVals.get("Banking & Financials"), totalMktVal);
            barCrypto.updateValues(sectorVals.get("Cryptocurrencies"), totalMktVal);
            barAuto.updateValues(sectorVals.get("Automotive & Others"), totalMktVal);
        }

        private static class SectorBarRow extends JPanel {
            private final String name;
            private final Color color;
            private final JLabel nameLbl = new JLabel();
            private final JLabel retLbl = new JLabel();
            private final JLabel valLbl = new JLabel();
            private final JProgressBar progBar = new JProgressBar(0, 100);

            public SectorBarRow(String name, Color color, double defWeight, double defRet) {
                this.name = name;
                this.color = color;
                setLayout(new BorderLayout(0, 3));
                setOpaque(false);

                JPanel top = new JPanel(new BorderLayout());
                top.setOpaque(false);
                nameLbl.setText(name);
                nameLbl.setFont(Theme.MICRO);
                nameLbl.setForeground(Theme.TXT_PRIMARY);
                top.add(nameLbl, BorderLayout.WEST);

                JPanel r = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
                r.setOpaque(false);
                retLbl.setFont(Theme.MICRO);
                retLbl.setText(String.format(Locale.US, "%+.1f%%", defRet));
                retLbl.setForeground(defRet >= 0 ? Theme.GAIN : Theme.LOSS);
                valLbl.setFont(new Font("JetBrains Mono", Font.BOLD, 10));
                valLbl.setForeground(Theme.TXT_PRIMARY);
                r.add(retLbl);
                r.add(valLbl);
                top.add(r, BorderLayout.EAST);
                add(top, BorderLayout.NORTH);

                progBar.setPreferredSize(new Dimension(100, 5));
                progBar.setForeground(color);
                progBar.setBackground(Theme.SIDEBAR_BG);
                progBar.setBorderPainted(false);
                progBar.setValue((int) defWeight);
                add(progBar, BorderLayout.SOUTH);
            }

            public void updateValues(double valUsd, double totalValUsd) {
                double inr = valUsd * USD_TO_INR;
                double weight = totalValUsd > 0 ? (valUsd / totalValUsd * 100.0) : 0;
                valLbl.setText(String.format(Locale.US, "₹%,.0f (%.1f%%)", inr, weight));
                progBar.setValue((int) Math.min(100, weight));
            }
        }
    }

    // =========================================================================
    // ROW 4 RIGHT: 2x2 ANALYTICAL KPI GRID
    // =========================================================================

    private static class AnalyticalGridCard extends JPanel {
        private final JLabel bestSymLbl = new JLabel("-");
        private final JLabel bestValLbl = new JLabel("No open positions");
        private final JLabel bestNoteLbl = new JLabel(" ");

        private final JLabel worstSymLbl = new JLabel("-");
        private final JLabel worstValLbl = new JLabel("No open positions");
        private final JLabel worstNoteLbl = new JLabel(" ");

        private final JLabel winRatioLbl = new JLabel("-");
        private final JLabel winSubLbl = new JLabel("0 Gainers / 0 Losers");
        private final JLabel profitFactorLbl = new JLabel("Profit factor: N/A");

        private final JLabel betaLbl = new JLabel("Sharpe N/A");
        private final JLabel betaSubLbl = new JLabel("Max Drawdown: N/A");
        private final JLabel riskNoteLbl = new JLabel("Sortino: N/A");

        public AnalyticalGridCard() {
            setLayout(new GridLayout(2, 2, 10, 10));
            setOpaque(false);

            add(buildCard("TOP ALPHA ASSET", bestSymLbl, bestValLbl, bestNoteLbl, Theme.GAIN, "rocket_launch"));
            add(buildCard("MAX DRAG ASSET", worstSymLbl, worstValLbl, worstNoteLbl, Theme.LOSS, "south_east"));
            add(buildCard("POSITION WIN RATIO", winRatioLbl, winSubLbl, profitFactorLbl, Theme.ACCENT, "pie_chart"));
            add(buildCard("VOLATILITY & RISK", betaLbl, betaSubLbl, riskNoteLbl, Theme.ACCENT, "show_chart"));
        }

        private JPanel buildCard(String title, JLabel hero, JLabel sub1, JLabel sub2, Color accent, String icon) {
            JPanel card = new JPanel(new BorderLayout(0, 2));
            card.setBackground(Theme.CARD_BG);
            card.setBorder(BorderFactory.createCompoundBorder(
                    new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 8, 1),
                    BorderFactory.createEmptyBorder(8, 10, 8, 10)
            ));

            JPanel top = new JPanel(new BorderLayout());
            top.setOpaque(false);
            JLabel t = new JLabel(title);
            t.setFont(new Font("Inter", Font.BOLD, 9));
            t.setForeground(Theme.TXT_MUTED);
            top.add(t, BorderLayout.WEST);

            JLabel i = new JLabel(icon.contains("rocket") ? "▲" : (icon.contains("south") ? "▼" : "●"));
            i.setFont(Theme.MICRO);
            i.setForeground(accent);
            top.add(i, BorderLayout.EAST);
            card.add(top, BorderLayout.NORTH);

            JPanel mid = new JPanel();
            mid.setLayout(new BoxLayout(mid, BoxLayout.Y_AXIS));
            mid.setOpaque(false);
            hero.setFont(new Font("JetBrains Mono", Font.BOLD, 14));
            hero.setForeground(Theme.TXT_PRIMARY);
            sub1.setFont(Theme.MICRO);
            sub1.setForeground(accent);
            sub2.setFont(Theme.TINY);
            sub2.setForeground(Theme.TXT_MUTED);
            mid.add(hero);
            mid.add(sub1);
            mid.add(sub2);
            card.add(mid, BorderLayout.CENTER);
            return card;
        }

        public void updateData(List<HoldingRowData> rows, com.papertrader.analytics.PerformanceReport report) {
            if (report != null) {
                // risk numbers from the saved equity history and closed trades (AnalyticsService)
                betaLbl.setText("Sharpe " + com.papertrader.analytics.PerformanceAnalyzer.format(report.sharpeRatio(), ""));
                betaSubLbl.setText("Max Drawdown: " + com.papertrader.analytics.PerformanceAnalyzer.format(-report.maxDrawdownPercent(), "%"));
                riskNoteLbl.setText("Sortino: " + com.papertrader.analytics.PerformanceAnalyzer.format(report.sortinoRatio(), "")
                        + " · " + report.closedTrades() + " closed trades");
                profitFactorLbl.setText("Profit factor: " + com.papertrader.analytics.PerformanceAnalyzer.format(report.profitFactor(), "x"));
            }
            if (rows.isEmpty()) {
                bestSymLbl.setText("-");
                bestValLbl.setText("No open positions");
                worstSymLbl.setText("-");
                worstValLbl.setText("No open positions");
                winRatioLbl.setText("-");
                winSubLbl.setText("0 Gainers / 0 Losers");
                return;
            }

            HoldingRowData best = Collections.max(rows, Comparator.comparingDouble(HoldingRowData::pnlPct));
            HoldingRowData worst = Collections.min(rows, Comparator.comparingDouble(HoldingRowData::pnlPct));

            bestSymLbl.setText(best.symbol());
            bestValLbl.setText(String.format(Locale.US, "%+.2f%% (%s₹%,.0f)", best.pnlPct(), best.pnlVal() >= 0 ? "+" : "-", Math.abs(best.pnlVal() * USD_TO_INR)));
            bestNoteLbl.setText(best.name() + " · " + String.format(Locale.US, "%.1f%% of portfolio", best.portfolioWeight()));

            worstSymLbl.setText(worst.symbol());
            worstValLbl.setText(String.format(Locale.US, "%+.2f%% (%s₹%,.0f)", worst.pnlPct(), worst.pnlVal() >= 0 ? "+" : "-", Math.abs(worst.pnlVal() * USD_TO_INR)));
            worstNoteLbl.setText(worst.name() + " · " + String.format(Locale.US, "%.1f%% of portfolio", worst.portfolioWeight()));

            long gainers = rows.stream().filter(r -> r.pnlVal() >= 0).count();
            long losers = rows.size() - gainers;
            double winPct = (gainers / (double) rows.size()) * 100.0;
            winRatioLbl.setText(String.format(Locale.US, "%.1f%%", winPct));
            winSubLbl.setText(gainers + " Gainers / " + losers + " Losers");
        }
    }

    // =========================================================================
    // ROW 4 RIGHT LOWER: HEALTH SCORE CARD
    // =========================================================================

    private static class HealthScoreCard extends JPanel {
        private final JLabel badgeLbl = new JLabel("- / 100 · NO POSITIONS");
        private final JLabel divValLbl = new JLabel("N/A");
        private final JLabel capValLbl = new JLabel("N/A");
        private final JProgressBar divBar = new JProgressBar(0, 100);
        private final JProgressBar capBar = new JProgressBar(0, 100);
        private final JLabel capDescLbl = new JLabel("Largest position share of the invested money.");

        public HealthScoreCard() {
            setLayout(new BorderLayout(0, 8));
            setBackground(Theme.CARD_BG);
            setBorder(BorderFactory.createCompoundBorder(
                    new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 8, 1),
                    BorderFactory.createEmptyBorder(10, 12, 10, 12)
            ));

            JPanel top = new JPanel(new BorderLayout());
            top.setOpaque(false);
            JLabel title = new JLabel("Health & Concentration Score");
            title.setFont(new Font("Inter", Font.BOLD, 13));
            title.setForeground(Theme.TXT_PRIMARY);
            top.add(title, BorderLayout.WEST);

            badgeLbl.setFont(Theme.MICRO);
            badgeLbl.setForeground(new Color(0xFF, 0xB4, 0xAB));
            badgeLbl.setBackground(new Color(147, 0, 10, 40));
            badgeLbl.setOpaque(true);
            badgeLbl.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            top.add(badgeLbl, BorderLayout.EAST);
            add(top, BorderLayout.NORTH);

            JPanel mid = new JPanel(new GridLayout(1, 2, 10, 0));
            mid.setOpaque(false);

            // Metric 1: Diversification
            JPanel m1 = new JPanel();
            m1.setLayout(new BoxLayout(m1, BoxLayout.Y_AXIS));
            m1.setBackground(Theme.SURFACE_SUB);
            m1.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
            JLabel l1 = new JLabel("Diversification Metric");
            l1.setFont(Theme.MICRO);
            l1.setForeground(Theme.TXT_MUTED);
            JLabel v1 = divValLbl;
            v1.setFont(Theme.MICRO);
            v1.setForeground(Theme.GAIN);
            divBar.setValue(0);
            divBar.setForeground(Theme.GAIN);
            divBar.setBackground(Theme.SIDEBAR_BG);
            divBar.setPreferredSize(new Dimension(100, 4));
            divBar.setBorderPainted(false);
            JLabel d1 = new JLabel("Score from position weights (1 - sum of squared weights).");
            d1.setFont(Theme.TINY);
            d1.setForeground(Theme.TXT_MUTED);
            m1.add(l1);
            m1.add(v1);
            m1.add(Box.createVerticalStrut(3));
            m1.add(divBar);
            m1.add(Box.createVerticalStrut(3));
            m1.add(d1);
            mid.add(m1);

            // Metric 2: Single Stock Cap
            JPanel m2 = new JPanel();
            m2.setLayout(new BoxLayout(m2, BoxLayout.Y_AXIS));
            m2.setBackground(Theme.SURFACE_SUB);
            m2.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
            JLabel l2 = new JLabel("Single Stock Cap");
            l2.setFont(Theme.MICRO);
            l2.setForeground(Theme.TXT_MUTED);
            JLabel v2 = capValLbl;
            v2.setFont(Theme.MICRO);
            v2.setForeground(new Color(0xFF, 0xB4, 0xAB));
            capBar.setValue(0);
            capBar.setForeground(new Color(0x93, 0x00, 0x0A));
            capBar.setBackground(Theme.SIDEBAR_BG);
            capBar.setPreferredSize(new Dimension(100, 4));
            capBar.setBorderPainted(false);
            capDescLbl.setFont(Theme.TINY);
            capDescLbl.setForeground(Theme.TXT_MUTED);
            m2.add(l2);
            m2.add(v2);
            m2.add(Box.createVerticalStrut(3));
            m2.add(capBar);
            m2.add(Box.createVerticalStrut(3));
            m2.add(capDescLbl);
            mid.add(m2);

            add(mid, BorderLayout.CENTER);

            JLabel adv = new JLabel("Shield: Simulation Risk Advisory: Analytical modeling calculated for virtual educational testing only.");
            adv.setFont(Theme.TINY);
            adv.setForeground(Theme.TXT_MUTED);
            add(adv, BorderLayout.SOUTH);
        }

        public void updateData(List<HoldingRowData> rows, double totalMktVal) {
            if (rows.isEmpty()) {
                badgeLbl.setText("- / 100 · NO POSITIONS");
                divValLbl.setText("N/A");
                capValLbl.setText("N/A");
                divBar.setValue(0);
                capBar.setValue(0);
                return;
            }
            HoldingRowData top = Collections.max(rows, Comparator.comparingDouble(HoldingRowData::portfolioWeight));
            // Diversification = 1 - Herfindahl index (sum of squared weights): 1 stock = 0, 10 equal stocks = 90
            double hhi = 0;
            for (HoldingRowData row : rows) {
                hhi += Math.pow(row.portfolioWeight() / 100.0, 2);
            }
            int div = (int) Math.round((1 - hhi) * 100);
            int health = (int) Math.round(div * 0.6 + Math.max(0, 100 - top.portfolioWeight() * 2) * 0.4);
            divValLbl.setText(div + "/100 " + (div >= 75 ? "(Optimal)" : div >= 50 ? "(Fair)" : "(Concentrated)"));
            divValLbl.setForeground(div >= 75 ? Theme.GAIN : div >= 50 ? Theme.WARNING : Theme.LOSS);
            divBar.setValue(div);
            badgeLbl.setText(health + " / 100 · " + (health >= 75 ? "LOW RISK" : health >= 50 ? "MODERATE RISK" : "HIGH RISK"));
            capValLbl.setText(String.format(Locale.US, "%.1f%% Peak", top.portfolioWeight()));
            capDescLbl.setText(String.format(Locale.US, "Concentration Warning: %s accounts for %.1f%% of total equity.", top.symbol(), top.portfolioWeight()));
            capBar.setValue((int) Math.min(100, top.portfolioWeight() * 3));
        }
    }

    // =========================================================================
    // ROW 5 LEFT: PERIODIC P&L BREAKDOWN
    // =========================================================================

    private static class PeriodicPnlCard extends JPanel {
        private final JLabel dayVal = new JLabel("₹0");
        private final JLabel dayPct = new JLabel("0.00%");
        private final JLabel weekVal = new JLabel("₹0");
        private final JLabel weekPct = new JLabel("0.00%");
        private final JLabel monthVal = new JLabel("₹0");
        private final JLabel monthPct = new JLabel("0.00%");
        private final JLabel yearVal = new JLabel("₹0");
        private final JLabel yearPct = new JLabel("0.00%");
        private final JButton realizedBtn = new JButton("Realized (₹0)");
        private final JLabel taxLbl = new JLabel("No positions at a loss");

        public PeriodicPnlCard() {
            setLayout(new BorderLayout(0, 10));
            setBackground(Theme.CARD_BG);
            setBorder(BorderFactory.createCompoundBorder(
                    new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 10, 1),
                    BorderFactory.createEmptyBorder(12, 14, 12, 14)
            ));

            JPanel hdr = new JPanel(new BorderLayout());
            hdr.setOpaque(false);
            JPanel left = new JPanel();
            left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
            left.setOpaque(false);
            JLabel title = new JLabel("P&L Performance Breakdown");
            title.setFont(new Font("Inter", Font.BOLD, 15));
            title.setForeground(Theme.TXT_PRIMARY);
            JLabel sub = new JLabel("Realized alpha vs active unrealized capital");
            sub.setFont(Theme.MICRO);
            sub.setForeground(Theme.TXT_MUTED);
            left.add(title);
            left.add(sub);
            hdr.add(left, BorderLayout.WEST);

            JPanel toggles = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            toggles.setBackground(Theme.SIDEBAR_BG);
            toggles.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
            JButton b1 = realizedBtn;
            b1.setFont(Theme.MICRO);
            b1.setBackground(Theme.SIDEBAR_BG);
            b1.setForeground(Theme.TXT_MUTED);
            b1.setFocusPainted(false);
            b1.setBorderPainted(false);
            JButton b2 = new JButton("Unrealized (Active)");
            b2.setFont(Theme.MICRO);
            b2.setBackground(Theme.SURFACE_SUB);
            b2.setForeground(Theme.TXT_PRIMARY);
            b2.setFocusPainted(false);
            b2.setBorderPainted(false);
            toggles.add(b1);
            toggles.add(b2);
            hdr.add(toggles, BorderLayout.EAST);
            add(hdr, BorderLayout.NORTH);

            // 4 Period Cards
            JPanel grid = new JPanel(new GridLayout(1, 4, 8, 0));
            grid.setOpaque(false);
            grid.add(buildPeriodTile("Day Return", dayVal, dayPct));
            grid.add(buildPeriodTile("1 Week", weekVal, weekPct));
            grid.add(buildPeriodTile("1 Month", monthVal, monthPct));
            grid.add(buildPeriodTile("All-Time", yearVal, yearPct));
            add(grid, BorderLayout.CENTER);

            // Bottom Tax-loss harvesting strip
            JPanel taxStrip = new JPanel(new BorderLayout());
            taxStrip.setBackground(Theme.SIDEBAR_BG);
            taxStrip.setBorder(BorderFactory.createCompoundBorder(
                    new Theme.RoundedBorder(Theme.BORDER_SUBTLE, 6, 1),
                    BorderFactory.createEmptyBorder(6, 10, 6, 10)
            ));
            JLabel t1 = new JLabel("Tax Loss Harvesting Eligible:");
            t1.setFont(Theme.MICRO);
            t1.setForeground(Theme.TXT_MUTED);
            JLabel t2 = taxLbl;
            t2.setFont(new Font("Inter", Font.BOLD, 10));
            t2.setForeground(Theme.LOSS);
            taxStrip.add(t1, BorderLayout.WEST);
            taxStrip.add(t2, BorderLayout.EAST);
            add(taxStrip, BorderLayout.SOUTH);
        }

        private JPanel buildPeriodTile(String label, JLabel val, JLabel pct) {
            JPanel p = new JPanel();
            p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
            p.setBackground(Theme.SURFACE_SUB);
            p.setBorder(BorderFactory.createCompoundBorder(
                    new Theme.RoundedBorder(Theme.BORDER_SUBTLE, 8, 1),
                    BorderFactory.createEmptyBorder(8, 10, 8, 10)
            ));
            JLabel l = new JLabel(label);
            l.setFont(Theme.MICRO);
            l.setForeground(Theme.TXT_MUTED);
            val.setFont(new Font("JetBrains Mono", Font.BOLD, 13));
            val.setForeground(Theme.GAIN);
            pct.setFont(Theme.MICRO);
            pct.setForeground(Theme.GAIN);
            p.add(l);
            p.add(Box.createVerticalStrut(4));
            p.add(val);
            p.add(pct);
            return p;
        }

        /**
         * Real period P&L: net worth now minus net worth at the start of the period (from the saved
         * equity history). All-time is measured from the starting cash.
         */
        public void updateData(double todayPnlUsd, double netWorth, List<com.papertrader.analytics.EquityPoint> curve,
                               double startingCash, double realizedUsd, List<HoldingRowData> rows) {
            long now = System.currentTimeMillis();
            setTile(dayVal, dayPct, todayPnlUsd, netWorth - todayPnlUsd);
            double weekStart = valueAt(curve, now - 7 * 86_400_000L, startingCash);
            setTile(weekVal, weekPct, netWorth - weekStart, weekStart);
            double monthStart = valueAt(curve, now - 30 * 86_400_000L, startingCash);
            setTile(monthVal, monthPct, netWorth - monthStart, monthStart);
            setTile(yearVal, yearPct, netWorth - startingCash, startingCash);

            realizedBtn.setText("Realized (" + inr(realizedUsd) + ")");

            // tax-loss harvesting: open positions currently at a loss could be sold to offset gains
            double loss = 0;
            List<String> names = new ArrayList<>();
            for (HoldingRowData row : rows) {
                if (row.pnlVal() < 0) {
                    loss += row.pnlVal();
                    names.add(row.symbol());
                }
            }
            taxLbl.setText(names.isEmpty() ? "No positions at a loss"
                    : inr(loss) + " available offset in " + String.join(" & ", names.subList(0, Math.min(3, names.size())))
                    + (names.size() > 3 ? " +" + (names.size() - 3) : ""));
        }

        /** Net worth at a past time: last saved point at/before it, else the oldest point, else the fallback. */
        private static double valueAt(List<com.papertrader.analytics.EquityPoint> curve, long time, double fallback) {
            double v = curve.isEmpty() ? fallback : curve.get(0).value();
            for (com.papertrader.analytics.EquityPoint ep : curve) {
                if (ep.time() > time) {
                    break;
                }
                v = ep.value();
            }
            return v;
        }

        private static String inr(double usd) {
            double v = usd * USD_TO_INR;
            return String.format(Locale.US, "%s₹%,.0f", v >= 0 ? "+" : "-", Math.abs(v));
        }

        private static void setTile(JLabel val, JLabel pct, double changeUsd, double baseUsd) {
            Color c = changeUsd >= 0 ? Theme.GAIN : Theme.LOSS;
            val.setText(inr(changeUsd));
            val.setForeground(c);
            double p = baseUsd > 0 ? changeUsd / baseUsd * 100 : 0;
            pct.setText(String.format(Locale.US, "%s %+.2f%%", changeUsd >= 0 ? "▲" : "▼", p));
            pct.setForeground(c);
        }
    }

    // =========================================================================
    // ROW 5 RIGHT: RECENT PORTFOLIO ACTIVITY MINI-LEDGER
    // =========================================================================

    private class RecentActivityCard extends JPanel {
        private final JPanel listContainer = new JPanel(new GridLayout(4, 1, 0, 4));

        public RecentActivityCard() {
            setLayout(new BorderLayout(0, 10));
            setBackground(Theme.CARD_BG);
            setBorder(BorderFactory.createCompoundBorder(
                    new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 10, 1),
                    BorderFactory.createEmptyBorder(12, 14, 12, 14)
            ));

            JPanel hdr = new JPanel(new BorderLayout());
            hdr.setOpaque(false);
            JPanel left = new JPanel();
            left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
            left.setOpaque(false);
            JLabel title = new JLabel("Recent Executions");
            title.setFont(new Font("Inter", Font.BOLD, 15));
            title.setForeground(Theme.TXT_PRIMARY);
            JLabel sub = new JLabel("Simulated order clearing history");
            sub.setFont(Theme.MICRO);
            sub.setForeground(Theme.TXT_MUTED);
            left.add(title);
            left.add(sub);
            hdr.add(left, BorderLayout.WEST);

            JButton btnViewAll = new JButton("View All ->");
            btnViewAll.setFont(Theme.MICRO);
            btnViewAll.setForeground(Theme.ACCENT);
            btnViewAll.setFocusPainted(false);
            btnViewAll.setBorderPainted(false);
            btnViewAll.setContentAreaFilled(false);
            btnViewAll.setCursor(new Cursor(Cursor.HAND_CURSOR));
            btnViewAll.addActionListener(e -> {
                if (navHandler != null) navHandler.accept("History");
            });
            hdr.add(btnViewAll, BorderLayout.EAST);
            add(hdr, BorderLayout.NORTH);

            listContainer.setOpaque(false);
            add(listContainer, BorderLayout.CENTER);
        }

        public void updateTransactions(List<Transaction> txs) {
            listContainer.removeAll();
            if (txs == null || txs.isEmpty()) {
                JLabel empty = new JLabel("No trades yet. Your executed orders will appear here.");
                empty.setFont(Theme.MICRO);
                empty.setForeground(Theme.TXT_MUTED);
                listContainer.add(empty);
            } else {
                int count = Math.min(4, txs.size());
                for (int i = txs.size() - 1; i >= txs.size() - count; i--) {
                    Transaction t = txs.get(i);
                    boolean indian = isIndianAsset(t.getSymbol());
                    String timeStr = t.getTimestamp().format(DateTimeFormatter.ofPattern("dd MMM, HH:mm"));
                    listContainer.add(buildTxItem(t.getSide().name(), t.getSymbol(), t.getQuantity(), t.getPrice(),
                            indian ? "NSE Clearing" : "NASDAQ", timeStr, indian));
                }
            }
            listContainer.revalidate();
            listContainer.repaint();
        }

        private JPanel buildTxItem(String side, String sym, int qty, double price, String ex, String time, boolean isIndian) {
            JPanel p = new JPanel(new BorderLayout(8, 0));
            p.setBackground(Theme.SURFACE_SUB);
            p.setBorder(BorderFactory.createCompoundBorder(
                    new Theme.RoundedBorder(Theme.BORDER_SUBTLE, 6, 1),
                    BorderFactory.createEmptyBorder(4, 8, 4, 8)
            ));

            boolean buy = "BUY".equalsIgnoreCase(side);
            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            left.setOpaque(false);

            JLabel badge = new JLabel(side, SwingConstants.CENTER);
            badge.setPreferredSize(new Dimension(32, 22));
            badge.setFont(new Font("JetBrains Mono", Font.BOLD, 9));
            badge.setForeground(buy ? Theme.GAIN : Theme.LOSS);
            badge.setBackground(buy ? Theme.GREEN_BG : Theme.RED_BG);
            badge.setOpaque(true);
            left.add(badge);

            JPanel textCol = new JPanel();
            textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));
            textCol.setOpaque(false);
            JLabel symL = new JLabel(sym + " · " + qty + " sh");
            symL.setFont(new Font("Inter", Font.BOLD, 11));
            symL.setForeground(Theme.TXT_PRIMARY);
            String cur = isIndian ? "₹" : "$";
            JLabel subL = new JLabel(String.format(Locale.US, "%s%,.2f | %s", cur, price, ex));
            subL.setFont(Theme.MICRO);
            subL.setForeground(Theme.TXT_MUTED);
            textCol.add(symL);
            textCol.add(subL);
            left.add(textCol);
            p.add(left, BorderLayout.WEST);

            JPanel right = new JPanel();
            right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
            right.setOpaque(false);
            double tot = price * qty;
            JLabel valL = new JLabel(String.format(Locale.US, "%s%,.2f", cur, tot), SwingConstants.RIGHT);
            valL.setFont(new Font("JetBrains Mono", Font.BOLD, 11));
            valL.setForeground(Theme.TXT_PRIMARY);
            JLabel timeL = new JLabel(time, SwingConstants.RIGHT);
            timeL.setFont(Theme.MICRO);
            timeL.setForeground(Theme.TXT_MUTED);
            right.add(valL);
            right.add(timeL);
            p.add(right, BorderLayout.EAST);

            return p;
        }
    }
}
