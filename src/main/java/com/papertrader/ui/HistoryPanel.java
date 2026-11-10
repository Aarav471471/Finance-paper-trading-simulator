package com.papertrader.ui;

import com.papertrader.analytics.PerformanceAnalyzer;
import com.papertrader.analytics.PerformanceReport;
import com.papertrader.analytics.TradeStats;
import com.papertrader.model.Asset;
import com.papertrader.service.AnalyticsService;
import com.papertrader.model.OrderSide;
import com.papertrader.model.Transaction;
import com.papertrader.model.User;
import com.papertrader.service.AuthService;
import com.papertrader.service.MarketService;
import com.papertrader.service.PortfolioService;
import com.papertrader.service.TradingService;
import com.papertrader.util.CsvExporter;
import com.papertrader.util.Formatter;

import javax.swing.AbstractCellEditor;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
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
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * PaperTrader 3D - Production Transaction History & Quantitative Audit Terminal.
 * 
 * Features:
 * 1. Top Action & Export Bar: Period selector (1W, 1M, 3M, 6M, 1Y, ALL) + Quick Export
 *    (CSV, Excel, PDF Statement, Tax P&L Report modal).
 * 2. Six Financial KPI Summary Cards: Total Trades, Buy Orders, Sell Orders, Realized P&L,
 *    Win Rate (with radial ring gauge), and Trading Fees (with brokerage note).
 * 3. Interactive Analytics Suite (3-Column Grid):
 *    - Chart A: Daily Realized P&L Session Histogram (Last 16 trading days with hover tooltips).
 *    - Chart B: Cumulative Growth Equity Curve vs NIFTY 50 / S&P Benchmark.
 *    - Chart C: Execution Ratio & Win/Loss Donut Distribution + Outlier Metric Band.
 * 4. Monthly P&L Heatmap Calendar (November active trading matrix) + 8 Advanced Quant Metrics Tiles
 *    (Profit Factor, Avg Holding, Risk/Reward, Win/Loss Streaks, Sharpe Ratio, Best/Worst Assets).
 * 5. Comprehensive Transaction Filtering Toolbar: Live search input + Reset + 6 Filter Dropdowns
 *    (Asset Class, Exchange, Side, Order Type, Execution Status, Date Span).
 * 6. Main Transaction Workspace (8 cols / 4 cols Split):
 *    - Left (8 cols): Tabbed Status Filters (ALL, OPEN, FILLED, CANCELLED, REJECTED) +
 *      Rich JTable with Custom Renderers + Pagination Controls.
 *    - Right (4 cols): Transaction Details Inspector Panel showing live Order Telemetry Timeline,
 *      Detailed Financial Audit Receipt breakdown (STT, Exchange fee, Stamp duty, GST, Net Settled, P&L),
 *      Download Trade Receipt (PDF), and "Re-Order in Terminal" navigation!
 */
public class HistoryPanel extends BasePanel {

    private static final double USD_TO_INR = 83.07;

    private final AuthService authService;
    private final TradingService tradingService;
    private final MarketService marketService;
    private final PortfolioService portfolioService;
    private final Consumer<String> navHandler;
    private AnalyticsService analytics;
    private TradeStats stats = TradeStats.of(List.of());
    private String period = "ALL";

    // Six KPI Cards
    private final TotalTradesKpiCard cardTrades = new TotalTradesKpiCard();
    private final SideKpiCard cardBuys = new SideKpiCard("Buy Orders", true);
    private final SideKpiCard cardSells = new SideKpiCard("Sell Orders", false);
    private final RealizedPnlKpiCard cardRealizedPnl = new RealizedPnlKpiCard();
    private final WinRateKpiCard cardWinRate = new WinRateKpiCard();
    private final FeesKpiCard cardFees = new FeesKpiCard();

    // Row 2 Analytics Suite
    private final DailyHistogramCard histogramCard = new DailyHistogramCard();
    private final CumulativeGrowthCard growthCard = new CumulativeGrowthCard();
    private final WinLossDonutCard donutCard = new WinLossDonutCard();

    // Row 3 Calendar & Quant
    private final MonthlyHeatmapCard heatmapCard = new MonthlyHeatmapCard();
    private final QuantMetricsGrid quantGrid = new QuantMetricsGrid();

    // Row 4 Toolbar & Filters
    private final JTextField searchField = new JTextField();
    private final JLabel recordCountLabel = new JLabel("Showing 0 of 0 records");
    private final JComboBox<String> filterAssetClass = new JComboBox<>(new String[]{"All Assets", "Indian Equity (NSE/BSE)", "US Equity (NASDAQ)", "Crypto Sim"});
    private final JComboBox<String> filterExchange = new JComboBox<>(new String[]{"All Exchanges", "NSE", "BSE", "NASDAQ", "Binance"});
    private final JComboBox<String> filterSide = new JComboBox<>(new String[]{"All Sides", "BUY", "SELL"});
    private final JComboBox<String> filterOrderType = new JComboBox<>(new String[]{"All Types", "Market", "Limit", "Stop", "Stop-Limit"});
    private final JComboBox<String> filterStatus = new JComboBox<>(new String[]{"All Statuses", "Filled", "Pending", "Partially Filled", "Cancelled", "Rejected"});
    private final JComboBox<String> filterDateSpan = new JComboBox<>(new String[]{"All History", "Today", "Yesterday", "Last 7 Days", "Last 30 Days", "Last 3 Months"});

    // Row 5 Workspace Table & Inspector
    private String activeStatusTab = "ALL";
    private final JButton tabAll = createStatusTabBtn("ALL ORDERS", 0, true);
    private final JButton tabPending = createStatusTabBtn("OPEN / PENDING", 0, false);
    private final JButton tabFilled = createStatusTabBtn("FILLED EXECUTIONS", 0, false);
    private final JButton tabCancelled = createStatusTabBtn("CANCELLED", 0, false);
    private final JButton tabRejected = createStatusTabBtn("REJECTED", 0, false);

    private final HistoryLedgerTableModel tableModel = new HistoryLedgerTableModel();
    private final JTable historyTable = new JTable(tableModel);

    // Right Inspector Panel
    private final OrderInspectorPanel inspectorPanel = new OrderInspectorPanel();

    // Pagination
    private int rowsPerPage = 10;
    private int currentPage = 1;
    private final JLabel pageStatusLabel = new JLabel("Showing 1 - 10 of 0 transactions");

    /** 2-arg constructor for backward compatibility. */
    public HistoryPanel(AuthService authService, TradingService tradingService) {
        this(authService, tradingService, null, null, null);
    }

    /** Full terminal constructor. */
    public HistoryPanel(AuthService authService, TradingService tradingService,
                        MarketService marketService, PortfolioService portfolioService,
                        Consumer<String> navHandler) {
        super("History", "Audit simulated execution logs, evaluate quantitative win/loss distribution, and export regulatory statement ledgers.");
        this.authService = authService;
        this.tradingService = tradingService;
        this.marketService = marketService;
        this.portfolioService = portfolioService;
        this.navHandler = navHandler;

        setLayout(new BorderLayout());
        setBackground(Theme.APP_BG);

        JPanel workspace = new JPanel();
        workspace.setLayout(new BoxLayout(workspace, BoxLayout.Y_AXIS));
        workspace.setBackground(Theme.APP_BG);
        workspace.setBorder(BorderFactory.createEmptyBorder(12, 16, 20, 16));

        // 1. Top Action & Export Bar
        workspace.add(buildTopActionBar());
        workspace.add(Box.createVerticalStrut(14));

        // 2. Six Financial KPI Summary Cards
        workspace.add(buildKpiRow());
        workspace.add(Box.createVerticalStrut(14));

        // 3. Interactive Analytics Suite (3-Column Grid)
        workspace.add(buildAnalyticsRow());
        workspace.add(Box.createVerticalStrut(14));

        // 4. Monthly P&L Heatmap Calendar & Advanced Quant Metrics
        workspace.add(buildHeatmapAndQuantSection());
        workspace.add(Box.createVerticalStrut(14));

        // 5. Powerful Transaction Filtering Toolbar
        workspace.add(buildFilterToolbar());
        workspace.add(Box.createVerticalStrut(14));

        // 6. Main Transaction Workspace (Table & Inspector Split)
        workspace.add(buildTableAndInspectorWorkspace());

        JScrollPane scrollPane = new JScrollPane(workspace);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setBackground(Theme.APP_BG);
        scrollPane.getViewport().setBackground(Theme.APP_BG);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        add(scrollPane, BorderLayout.CENTER);
    }

    // =========================================================================
    // SECTION 1: TOP ACTION & EXPORT BAR
    // =========================================================================

    private JPanel buildTopActionBar() {
        JPanel bar = new JPanel(new BorderLayout(14, 8));
        bar.setBackground(Theme.SURFACE_SUB);
        bar.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 10, 1),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)
        ));

        // Left: Title + Audit Badge + Subtitle
        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setOpaque(false);

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        titleRow.setOpaque(false);
        JLabel title = new JLabel("Transaction History & Analytics");
        title.setFont(new Font("Inter", Font.BOLD, 18));
        title.setForeground(Theme.TXT_PRIMARY);
        titleRow.add(title);

        JLabel auditBadge = new JLabel("AUDIT V4.2");
        auditBadge.setFont(Theme.MICRO);
        auditBadge.setForeground(Theme.ACCENT);
        auditBadge.setBackground(Theme.SIDEBAR_BG);
        auditBadge.setOpaque(true);
        auditBadge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER_CUSTOM, 1),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)
        ));
        titleRow.add(auditBadge);
        left.add(titleRow);
        left.add(Box.createVerticalStrut(3));

        JLabel sub = new JLabel("Audit simulated execution logs, evaluate quantitative win/loss distribution, and export regulatory statement ledgers.");
        sub.setFont(Theme.MICRO);
        sub.setForeground(Theme.TXT_MUTED);
        left.add(sub);
        bar.add(left, BorderLayout.WEST);

        // Right: Period Selector & Quick Export Buttons
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);

        // Period Buttons
        JPanel periodBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        periodBox.setBackground(Theme.SIDEBAR_BG);
        periodBox.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER_CUSTOM, 1),
                BorderFactory.createEmptyBorder(2, 2, 2, 2)
        ));
        String[] periods = {"1W", "1M", "3M", "6M", "1Y", "ALL"};
        List<JButton> periodButtons = new ArrayList<>();
        for (String p : periods) {
            JButton pb = new JButton(p);
            periodButtons.add(pb);
            // the chosen period limits every number and the table on this page
            pb.addActionListener(e -> {
                period = p;
                for (JButton other : periodButtons) {
                    boolean on = other == pb;
                    other.setBackground(on ? Theme.ACCENT : Theme.SIDEBAR_BG);
                    other.setForeground(on ? Color.WHITE : Theme.TXT_MUTED);
                }
                refresh();
            });
            pb.setFont(Theme.MICRO);
            pb.setFocusPainted(false);
            pb.setBorderPainted(false);
            pb.setCursor(new Cursor(Cursor.HAND_CURSOR));
            if ("ALL".equals(p)) {
                pb.setBackground(Theme.ACCENT);
                pb.setForeground(Color.WHITE);
            } else {
                pb.setBackground(Theme.SIDEBAR_BG);
                pb.setForeground(Theme.TXT_MUTED);
            }
            periodBox.add(pb);
        }
        right.add(periodBox);

        // Quick Export Buttons: CSV, Excel, PDF, Tax P&L Report
        JButton btnCsv = createVectorActionBtn("CSV", Theme.SURFACE_SUB, Theme.BORDER_CUSTOM, Theme.TXT_PRIMARY, 1);
        btnCsv.addActionListener(e -> onExportCsv());
        right.add(btnCsv);

        JButton btnExcel = createVectorActionBtn("Excel", Theme.SURFACE_SUB, Theme.BORDER_CUSTOM, Theme.TXT_PRIMARY, 2);
        btnExcel.addActionListener(e -> onExportExcel());
        right.add(btnExcel);

        JButton btnPdf = createVectorActionBtn("PDF", Theme.SURFACE_SUB, Theme.BORDER_CUSTOM, Theme.TXT_PRIMARY, 3);
        btnPdf.addActionListener(e -> onExportPdfStatement());
        right.add(btnPdf);

        JButton btnTax = createVectorActionBtn("Tax P&L Report", Theme.ACCENT, Theme.ACCENT, Color.WHITE, 4);
        btnTax.addActionListener(e -> onShowTaxReport());
        right.add(btnTax);

        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    // =========================================================================
    // SECTION 2: SIX FINANCIAL KPI SUMMARY CARDS
    // =========================================================================

    private JPanel buildKpiRow() {
        JPanel grid = new JPanel(new GridLayout(1, 6, 10, 0));
        grid.setOpaque(false);
        grid.add(cardTrades);
        grid.add(cardBuys);
        grid.add(cardSells);
        grid.add(cardRealizedPnl);
        grid.add(cardWinRate);
        grid.add(cardFees);
        return grid;
    }

    // =========================================================================
    // SECTION 3: INTERACTIVE ANALYTICS SUITE (3-COLUMN GRID)
    // =========================================================================

    private JPanel buildAnalyticsRow() {
        JPanel row = new JPanel(new GridLayout(1, 3, 12, 0));
        row.setOpaque(false);
        row.add(histogramCard);
        row.add(growthCard);
        row.add(donutCard);
        return row;
    }

    // =========================================================================
    // SECTION 4: MONTHLY P&L HEATMAP CALENDAR & ADVANCED QUANT METRICS
    // =========================================================================

    private JPanel buildHeatmapAndQuantSection() {
        JPanel sec = new JPanel();
        sec.setLayout(new BoxLayout(sec, BoxLayout.Y_AXIS));
        sec.setBackground(Theme.CARD_BG);
        sec.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 10, 1),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)
        ));

        sec.add(heatmapCard);
        sec.add(Box.createVerticalStrut(10));
        sec.add(quantGrid);
        return sec;
    }

    // =========================================================================
    // SECTION 5: POWERFUL TRANSACTION FILTERING TOOLBAR
    // =========================================================================

    private JPanel buildFilterToolbar() {
        JPanel toolbar = new JPanel();
        toolbar.setLayout(new BoxLayout(toolbar, BoxLayout.Y_AXIS));
        toolbar.setBackground(Theme.CARD_BG);
        toolbar.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 10, 1),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)
        ));

        // Top Row: Search Input + Counter + Reset
        JPanel topRow = new JPanel(new BorderLayout(12, 0));
        topRow.setOpaque(false);

        searchField.setBackground(Theme.SIDEBAR_BG);
        searchField.setForeground(Theme.TXT_PRIMARY);
        searchField.setCaretColor(Theme.ACCENT);
        searchField.setFont(Theme.SMALL);
        searchField.putClientProperty("JTextField.placeholderText", "Search by symbol, company, Order ID (e.g. #ORD-9842), Txn ID...");
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER_CUSTOM, 1),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { applyFilters(); }
            @Override public void removeUpdate(DocumentEvent e) { applyFilters(); }
            @Override public void changedUpdate(DocumentEvent e) { applyFilters(); }
        });
        topRow.add(searchField, BorderLayout.CENTER);

        JPanel rightStats = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightStats.setOpaque(false);
        recordCountLabel.setFont(Theme.MICRO);
        recordCountLabel.setForeground(Theme.TXT_MUTED);
        rightStats.add(recordCountLabel);

        JButton btnReset = new JButton("Reset Filters");
        btnReset.setFont(Theme.MICRO);
        btnReset.setBackground(Theme.SURFACE_SUB);
        btnReset.setForeground(Theme.TXT_PRIMARY);
        btnReset.setFocusPainted(false);
        btnReset.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER_CUSTOM, 1),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        btnReset.addActionListener(e -> resetFilters());
        rightStats.add(btnReset);
        topRow.add(rightStats, BorderLayout.EAST);

        toolbar.add(topRow);
        toolbar.add(Box.createVerticalStrut(8));

        // Bottom Row: 6 Filter Dropdowns
        JPanel filterRow = new JPanel(new GridLayout(1, 6, 8, 0));
        filterRow.setOpaque(false);

        filterRow.add(buildDropdownCol("Asset Class", filterAssetClass));
        filterRow.add(buildDropdownCol("Exchange", filterExchange));
        filterRow.add(buildDropdownCol("Order Side", filterSide));
        filterRow.add(buildDropdownCol("Order Type", filterOrderType));
        filterRow.add(buildDropdownCol("Execution Status", filterStatus));
        filterRow.add(buildDropdownCol("Date Span", filterDateSpan));

        toolbar.add(filterRow);
        return toolbar;
    }

    private JPanel buildDropdownCol(String title, JComboBox<String> combo) {
        JPanel col = new JPanel();
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setOpaque(false);

        JLabel l = new JLabel(title.toUpperCase());
        l.setFont(Theme.TINY);
        l.setForeground(Theme.TXT_MUTED);
        col.add(l);
        col.add(Box.createVerticalStrut(2));

        combo.setBackground(Theme.SIDEBAR_BG);
        combo.setForeground(Theme.TXT_PRIMARY);
        combo.setFont(Theme.MICRO);
        combo.setBorder(BorderFactory.createLineBorder(Theme.BORDER_CUSTOM, 1));
        combo.addActionListener(e -> applyFilters());
        col.add(combo);
        return col;
    }

    private void resetFilters() {
        searchField.setText("");
        filterAssetClass.setSelectedIndex(0);
        filterExchange.setSelectedIndex(0);
        filterSide.setSelectedIndex(0);
        filterOrderType.setSelectedIndex(0);
        filterStatus.setSelectedIndex(0);
        filterDateSpan.setSelectedIndex(0);
        setStatusTab("ALL");
    }

    // =========================================================================
    // SECTION 6: MAIN WORKSPACE (TABLE & DETAIL INSPECTOR SPLIT)
    // =========================================================================

    private JPanel buildTableAndInspectorWorkspace() {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);

        // Left 8 Cols: Table Container
        JPanel tableContainer = new JPanel(new BorderLayout(0, 0));
        tableContainer.setBackground(Theme.CARD_BG);
        tableContainer.setBorder(new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 10, 1));

        // Status Tabs Header
        JPanel tabHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 6));
        tabHeader.setBackground(Theme.SURFACE_SUB);
        tabHeader.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_CUSTOM));

        tabHeader.add(tabAll);
        tabHeader.add(tabPending);
        tabHeader.add(tabFilled);
        tabHeader.add(tabCancelled);
        tabHeader.add(tabRejected);

        tabAll.addActionListener(e -> setStatusTab("ALL"));
        tabPending.addActionListener(e -> setStatusTab("PENDING"));
        tabFilled.addActionListener(e -> setStatusTab("FILLED"));
        tabCancelled.addActionListener(e -> setStatusTab("CANCELLED"));
        tabRejected.addActionListener(e -> setStatusTab("REJECTED"));

        tableContainer.add(tabHeader, BorderLayout.NORTH);

        // JTable Setup
        historyTable.setRowHeight(38);
        historyTable.setBackground(Theme.CARD_BG);
        historyTable.setForeground(Theme.TXT_PRIMARY);
        historyTable.setGridColor(Theme.BORDER_SUBTLE);
        historyTable.setSelectionBackground(Theme.SURFACE_SUB);
        historyTable.setSelectionForeground(Theme.TXT_PRIMARY);
        historyTable.getTableHeader().setBackground(Theme.SIDEBAR_BG);
        historyTable.getTableHeader().setForeground(Theme.TXT_MUTED);
        historyTable.getTableHeader().setFont(Theme.MONO_SMALL);
        historyTable.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_CUSTOM));
        historyTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Custom Column Renderers
        historyTable.getColumnModel().getColumn(0).setPreferredWidth(100);
        historyTable.getColumnModel().getColumn(0).setCellRenderer(new DateTimeCellRenderer());

        historyTable.getColumnModel().getColumn(1).setPreferredWidth(70);
        historyTable.getColumnModel().getColumn(1).setCellRenderer(new OrderIdCellRenderer());

        historyTable.getColumnModel().getColumn(2).setPreferredWidth(140);
        historyTable.getColumnModel().getColumn(2).setCellRenderer(new AssetSymbolCellRenderer());

        historyTable.getColumnModel().getColumn(3).setPreferredWidth(50);
        historyTable.getColumnModel().getColumn(3).setCellRenderer(new SideCellRenderer());

        historyTable.getColumnModel().getColumn(4).setPreferredWidth(80);
        historyTable.getColumnModel().getColumn(4).setCellRenderer(new DefaultAlignRenderer(SwingConstants.LEFT, Theme.TXT_SECONDARY));

        historyTable.getColumnModel().getColumn(5).setPreferredWidth(60);
        historyTable.getColumnModel().getColumn(5).setCellRenderer(new DefaultAlignRenderer(SwingConstants.RIGHT, Theme.TXT_PRIMARY));

        historyTable.getColumnModel().getColumn(6).setPreferredWidth(80);
        historyTable.getColumnModel().getColumn(6).setCellRenderer(new PriceCellRenderer());

        historyTable.getColumnModel().getColumn(7).setPreferredWidth(90);
        historyTable.getColumnModel().getColumn(7).setCellRenderer(new GrossValueCellRenderer());

        historyTable.getColumnModel().getColumn(8).setPreferredWidth(90);
        historyTable.getColumnModel().getColumn(8).setCellRenderer(new RealizedPnlCellRenderer());

        historyTable.getColumnModel().getColumn(9).setPreferredWidth(70);
        historyTable.getColumnModel().getColumn(9).setCellRenderer(new StatusCellRenderer());

        historyTable.getColumnModel().getColumn(10).setPreferredWidth(80);
        historyTable.getColumnModel().getColumn(10).setCellRenderer(new ActionsCellRenderer());
        historyTable.getColumnModel().getColumn(10).setCellEditor(new ActionsCellEditor());

        // Selection Listener -> Updates Inspector Panel
        historyTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedIdx = historyTable.getSelectedRow();
                if (selectedIdx >= 0 && selectedIdx < tableModel.currentPageList.size()) {
                    AuditRowData r = tableModel.currentPageList.get(selectedIdx);
                    inspectorPanel.updateOrder(r);
                }
            }
        });

        JScrollPane tableScroll = new JScrollPane(historyTable);
        tableScroll.setBorder(BorderFactory.createEmptyBorder());
        tableScroll.getViewport().setBackground(Theme.CARD_BG);
        tableScroll.setPreferredSize(new Dimension(750, 320));
        tableContainer.add(tableScroll, BorderLayout.CENTER);

        // Pagination Footer
        JPanel pageFooter = new JPanel(new BorderLayout());
        pageFooter.setBackground(Theme.SURFACE_SUB);
        pageFooter.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER_CUSTOM),
                BorderFactory.createEmptyBorder(8, 14, 8, 14)
        ));

        pageStatusLabel.setFont(Theme.MICRO);
        pageStatusLabel.setForeground(Theme.TXT_MUTED);
        pageFooter.add(pageStatusLabel, BorderLayout.WEST);

        JPanel pageBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        pageBtns.setOpaque(false);
        JButton btnPrev = new JButton("<");
        btnPrev.setFont(Theme.MICRO);
        btnPrev.setBackground(Theme.SIDEBAR_BG);
        btnPrev.setForeground(Theme.TXT_PRIMARY);
        btnPrev.addActionListener(e -> {
            if (currentPage > 1) {
                currentPage--;
                tableModel.paginate(currentPage, rowsPerPage);
                updatePaginationLabel();
            }
        });

        JButton btnNext = new JButton(">");
        btnNext.setFont(Theme.MICRO);
        btnNext.setBackground(Theme.SIDEBAR_BG);
        btnNext.setForeground(Theme.TXT_PRIMARY);
        btnNext.addActionListener(e -> {
            int maxP = (int) Math.ceil((double) tableModel.filteredList.size() / rowsPerPage);
            if (currentPage < maxP) {
                currentPage++;
                tableModel.paginate(currentPage, rowsPerPage);
                updatePaginationLabel();
            }
        });

        pageBtns.add(btnPrev);
        pageBtns.add(btnNext);
        pageFooter.add(pageBtns, BorderLayout.EAST);
        tableContainer.add(pageFooter, BorderLayout.SOUTH);

        row.add(tableContainer, BorderLayout.CENTER);

        // Right 4 Cols: Inspector Panel
        inspectorPanel.setPreferredSize(new Dimension(380, 420));
        row.add(inspectorPanel, BorderLayout.EAST);

        return row;
    }

    private JButton createStatusTabBtn(String label, int count, boolean active) {
        JButton btn = new JButton(String.format("%s (%d)", label, count));
        btn.setFont(Theme.MICRO);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        setStatusTabStyle(btn, active);
        return btn;
    }

    private void setStatusTabStyle(JButton btn, boolean active) {
        if (active) {
            btn.setBackground(Theme.CARD_BG);
            btn.setForeground(Theme.ACCENT);
        } else {
            btn.setBackground(Theme.SURFACE_SUB);
            btn.setForeground(Theme.TXT_MUTED);
        }
    }

    private void setStatusTab(String tab) {
        this.activeStatusTab = tab;
        setStatusTabStyle(tabAll, "ALL".equals(tab));
        setStatusTabStyle(tabPending, "PENDING".equals(tab));
        setStatusTabStyle(tabFilled, "FILLED".equals(tab));
        setStatusTabStyle(tabCancelled, "CANCELLED".equals(tab));
        setStatusTabStyle(tabRejected, "REJECTED".equals(tab));
        applyFilters();
    }

    private void applyFilters() {
        String q = searchField.getText();
        String assetClass = (String) filterAssetClass.getSelectedItem();
        String ex = (String) filterExchange.getSelectedItem();
        String side = (String) filterSide.getSelectedItem();
        String type = (String) filterOrderType.getSelectedItem();
        String status = (String) filterStatus.getSelectedItem();
        String dateSpan = (String) filterDateSpan.getSelectedItem();

        tableModel.applyFilter(activeStatusTab, q, assetClass, ex, side, type, status, dateSpan);
        currentPage = 1;
        tableModel.paginate(currentPage, rowsPerPage);
        updatePaginationLabel();

        recordCountLabel.setText(String.format("Showing %d of %d records",
                tableModel.filteredList.size(), tableModel.allList.size()));

        if (tableModel.currentPageList.size() > 0) {
            historyTable.setRowSelectionInterval(0, 0);
            inspectorPanel.updateOrder(tableModel.currentPageList.get(0));
        }
    }

    private void updatePaginationLabel() {
        int total = tableModel.filteredList.size();
        int start = total == 0 ? 0 : (currentPage - 1) * rowsPerPage + 1;
        int end = Math.min(currentPage * rowsPerPage, total);
        pageStatusLabel.setText(String.format("Showing %d - %d of %d transactions", start, end, total));
    }

    // =========================================================================
    // REFRESH & DATA POPULATION
    // =========================================================================

    @Override
    public void refresh() {
        User user = authService.getCurrentUser();
        if (user == null) return;

        List<Transaction> txList = tradingService != null ? tradingService.getTransactionHistory(user) : new ArrayList<>();
        LocalDateTime cutoff = periodStart();
        if (cutoff != null) {
            txList = txList.stream().filter(t -> !t.getTimestamp().isBefore(cutoff)).toList();
        }
        List<AuditRowData> auditRows = buildAuditRowDataList(txList);
        if (tradingService != null) {
            auditRows.addAll(0, buildPendingRows(tradingService.getPendingOrders(user)));
        }
        stats = TradeStats.of(txList);
        double unrealized = portfolioService != null
                ? portfolioService.getTotalMarketValue(user) - portfolioService.getTotalCostBasis(user) : Double.NaN;
        PerformanceReport report = analytics != null ? analytics.getReport(user) : null;

        // Update Tab Counts
        long pendingCount = auditRows.stream().filter(r -> "PENDING".equalsIgnoreCase(r.status())).count();
        long filledCount = auditRows.stream().filter(r -> "FILLED".equalsIgnoreCase(r.status())).count();
        long cancelCount = auditRows.stream().filter(r -> "CANCELLED".equalsIgnoreCase(r.status())).count();
        long rejectCount = auditRows.stream().filter(r -> "REJECTED".equalsIgnoreCase(r.status())).count();

        tabAll.setText("ALL ORDERS (" + auditRows.size() + ")");
        tabPending.setText("OPEN / PENDING (" + pendingCount + ")");
        tabFilled.setText("FILLED EXECUTIONS (" + filledCount + ")");
        tabCancelled.setText("CANCELLED (" + cancelCount + ")");
        tabRejected.setText("REJECTED (" + rejectCount + ")");

        // Update Table
        tableModel.setAllRows(auditRows);
        applyFilters();

        // Update 6 KPI Cards
        int pending = (int) pendingCount;
        cardTrades.updateData(stats, pending);
        cardBuys.updateData(stats);
        cardSells.updateData(stats);
        cardRealizedPnl.updateData(stats, unrealized);
        cardWinRate.updateData(stats);
        cardFees.updateData(stats, tradingService != null ? tradingService.getFeeDescription() : "");

        // Update Analytics
        histogramCard.updateData(stats);
        growthCard.updateData(stats);
        donutCard.updateData(stats);

        // Update Heatmap & Quant
        heatmapCard.updateData(stats);
        quantGrid.updateData(stats, report);
    }

    private List<AuditRowData> buildAuditRowDataList(List<Transaction> txs) {
        List<AuditRowData> rows = new ArrayList<>();
        if (txs == null) {
            return rows;
        } else {
            for (int i = 0; i < txs.size(); i++) {
                Transaction t = txs.get(i);
                String sym = t.getSymbol();
                boolean ind = isIndianAsset(sym);
                String ex = ind ? "NSE" : (isCryptoAsset(sym) ? "BINANCE" : "NASDAQ");
                String cat = ind ? "Equity" : (isCryptoAsset(sym) ? "Crypto" : "Tech");
                double gross = t.getPrice() * t.getQuantity();
                double pnl = t.getRealizedPnl();   // real: (sell price - average cost) x qty - fee, saved on every SELL
                rows.add(new AuditRowData(
                        t.getId(),
                        String.format("#ORD-%04d", t.getId()),
                        t.getTimestamp(),
                        sym,
                        assetName(sym),
                        ex,
                        cat,
                        t.getSide().name(),
                        t.getOrderType() + " · CNC",
                        t.getQuantity(),
                        t.getPrice(),
                        gross,
                        pnl,
                        "FILLED",
                        t.getFee()
                ));
            }
        }
        return rows;
    }

    /** Start of the selected period (null = all history). */
    private LocalDateTime periodStart() {
        LocalDateTime now = LocalDateTime.now();
        return switch (period) {
            case "1W" -> now.minusWeeks(1);
            case "1M" -> now.minusMonths(1);
            case "3M" -> now.minusMonths(3);
            case "6M" -> now.minusMonths(6);
            case "1Y" -> now.minusYears(1);
            default -> null;
        };
    }

    /** Open limit/stop orders, shown as PENDING rows. */
    private List<AuditRowData> buildPendingRows(List<com.papertrader.model.Order> orders) {
        List<AuditRowData> rows = new ArrayList<>();
        for (com.papertrader.model.Order o : orders) {
            Asset a = o.getAsset();
            String sym = a.getSymbol();
            boolean ind = isIndianAsset(sym);
            rows.add(new AuditRowData(o.getId(), String.format("#ORD-%04d", o.getId()), o.getCreatedAt(), sym, a.getName(),
                    ind ? "NSE" : (isCryptoAsset(sym) ? "BINANCE" : "NASDAQ"), ind ? "Equity" : (isCryptoAsset(sym) ? "Crypto" : "Tech"),
                    o.getSide().name(), o.getOrderType() + " · " + o.getConditionText(), o.getQuantity(), a.getPrice(),
                    a.getPrice() * o.getQuantity(), 0.0, "PENDING", 0.0));
        }
        return rows;
    }

    private String assetName(String sym) {
        return marketService == null ? sym : marketService.findBySymbol(sym).map(Asset::getName).orElse(sym);
    }

    /** Gives the page the saved equity history (for the Sharpe ratio). */
    public void setAnalytics(AnalyticsService analytics) {
        this.analytics = analytics;
    }

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

    // =========================================================================
    // EXPORT HANDLERS (CSV, EXCEL, PDF, TAX P&L)
    // =========================================================================

    private void onExportCsv() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("simulated_transactions_log.csv"));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            try (FileWriter fw = new FileWriter(f)) {
                fw.write("OrderID,Timestamp,Symbol,Side,Type,Quantity,Price,GrossValue,RealizedPnL,Status\n");
                for (AuditRowData r : tableModel.filteredList) {
                    fw.write(String.format("%s,%s,%s,%s,%s,%d,%.2f,%.2f,%.2f,%s\n",
                            r.orderId(), r.timestamp(), r.symbol(), r.side(), r.orderType(),
                            r.quantity(), r.price(), r.grossValue(), r.realizedPnl(), r.status()));
                }
                JOptionPane.showMessageDialog(this, "CSV Transaction Log exported successfully:\n" + f.getAbsolutePath(), "Export Complete", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Export Failed", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void onExportExcel() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("regulatory_transaction_ledger.tsv"));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            try (FileWriter fw = new FileWriter(f)) {
                fw.write("Order ID\tDate Time\tSymbol\tExchange\tSide\tOrder Type\tQty\tExec Price\tGross Value\tRealized P&L\tStatus\n");
                for (AuditRowData r : tableModel.filteredList) {
                    fw.write(String.format("%s\t%s\t%s\t%s\t%s\t%s\t%d\t%.2f\t%.2f\t%.2f\t%s\n",
                            r.orderId(), r.timestamp(), r.symbol(), r.exchange(), r.side(), r.orderType(),
                            r.quantity(), r.price(), r.grossValue(), r.realizedPnl(), r.status()));
                }
                JOptionPane.showMessageDialog(this, "Excel/TSV Ledger exported successfully:\n" + f.getAbsolutePath(), "Export Complete", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Export Failed", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void onExportPdfStatement() {
        JDialog d = new JDialog(SwingUtilities.getWindowAncestor(this), "Official Clearing Statement (PDF Preview)", JDialog.ModalityType.APPLICATION_MODAL);
        d.setSize(620, 500);
        d.setLocationRelativeTo(this);

        JPanel p = new JPanel(new BorderLayout(10, 10));
        p.setBackground(Theme.CARD_BG);
        p.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));

        JLabel title = new JLabel("PAPERTRADER 3D - OFFICIAL TRANSACTION CLEARING STATEMENT");
        title.setFont(new Font("Inter", Font.BOLD, 14));
        title.setForeground(Theme.TXT_PRIMARY);
        p.add(title, BorderLayout.NORTH);

        StringBuilder sb = new StringBuilder();
        sb.append("================================================================================\n");
        sb.append("STATEMENT PERIOD: ALL-TIME SIMULATION LEDGER\n");
        sb.append("TOTAL CLEARED ORDERS: " + tableModel.filteredList.size() + "\n");
        sb.append("================================================================================\n\n");
        sb.append(String.format("%-10s %-16s %-10s %-6s %-10s %-12s %-12s\n", "ORDER ID", "DATE TIME", "SYMBOL", "SIDE", "QTY", "PRICE", "STATUS"));
        sb.append("--------------------------------------------------------------------------------\n");
        for (AuditRowData r : tableModel.filteredList) {
            sb.append(String.format("%-10s %-16s %-10s %-6s %-10d $%-11.2f %-12s\n",
                    r.orderId(), r.timestamp().format(DateTimeFormatter.ofPattern("dd/MM HH:mm")),
                    r.symbol(), r.side(), r.quantity(), r.price(), r.status()));
        }
        sb.append("--------------------------------------------------------------------------------\n");
        sb.append("CONFIDENTIAL SIMULATION AUDIT - FINTECH PAPER TRADING TERMINAL 2024\n");

        javax.swing.JTextArea area = new javax.swing.JTextArea(sb.toString());
        area.setFont(new Font("JetBrains Mono", Font.PLAIN, 11));
        area.setBackground(Theme.SIDEBAR_BG);
        area.setForeground(Theme.TXT_PRIMARY);
        area.setEditable(false);
        p.add(new JScrollPane(area), BorderLayout.CENTER);

        JButton btnClose = new JButton("Close Statement");
        btnClose.setBackground(Theme.ACCENT);
        btnClose.setForeground(Color.WHITE);
        btnClose.addActionListener(e -> d.dispose());
        p.add(btnClose, BorderLayout.SOUTH);

        d.setContentPane(p);
        d.setVisible(true);
    }

    private void onShowTaxReport() {
        JDialog d = new JDialog(SwingUtilities.getWindowAncestor(this), "Tax P&L Regulatory Report", JDialog.ModalityType.APPLICATION_MODAL);
        d.setSize(600, 460);
        d.setLocationRelativeTo(this);

        JPanel p = new JPanel(new BorderLayout(10, 10));
        p.setBackground(Theme.CARD_BG);
        p.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));

        JLabel title = new JLabel("SIMULATED CAPITAL GAINS & TAX AUDIT STATEMENT");
        title.setFont(new Font("Inter", Font.BOLD, 15));
        title.setForeground(Theme.TXT_PRIMARY);
        p.add(title, BorderLayout.NORTH);

        // Real numbers from the user's trades. Holding period uses FIFO; rates are India's
        // equity rules (STCG 20%, LTCG 12.5% above the 1.25 lakh yearly exemption) as an estimate.
        TradeStats st = stats;
        java.time.LocalDate today = java.time.LocalDate.now();
        int fyStart = today.getMonthValue() >= 4 ? today.getYear() : today.getYear() - 1;
        double stcg = st.shortTermGains * USD_TO_INR;
        double ltcg = st.longTermGains * USD_TO_INR;
        double harvest = 0;
        User u = authService.getCurrentUser();
        if (u != null && marketService != null) {
            for (com.papertrader.model.Holding h : u.getPortfolio().getHoldings()) {
                double price = marketService.findBySymbol(h.getSymbol()).map(Asset::getPrice).orElse(h.getAverageCost());
                double pl = (price - h.getAverageCost()) * h.getQuantity();
                if (pl < 0) {
                    harvest += pl;
                }
            }
        }
        harvest *= USD_TO_INR;
        double stcgTax = Math.max(0, stcg) * 0.20;
        double ltcgTax = Math.max(0, ltcg - 125_000) * 0.125;
        double feesInr = st.fees * USD_TO_INR;
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("FINANCIAL YEAR: %d - %d (SIMULATED TRADES)%n", fyStart, fyStart + 1));
        sb.append("--------------------------------------------------------------------------------\n");
        sb.append(String.format(Locale.US, "CLOSED TRADES (SELLS):                %d (%d wins / %d losses)%n", st.sells, st.wins, st.losses));
        sb.append(String.format(Locale.US, "SHORT-TERM CAPITAL GAINS (STCG):      %s%n", inr(stcg)));
        sb.append(String.format(Locale.US, "LONG-TERM CAPITAL GAINS (LTCG):       %s%n", inr(ltcg)));
        sb.append(String.format(Locale.US, "NET REALIZED CAPITAL GAINS:           %s%n", inr(stcg + ltcg)));
        sb.append(String.format(Locale.US, "TAX LOSS HARVESTING (OPEN LOSSES):    %s%n", inr(harvest)));
        sb.append("--------------------------------------------------------------------------------\n");
        sb.append(String.format(Locale.US, "EST. TAX ON STCG (20%%):               %s%n", inr(stcgTax)));
        sb.append(String.format(Locale.US, "EST. TAX ON LTCG (12.5%% > 1.25L):     %s%n", inr(ltcgTax)));
        sb.append(String.format(Locale.US, "TRADING FEES PAID (%s): %s%n",
                tradingService != null ? tradingService.getFeeDescription() : "fees", inr(feesInr)));
        sb.append("--------------------------------------------------------------------------------\n");
        sb.append(String.format(Locale.US, "NET AFTER ESTIMATED TAX:              %s%n", inr(stcg + ltcg - stcgTax - ltcgTax)));
        sb.append("\nEducational estimate only. Rupee values use the USD/INR rate shown in the app.\n");

        javax.swing.JTextArea area = new javax.swing.JTextArea(sb.toString());
        area.setFont(new Font("JetBrains Mono", Font.PLAIN, 12));
        area.setBackground(Theme.SIDEBAR_BG);
        area.setForeground(Theme.TXT_PRIMARY);
        area.setEditable(false);
        p.add(new JScrollPane(area), BorderLayout.CENTER);

        JButton btnClose = new JButton("Dismiss");
        btnClose.setBackground(Theme.ACCENT);
        btnClose.setForeground(Color.WHITE);
        btnClose.addActionListener(e -> d.dispose());
        p.add(btnClose, BorderLayout.SOUTH);

        d.setContentPane(p);
        d.setVisible(true);
    }

    private static String inr(double v) {
        return String.format(Locale.US, "%s₹%,.2f", v < 0 ? "-" : "+", Math.abs(v));
    }

    private JButton createVectorActionBtn(String text, Color bg, Color border, Color fg, int type) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(border);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);

                int cy = getHeight() / 2;
                g2.setColor(fg);
                if (type == 1) { // download CSV
                    g2.drawLine(10, cy - 3, 10, cy + 3);
                    g2.drawLine(8, cy + 1, 10, cy + 3);
                    g2.drawLine(12, cy + 1, 10, cy + 3);
                    g2.drawLine(7, cy + 4, 13, cy + 4);
                } else if (type == 2) { // table view Excel
                    g2.drawRect(7, cy - 4, 8, 8);
                    g2.drawLine(7, cy, 15, cy);
                    g2.drawLine(11, cy - 4, 11, cy + 4);
                } else if (type == 3) { // PDF
                    g2.drawRoundRect(7, cy - 5, 8, 10, 2, 2);
                    g2.drawLine(9, cy - 2, 13, cy - 2);
                    g2.drawLine(9, cy + 1, 13, cy + 1);
                } else if (type == 4) { // receipt
                    g2.drawRect(7, cy - 5, 8, 10);
                    g2.drawLine(9, cy - 2, 13, cy - 2);
                    g2.drawLine(9, cy + 1, 11, cy + 1);
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
        btn.setBorder(BorderFactory.createEmptyBorder(6, 20, 6, 12));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    // =========================================================================
    // AUDIT ROW DATA RECORD
    // =========================================================================

    public record AuditRowData(
            int id,
            String orderId,
            LocalDateTime timestamp,
            String symbol,
            String name,
            String exchange,
            String category,
            String side,
            String orderType,
            int quantity,
            double price,
            double grossValue,
            double realizedPnl,
            String status,
            double fee          // fee actually charged by the simulator's FeeCalculator
    ) {}

    // =========================================================================
    // HISTORY TABLE MODEL
    // =========================================================================

    private class HistoryLedgerTableModel extends AbstractTableModel {
        private final String[] columns = {
                "Date & Time", "Order ID", "Asset / Symbol", "Side", "Type",
                "Qty", "Exec Price", "Gross Value", "Realized P&L", "Status", "Actions"
        };
        private final List<AuditRowData> allList = new ArrayList<>();
        private final List<AuditRowData> filteredList = new ArrayList<>();
        private final List<AuditRowData> currentPageList = new ArrayList<>();

        public void setAllRows(List<AuditRowData> list) {
            allList.clear();
            allList.addAll(list);
        }

        public void applyFilter(String tab, String query, String assetClass, String ex, String side, String type, String status, String dateSpan) {
            filteredList.clear();
            String q = query != null ? query.trim().toUpperCase() : "";

            for (AuditRowData r : allList) {
                // Tab filter
                if (!"ALL".equalsIgnoreCase(tab) && !tab.equalsIgnoreCase(r.status())) {
                    continue;
                }
                // Asset class filter
                if (!"All Assets".equals(assetClass)) {
                    if (assetClass.contains("Indian") && !isIndianAsset(r.symbol())) continue;
                    if (assetClass.contains("US") && (isIndianAsset(r.symbol()) || isCryptoAsset(r.symbol()))) continue;
                    if (assetClass.contains("Crypto") && !isCryptoAsset(r.symbol())) continue;
                }
                // Exchange filter
                if (!"All Exchanges".equals(ex) && !ex.equalsIgnoreCase(r.exchange())) {
                    continue;
                }
                // Side filter
                if (!"All Sides".equals(side) && !side.equalsIgnoreCase(r.side())) {
                    continue;
                }
                // Type filter
                if (!"All Types".equals(type) && !r.orderType().toUpperCase().contains(type.toUpperCase())) {
                    continue;
                }
                // Status filter
                if (!"All Statuses".equals(status) && !status.equalsIgnoreCase(r.status())) {
                    continue;
                }
                // Text search
                if (!q.isEmpty()) {
                    boolean match = r.symbol().toUpperCase().contains(q)
                            || r.orderId().toUpperCase().contains(q)
                            || r.name().toUpperCase().contains(q);
                    if (!match) continue;
                }
                filteredList.add(r);
            }
        }

        public void paginate(int page, int perPage) {
            currentPageList.clear();
            int start = (page - 1) * perPage;
            int end = Math.min(start + perPage, filteredList.size());
            if (start < filteredList.size()) {
                currentPageList.addAll(filteredList.subList(start, end));
            }
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return currentPageList.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int c) { return columns[c]; }
        @Override public boolean isCellEditable(int r, int c) { return c == 10; }

        @Override
        public Object getValueAt(int row, int col) {
            if (row < 0 || row >= currentPageList.size()) return null;
            AuditRowData r = currentPageList.get(row);
            switch (col) {
                case 0: return r.timestamp();
                case 1: return r.orderId();
                case 2: return r;
                case 3: return r.side();
                case 4: return r.orderType();
                case 5: return r.quantity() + " Sh";
                case 6: return r;
                case 7: return r;
                case 8: return r;
                case 9: return r.status();
                case 10: return r;
                default: return null;
            }
        }
    }

    // =========================================================================
    // TABLE CELL RENDERERS & EDITORS
    // =========================================================================

    private static class DateTimeCellRenderer extends DefaultTableCellRenderer {
        private final JLabel dLbl = new JLabel();
        private final JLabel tLbl = new JLabel();
        private final JPanel panel = new JPanel();

        public DateTimeCellRenderer() {
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setOpaque(true);
            dLbl.setFont(new Font("Inter", Font.PLAIN, 11));
            dLbl.setForeground(Theme.TXT_PRIMARY);
            tLbl.setFont(new Font("JetBrains Mono", Font.PLAIN, 10));
            tLbl.setForeground(Theme.TXT_MUTED);
            panel.add(dLbl);
            panel.add(tLbl);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            panel.setBackground(isSelected ? Theme.SURFACE_SUB : Theme.CARD_BG);
            if (value instanceof LocalDateTime ldt) {
                dLbl.setText(ldt.format(DateTimeFormatter.ofPattern("dd MMM yyyy")));
                tLbl.setText(ldt.format(DateTimeFormatter.ofPattern("HH:mm:ss")) + " IST");
            }
            return panel;
        }
    }

    private static class OrderIdCellRenderer extends DefaultTableCellRenderer {
        public OrderIdCellRenderer() {
            setFont(new Font("JetBrains Mono", Font.BOLD, 11));
            setForeground(Theme.ACCENT);
        }
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setBackground(isSelected ? Theme.SURFACE_SUB : Theme.CARD_BG);
            return this;
        }
    }

    private static class AssetSymbolCellRenderer extends JPanel implements TableCellRenderer {
        private final JLabel iconLbl = new JLabel("", SwingConstants.CENTER);
        private final JLabel symLbl = new JLabel();
        private final JLabel subLbl = new JLabel();

        public AssetSymbolCellRenderer() {
            setLayout(new FlowLayout(FlowLayout.LEFT, 8, 4));
            setOpaque(true);

            iconLbl.setPreferredSize(new Dimension(24, 24));
            iconLbl.setFont(new Font("Inter", Font.BOLD, 10));
            iconLbl.setOpaque(true);

            JPanel textCol = new JPanel();
            textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));
            textCol.setOpaque(false);
            symLbl.setFont(new Font("Inter", Font.BOLD, 11));
            subLbl.setFont(new Font("Inter", Font.PLAIN, 9));
            textCol.add(symLbl);
            textCol.add(subLbl);

            add(iconLbl);
            add(textCol);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            setBackground(isSelected ? Theme.SURFACE_SUB : Theme.CARD_BG);
            if (value instanceof AuditRowData r) {
                symLbl.setText(r.symbol());
                symLbl.setForeground(Theme.TXT_PRIMARY);
                subLbl.setText(r.exchange() + " · " + r.category());
                subLbl.setForeground(Theme.TXT_MUTED);

                String initial = r.symbol().substring(0, 1);
                iconLbl.setText(initial);
                iconLbl.setBackground(Theme.SIDEBAR_BG);
                iconLbl.setForeground(Theme.ACCENT);
            }
            return this;
        }
    }

    private static class SideCellRenderer extends DefaultTableCellRenderer {
        public SideCellRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
            setFont(new Font("JetBrains Mono", Font.BOLD, 10));
        }
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setBackground(isSelected ? Theme.SURFACE_SUB : Theme.CARD_BG);
            String side = String.valueOf(value);
            boolean buy = "BUY".equalsIgnoreCase(side);
            setText(side);
            setForeground(buy ? Theme.GAIN : Theme.LOSS);
            return this;
        }
    }

    private static class DefaultAlignRenderer extends DefaultTableCellRenderer {
        public DefaultAlignRenderer(int align, Color fg) {
            setHorizontalAlignment(align);
            setFont(Theme.MONO_BODY);
            setForeground(fg);
        }
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setBackground(isSelected ? Theme.SURFACE_SUB : Theme.CARD_BG);
            return this;
        }
    }

    private static class PriceCellRenderer extends DefaultTableCellRenderer {
        public PriceCellRenderer() {
            setHorizontalAlignment(SwingConstants.RIGHT);
            setFont(Theme.MONO_BODY);
        }
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setBackground(isSelected ? Theme.SURFACE_SUB : Theme.CARD_BG);
            if (value instanceof AuditRowData r) {
                String cur = isIndianAsset(r.symbol()) ? "₹" : "$";
                setText(String.format(Locale.US, "%s%,.2f", cur, r.price()));
                setForeground(Theme.TXT_PRIMARY);
            }
            return this;
        }
    }

    private static class GrossValueCellRenderer extends DefaultTableCellRenderer {
        public GrossValueCellRenderer() {
            setHorizontalAlignment(SwingConstants.RIGHT);
            setFont(Theme.MONO_BODY);
        }
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setBackground(isSelected ? Theme.SURFACE_SUB : Theme.CARD_BG);
            if (value instanceof AuditRowData r) {
                String cur = isIndianAsset(r.symbol()) ? "₹" : "$";
                setText(String.format(Locale.US, "%s%,.2f", cur, r.grossValue()));
                setForeground(Theme.TXT_PRIMARY);
            }
            return this;
        }
    }

    private static class RealizedPnlCellRenderer extends DefaultTableCellRenderer {
        public RealizedPnlCellRenderer() {
            setHorizontalAlignment(SwingConstants.RIGHT);
            setFont(Theme.MONO_BODY);
        }
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setBackground(isSelected ? Theme.SURFACE_SUB : Theme.CARD_BG);
            if (value instanceof AuditRowData r) {
                if (r.realizedPnl() == 0.0 && !"SELL".equalsIgnoreCase(r.side())) {
                    setText("-");
                    setForeground(Theme.TXT_MUTED);
                } else {
                    String cur = isIndianAsset(r.symbol()) ? "₹" : "$";
                    String sign = r.realizedPnl() >= 0 ? "+" : "-";
                    Color c = r.realizedPnl() >= 0 ? Theme.GAIN : Theme.LOSS;
                    setText(String.format(Locale.US, "%s%s%,.2f", sign, cur, Math.abs(r.realizedPnl())));
                    setForeground(c);
                }
            }
            return this;
        }
    }

    private static class StatusCellRenderer extends DefaultTableCellRenderer {
        public StatusCellRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
            setFont(new Font("Inter", Font.BOLD, 9));
        }
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setBackground(isSelected ? Theme.SURFACE_SUB : Theme.CARD_BG);
            String st = String.valueOf(value);
            setText("● " + st);
            if ("FILLED".equalsIgnoreCase(st)) setForeground(Theme.GAIN);
            else if ("PENDING".equalsIgnoreCase(st)) setForeground(Theme.ACCENT);
            else setForeground(Theme.LOSS);
            return this;
        }
    }

    private class ActionsCellRenderer extends JPanel implements TableCellRenderer {
        private final JButton btnView = createVectorMiniBtn("V");
        private final JButton btnRepeat = createVectorMiniBtn("R");

        public ActionsCellRenderer() {
            setLayout(new FlowLayout(FlowLayout.RIGHT, 4, 6));
            setOpaque(true);
            add(btnView);
            add(btnRepeat);
        }
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            setBackground(isSelected ? Theme.SURFACE_SUB : Theme.CARD_BG);
            return this;
        }
    }

    private class ActionsCellEditor extends AbstractCellEditor implements TableCellEditor {
        private final JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 6));
        private final JButton btnView = createVectorMiniBtn("V");
        private final JButton btnRepeat = createVectorMiniBtn("R");
        private AuditRowData currentRow;

        public ActionsCellEditor() {
            panel.setOpaque(true);
            panel.setBackground(Theme.SURFACE_SUB);

            btnView.addActionListener(e -> {
                fireEditingStopped();
                if (currentRow != null) {
                    inspectorPanel.updateOrder(currentRow);
                }
            });

            btnRepeat.addActionListener(e -> {
                fireEditingStopped();
                if (currentRow != null && navHandler != null) {
                    navHandler.accept("Trade:" + currentRow.symbol());
                }
            });

            panel.add(btnView);
            panel.add(btnRepeat);
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            if (value instanceof AuditRowData r) currentRow = r;
            return panel;
        }
        @Override public Object getCellEditorValue() { return currentRow; }
    }

    private static JButton createVectorMiniBtn(String type) {
        JButton b = new JButton(type);
        b.setFont(new Font("JetBrains Mono", Font.BOLD, 9));
        b.setBackground(Theme.SIDEBAR_BG);
        b.setForeground(Theme.TXT_MUTED);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER_CUSTOM, 1),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)
        ));
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return b;
    }

    // =========================================================================
    // RIGHT INSPECTOR PANEL IMPLEMENTATION
    // =========================================================================

    private class OrderInspectorPanel extends JPanel {
        private final JLabel orderTitle = new JLabel("No order selected");
        private final JLabel statusBadge = new JLabel("SIMULATED FILLED", SwingConstants.CENTER);
        private final JLabel timestampLbl = new JLabel("-");

        private final JLabel assetIcon = new JLabel("-", SwingConstants.CENTER);
        private final JLabel assetName = new JLabel("-");
        private final JLabel assetSub = new JLabel("Click a row to inspect it");
        private final JLabel sideTag = new JLabel("-");
        private final JLabel qtyPriceLbl = new JLabel("-");

        // Receipt fields
        private final JLabel grossValLbl = new JLabel("-");
        private final JLabel brokerageLbl = new JLabel("-");
        private final JLabel sttTaxLbl = new JLabel("-");
        private final JLabel turnoverFeeLbl = new JLabel("-");
        private final JLabel sebiLbl = new JLabel("-");
        private final JLabel stampLbl = new JLabel("-");
        private final JLabel gstLbl = new JLabel("-");
        private final JLabel netSettledLbl = new JLabel("-");
        private final JLabel realizedPnlLbl = new JLabel("-");

        private AuditRowData selectedRow;

        public OrderInspectorPanel() {
            setLayout(new BorderLayout(0, 10));
            setBackground(Theme.CARD_BG);
            setBorder(BorderFactory.createCompoundBorder(
                    new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 10, 1),
                    BorderFactory.createEmptyBorder(12, 14, 12, 14)
            ));

            // Header Row
            JPanel hdr = new JPanel(new BorderLayout());
            hdr.setOpaque(false);

            JPanel topTitles = new JPanel();
            topTitles.setLayout(new BoxLayout(topTitles, BoxLayout.Y_AXIS));
            topTitles.setOpaque(false);

            JPanel badgeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            badgeRow.setOpaque(false);
            orderTitle.setFont(new Font("JetBrains Mono", Font.BOLD, 15));
            orderTitle.setForeground(Theme.TXT_PRIMARY);
            statusBadge.setFont(Theme.MICRO);
            statusBadge.setForeground(Theme.GAIN);
            statusBadge.setBackground(Theme.GREEN_BG);
            statusBadge.setOpaque(true);
            statusBadge.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            badgeRow.add(orderTitle);
            badgeRow.add(statusBadge);
            topTitles.add(badgeRow);

            timestampLbl.setFont(Theme.MICRO);
            timestampLbl.setForeground(Theme.TXT_MUTED);
            topTitles.add(timestampLbl);
            hdr.add(topTitles, BorderLayout.WEST);

            JButton btnClose = new JButton("X");
            btnClose.setFont(Theme.MICRO);
            btnClose.setBackground(Theme.SIDEBAR_BG);
            btnClose.setForeground(Theme.TXT_MUTED);
            btnClose.setFocusPainted(false);
            btnClose.addActionListener(e -> setVisible(false));
            hdr.add(btnClose, BorderLayout.EAST);
            add(hdr, BorderLayout.NORTH);

            // Center: Asset Ticket + Execution Telemetry + Financial Receipt
            JPanel center = new JPanel();
            center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
            center.setOpaque(false);

            // 1. Asset Ticket Card
            JPanel ticket = new JPanel(new BorderLayout(8, 0));
            ticket.setBackground(Theme.SURFACE_SUB);
            ticket.setBorder(BorderFactory.createCompoundBorder(
                    new Theme.RoundedBorder(Theme.BORDER_SUBTLE, 8, 1),
                    BorderFactory.createEmptyBorder(8, 10, 8, 10)
            ));

            JPanel tLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            tLeft.setOpaque(false);
            assetIcon.setPreferredSize(new Dimension(32, 32));
            assetIcon.setFont(new Font("Inter", Font.BOLD, 12));
            assetIcon.setBackground(Theme.SIDEBAR_BG);
            assetIcon.setForeground(Theme.ACCENT);
            assetIcon.setOpaque(true);
            tLeft.add(assetIcon);

            JPanel tCol = new JPanel();
            tCol.setLayout(new BoxLayout(tCol, BoxLayout.Y_AXIS));
            tCol.setOpaque(false);
            assetName.setFont(new Font("Inter", Font.BOLD, 13));
            assetName.setForeground(Theme.TXT_PRIMARY);
            assetSub.setFont(Theme.MICRO);
            assetSub.setForeground(Theme.TXT_MUTED);
            tCol.add(assetName);
            tCol.add(assetSub);
            tLeft.add(tCol);
            ticket.add(tLeft, BorderLayout.WEST);

            JPanel tRight = new JPanel();
            tRight.setLayout(new BoxLayout(tRight, BoxLayout.Y_AXIS));
            tRight.setOpaque(false);
            sideTag.setFont(Theme.MICRO);
            sideTag.setForeground(Theme.GAIN);
            qtyPriceLbl.setFont(new Font("JetBrains Mono", Font.BOLD, 11));
            qtyPriceLbl.setForeground(Theme.TXT_PRIMARY);
            tRight.add(sideTag);
            tRight.add(qtyPriceLbl);
            ticket.add(tRight, BorderLayout.EAST);
            center.add(ticket);
            center.add(Box.createVerticalStrut(8));

            // 2. Timeline Strip
            JPanel telemetry = new JPanel();
            telemetry.setLayout(new BoxLayout(telemetry, BoxLayout.Y_AXIS));
            telemetry.setOpaque(false);
            JLabel telemTitle = new JLabel("ENGINE MATCHING TELEMETRY");
            telemTitle.setFont(new Font("Inter", Font.BOLD, 9));
            telemTitle.setForeground(Theme.TXT_MUTED);
            telemetry.add(telemTitle);
            telemetry.add(Box.createVerticalStrut(4));

            telemetry.add(buildTelemetryStep("Order Created (Client UI)", "14:28:10.120", Theme.GAIN));
            telemetry.add(buildTelemetryStep("Submitted to Paper Engine", "14:28:10.450", Theme.GAIN));
            telemetry.add(buildTelemetryStep("Accepted by Matching Node", "14:28:10.820", Theme.GAIN));
            telemetry.add(buildTelemetryStep("Executed & Settled", "14:28:12.000", Theme.ACCENT));
            center.add(telemetry);
            center.add(Box.createVerticalStrut(8));

            // 3. Receipt
            JPanel receipt = new JPanel();
            receipt.setLayout(new BoxLayout(receipt, BoxLayout.Y_AXIS));
            receipt.setBackground(Theme.SIDEBAR_BG);
            receipt.setBorder(BorderFactory.createCompoundBorder(
                    new Theme.RoundedBorder(Theme.BORDER_SUBTLE, 6, 1),
                    BorderFactory.createEmptyBorder(6, 8, 6, 8)
            ));

            receipt.add(buildReceiptRow("Gross Order Value", grossValLbl));
            receipt.add(buildReceiptRow("Simulator Fee (charged)", brokerageLbl));
            receipt.add(buildReceiptRow("STT / CTT Tax (not simulated)", sttTaxLbl));
            receipt.add(buildReceiptRow("Exchange Turnover Fee (not simulated)", turnoverFeeLbl));
            receipt.add(buildReceiptRow("SEBI Charges (not simulated)", sebiLbl));
            receipt.add(buildReceiptRow("Stamp Duty (not simulated)", stampLbl));
            receipt.add(buildReceiptRow("GST (not simulated)", gstLbl));
            receipt.add(Box.createVerticalStrut(4));

            JPanel line = new JPanel();
            line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
            line.setBackground(Theme.BORDER_CUSTOM);
            receipt.add(line);
            receipt.add(Box.createVerticalStrut(4));

            receipt.add(buildReceiptRow("Net Settlement", netSettledLbl));
            receipt.add(buildReceiptRow("Realized P&L on Exit", realizedPnlLbl));
            center.add(receipt);

            add(center, BorderLayout.CENTER);

            // Bottom Actions: Download Receipt & Re-Order
            JPanel btm = new JPanel(new GridLayout(2, 1, 0, 4));
            btm.setOpaque(false);

            JButton btnDlReceipt = new JButton("Download Trade Receipt (PDF)");
            btnDlReceipt.setBackground(Theme.ACCENT);
            btnDlReceipt.setForeground(Color.WHITE);
            btnDlReceipt.setFont(Theme.MICRO);
            btnDlReceipt.addActionListener(e -> onDownloadTradeReceipt());
            btm.add(btnDlReceipt);

            JButton btnReorder = new JButton("Re-Order in Terminal");
            btnReorder.setBackground(Theme.SURFACE_SUB);
            btnReorder.setForeground(Theme.TXT_PRIMARY);
            btnReorder.setFont(Theme.MICRO);
            btnReorder.addActionListener(e -> {
                if (selectedRow != null && navHandler != null) {
                    navHandler.accept("Trade:" + selectedRow.symbol());
                }
            });
            btm.add(btnReorder);

            add(btm, BorderLayout.SOUTH);
        }

        private JPanel buildTelemetryStep(String label, String time, Color dotColor) {
            JPanel p = new JPanel(new BorderLayout());
            p.setOpaque(false);
            JLabel l = new JLabel("● " + label);
            l.setFont(Theme.TINY);
            l.setForeground(dotColor);
            JLabel t = new JLabel(time);
            t.setFont(new Font("JetBrains Mono", Font.PLAIN, 9));
            t.setForeground(Theme.TXT_MUTED);
            p.add(l, BorderLayout.WEST);
            p.add(t, BorderLayout.EAST);
            return p;
        }

        private JPanel buildReceiptRow(String label, JLabel valLbl) {
            JPanel p = new JPanel(new BorderLayout());
            p.setOpaque(false);
            JLabel l = new JLabel(label);
            l.setFont(Theme.TINY);
            l.setForeground(Theme.TXT_MUTED);
            valLbl.setFont(new Font("JetBrains Mono", Font.BOLD, 10));
            valLbl.setForeground(Theme.TXT_PRIMARY);
            p.add(l, BorderLayout.WEST);
            p.add(valLbl, BorderLayout.EAST);
            return p;
        }

        public void updateOrder(AuditRowData r) {
            this.selectedRow = r;
            orderTitle.setText(r.orderId());
            statusBadge.setText("SIMULATED " + r.status());
            timestampLbl.setText(r.timestamp().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss")));

            assetIcon.setText(r.symbol().substring(0, 1));
            assetName.setText(r.symbol());
            assetSub.setText(r.name() + " · " + r.exchange());
            sideTag.setText(r.side() + " / CNC");
            sideTag.setForeground("BUY".equalsIgnoreCase(r.side()) ? Theme.GAIN : Theme.LOSS);

            boolean indian = isIndianAsset(r.symbol());
            String cur = indian ? "₹" : "$";
            qtyPriceLbl.setText(String.format(Locale.US, "%d Sh @ %s%,.2f", r.quantity(), cur, r.price()));

            // The simulator charges one fee (FeeCalculator); Indian statutory levies are not simulated.
            double gross = r.grossValue();
            double fee = r.fee();
            double net = "BUY".equalsIgnoreCase(r.side()) ? gross + fee : gross - fee;   // paid vs received

            grossValLbl.setText(String.format(Locale.US, "%s%,.2f", cur, gross));
            brokerageLbl.setText(String.format(Locale.US, "%s%,.2f", cur, fee));
            String zero = cur + "0.00";
            sttTaxLbl.setText(zero);
            turnoverFeeLbl.setText(zero);
            sebiLbl.setText(zero);
            stampLbl.setText(zero);
            gstLbl.setText(zero);
            netSettledLbl.setText(String.format(Locale.US, "%s%,.2f", cur, net));

            if (r.realizedPnl() != 0.0) {
                String sign = r.realizedPnl() >= 0 ? "+" : "-";
                realizedPnlLbl.setText(String.format(Locale.US, "%s%s%,.2f", sign, cur, Math.abs(r.realizedPnl())));
                realizedPnlLbl.setForeground(r.realizedPnl() >= 0 ? Theme.GAIN : Theme.LOSS);
            } else {
                realizedPnlLbl.setText("-");
                realizedPnlLbl.setForeground(Theme.TXT_MUTED);
            }
        }

        private void onDownloadTradeReceipt() {
            if (selectedRow == null) return;
            JFileChooser fc = new JFileChooser();
            fc.setSelectedFile(new File("trade_receipt_" + selectedRow.orderId().replace("#", "") + ".txt"));
            if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                try (FileWriter fw = new FileWriter(fc.getSelectedFile())) {
                    fw.write("PAPERTRADER 3D - TRADE EXECUTION RECEIPT\n");
                    fw.write("ORDER ID: " + selectedRow.orderId() + "\n");
                    fw.write("SYMBOL: " + selectedRow.symbol() + "\n");
                    fw.write("SIDE: " + selectedRow.side() + "\n");
                    fw.write("QUANTITY: " + selectedRow.quantity() + "\n");
                    fw.write("EXECUTION PRICE: " + selectedRow.price() + "\n");
                    fw.write("GROSS VALUE: " + selectedRow.grossValue() + "\n");
                    fw.write("FEE CHARGED: " + selectedRow.fee() + "\n");
                    fw.write("REALIZED P&L: " + selectedRow.realizedPnl() + "\n");
                    fw.write("STATUS: " + selectedRow.status() + "\n");
                    fw.write("DATE: " + Formatter.dateTime(selectedRow.timestamp()) + "\n");
                    JOptionPane.showMessageDialog(this, "Trade receipt saved successfully!", "Saved", JOptionPane.INFORMATION_MESSAGE);
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }

    // =========================================================================
    // SECTION 2 KPI CARDS IMPLEMENTATIONS (all values come from TradeStats = the real trades)
    // =========================================================================

    /** Rupee text for a USD amount, e.g. "+₹1,234". */
    private static String rupees(double usd) {
        double v = usd * USD_TO_INR;
        return String.format(Locale.US, "%s₹%,.0f", v < 0 ? "-" : "+", Math.abs(v));
    }

    /** Shared look of the six KPI cards: title + badge on top, big value, small line below. */
    private static JPanel kpiFrame(JPanel card, String title, JLabel badge, JLabel value, JLabel sub) {
        card.setLayout(new BorderLayout(0, 4));
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
        badge.setFont(Theme.MICRO);
        top.add(t, BorderLayout.WEST);
        top.add(badge, BorderLayout.EAST);
        card.add(top, BorderLayout.NORTH);

        value.setFont(Theme.BIG_NUMBER);
        card.add(value, BorderLayout.CENTER);

        sub.setFont(Theme.TINY);
        sub.setForeground(Theme.TXT_MUTED);
        card.add(sub, BorderLayout.SOUTH);
        return card;
    }

    private static class TotalTradesKpiCard extends JPanel {
        private final JLabel badge = new JLabel("+0 Today");
        private final JLabel valLbl = new JLabel("0");
        private final JLabel subLbl = new JLabel("Pending orders: 0");

        public TotalTradesKpiCard() {
            kpiFrame(this, "TOTAL TRADES", badge, valLbl, subLbl);
            badge.setForeground(Theme.GAIN);
            valLbl.setForeground(Theme.TXT_PRIMARY);
        }

        public void updateData(TradeStats st, int pending) {
            valLbl.setText(String.valueOf(st.trades));
            badge.setText("+" + st.todayTrades + " Today");
            subLbl.setText("Pending orders: " + pending);
        }
    }

    private static class SideKpiCard extends JPanel {
        private final JLabel badge = new JLabel("0.0% Vol");
        private final JLabel valLbl = new JLabel("0");
        private final JLabel grossLbl = new JLabel("Gross: ₹0.00");
        private final boolean isBuy;

        public SideKpiCard(String title, boolean buy) {
            this.isBuy = buy;
            kpiFrame(this, title.toUpperCase(), badge, valLbl, grossLbl);
            badge.setForeground(buy ? Theme.GAIN : Theme.LOSS);
            valLbl.setForeground(buy ? Theme.GAIN : Theme.LOSS);
        }

        public void updateData(TradeStats st) {
            double mine = isBuy ? st.buyGross : st.sellGross;
            double total = st.buyGross + st.sellGross;
            valLbl.setText(String.valueOf(isBuy ? st.buys : st.sells));
            badge.setText(String.format(Locale.US, "%.1f%% Vol", total > 0 ? mine / total * 100 : 0));
            grossLbl.setText(String.format(Locale.US, "Gross %s: ₹%,.1fL", isBuy ? "Buy" : "Sell", (mine * USD_TO_INR) / 100000.0));
        }
    }

    private static class RealizedPnlKpiCard extends JPanel {
        private final JLabel badge = new JLabel("0 Closed");
        private final JLabel valLbl = new JLabel("+₹0");
        private final JLabel subLbl = new JLabel("Unrealized: N/A");

        public RealizedPnlKpiCard() {
            kpiFrame(this, "REALIZED P&L", badge, valLbl, subLbl);
            badge.setForeground(Theme.ACCENT);
        }

        public void updateData(TradeStats st, double unrealizedUsd) {
            valLbl.setText(rupees(st.realized));
            valLbl.setForeground(st.realized >= 0 ? Theme.GAIN : Theme.LOSS);
            badge.setText(st.sells + " Closed");
            subLbl.setText("Unrealized: " + (Double.isNaN(unrealizedUsd) ? "N/A" : rupees(unrealizedUsd)));
        }
    }

    private static class WinRateKpiCard extends JPanel {
        private final JLabel badge = new JLabel("PF: n/a");
        private final JLabel valLbl = new JLabel("-");
        private final JLabel subLbl = new JLabel("Win / Loss: 0 W / 0 L");

        public WinRateKpiCard() {
            kpiFrame(this, "WIN RATE", badge, valLbl, subLbl);
            badge.setForeground(Theme.ACCENT);
            valLbl.setForeground(Theme.TXT_PRIMARY);
        }

        public void updateData(TradeStats st) {
            double rate = st.winRate();
            valLbl.setText(Double.isNaN(rate) ? "-" : String.format(Locale.US, "%.1f%%", rate));
            subLbl.setText(String.format("Win / Loss: %d W / %d L", st.wins, st.losses));
            badge.setText("PF: " + PerformanceAnalyzer.format(st.profitFactor(), ""));
        }
    }

    private static class FeesKpiCard extends JPanel {
        private final JLabel badge = new JLabel("0.000% Vol");
        private final JLabel valLbl = new JLabel("₹0.00");
        private final JLabel subLbl = new JLabel("Fee model: -");

        public FeesKpiCard() {
            kpiFrame(this, "TRADING FEES", badge, valLbl, subLbl);
            badge.setForeground(Theme.TXT_MUTED);
            valLbl.setForeground(Theme.TXT_PRIMARY);
            subLbl.setForeground(Theme.GAIN);
        }

        public void updateData(TradeStats st, String feeModel) {
            double volume = st.buyGross + st.sellGross;
            valLbl.setText(String.format(Locale.US, "₹%,.2f", st.fees * USD_TO_INR));
            badge.setText(String.format(Locale.US, "%.3f%% Vol", volume > 0 ? st.fees / volume * 100 : 0));
            subLbl.setText("Fee model: " + feeModel);
        }
    }

    // =========================================================================
    // SECTION 3 ANALYTICS SUITE: HISTOGRAM, GROWTH, DONUT
    // =========================================================================

    private static JPanel cardHeader(String title, JLabel sub) {
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JLabel t = new JLabel(title);
        t.setFont(new Font("Inter", Font.BOLD, 13));
        t.setForeground(Theme.TXT_PRIMARY);
        sub.setFont(Theme.MICRO);
        sub.setForeground(Theme.TXT_MUTED);
        top.add(t, BorderLayout.NORTH);
        top.add(sub, BorderLayout.SOUTH);
        return top;
    }

    private static void styleAnalyticsCard(JPanel card) {
        card.setBackground(Theme.CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 8, 1),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)
        ));
    }

    private static JLabel emptyNote(String text) {
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setFont(Theme.MICRO);
        l.setForeground(Theme.TXT_MUTED);
        return l;
    }

    /** Realized P&L of the last 14 days that had a sell, one bar per day. */
    private static class DailyHistogramCard extends JPanel {
        private final JLabel sub = new JLabel("No closed trades yet");
        private final JLabel maxWin = new JLabel("Best Trade: -");
        private final JLabel maxLoss = new JLabel("Worst Trade: -");
        private List<Double> bars = List.of();

        public DailyHistogramCard() {
            setLayout(new BorderLayout(0, 8));
            styleAnalyticsCard(this);
            add(cardHeader("Daily Realized P&L", sub), BorderLayout.NORTH);

            JPanel canvas = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int w = getWidth();
                    int h = getHeight();
                    int baselineY = h / 2;
                    g2.setColor(Theme.BORDER_CUSTOM);
                    g2.drawLine(0, baselineY, w, baselineY);
                    if (bars.isEmpty()) {
                        g2.setColor(Theme.TXT_MUTED);
                        g2.setFont(Theme.MICRO);
                        g2.drawString("Sell a position to see daily results", 10, baselineY - 8);
                        g2.dispose();
                        return;
                    }
                    double maxAbs = 1e-9;
                    for (double v : bars) {
                        maxAbs = Math.max(maxAbs, Math.abs(v));
                    }
                    int slot = Math.max(6, (w - 10) / 14);
                    int barW = Math.max(4, slot - 5);
                    for (int i = 0; i < bars.size(); i++) {
                        double val = bars.get(i);
                        int x = 10 + i * slot;
                        int barH = Math.max(1, (int) (Math.abs(val) / maxAbs * (h * 0.45)));
                        g2.setColor(val >= 0 ? Theme.GAIN : Theme.LOSS);
                        if (val >= 0) {
                            g2.fillRoundRect(x, baselineY - barH, barW, barH, 4, 4);
                        } else {
                            g2.fillRoundRect(x, baselineY, barW, barH, 4, 4);
                        }
                    }
                    g2.dispose();
                }
            };
            canvas.setPreferredSize(new Dimension(200, 110));
            canvas.setOpaque(false);
            add(canvas, BorderLayout.CENTER);

            JPanel ftr = new JPanel(new BorderLayout());
            ftr.setOpaque(false);
            maxWin.setFont(Theme.MICRO);
            maxWin.setForeground(Theme.GAIN);
            maxLoss.setFont(Theme.MICRO);
            maxLoss.setForeground(Theme.LOSS);
            ftr.add(maxWin, BorderLayout.WEST);
            ftr.add(maxLoss, BorderLayout.EAST);
            add(ftr, BorderLayout.SOUTH);
        }

        public void updateData(TradeStats st) {
            List<Double> days = new ArrayList<>(st.dailyPnl.values());
            bars = days.subList(Math.max(0, days.size() - 14), days.size());
            sub.setText(bars.isEmpty() ? "No closed trades yet" : "Last " + bars.size() + " trading days with closed trades");
            maxWin.setText("Best Trade: " + (st.wins == 0 ? "-" : rupees(st.bestTrade)));
            maxLoss.setText("Worst Trade: " + (st.losses == 0 ? "-" : rupees(st.worstTrade)));
            repaint();
        }
    }

    /** Running total of realized P&L after every sell, against the break-even line. */
    private static class CumulativeGrowthCard extends JPanel {
        private final JLabel sub = new JLabel("Running total of realized P&L");
        private final JLabel leg1 = new JLabel("● Realized P&L: +₹0");
        private List<Double> curve = List.of();

        public CumulativeGrowthCard() {
            setLayout(new BorderLayout(0, 8));
            styleAnalyticsCard(this);
            add(cardHeader("Cumulative Growth Curve", sub), BorderLayout.NORTH);

            JPanel canvas = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int w = getWidth();
                    int h = getHeight();
                    List<Double> pts = new ArrayList<>();
                    pts.add(0.0);               // every account starts at break-even
                    pts.addAll(curve);
                    double min = Math.min(0, Collections.min(pts));
                    double max = Math.max(0, Collections.max(pts));
                    double range = max - min < 1e-9 ? 1 : max - min;
                    int zeroY = (int) (h - 4 - (0 - min) / range * (h - 8));

                    g2.setColor(Theme.TXT_MUTED);
                    g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{4f, 4f}, 0f));
                    g2.drawLine(0, zeroY, w, zeroY);
                    if (curve.isEmpty()) {
                        g2.dispose();
                        return;
                    }
                    Path2D p = new Path2D.Double();
                    double lastX = 0;
                    double lastY = zeroY;
                    for (int i = 0; i < pts.size(); i++) {
                        lastX = (double) i / (pts.size() - 1) * (w - 4);
                        lastY = h - 4 - (pts.get(i) - min) / range * (h - 8);
                        if (i == 0) p.moveTo(lastX, lastY);
                        else p.lineTo(lastX, lastY);
                    }
                    Color c = pts.get(pts.size() - 1) >= 0 ? Theme.GAIN : Theme.LOSS;
                    Path2D area = new Path2D.Double(p);
                    area.lineTo(lastX, zeroY);
                    area.lineTo(0, zeroY);
                    area.closePath();
                    g2.setPaint(new GradientPaint(0, 0, new Color(c.getRed(), c.getGreen(), c.getBlue(), 60),
                            0, h, new Color(c.getRed(), c.getGreen(), c.getBlue(), 0)));
                    g2.fill(area);
                    g2.setColor(c);
                    g2.setStroke(new BasicStroke(2.0f));
                    g2.draw(p);
                    g2.fillOval((int) lastX - 3, (int) lastY - 3, 6, 6);
                    g2.dispose();
                }
            };
            canvas.setPreferredSize(new Dimension(200, 110));
            canvas.setOpaque(false);
            add(canvas, BorderLayout.CENTER);

            JPanel ftr = new JPanel(new BorderLayout());
            ftr.setOpaque(false);
            leg1.setFont(Theme.MICRO);
            leg1.setForeground(Theme.GAIN);
            JLabel leg2 = new JLabel("--- Break-even");
            leg2.setFont(Theme.MICRO);
            leg2.setForeground(Theme.TXT_MUTED);
            ftr.add(leg1, BorderLayout.WEST);
            ftr.add(leg2, BorderLayout.EAST);
            add(ftr, BorderLayout.SOUTH);
        }

        public void updateData(TradeStats st) {
            curve = new ArrayList<>(st.cumulativePnl);
            sub.setText(curve.isEmpty() ? "No closed trades yet" : "Running total after each of " + curve.size() + " sells");
            leg1.setText("● Realized P&L: " + rupees(st.realized));
            leg1.setForeground(st.realized >= 0 ? Theme.GAIN : Theme.LOSS);
            repaint();
        }
    }

    /** Wins / losses / break-even sells as a donut. */
    private static class WinLossDonutCard extends JPanel {
        private final JLabel winsLbl = new JLabel("0 (0.0%)");
        private final JLabel lossLbl = new JLabel("0 (0.0%)");
        private final JLabel evenLbl = new JLabel("0 (0.0%)");
        private final JLabel avg = new JLabel("Avg Win/Loss: - / -");
        private int wins;
        private int losses;
        private int even;

        public WinLossDonutCard() {
            setLayout(new BorderLayout(0, 6));
            styleAnalyticsCard(this);
            add(cardHeader("Execution Ratio & Spread", new JLabel("Outcome of every closed (sell) trade")), BorderLayout.NORTH);

            JPanel mid = new JPanel(new BorderLayout(8, 0));
            mid.setOpaque(false);
            JPanel donut = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int size = Math.min(getWidth(), getHeight()) - 10;
                    int x = (getWidth() - size) / 2;
                    int y = (getHeight() - size) / 2;
                    int stroke = 10;
                    g2.setStroke(new BasicStroke(stroke));
                    double ax = x + stroke / 2.0;
                    double ay = y + stroke / 2.0;
                    double d = size - stroke;
                    int total = wins + losses + even;
                    if (total == 0) {
                        g2.setColor(Theme.BORDER_CUSTOM);
                        g2.draw(new Arc2D.Double(ax, ay, d, d, 0, 360, Arc2D.OPEN));
                    } else {
                        double start = 90;
                        int[] counts = {wins, losses, even};
                        Color[] colors = {Theme.GAIN, Theme.LOSS, Theme.TXT_MUTED};
                        for (int i = 0; i < 3; i++) {
                            double sweep = -360.0 * counts[i] / total;
                            g2.setColor(colors[i]);
                            g2.draw(new Arc2D.Double(ax, ay, d, d, start, sweep, Arc2D.OPEN));
                            start += sweep;
                        }
                    }
                    g2.setColor(Theme.TXT_PRIMARY);
                    g2.setFont(new Font("JetBrains Mono", Font.BOLD, 12));
                    String wStr = wins + losses == 0 ? "-" : String.format(Locale.US, "%.1f%%", wins * 100.0 / (wins + losses));
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(wStr, getWidth() / 2 - fm.stringWidth(wStr) / 2, getHeight() / 2 + 4);
                    g2.dispose();
                }
            };
            donut.setPreferredSize(new Dimension(80, 80));
            donut.setOpaque(false);
            mid.add(donut, BorderLayout.WEST);

            JPanel statsPanel = new JPanel(new GridLayout(3, 1, 0, 2));
            statsPanel.setOpaque(false);
            statsPanel.add(buildOutcomeRow("Wins", winsLbl, Theme.GAIN));
            statsPanel.add(buildOutcomeRow("Losses", lossLbl, Theme.LOSS));
            statsPanel.add(buildOutcomeRow("Even", evenLbl, Theme.TXT_MUTED));
            mid.add(statsPanel, BorderLayout.CENTER);
            add(mid, BorderLayout.CENTER);

            JPanel ftr = new JPanel(new BorderLayout());
            ftr.setOpaque(false);
            avg.setFont(Theme.MICRO);
            avg.setForeground(Theme.TXT_SECONDARY);
            ftr.add(avg, BorderLayout.WEST);
            add(ftr, BorderLayout.SOUTH);
        }

        private JPanel buildOutcomeRow(String name, JLabel v, Color c) {
            JPanel p = new JPanel(new BorderLayout());
            p.setOpaque(false);
            JLabel n = new JLabel("● " + name);
            n.setFont(Theme.TINY);
            n.setForeground(c);
            v.setFont(new Font("JetBrains Mono", Font.BOLD, 10));
            v.setForeground(c);
            p.add(n, BorderLayout.WEST);
            p.add(v, BorderLayout.EAST);
            return p;
        }

        public void updateData(TradeStats st) {
            wins = st.wins;
            losses = st.losses;
            even = st.even;
            int total = Math.max(1, wins + losses + even);
            winsLbl.setText(String.format(Locale.US, "%d (%.1f%%)", wins, wins * 100.0 / total));
            lossLbl.setText(String.format(Locale.US, "%d (%.1f%%)", losses, losses * 100.0 / total));
            evenLbl.setText(String.format(Locale.US, "%d (%.1f%%)", even, even * 100.0 / total));
            avg.setText("Avg Win/Loss: " + (st.wins == 0 ? "-" : rupees(st.avgWin())) + " / "
                    + (st.losses == 0 ? "-" : rupees(st.avgLoss())));
            repaint();
        }
    }

    // =========================================================================
    // SECTION 4 MONTHLY P&L HEATMAP CALENDAR & QUANT GRID
    // =========================================================================

    /** The current month, Monday-Friday, coloured by the realized P&L of each day. */
    private static class MonthlyHeatmapCard extends JPanel {
        private final JLabel title = new JLabel();
        private final JLabel perf = new JLabel();
        private final JLabel greenDays = new JLabel();
        private final JLabel redDays = new JLabel();
        private final JPanel cal = new JPanel(new GridLayout(0, 5, 4, 4));
        private String shownKey = "";

        public MonthlyHeatmapCard() {
            setLayout(new BorderLayout(0, 8));
            setOpaque(false);

            JPanel top = new JPanel(new BorderLayout());
            top.setOpaque(false);
            title.setFont(new Font("Inter", Font.BOLD, 13));
            title.setForeground(Theme.TXT_PRIMARY);
            top.add(title, BorderLayout.WEST);

            JPanel rightTags = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            rightTags.setOpaque(false);
            perf.setFont(Theme.MICRO);
            greenDays.setFont(Theme.MICRO);
            greenDays.setForeground(Theme.GAIN);
            redDays.setFont(Theme.MICRO);
            redDays.setForeground(Theme.LOSS);
            rightTags.add(perf);
            rightTags.add(greenDays);
            rightTags.add(redDays);
            top.add(rightTags, BorderLayout.EAST);
            add(top, BorderLayout.NORTH);

            cal.setOpaque(false);
            add(cal, BorderLayout.CENTER);
            updateData(TradeStats.of(List.of()));
        }

        public void updateData(TradeStats st) {
            java.time.YearMonth month = java.time.YearMonth.now();
            java.util.Map<java.time.LocalDate, Double> days = st.dailyPnl.subMap(month.atDay(1), true, month.atEndOfMonth(), true);
            String key = month + days.toString();
            if (key.equals(shownKey)) {
                return;    // nothing changed: do not rebuild the grid every second
            }
            shownKey = key;

            double total = days.values().stream().mapToDouble(Double::doubleValue).sum();
            long green = days.values().stream().filter(v -> v > 0).count();
            long red = days.values().stream().filter(v -> v < 0).count();
            title.setText(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")) + " P&L Matrix · Realized by day");
            perf.setText("Monthly Performance: " + rupees(total));
            perf.setForeground(total >= 0 ? Theme.GAIN : Theme.LOSS);
            greenDays.setText(green + " Green Days");
            redDays.setText(red + " Red Days");

            cal.removeAll();
            for (String d : new String[]{"Mon", "Tue", "Wed", "Thu", "Fri"}) {
                JLabel l = new JLabel(d, SwingConstants.CENTER);
                l.setFont(Theme.TINY);
                l.setForeground(Theme.TXT_MUTED);
                l.setBackground(Theme.SURFACE_SUB);
                l.setOpaque(true);
                cal.add(l);
            }
            // blank cells before the first weekday so the dates sit under the right day name
            java.time.LocalDate first = month.atDay(1);
            int lead = first.getDayOfWeek().getValue() - 1;   // Monday = 0
            if (lead >= 5) {
                lead = 0;                                       // month starts on a weekend
            }
            for (int i = 0; i < lead; i++) {
                cal.add(blankCell());
            }
            for (int d = 1; d <= month.lengthOfMonth(); d++) {
                java.time.LocalDate date = month.atDay(d);
                if (date.getDayOfWeek().getValue() >= 6) {
                    continue;   // weekends are not shown
                }
                Double pnl = days.get(date);
                cal.add(buildCalCell(date.format(DateTimeFormatter.ofPattern("dd MMM")), pnl));
            }
            cal.revalidate();
            cal.repaint();
        }

        private JPanel blankCell() {
            JPanel p = new JPanel();
            p.setOpaque(false);
            return p;
        }

        private JPanel buildCalCell(String date, Double pnl) {
            boolean has = pnl != null && pnl != 0;
            boolean gain = has && pnl > 0;
            JPanel p = new JPanel(new BorderLayout());
            p.setBackground(!has ? Theme.SURFACE_SUB : gain ? new Color(34, 197, 94, 25) : new Color(239, 68, 68, 25));
            p.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(!has ? Theme.BORDER_SUBTLE
                            : gain ? new Color(34, 197, 94, 60) : new Color(239, 68, 68, 60), 1),
                    BorderFactory.createEmptyBorder(2, 4, 2, 4)
            ));
            JLabel d = new JLabel(date);
            d.setFont(Theme.TINY);
            d.setForeground(Theme.TXT_MUTED);
            String text = "-";
            if (has) {
                double inr = pnl * USD_TO_INR;
                text = Math.abs(inr) >= 1000
                        ? String.format(Locale.US, "%s₹%.1fk", inr < 0 ? "-" : "+", Math.abs(inr) / 1000)
                        : String.format(Locale.US, "%s₹%.0f", inr < 0 ? "-" : "+", Math.abs(inr));
            }
            JLabel v = new JLabel(text, SwingConstants.RIGHT);
            v.setFont(new Font("JetBrains Mono", Font.BOLD, 9));
            v.setForeground(!has ? Theme.TXT_MUTED : gain ? Theme.GAIN : Theme.LOSS);
            p.add(d, BorderLayout.WEST);
            p.add(v, BorderLayout.EAST);
            return p;
        }
    }

    /** Eight quant tiles, every one calculated from the real trades. */
    private static class QuantMetricsGrid extends JPanel {
        private final JLabel profitFactor = new JLabel("n/a");
        private final JLabel avgHolding = new JLabel("n/a");
        private final JLabel riskReward = new JLabel("n/a");
        private final JLabel winStreak = new JLabel("0 Trades");
        private final JLabel lossStreak = new JLabel("0 Trades");
        private final JLabel sharpe = new JLabel("n/a");
        private final JLabel bestAsset = new JLabel("-");
        private final JLabel worstAsset = new JLabel("-");

        public QuantMetricsGrid() {
            setLayout(new GridLayout(1, 8, 6, 0));
            setOpaque(false);
            add(buildQuantTile("Profit Factor", profitFactor, Theme.GAIN));
            add(buildQuantTile("Avg Holding", avgHolding, Theme.TXT_PRIMARY));
            add(buildQuantTile("Risk / Reward", riskReward, Theme.ACCENT));
            add(buildQuantTile("Max Win Streak", winStreak, Theme.GAIN));
            add(buildQuantTile("Max Loss Streak", lossStreak, Theme.LOSS));
            add(buildQuantTile("Sharpe Ratio", sharpe, Theme.TXT_PRIMARY));
            add(buildQuantTile("Best Asset", bestAsset, Theme.GAIN));
            add(buildQuantTile("Worst Asset", worstAsset, Theme.LOSS));
        }

        private JPanel buildQuantTile(String title, JLabel v, Color c) {
            JPanel p = new JPanel();
            p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
            p.setBackground(Theme.SURFACE_SUB);
            p.setBorder(BorderFactory.createCompoundBorder(
                    new Theme.RoundedBorder(Theme.BORDER_SUBTLE, 6, 1),
                    BorderFactory.createEmptyBorder(4, 6, 4, 6)
            ));
            JLabel t = new JLabel(title.toUpperCase());
            t.setFont(new Font("Inter", Font.BOLD, 8));
            t.setForeground(Theme.TXT_MUTED);
            v.setFont(new Font("JetBrains Mono", Font.BOLD, 10));
            v.setForeground(c);
            p.add(t);
            p.add(Box.createVerticalStrut(2));
            p.add(v);
            return p;
        }

        public void updateData(TradeStats st, PerformanceReport report) {
            profitFactor.setText(PerformanceAnalyzer.format(st.profitFactor(), "x"));
            double days = st.avgHoldingDays;
            avgHolding.setText(Double.isNaN(days) ? "n/a"
                    : days < 1 ? String.format(Locale.US, "%.1f Hours", days * 24) : String.format(Locale.US, "%.1f Days", days));
            double rr = st.rewardToRisk();
            riskReward.setText(Double.isNaN(rr) ? "n/a" : String.format(Locale.US, "1 : %.2f", rr));
            winStreak.setText(st.maxWinStreak + " Trades");
            lossStreak.setText(st.maxLossStreak + " Trades");
            sharpe.setText(report == null ? "n/a" : PerformanceAnalyzer.format(report.sharpeRatio(), ""));

            java.util.Map.Entry<String, Double> best = null;
            java.util.Map.Entry<String, Double> worst = null;
            for (java.util.Map.Entry<String, Double> e : st.pnlBySymbol.entrySet()) {
                if (best == null || e.getValue() > best.getValue()) best = e;
                if (worst == null || e.getValue() < worst.getValue()) worst = e;
            }
            bestAsset.setText(best == null ? "-" : best.getKey() + " (" + rupees(best.getValue()) + ")");
            worstAsset.setText(worst == null ? "-" : worst.getKey() + " (" + rupees(worst.getValue()) + ")");
        }
    }
}
