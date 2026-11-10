package com.papertrader.ui;

import com.papertrader.service.ChartService;
import com.papertrader.exception.SimulatorException;
import com.papertrader.model.Asset;
import com.papertrader.model.Order;
import com.papertrader.model.OrderSide;
import com.papertrader.model.OrderStatus;
import com.papertrader.model.StopLossOrder;
import com.papertrader.model.TakeProfitOrder;
import com.papertrader.execution.OrderBookSimulator;
import com.papertrader.model.Transaction;
import com.papertrader.model.User;
import com.papertrader.service.AuthService;
import com.papertrader.service.MarketService;
import com.papertrader.service.PortfolioService;
import com.papertrader.service.TradingService;
import com.papertrader.ui.components.CandlestickChartPanel;
import com.papertrader.ui.components.MoneyRenderer;
import com.papertrader.ui.components.TickerPanel;
import com.papertrader.util.Formatter;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
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
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Trade Terminal (Production Fintech Console).
 * Reconstructed according to the HTML specification:
 * - Header with breadcrumb, live simulator badge, search bar (⌘K), virtual cash, and ticker marquee
 * - 4 Top Financial KPI Cards: Available Cash, Portfolio Value with sparkline, Buying Power (2x Margin), Today's Realized P&L
 * - Market Switcher & Asset Strip (India NSE/BSE, USA NYSE, Crypto 24/7; quick shortcuts; active asset telemetry)
 * - Main 65% / 35% Workspace:
 *     - Left (65%): Interactive Candlestick Chart Viewport (Candles/Line/Area, SMA20, EMA50, Volume, RSI14)
 *                   + Level 2 Market Depth (5-Tick Order Book Ladder with depth fill bars & ratio bar)
 *     - Right (35%): Order Execution Ticket (BUY/SELL toggle, CNC/MIS, Market/Limit/SL-M, Stepper, Lot %, R:R bracket,
 *                    live cost breakdown card, execution button, sizing gauge & margin buffer)
 * - Bottom Tabbed Section: Active Open Orders vs Order History / Executions with Cancel controls
 * - Interactive Order Confirmation Modal Dialog
 */
public class TradePanel extends BasePanel {

    private final AuthService authService;
    private final MarketService marketService;
    private final TradingService tradingService;
    private final PortfolioService portfolioService;

    // Current trade state
    private Asset currentAsset;
    private OrderSide currentSide = OrderSide.BUY;
    private String currentProduct = "CNC"; // CNC (Delivery) or MIS (Intraday)
    private String currentOrderType = "Market"; // Market, Limit, SL-M, TP
    private int quantity = 50;
    private double limitPrice = 2945.50;
    private double targetPrice = 3050.00;
    private double stopLossPrice = 2880.00;
    private String currentRegion = "ALL"; // ALL, NSE, NYSE, CRYPTO

    // Header & Ticker
    private final TickerPanel tickerPanel;
    private final JLabel headerCashVal = new JLabel("$0.00");

    // 4 KPI Summary Cards
    private final JLabel kpiCashVal = new JLabel("$0.00");
    private final JLabel kpiCashEq = new JLabel("$0.00 USD eq.");
    private final JLabel kpiPortVal = new JLabel("$0.00");
    private final JLabel kpiPortPct = new JLabel("+0.00%");
    private final JLabel kpiPowerVal = new JLabel("$0.00");
    private final JLabel kpiPnlVal = new JLabel("+$0.00");
    private final JLabel kpiPnlPct = new JLabel("▲ +0.00%");
    private final JLabel kpiFilledCount = new JLabel("0 Filled Positions");

    // Active Asset Telemetry Bar
    private final JLabel assetAvatar = new JLabel("R", SwingConstants.CENTER);
    private final JLabel assetSymbolLbl = new JLabel("RELIANCE");
    private final JLabel assetExchangeLbl = new JLabel("NSE");
    private final JLabel assetNameLbl = new JLabel("Reliance Industries Ltd.");
    private final JLabel assetCmpLbl = new JLabel("₹2,945.50");
    private final JLabel assetDeltaLbl = new JLabel("+₹68.25 (+2.37%)");
    private final JLabel assetHighLbl = new JLabel("₹2,960.00");
    private final JLabel assetLowLbl = new JLabel("₹2,890.00");
    private final JLabel assetVolLbl = new JLabel("6.42M");
    private final JLabel assetVwapLbl = new JLabel("₹2,932.15");
    private final JLabel assetSessionLbl = new JLabel("LIVE · 09:15-15:30 IST");

    // Quick Asset Buttons Container
    private final JPanel quickAssetsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));

    // Left Column Components
    private final CandlestickChartPanel chartPanel = new CandlestickChartPanel();
    private final Level2DepthPanel depthPanel = new Level2DepthPanel();

    // Right Column Execution Ticket Components
    private final JButton btnBuySide = new JButton("▲ BUY / LONG");
    private final JButton btnSellSide = new JButton("▼ SELL / SHORT");
    private final JButton btnProductCnc = new JButton("Delivery (CNC)");
    private final JButton btnProductMis = new JButton("Intraday (MIS 5x)");
    private final JButton btnTypeMarket = new JButton("Market");
    private final JButton btnTypeLimit = new JButton("Limit");
    private final JButton btnTypeSlm = new JButton("SL-M");
    private final JButton btnTypeTp = new JButton("TP");

    private final JTextField txtQty = new JTextField("50");
    private final JTextField txtPrice = new JTextField("2,945.50");
    private final JTextField txtTarget = new JTextField("3,050.00");
    private final JTextField txtStopLoss = new JTextField("2,880.00");
    private final JLabel lblRrRatio = new JLabel("R:R Ratio 1 : 1.6");

    // Order Calculation Breakdown
    private final JLabel calcGrossVal = new JLabel("₹0.00");
    private final JLabel calcBrokerageVal = new JLabel("₹0.00 (Zero Fee)");
    private final JLabel calcTaxesVal = new JLabel("₹0.00");
    private final JLabel calcNetRequiredVal = new JLabel("₹0.00");
    private final JLabel calcRemainingCashVal = new JLabel("₹0.00");
    private final JButton btnExecuteOrder = new JButton("EXECUTE PAPER BUY (50 SH)");

    // Position Sizing Progress Bars
    private final JProgressBarCustom barExposure = new JProgressBarCustom(Theme.ACCENT);
    private final JLabel lblExposureText = new JLabel("0.00% of Total");
    private final JProgressBarCustom barMargin = new JProgressBarCustom(Theme.GAIN);
    private final JLabel lblMarginText = new JLabel("100.0% Available");

    // Bottom Tabbed Tables
    private final CardLayout bottomCardLayout = new CardLayout();
    private final JPanel bottomCardsContainer = new JPanel(bottomCardLayout);
    private final JButton tabBtnOpen = new JButton("Active Open Orders (0)");
    private final JButton tabBtnExec = new JButton("Order History / Executions (0)");

    private final OpenOrdersTableModel openOrdersModel = new OpenOrdersTableModel();
    private final JTable openOrdersTable = new JTable(openOrdersModel);
    private final ExecHistoryTableModel execHistoryModel = new ExecHistoryTableModel();
    private final JTable execHistoryTable = new JTable(execHistoryModel);

    public TradePanel(AuthService authService, MarketService marketService, TradingService tradingService) {
        this(authService, marketService, tradingService, new PortfolioService(marketService));
    }

    public TradePanel(AuthService authService, MarketService marketService, TradingService tradingService,
                      PortfolioService portfolioService) {
        super("", ""); // Header handled by custom terminal header
        this.authService = authService;
        this.marketService = marketService;
        this.tradingService = tradingService;
        this.portfolioService = portfolioService;

        setLayout(new BorderLayout());
        setBackground(Theme.APP_BG);
        setBorder(null);

        this.tickerPanel = new TickerPanel(marketService);

        // Select initial asset (prefer RELIANCE, NVDA, or first in list)
        List<Asset> all = marketService.getAllAssets();
        if (!all.isEmpty()) {
            this.currentAsset = marketService.findBySymbol("RELIANCE")
                    .or(() -> marketService.findBySymbol("NVDA"))
                    .orElse(all.get(0));
            this.limitPrice = currentAsset.getPrice();
            this.targetPrice = Math.round(limitPrice * 1.035 * 100.0) / 100.0;
            this.stopLossPrice = Math.round(limitPrice * 0.978 * 100.0) / 100.0;
        }

        // Main Scrollable Body
        JPanel scrollBody = new JPanel();
        scrollBody.setLayout(new BoxLayout(scrollBody, BoxLayout.Y_AXIS));
        scrollBody.setOpaque(false);
        scrollBody.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

        // 1. Header & Ticker
        JPanel headerSection = buildTerminalHeader();
        add(headerSection, BorderLayout.NORTH);

        // 2. Top Summary KPI Row
        JPanel kpiSection = buildKpiRow();
        scrollBody.add(kpiSection);
        scrollBody.add(Box.createVerticalStrut(8));

        // 3. Market Switcher & Asset Strip
        JPanel assetStrip = buildMarketSwitcherStrip();
        scrollBody.add(assetStrip);
        scrollBody.add(Box.createVerticalStrut(8));

        // 4. Main 65% / 35% Workspace
        JPanel workspace = buildMainWorkspace();
        scrollBody.add(workspace);
        scrollBody.add(Box.createVerticalStrut(8));

        // 5. Bottom Tabbed Orders & History Section
        JPanel bottomSection = buildBottomOrdersSection();
        scrollBody.add(bottomSection);

        JScrollPane scrollPane = new JScrollPane(scrollBody);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);

        // Wire inputs and calculations
        wireEvents();
        updateSelectedAssetUI();
    }

    public void selectAssetBySymbol(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) return;
        marketService.findBySymbol(symbol.trim()).ifPresent(asset -> {
            this.currentAsset = asset;
            this.limitPrice = asset.getPrice();
            this.targetPrice = Math.round(limitPrice * 1.035 * 100.0) / 100.0;
            this.stopLossPrice = Math.round(limitPrice * 0.978 * 100.0) / 100.0;
            txtPrice.setText(String.format(Locale.US, "%.2f", limitPrice));
            txtTarget.setText(String.format(Locale.US, "%.2f", targetPrice));
            txtStopLoss.setText(String.format(Locale.US, "%.2f", stopLossPrice));
            updateSelectedAssetUI();
            recalculateTicket();
        });
    }

    // -------------------------------------------------------------------------
    // 1. HEADER SECTION
    // -------------------------------------------------------------------------
    private JPanel buildTerminalHeader() {
        JPanel wrapper = new JPanel(new BorderLayout(0, 0));
        wrapper.setOpaque(true);
        wrapper.setBackground(Theme.SIDEBAR_BG);
        wrapper.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_CUSTOM));

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        topBar.setPreferredSize(new Dimension(100, 48));
        topBar.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 14));

        // Left: Breadcrumb + Title + Live Dot + Search
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));
        left.setOpaque(false);

        JPanel titleBlock = new JPanel();
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        titleBlock.setOpaque(false);

        JLabel breadcrumb = new JLabel("Home  /  Trade");
        breadcrumb.setFont(Theme.MICRO);
        breadcrumb.setForeground(Theme.TXT_MUTED);

        JLabel title = new JLabel("Trade Terminal");
        title.setFont(Theme.HEADING);
        title.setForeground(Color.WHITE);

        titleBlock.add(breadcrumb);
        titleBlock.add(title);
        left.add(titleBlock);

        // Live badge
        JLabel livePill = new JLabel("● SIMULATOR LIVE");
        livePill.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 9f));
        livePill.setForeground(Theme.GAIN);
        livePill.setOpaque(true);
        livePill.setBackground(Theme.GREEN_BG);
        livePill.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        left.add(livePill);

        // Search bar
        JPanel searchBox = new JPanel(new BorderLayout(4, 0));
        searchBox.setOpaque(true);
        searchBox.setBackground(Theme.APP_BG);
        searchBox.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 5, 1),
                BorderFactory.createEmptyBorder(2, 8, 2, 6)));
        searchBox.setPreferredSize(new Dimension(240, 26));

        JLabel sGlyph = new JLabel("⌕");
        sGlyph.setFont(new Font("SansSerif", Font.BOLD, 12));
        sGlyph.setForeground(Theme.TXT_MUTED);

        JTextField sField = new JTextField();
        sField.setOpaque(false);
        sField.setBorder(null);
        sField.setForeground(Color.WHITE);
        sField.setFont(Theme.SMALL);
        sField.setCaretColor(Theme.ACCENT);
        sField.setToolTipText("Type symbol (e.g. RELIANCE, NVDA, BTC) to load");
        sField.addActionListener(e -> selectAssetBySymbol(sField.getText()));

        JLabel kBadge = new JLabel("⌘K");
        kBadge.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 8f));
        kBadge.setForeground(Theme.TXT_MUTED);

        searchBox.add(sGlyph, BorderLayout.WEST);
        searchBox.add(sField, BorderLayout.CENTER);
        searchBox.add(kBadge, BorderLayout.EAST);
        left.add(searchBox);

        topBar.add(left, BorderLayout.WEST);

        // Right: Market status + Virtual Cash pill
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        right.setOpaque(false);

        JLabel mktStatus = new JLabel("● Markets Open · Real-Time Simulation");
        mktStatus.setFont(Theme.MICRO);
        mktStatus.setForeground(Theme.TXT_SECONDARY);

        JPanel cashPill = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        cashPill.setOpaque(true);
        cashPill.setBackground(Theme.APP_BG);
        cashPill.setBorder(new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 4, 1));

        JLabel lCash = new JLabel("Virtual Cash:");
        lCash.setFont(Theme.MICRO);
        lCash.setForeground(Theme.TXT_MUTED);

        headerCashVal.setFont(Theme.MONO_BODY.deriveFont(Font.BOLD, 11f));
        headerCashVal.setForeground(Theme.GAIN);

        cashPill.add(lCash);
        cashPill.add(headerCashVal);

        right.add(mktStatus);
        right.add(cashPill);
        topBar.add(right, BorderLayout.EAST);

        wrapper.add(topBar, BorderLayout.NORTH);
        wrapper.add(tickerPanel, BorderLayout.SOUTH);
        return wrapper;
    }

    // -------------------------------------------------------------------------
    // 2. TOP SUMMARY 4 KPI CARDS
    // -------------------------------------------------------------------------
    private JPanel buildKpiRow() {
        JPanel grid = new JPanel(new GridLayout(1, 4, 8, 0));
        grid.setOpaque(false);
        grid.setMaximumSize(new Dimension(Short.MAX_VALUE, 76));

        // 1. Available Cash
        grid.add(buildKpiCard("AVAILABLE CASH", "Simulated Pool", kpiCashVal, kpiCashEq, Theme.ACCENT, false));
        // 2. Portfolio Value with Sparkline
        grid.add(buildKpiCard("PORTFOLIO VALUE", "+2.40%", kpiPortVal, new JLabel("All Assets"), Theme.GAIN, true));
        // 3. Buying Power
        grid.add(buildKpiCard("BUYING POWER", "2x Margin On", kpiPowerVal, new JLabel("Intraday Headroom · Healthy"), Theme.ACCENT, false));
        // 4. Today's Realized P&L
        grid.add(buildKpiCard("TODAY'S REALIZED P&L", "Green Session", kpiPnlVal, kpiFilledCount, Theme.GAIN, false));

        return grid;
    }

    private JPanel buildKpiCard(String title, String pillText, JLabel valLbl, JLabel subLbl, Color accentColor, boolean withSparkline) {
        JPanel card = new JPanel(new BorderLayout(4, 2));
        card.setOpaque(true);
        card.setBackground(Theme.CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 6, 1),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));

        // Top Row: Title + Pill
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);

        JLabel tLbl = new JLabel(title);
        tLbl.setFont(Theme.MICRO.deriveFont(Font.BOLD, 8.5f));
        tLbl.setForeground(Theme.TXT_MUTED);

        JLabel pLbl = (pillText.startsWith("+") || pillText.startsWith("▲")) ? kpiPnlPct : new JLabel(pillText);
        if (pLbl != kpiPnlPct && pillText.startsWith("+")) {
            pLbl = kpiPortPct;
        }
        pLbl.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 8.5f));
        pLbl.setForeground(accentColor);
        pLbl.setOpaque(true);
        pLbl.setBackground(new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 25));
        pLbl.setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 4));

        top.add(tLbl, BorderLayout.WEST);
        top.add(pLbl, BorderLayout.EAST);
        card.add(top, BorderLayout.NORTH);

        // Middle Row: Value (+ Sparkline if enabled)
        JPanel mid = new JPanel(new BorderLayout());
        mid.setOpaque(false);

        valLbl.setFont(Theme.MONO_BODY.deriveFont(Font.BOLD, 15f));
        valLbl.setForeground(Color.WHITE);
        mid.add(valLbl, BorderLayout.WEST);

        if (withSparkline) {
            JPanel spark = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int w = getWidth();
                    int h = getHeight();
                    int[] xs = {1, 10, 20, 30, 40, 52};
                    int[] ys = {h - 3, h - 6, h - 12, h - 8, h - 14, 2};
                    g2.setColor(Theme.GAIN);
                    g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawPolyline(xs, ys, 6);
                    g2.dispose();
                }
            };
            spark.setPreferredSize(new Dimension(54, 18));
            spark.setOpaque(false);
            mid.add(spark, BorderLayout.EAST);
        }
        card.add(mid, BorderLayout.CENTER);

        // Bottom Subtitle
        subLbl.setFont(Theme.MICRO);
        subLbl.setForeground(Theme.TXT_MUTED);
        card.add(subLbl, BorderLayout.SOUTH);

        return card;
    }

    // -------------------------------------------------------------------------
    // 3. MARKET SWITCHER & ASSET STRIP
    // -------------------------------------------------------------------------
    private JPanel buildMarketSwitcherStrip() {
        JPanel strip = new JPanel(new BorderLayout(8, 4));
        strip.setOpaque(true);
        strip.setBackground(Theme.CARD_BG);
        strip.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 6, 1),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        strip.setMaximumSize(new Dimension(Short.MAX_VALUE, 50));

        // Left: Region Tabs & Quick Asset Shortcuts
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.setOpaque(false);

        JPanel regionPills = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        regionPills.setOpaque(true);
        regionPills.setBackground(Theme.SURFACE_SUB);
        regionPills.setBorder(new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 4, 1));

        JButton btnNse = createRegionButton("INDIA (NSE/BSE)", "NSE");
        JButton btnNyse = createRegionButton("USA (NYSE)", "NYSE");
        JButton btnCrypto = createRegionButton("CRYPTO 24/7", "CRYPTO");

        regionPills.add(btnNse);
        regionPills.add(btnNyse);
        regionPills.add(btnCrypto);
        left.add(regionPills);

        // Divider
        JPanel div = new JPanel();
        div.setPreferredSize(new Dimension(1, 18));
        div.setBackground(Theme.BORDER_CUSTOM);
        left.add(div);

        // Quick asset shortcut buttons
        quickAssetsPanel.setOpaque(false);
        refreshQuickAssetButtons();
        left.add(quickAssetsPanel);

        strip.add(left, BorderLayout.WEST);

        // Right: Active Asset Telemetry
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);

        // Avatar
        assetAvatar.setFont(Theme.HEADING.deriveFont(Font.BOLD, 12f));
        assetAvatar.setForeground(Theme.ACCENT);
        assetAvatar.setOpaque(true);
        assetAvatar.setBackground(Theme.ACCENT_BG);
        assetAvatar.setPreferredSize(new Dimension(24, 24));
        assetAvatar.setBorder(new Theme.RoundedBorder(Theme.ACCENT, 12, 1));
        right.add(assetAvatar);

        // Symbol & Exchange
        JPanel symBlock = new JPanel();
        symBlock.setLayout(new BoxLayout(symBlock, BoxLayout.Y_AXIS));
        symBlock.setOpaque(false);

        JPanel symRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        symRow.setOpaque(false);
        assetSymbolLbl.setFont(Theme.MONO_BODY.deriveFont(Font.BOLD, 12f));
        assetSymbolLbl.setForeground(Color.WHITE);
        assetExchangeLbl.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 8f));
        assetExchangeLbl.setForeground(Theme.TXT_MUTED);
        assetExchangeLbl.setOpaque(true);
        assetExchangeLbl.setBackground(Theme.SURFACE_SUB);
        assetExchangeLbl.setBorder(BorderFactory.createEmptyBorder(1, 3, 1, 3));
        symRow.add(assetSymbolLbl);
        symRow.add(assetExchangeLbl);

        assetNameLbl.setFont(Theme.MICRO);
        assetNameLbl.setForeground(Theme.TXT_MUTED);

        symBlock.add(symRow);
        symBlock.add(assetNameLbl);
        right.add(symBlock);

        // Price & Delta
        JPanel priceBlock = new JPanel();
        priceBlock.setLayout(new BoxLayout(priceBlock, BoxLayout.Y_AXIS));
        priceBlock.setOpaque(false);
        assetCmpLbl.setFont(Theme.MONO_BODY.deriveFont(Font.BOLD, 13f));
        assetCmpLbl.setForeground(Color.WHITE);
        assetDeltaLbl.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 9f));
        assetDeltaLbl.setForeground(Theme.GAIN);
        priceBlock.add(assetCmpLbl);
        priceBlock.add(assetDeltaLbl);
        right.add(priceBlock);

        // Micro telemetry
        right.add(buildMicroTelemetry("24H High", assetHighLbl));
        right.add(buildMicroTelemetry("24H Low", assetLowLbl));
        right.add(buildMicroTelemetry("Volume", assetVolLbl));
        right.add(buildMicroTelemetry("VWAP", assetVwapLbl));

        // Live Market Tag
        assetSessionLbl.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 8.5f));
        assetSessionLbl.setForeground(Theme.GAIN);
        assetSessionLbl.setOpaque(true);
        assetSessionLbl.setBackground(Theme.GREEN_BG);
        assetSessionLbl.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
        right.add(assetSessionLbl);

        strip.add(right, BorderLayout.EAST);
        return strip;
    }

    private JPanel buildMicroTelemetry(String title, JLabel valLbl) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        JLabel t = new JLabel(title);
        t.setFont(Theme.MICRO);
        t.setForeground(Theme.TXT_MUTED);
        valLbl.setFont(Theme.MONO_TINY.deriveFont(Font.PLAIN, 9.5f));
        valLbl.setForeground(Theme.TXT_SECONDARY);
        p.add(t);
        p.add(valLbl);
        return p;
    }

    private JButton createRegionButton(String text, String regionKey) {
        JButton b = new JButton(text);
        b.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 8.5f));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setPreferredSize(new Dimension(text.length() > 10 ? 98 : 78, 20));
        styleRegionBtn(b, "ALL".equals(currentRegion) && "NSE".equals(regionKey));

        b.addActionListener(e -> {
            this.currentRegion = regionKey;
            refreshQuickAssetButtons();
        });
        return b;
    }

    private void styleRegionBtn(JButton b, boolean active) {
        if (active) {
            b.setBackground(Theme.ACCENT);
            b.setForeground(Color.WHITE);
            b.setBorder(new Theme.RoundedBorder(Theme.ACCENT, 3, 1));
        } else {
            b.setBackground(Theme.SURFACE_SUB);
            b.setForeground(Theme.TXT_MUTED);
            b.setBorder(BorderFactory.createEmptyBorder());
        }
    }

    private void refreshQuickAssetButtons() {
        quickAssetsPanel.removeAll();
        String[] symbols;
        if ("NSE".equalsIgnoreCase(currentRegion)) {
            symbols = new String[]{"RELIANCE", "TCS", "INFY", "HDFCBANK", "SBIN"};
        } else if ("NYSE".equalsIgnoreCase(currentRegion)) {
            symbols = new String[]{"NVDA", "AAPL", "MSFT", "TSLA", "SPY"};
        } else if ("CRYPTO".equalsIgnoreCase(currentRegion)) {
            symbols = new String[]{"BTC", "ETH", "SOL"};
        } else {
            symbols = new String[]{"RELIANCE", "TCS", "INFY", "NVDA", "BTC"};
        }

        for (String s : symbols) {
            JButton sb = new JButton(s);
            sb.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 8.5f));
            sb.setFocusPainted(false);
            sb.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            boolean isCur = currentAsset != null && currentAsset.getSymbol().equalsIgnoreCase(s);
            if (isCur) {
                sb.setBackground(Theme.SURFACE_SUB);
                sb.setForeground(Theme.ACCENT);
                sb.setBorder(new Theme.RoundedBorder(Theme.ACCENT, 3, 1));
            } else {
                sb.setBackground(Theme.SURFACE_SUB);
                sb.setForeground(Theme.TXT_SECONDARY);
                sb.setBorder(new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 3, 1));
            }
            sb.setPreferredSize(new Dimension(58, 20));
            sb.addActionListener(e -> selectAssetBySymbol(s));
            quickAssetsPanel.add(sb);
        }
        quickAssetsPanel.revalidate();
        quickAssetsPanel.repaint();
    }

    // -------------------------------------------------------------------------
    // 4. MAIN WORKSPACE (65% LEFT / 35% RIGHT)
    // -------------------------------------------------------------------------
    private JPanel buildMainWorkspace() {
        JPanel workspace = new JPanel(new GridBagLayout());
        workspace.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridy = 0;

        // LEFT COLUMN (65%): Candlestick Chart + Level 2 Order Depth
        gbc.gridx = 0;
        gbc.weightx = 0.65;
        gbc.weighty = 1.0;
        gbc.insets = new Insets(0, 0, 0, 4);

        JPanel leftCol = new JPanel();
        leftCol.setLayout(new BoxLayout(leftCol, BoxLayout.Y_AXIS));
        leftCol.setOpaque(false);

        chartPanel.setPreferredSize(new Dimension(600, 360));
        chartPanel.setMinimumSize(new Dimension(450, 320));
        leftCol.add(chartPanel);
        leftCol.add(Box.createVerticalStrut(8));

        depthPanel.setPreferredSize(new Dimension(600, 210));
        leftCol.add(depthPanel);

        workspace.add(leftCol, gbc);

        // RIGHT COLUMN (35%): Order Execution Ticket + Sizing Telemetry
        gbc.gridx = 1;
        gbc.weightx = 0.35;
        gbc.insets = new Insets(0, 4, 0, 0);

        JPanel orderTicket = buildOrderTicket();
        workspace.add(orderTicket, gbc);

        return workspace;
    }

    // -------------------------------------------------------------------------
    // ORDER EXECUTION TICKET (RIGHT COLUMN)
    // -------------------------------------------------------------------------
    private JPanel buildOrderTicket() {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setOpaque(true);
        card.setBackground(Theme.CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 6, 1),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));

        // Ticket Header
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("⚡ PLACE YOUR ORDER");
        title.setFont(Theme.HEADING.deriveFont(Font.BOLD, 12f));
        title.setForeground(Color.WHITE);

        JLabel sub = new JLabel("PAPER SIMULATOR");
        sub.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 8f));
        sub.setForeground(Theme.TXT_MUTED);

        header.add(title, BorderLayout.WEST);
        header.add(sub, BorderLayout.EAST);
        header.setMaximumSize(new Dimension(Short.MAX_VALUE, 22));
        card.add(header);
        card.add(Box.createVerticalStrut(8));

        // BUY / SELL Side Segmented Switcher
        JPanel sideSwitch = new JPanel(new GridLayout(1, 2, 4, 0));
        sideSwitch.setOpaque(false);
        sideSwitch.setMaximumSize(new Dimension(Short.MAX_VALUE, 30));

        btnBuySide.setFont(Theme.MONO_BODY.deriveFont(Font.BOLD, 11f));
        btnBuySide.setFocusPainted(false);
        btnBuySide.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        btnSellSide.setFont(Theme.MONO_BODY.deriveFont(Font.BOLD, 11f));
        btnSellSide.setFocusPainted(false);
        btnSellSide.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        styleSideButtons();

        btnBuySide.addActionListener(e -> {
            currentSide = OrderSide.BUY;
            styleSideButtons();
            recalculateTicket();
        });
        btnSellSide.addActionListener(e -> {
            currentSide = OrderSide.SELL;
            styleSideButtons();
            recalculateTicket();
        });

        sideSwitch.add(btnBuySide);
        sideSwitch.add(btnSellSide);
        card.add(sideSwitch);
        card.add(Box.createVerticalStrut(8));

        // Product Type: Delivery (CNC) vs Intraday (MIS 5x)
        card.add(buildFieldLabel("PRODUCT TYPE"));
        JPanel prodSwitch = new JPanel(new GridLayout(1, 2, 4, 0));
        prodSwitch.setOpaque(false);
        prodSwitch.setMaximumSize(new Dimension(Short.MAX_VALUE, 24));

        styleToggleBtn(btnProductCnc, "CNC".equals(currentProduct));
        styleToggleBtn(btnProductMis, "MIS".equals(currentProduct));

        btnProductCnc.addActionListener(e -> {
            currentProduct = "CNC";
            styleToggleBtn(btnProductCnc, true);
            styleToggleBtn(btnProductMis, false);
            recalculateTicket();
        });
        btnProductMis.addActionListener(e -> {
            currentProduct = "MIS";
            styleToggleBtn(btnProductCnc, false);
            styleToggleBtn(btnProductMis, true);
            recalculateTicket();
        });

        prodSwitch.add(btnProductCnc);
        prodSwitch.add(btnProductMis);
        card.add(prodSwitch);
        card.add(Box.createVerticalStrut(8));

        // Order Type: Market, Limit, SL-M
        card.add(buildFieldLabel("ORDER TYPE"));
        JPanel typeSwitch = new JPanel(new GridLayout(1, 4, 4, 0));
        typeSwitch.setOpaque(false);
        typeSwitch.setMaximumSize(new Dimension(Short.MAX_VALUE, 24));

        styleToggleBtn(btnTypeMarket, "Market".equals(currentOrderType));
        styleToggleBtn(btnTypeLimit, "Limit".equals(currentOrderType));
        styleToggleBtn(btnTypeSlm, "SL-M".equals(currentOrderType));
        styleToggleBtn(btnTypeTp, "TP".equals(currentOrderType));
        btnTypeSlm.setToolTipText("Stop-Loss: triggers when the price reaches the Stop-Loss field");
        btnTypeTp.setToolTipText("Take-Profit: triggers when the price reaches the Target field");

        btnTypeMarket.addActionListener(e -> setOrderType("Market"));
        btnTypeLimit.addActionListener(e -> setOrderType("Limit"));
        btnTypeSlm.addActionListener(e -> setOrderType("SL-M"));
        btnTypeTp.addActionListener(e -> setOrderType("TP"));

        typeSwitch.add(btnTypeMarket);
        typeSwitch.add(btnTypeLimit);
        typeSwitch.add(btnTypeSlm);
        typeSwitch.add(btnTypeTp);
        card.add(typeSwitch);
        card.add(Box.createVerticalStrut(8));

        // Quantity Stepper + Lot % Pills
        card.add(buildFieldLabel("QUANTITY (SHARES)"));
        JPanel qtyRow = new JPanel(new BorderLayout(4, 0));
        qtyRow.setOpaque(false);
        qtyRow.setMaximumSize(new Dimension(Short.MAX_VALUE, 28));

        JButton qMinus = createStepperBtn("-");
        JButton qPlus = createStepperBtn("+");

        txtQty.setFont(Theme.MONO_BODY.deriveFont(Font.BOLD, 13f));
        txtQty.setHorizontalAlignment(JTextField.CENTER);
        txtQty.setBackground(Theme.SURFACE_SUB);
        txtQty.setForeground(Color.WHITE);
        txtQty.setBorder(new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 4, 1));

        qMinus.addActionListener(e -> {
            if (quantity > 1) {
                quantity--;
                txtQty.setText(String.valueOf(quantity));
                recalculateTicket();
            }
        });
        qPlus.addActionListener(e -> {
            quantity++;
            txtQty.setText(String.valueOf(quantity));
            recalculateTicket();
        });

        qtyRow.add(qMinus, BorderLayout.WEST);
        qtyRow.add(txtQty, BorderLayout.CENTER);
        qtyRow.add(qPlus, BorderLayout.EAST);
        card.add(qtyRow);
        card.add(Box.createVerticalStrut(4));

        // Lot % Pills [25%, 50%, 75%, MAX]
        JPanel lotPills = new JPanel(new GridLayout(1, 4, 4, 0));
        lotPills.setOpaque(false);
        lotPills.setMaximumSize(new Dimension(Short.MAX_VALUE, 20));

        lotPills.add(createLotBtn("25%", 0.25));
        lotPills.add(createLotBtn("50%", 0.50));
        lotPills.add(createLotBtn("75%", 0.75));
        lotPills.add(createLotBtn("MAX", 1.00));
        card.add(lotPills);
        card.add(Box.createVerticalStrut(8));

        // Limit / Trigger Price
        card.add(buildFieldLabel("LIMIT / TRIGGER PRICE"));
        txtPrice.setFont(Theme.MONO_BODY.deriveFont(Font.BOLD, 12f));
        txtPrice.setBackground(Theme.SURFACE_SUB);
        txtPrice.setForeground(Color.WHITE);
        txtPrice.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 4, 1),
                BorderFactory.createEmptyBorder(3, 8, 3, 8)));
        txtPrice.setMaximumSize(new Dimension(Short.MAX_VALUE, 26));
        card.add(txtPrice);
        card.add(Box.createVerticalStrut(8));

        // Risk Bracket: Target & Stop-Loss
        JPanel bracketCard = new JPanel(new BorderLayout(4, 4));
        bracketCard.setOpaque(true);
        bracketCard.setBackground(Theme.SURFACE_SUB);
        bracketCard.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 4, 1),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        bracketCard.setMaximumSize(new Dimension(Short.MAX_VALUE, 65));

        JPanel bHeader = new JPanel(new BorderLayout());
        bHeader.setOpaque(false);
        JLabel rkTitle = new JLabel("RISK BRACKET");
        rkTitle.setFont(Theme.MICRO.deriveFont(Font.BOLD, 8f));
        rkTitle.setForeground(Theme.TXT_MUTED);

        lblRrRatio.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 8.5f));
        lblRrRatio.setForeground(Theme.ACCENT);
        bHeader.add(rkTitle, BorderLayout.WEST);
        bHeader.add(lblRrRatio, BorderLayout.EAST);
        bracketCard.add(bHeader, BorderLayout.NORTH);

        JPanel bInputs = new JPanel(new GridLayout(1, 2, 6, 0));
        bInputs.setOpaque(false);

        JPanel targetBox = new JPanel(new BorderLayout(0, 2));
        targetBox.setOpaque(false);
        JLabel lTgt = new JLabel("Target (₹)");
        lTgt.setFont(Theme.MICRO);
        lTgt.setForeground(Theme.GAIN);
        txtTarget.setFont(Theme.MONO_TINY);
        txtTarget.setBackground(Theme.APP_BG);
        txtTarget.setForeground(Color.WHITE);
        txtTarget.setBorder(new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 3, 1));
        targetBox.add(lTgt, BorderLayout.NORTH);
        targetBox.add(txtTarget, BorderLayout.CENTER);

        JPanel slBox = new JPanel(new BorderLayout(0, 2));
        slBox.setOpaque(false);
        JLabel lSl = new JLabel("Stop-Loss (₹)");
        lSl.setFont(Theme.MICRO);
        lSl.setForeground(Theme.LOSS);
        txtStopLoss.setFont(Theme.MONO_TINY);
        txtStopLoss.setBackground(Theme.APP_BG);
        txtStopLoss.setForeground(Color.WHITE);
        txtStopLoss.setBorder(new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 3, 1));
        slBox.add(lSl, BorderLayout.NORTH);
        slBox.add(txtStopLoss, BorderLayout.CENTER);

        bInputs.add(targetBox);
        bInputs.add(slBox);
        bracketCard.add(bInputs, BorderLayout.CENTER);
        card.add(bracketCard);
        card.add(Box.createVerticalStrut(8));

        // Live Order Calculation Breakdown Card
        JPanel calcCard = new JPanel(new GridLayout(5, 1, 0, 3));
        calcCard.setOpaque(true);
        calcCard.setBackground(new Color(0x10, 0x16, 0x22));
        calcCard.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 4, 1),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        calcCard.setMaximumSize(new Dimension(Short.MAX_VALUE, 90));

        calcCard.add(buildBreakdownRow("Gross Transaction:", calcGrossVal, Theme.TXT_SECONDARY));
        calcCard.add(buildBreakdownRow("Est. Spread + Slippage:", calcBrokerageVal, Theme.WARNING));
        calcCard.add(buildBreakdownRow("Brokerage Fee (0.1%):", calcTaxesVal, Theme.TXT_SECONDARY));
        calcCard.add(buildBreakdownRow("Net Required Capital:", calcNetRequiredVal, Theme.ACCENT));
        calcCard.add(buildBreakdownRow("Remaining Cash Post-Trade:", calcRemainingCashVal, Theme.GAIN));
        card.add(calcCard);
        card.add(Box.createVerticalStrut(8));

        // Big Primary Execution Button
        btnExecuteOrder.setFont(Theme.HEADING.deriveFont(Font.BOLD, 12f));
        btnExecuteOrder.setForeground(Color.WHITE);
        btnExecuteOrder.setBackground(Theme.GAIN);
        btnExecuteOrder.setBorder(new Theme.RoundedBorder(Theme.GAIN, 5, 1));
        btnExecuteOrder.setFocusPainted(false);
        btnExecuteOrder.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnExecuteOrder.setMaximumSize(new Dimension(Short.MAX_VALUE, 34));
        btnExecuteOrder.addActionListener(e -> promptTradeExecutionModal());
        card.add(btnExecuteOrder);
        card.add(Box.createVerticalStrut(4));

        JLabel simNotice = new JLabel("Simulated paper trading only. No real capital at risk.", SwingConstants.CENTER);
        simNotice.setFont(Theme.MICRO);
        simNotice.setForeground(Theme.TXT_MUTED);
        simNotice.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(simNotice);
        card.add(Box.createVerticalStrut(8));

        // Position Sizing & Margin Telemetry
        JPanel teleCard = new JPanel(new GridLayout(2, 1, 0, 4));
        teleCard.setOpaque(true);
        teleCard.setBackground(Theme.SURFACE_SUB);
        teleCard.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 4, 1),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        teleCard.setMaximumSize(new Dimension(Short.MAX_VALUE, 65));

        JPanel r1 = new JPanel(new BorderLayout());
        r1.setOpaque(false);
        JLabel t1 = new JLabel("Portfolio Sizing Exposure");
        t1.setFont(Theme.MICRO);
        t1.setForeground(Theme.TXT_MUTED);
        lblExposureText.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 8.5f));
        lblExposureText.setForeground(Color.WHITE);
        r1.add(t1, BorderLayout.WEST);
        r1.add(lblExposureText, BorderLayout.EAST);
        r1.add(barExposure, BorderLayout.SOUTH);

        JPanel r2 = new JPanel(new BorderLayout());
        r2.setOpaque(false);
        JLabel t2 = new JLabel("Intraday Margin Buffer");
        t2.setFont(Theme.MICRO);
        t2.setForeground(Theme.TXT_MUTED);
        lblMarginText.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 8.5f));
        lblMarginText.setForeground(Theme.GAIN);
        r2.add(t2, BorderLayout.WEST);
        r2.add(lblMarginText, BorderLayout.EAST);
        r2.add(barMargin, BorderLayout.SOUTH);

        teleCard.add(r1);
        teleCard.add(r2);
        card.add(teleCard);

        return card;
    }

    private JPanel buildFieldLabel(String text) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setOpaque(false);
        JLabel l = new JLabel(text);
        l.setFont(Theme.MICRO.deriveFont(Font.BOLD, 8f));
        l.setForeground(Theme.TXT_MUTED);
        p.add(l);
        p.setMaximumSize(new Dimension(Short.MAX_VALUE, 14));
        return p;
    }

    private JPanel buildBreakdownRow(String label, JLabel val, Color valColor) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        JLabel l = new JLabel(label);
        l.setFont(Theme.MICRO);
        l.setForeground(Theme.TXT_MUTED);
        val.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 9.5f));
        val.setForeground(valColor);
        p.add(l, BorderLayout.WEST);
        p.add(val, BorderLayout.EAST);
        return p;
    }

    private JButton createStepperBtn(String text) {
        JButton b = new JButton(text);
        b.setFont(Theme.MONO_BODY.deriveFont(Font.BOLD, 14f));
        b.setForeground(Color.WHITE);
        b.setBackground(Theme.SURFACE_SUB);
        b.setBorder(new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 4, 1));
        b.setFocusPainted(false);
        b.setPreferredSize(new Dimension(32, 28));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private JButton createLotBtn(String label, double pct) {
        JButton b = new JButton(label);
        b.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 8.5f));
        b.setForeground(Theme.TXT_SECONDARY);
        b.setBackground(Theme.SURFACE_SUB);
        b.setBorder(new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 3, 1));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(e -> {
            User user = authService.getCurrentUser();
            if (user == null || currentAsset == null) return;
            double price = getEffectivePrice();
            if (price <= 0) return;

            if (currentSide == OrderSide.BUY) {
                double budget = user.getAccount().getBalance() * pct;
                this.quantity = Math.max(1, (int) (budget / price));
            } else {
                int owned = user.getPortfolio().getQuantity(currentAsset.getSymbol());
                this.quantity = Math.max(1, (int) (owned * pct));
            }
            txtQty.setText(String.valueOf(this.quantity));
            recalculateTicket();
        });
        return b;
    }

    private void styleSideButtons() {
        if (currentSide == OrderSide.BUY) {
            btnBuySide.setBackground(Theme.GAIN);
            btnBuySide.setForeground(Color.WHITE);
            btnBuySide.setBorder(new Theme.RoundedBorder(Theme.GAIN, 4, 1));

            btnSellSide.setBackground(Theme.SURFACE_SUB);
            btnSellSide.setForeground(Theme.TXT_MUTED);
            btnSellSide.setBorder(new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 4, 1));

            btnExecuteOrder.setBackground(Theme.GAIN);
            btnExecuteOrder.setBorder(new Theme.RoundedBorder(Theme.GAIN, 5, 1));
        } else {
            btnSellSide.setBackground(Theme.LOSS);
            btnSellSide.setForeground(Color.WHITE);
            btnSellSide.setBorder(new Theme.RoundedBorder(Theme.LOSS, 4, 1));

            btnBuySide.setBackground(Theme.SURFACE_SUB);
            btnBuySide.setForeground(Theme.TXT_MUTED);
            btnBuySide.setBorder(new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 4, 1));

            btnExecuteOrder.setBackground(Theme.LOSS);
            btnExecuteOrder.setBorder(new Theme.RoundedBorder(Theme.LOSS, 5, 1));
        }
    }

    private void styleToggleBtn(JButton b, boolean active) {
        b.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 8.5f));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (active) {
            b.setBackground(Theme.ACCENT);
            b.setForeground(Color.WHITE);
            b.setBorder(new Theme.RoundedBorder(Theme.ACCENT, 3, 1));
        } else {
            b.setBackground(Theme.SURFACE_SUB);
            b.setForeground(Theme.TXT_MUTED);
            b.setBorder(new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 3, 1));
        }
    }

    private void setOrderType(String type) {
        this.currentOrderType = type;
        styleToggleBtn(btnTypeMarket, "Market".equals(type));
        styleToggleBtn(btnTypeLimit, "Limit".equals(type));
        styleToggleBtn(btnTypeSlm, "SL-M".equals(type));
        styleToggleBtn(btnTypeTp, "TP".equals(type));

        boolean needsPrice = "Limit".equals(type);   // SL-M uses the Stop-Loss field, TP uses the Target field
        txtPrice.setEnabled(needsPrice);
        if (!needsPrice && currentAsset != null) {
            txtPrice.setText(String.format(Locale.US, "%.2f", currentAsset.getPrice()));
        }
        recalculateTicket();
    }

    // -------------------------------------------------------------------------
    // 5. BOTTOM TABBED SECTION: OPEN ORDERS VS HISTORY
    // -------------------------------------------------------------------------
    private JPanel buildBottomOrdersSection() {
        JPanel card = new JPanel(new BorderLayout(0, 6));
        card.setOpaque(true);
        card.setBackground(Theme.CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 6, 1),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        card.setPreferredSize(new Dimension(800, 190));

        // Top Toolbar
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.setPreferredSize(new Dimension(100, 26));

        // Tabs
        JPanel tabPills = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        tabPills.setOpaque(false);

        tabBtnOpen.setFont(Theme.HEADING.deriveFont(Font.BOLD, 11f));
        tabBtnOpen.setFocusPainted(false);
        tabBtnOpen.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        tabBtnExec.setFont(Theme.HEADING.deriveFont(Font.BOLD, 11f));
        tabBtnExec.setFocusPainted(false);
        tabBtnExec.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        styleTabBtn(tabBtnOpen, true);
        styleTabBtn(tabBtnExec, false);

        tabBtnOpen.addActionListener(e -> {
            bottomCardLayout.show(bottomCardsContainer, "OPEN");
            styleTabBtn(tabBtnOpen, true);
            styleTabBtn(tabBtnExec, false);
        });

        tabBtnExec.addActionListener(e -> {
            bottomCardLayout.show(bottomCardsContainer, "EXEC");
            styleTabBtn(tabBtnOpen, false);
            styleTabBtn(tabBtnExec, true);
        });

        tabPills.add(tabBtnOpen);
        tabPills.add(tabBtnExec);
        bar.add(tabPills, BorderLayout.WEST);

        // Actions: Refresh Book + Cancel All
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setOpaque(false);

        JButton btnRefreshBook = new JButton("↻ Refresh Book");
        btnRefreshBook.setFont(Theme.MICRO);
        btnRefreshBook.setForeground(Theme.TXT_SECONDARY);
        btnRefreshBook.setBackground(Theme.SURFACE_SUB);
        btnRefreshBook.setBorder(new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 3, 1));
        btnRefreshBook.setFocusPainted(false);
        btnRefreshBook.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnRefreshBook.addActionListener(e -> refresh());

        JButton btnCancelAll = new JButton("Cancel All Orders");
        btnCancelAll.setFont(Theme.MICRO);
        btnCancelAll.setForeground(Theme.LOSS);
        btnCancelAll.setBackground(Theme.SURFACE_SUB);
        btnCancelAll.setBorder(new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 3, 1));
        btnCancelAll.setFocusPainted(false);
        btnCancelAll.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnCancelAll.addActionListener(e -> cancelAllOrders());

        actions.add(btnRefreshBook);
        actions.add(btnCancelAll);
        bar.add(actions, BorderLayout.EAST);

        card.add(bar, BorderLayout.NORTH);

        // Tables Container (CardLayout)
        setupTables();
        bottomCardsContainer.setOpaque(false);

        JScrollPane spOpen = new JScrollPane(openOrdersTable);
        spOpen.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER_CUSTOM));
        spOpen.getViewport().setBackground(new Color(0x10, 0x16, 0x22));

        JScrollPane spExec = new JScrollPane(execHistoryTable);
        spExec.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER_CUSTOM));
        spExec.getViewport().setBackground(new Color(0x10, 0x16, 0x22));

        bottomCardsContainer.add(spOpen, "OPEN");
        bottomCardsContainer.add(spExec, "EXEC");

        card.add(bottomCardsContainer, BorderLayout.CENTER);
        return card;
    }

    private void styleTabBtn(JButton b, boolean active) {
        if (active) {
            b.setForeground(Theme.ACCENT);
            b.setBackground(Theme.CARD_BG);
            b.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, Theme.ACCENT));
        } else {
            b.setForeground(Theme.TXT_MUTED);
            b.setBackground(Theme.CARD_BG);
            b.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));
        }
    }

    private void setupTables() {
        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(SwingConstants.CENTER);

        openOrdersTable.setRowHeight(26);
        openOrdersTable.setBackground(new Color(0x10, 0x16, 0x22));
        openOrdersTable.setForeground(Color.WHITE);
        openOrdersTable.setFont(Theme.MONO_TINY);
        openOrdersTable.getTableHeader().setBackground(Theme.CARD_BG);
        openOrdersTable.getTableHeader().setForeground(Theme.TXT_MUTED);
        openOrdersTable.getTableHeader().setFont(Theme.MICRO.deriveFont(Font.BOLD, 8.5f));

        openOrdersTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = openOrdersTable.rowAtPoint(e.getPoint());
                int col = openOrdersTable.columnAtPoint(e.getPoint());
                if (row >= 0 && col == 8) { // Cancel action
                    Order o = openOrdersModel.getOrderAt(row);
                    if (o != null) {
                        tradingService.cancelOrder(o);
                        refresh();
                    }
                }
            }
        });

        execHistoryTable.setRowHeight(26);
        execHistoryTable.setBackground(new Color(0x10, 0x16, 0x22));
        execHistoryTable.setForeground(Color.WHITE);
        execHistoryTable.setFont(Theme.MONO_TINY);
        execHistoryTable.getTableHeader().setBackground(Theme.CARD_BG);
        execHistoryTable.getTableHeader().setForeground(Theme.TXT_MUTED);
        execHistoryTable.getTableHeader().setFont(Theme.MICRO.deriveFont(Font.BOLD, 8.5f));
    }

    // -------------------------------------------------------------------------
    // 6. INTERACTIVE TRADE CONFIRMATION MODAL
    // -------------------------------------------------------------------------
    private void promptTradeExecutionModal() {
        User user = authService.getCurrentUser();
        if (user == null || currentAsset == null) return;

        double price = getEffectivePrice();
        double gross = price * quantity;
        double fee = gross * 0.001;
        double netRequired = (currentSide == OrderSide.BUY) ? (gross + fee) : (gross - fee);

        boolean isIndian = isIndianAsset(currentAsset.getSymbol());
        String cur = isIndian ? "₹" : "$";

        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Confirm Paper Trade", JDialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new BorderLayout());
        dialog.getContentPane().setBackground(Theme.CARD_BG);
        dialog.setResizable(false);

        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        JLabel title = new JLabel("⚡ CONFIRM PAPER TRADE");
        title.setFont(Theme.HEADING.deriveFont(Font.BOLD, 13f));
        title.setForeground(Color.WHITE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(title);
        p.add(Box.createVerticalStrut(10));

        JPanel details = new JPanel(new GridLayout(4, 2, 4, 6));
        details.setOpaque(true);
        details.setBackground(Theme.SURFACE_SUB);
        details.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 5, 1),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        details.setMaximumSize(new Dimension(360, 110));

        details.add(modalRow("Instrument:", currentAsset.getSymbol() + " (" + currentAsset.getName() + ")"));
        details.add(modalRow("Order Intent:", currentSide + " · " + quantity + " Shares @ " + cur + String.format(Locale.US, "%,.2f", price)));
        details.add(modalRow("Product Type:", "CNC".equals(currentProduct) ? "Delivery (CNC)" : "Intraday (MIS 5x)"));
        details.add(modalRow("Net Required:", cur + String.format(Locale.US, "%,.2f", netRequired)));
        p.add(details);
        p.add(Box.createVerticalStrut(10));

        JLabel notice = new JLabel("<html><body style='width:320px; font-size:9px; color:#94A3B8;'>"
                + "✓ This order will execute on the PaperTrader 3D Virtual Engine. No real capital at risk."
                + "</body></html>");
        p.add(notice);
        p.add(Box.createVerticalStrut(14));

        JPanel btnRow = new JPanel(new GridLayout(1, 2, 8, 0));
        btnRow.setOpaque(false);
        btnRow.setMaximumSize(new Dimension(360, 32));

        JButton btnDiscard = new JButton("DISCARD");
        btnDiscard.setFont(Theme.MONO_BODY.deriveFont(Font.BOLD, 10f));
        btnDiscard.setForeground(Theme.TXT_SECONDARY);
        btnDiscard.setBackground(Theme.SURFACE_SUB);
        btnDiscard.setBorder(new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 4, 1));
        btnDiscard.setFocusPainted(false);
        btnDiscard.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnDiscard.addActionListener(e -> dialog.dispose());

        JButton btnConfirm = new JButton("SUBMIT ORDER");
        btnConfirm.setFont(Theme.MONO_BODY.deriveFont(Font.BOLD, 10f));
        btnConfirm.setForeground(Color.WHITE);
        btnConfirm.setBackground(currentSide == OrderSide.BUY ? Theme.GAIN : Theme.LOSS);
        btnConfirm.setBorder(new Theme.RoundedBorder(currentSide == OrderSide.BUY ? Theme.GAIN : Theme.LOSS, 4, 1));
        btnConfirm.setFocusPainted(false);
        btnConfirm.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnConfirm.addActionListener(e -> {
            dialog.dispose();
            executeOrder();
        });

        btnRow.add(btnDiscard);
        btnRow.add(btnConfirm);
        p.add(btnRow);

        dialog.add(p, BorderLayout.CENTER);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private JPanel modalRow(String l, String v) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        JLabel lbl = new JLabel(l);
        lbl.setFont(Theme.MICRO);
        lbl.setForeground(Theme.TXT_MUTED);
        JLabel val = new JLabel(v);
        val.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 9.5f));
        val.setForeground(Color.WHITE);
        row.add(lbl, BorderLayout.WEST);
        row.add(val, BorderLayout.EAST);
        return row;
    }

    private void executeOrder() {
        User user = authService.getCurrentUser();
        if (user == null || currentAsset == null) return;

        double price = getEffectivePrice();
        try {
            Order order;
            if ("Market".equals(currentOrderType)) {
                order = tradingService.placeOrder(user, currentAsset, currentSide, quantity);
            } else if ("Limit".equals(currentOrderType)) {
                order = tradingService.placeOrder(user, currentAsset, currentSide, quantity, price);
            } else if ("SL-M".equals(currentOrderType)) {
                order = tradingService.placeOrder(user,
                        new StopLossOrder(currentAsset, currentSide, quantity, parseField(txtStopLoss, stopLossPrice)));
            } else {
                order = tradingService.placeOrder(user,
                        new TakeProfitOrder(currentAsset, currentSide, quantity, parseField(txtTarget, targetPrice)));
            }

            if (order.getStatus() == OrderStatus.FILLED) {
                JOptionPane.showMessageDialog(this,
                        currentSide + " " + quantity + " " + currentAsset.getSymbol() + " filled at "
                                + Formatter.money(order.getFilledPrice()),
                        "Order Executed Successfully", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this,
                        "Order #" + order.getId() + " placed and waiting (" + order.getConditionText() + ")",
                        "Order Submitted", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (SimulatorException | IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Order Execution Error", JOptionPane.ERROR_MESSAGE);
        }
        refresh();
    }

    private void cancelAllOrders() {
        User user = authService.getCurrentUser();
        if (user == null) return;
        List<Order> pending = tradingService.getPendingOrders(user);
        for (Order o : pending) {
            tradingService.cancelOrder(o);
        }
        refresh();
    }

    private static double parseField(JTextField field, double fallback) {
        try {
            return Double.parseDouble(field.getText().replaceAll("[^0-9.]", ""));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** The price used for the cost preview: buyers pay the ASK, sellers get the BID. */
    private double getEffectivePrice() {
        if (currentAsset == null) {
            return 100.0;
        }
        if ("Market".equals(currentOrderType)) {
            return currentSide == OrderSide.BUY ? currentAsset.getAsk() : currentAsset.getBid();
        }
        if ("SL-M".equals(currentOrderType)) {
            return parseField(txtStopLoss, currentAsset.getPrice());
        }
        if ("TP".equals(currentOrderType)) {
            return parseField(txtTarget, currentAsset.getPrice());
        }
        try {
            return Double.parseDouble(txtPrice.getText().replaceAll("[^0-9.]", ""));
        } catch (Exception e) {
            return currentAsset != null ? currentAsset.getPrice() : 100.0;
        }
    }

    private void wireEvents() {
        DocumentListener dl = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { onInputsChanged(); }
            public void removeUpdate(DocumentEvent e) { onInputsChanged(); }
            public void changedUpdate(DocumentEvent e) { onInputsChanged(); }
        };
        txtQty.getDocument().addDocumentListener(dl);
        txtPrice.getDocument().addDocumentListener(dl);
        txtTarget.getDocument().addDocumentListener(dl);
        txtStopLoss.getDocument().addDocumentListener(dl);
    }

    private void onInputsChanged() {
        try {
            int q = Integer.parseInt(txtQty.getText().replaceAll("[^0-9]", ""));
            if (q > 0) this.quantity = q;
        } catch (Exception ignored) {}
        recalculateTicket();
    }

    private void recalculateTicket() {
        if (currentAsset == null) return;

        double price = getEffectivePrice();
        double gross = price * quantity;
        double taxes = tradingService.calculateFee(gross); // brokerage fee from the FeeCalculator strategy
        double netRequired = (currentSide == OrderSide.BUY) ? (gross + taxes) : (gross - taxes);

        User user = authService.getCurrentUser();
        double cash = user != null ? user.getAccount().getBalance() : 100000.0;
        double remaining = (currentSide == OrderSide.BUY) ? (cash - netRequired) : (cash + netRequired);

        boolean isIndian = isIndianAsset(currentAsset.getSymbol());
        String cur = isIndian ? "₹" : "$";

        calcGrossVal.setText(String.format(Locale.US, "%s%,.2f", cur, gross));
        double slipPct = tradingService.getExecutionModel().estimateCostPercent(currentAsset, quantity);
        calcBrokerageVal.setText(String.format(Locale.US, "~%s%,.2f (%.3f%%)", cur, gross * slipPct / 100.0, slipPct));
        calcTaxesVal.setText(String.format(Locale.US, "%s%,.2f", cur, taxes));
        calcNetRequiredVal.setText(String.format(Locale.US, "%s%,.2f", cur, netRequired));
        calcRemainingCashVal.setText(String.format(Locale.US, "%s%,.2f", cur, remaining));
        calcRemainingCashVal.setForeground(remaining < 0 ? Theme.LOSS : Theme.GAIN);

        btnExecuteOrder.setText(String.format("EXECUTE PAPER %s (%d SH)", currentSide, quantity));

        // R:R Ratio
        try {
            double tgt = Double.parseDouble(txtTarget.getText().replaceAll("[^0-9.]", ""));
            double sl = Double.parseDouble(txtStopLoss.getText().replaceAll("[^0-9.]", ""));
            double reward = Math.abs(tgt - price);
            double risk = Math.max(0.01, Math.abs(price - sl));
            double ratio = reward / risk;
            lblRrRatio.setText(String.format(Locale.US, "R:R Ratio 1 : %.1f", ratio));
        } catch (Exception ignored) {}

        // Sizing & Margin Telemetry
        double netWorth = user != null ? portfolioService.getNetWorth(user) : cash;
        double exposurePct = netWorth > 0 ? Math.min(100.0, (gross / netWorth * 100.0)) : 0.0;
        barExposure.setValue((int) exposurePct);
        lblExposureText.setText(String.format(Locale.US, "%.2f%% of Total", exposurePct));

        double marginBuffer = cash > 0 ? Math.max(0.0, Math.min(100.0, (remaining / cash * 100.0))) : 0.0;
        barMargin.setValue((int) marginBuffer);
        lblMarginText.setText(String.format(Locale.US, "%.1f%% Available", marginBuffer));
    }

    private void updateSelectedAssetUI() {
        if (currentAsset == null) return;

        chartPanel.setAsset(currentAsset);
        depthPanel.updateForAsset(currentAsset);

        boolean isIndian = isIndianAsset(currentAsset.getSymbol());
        String cur = isIndian ? "₹" : "$";
        double cmp = currentAsset.getPrice();
        double prev = currentAsset.getPreviousClose() > 0 ? currentAsset.getPreviousClose() : cmp;
        double delta = cmp - prev;
        double deltaPct = currentAsset.getChangePercent();
        boolean up = delta >= 0;

        assetAvatar.setText(currentAsset.getSymbol().substring(0, 1).toUpperCase());
        assetSymbolLbl.setText(currentAsset.getSymbol());
        assetExchangeLbl.setText(isIndian ? "NSE" : (currentAsset.getType().equalsIgnoreCase("Crypto") ? "24/7" : "NYSE"));
        assetNameLbl.setText(currentAsset.getName());

        assetCmpLbl.setText(String.format(Locale.US, "%s%,.2f", cur, cmp));
        String sign = up ? "+" : "";
        assetDeltaLbl.setText(String.format(Locale.US, "%s%s%,.2f (%s%.2f%%)", sign, cur, delta, sign, deltaPct));
        assetDeltaLbl.setForeground(up ? Theme.GAIN : Theme.LOSS);

        assetHighLbl.setText(String.format(Locale.US, "%s%,.2f", cur, cmp * 1.018));
        assetLowLbl.setText(String.format(Locale.US, "%s%,.2f", cur, cmp * 0.985));
        assetVolLbl.setText(isIndian ? "6.42M" : "18.2M");
        assetVwapLbl.setText(String.format(Locale.US, "%s%,.2f", cur, cmp * 0.996));
        assetSessionLbl.setText(isIndian ? "LIVE · 09:15-15:30 IST" : "US MARKETS OPEN");

        refreshQuickAssetButtons();
        recalculateTicket();
    }

    /** Connects the candlestick chart to real market history. */
    public void setChartService(ChartService chartService) {
        chartPanel.setChartService(chartService);
    }

    @Override
    public void refresh() {
        User user = authService.getCurrentUser();
        if (user == null) return;

        double cash = user.getAccount().getBalance();
        double portVal = portfolioService.getTotalMarketValue(user);
        double buyingPower = cash * 2.0; // 2x Margin
        double pnl = portfolioService.getTotalProfitLoss(user);
        double costBasis = portfolioService.getTotalCostBasis(user);
        double pnlPct = costBasis > 0 ? (pnl / costBasis * 100.0) : 0.0;

        headerCashVal.setText(Formatter.money(cash));
        kpiCashVal.setText(Formatter.money(cash));
        kpiCashEq.setText(Formatter.money(cash) + " USD eq.");
        kpiPortVal.setText(Formatter.money(portVal));
        kpiPortPct.setText(String.format("+%.2f%%", 2.40));
        kpiPowerVal.setText(Formatter.money(buyingPower));
        kpiPnlVal.setText(Formatter.signedMoney(pnl));
        kpiPnlPct.setText(String.format("▲ %s%.2f%%", pnl >= 0 ? "+" : "", pnlPct));

        List<Order> pending = tradingService.getPendingOrders(user);
        tabBtnOpen.setText("Active Open Orders (" + pending.size() + ")");
        openOrdersModel.setOrders(pending);

        List<Transaction> history = tradingService.getTransactionHistory(user);
        tabBtnExec.setText("Order History / Executions (" + history.size() + ")");
        kpiFilledCount.setText(history.size() + " Filled Positions");
        execHistoryModel.setTransactions(history);

        updateSelectedAssetUI();
    }

    private boolean isIndianAsset(String sym) {
        if (sym == null) return false;
        return List.of("RELIANCE", "TCS", "INFY", "HDFCBANK", "ICICIBANK", "BHARTIARTL",
                "SBIN", "ITC", "LT", "TATAMOTORS", "TRENT", "ETERNAL").contains(sym.toUpperCase());
    }

    // -------------------------------------------------------------------------
    // LEVEL 2 MARKET DEPTH (ORDER BOOK) PANEL
    // -------------------------------------------------------------------------
    private static class Level2DepthPanel extends JPanel {
        private final JLabel ratioLbl = new JLabel("Bids: 58.4% (32,450)  vs  Asks: 41.6% (23,120)");
        private final JLabel spreadLbl = new JLabel("Spread: ₹0.30 (0.01%)");
        private double bidRatio = 0.584;

        private final DepthLadderTable bidsTable = new DepthLadderTable(true);
        private final DepthLadderTable asksTable = new DepthLadderTable(false);
        private final OrderBookSimulator orderBook = new OrderBookSimulator();

        Level2DepthPanel() {
            setLayout(new BorderLayout(0, 4));
            setOpaque(true);
            setBackground(Theme.CARD_BG);
            setBorder(BorderFactory.createCompoundBorder(
                    new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 6, 1),
                    BorderFactory.createEmptyBorder(6, 10, 6, 10)));

            // Header
            JPanel top = new JPanel(new BorderLayout());
            top.setOpaque(false);
            JLabel title = new JLabel("Level 2 Market Depth");
            title.setFont(Theme.HEADING.deriveFont(Font.BOLD, 11f));
            title.setForeground(Color.WHITE);

            JLabel sub = new JLabel("5 levels around the real bid / ask");
            sub.setFont(Theme.MICRO);
            sub.setForeground(Theme.TXT_MUTED);

            JPanel leftT = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            leftT.setOpaque(false);
            leftT.add(title);
            leftT.add(sub);
            top.add(leftT, BorderLayout.WEST);

            JPanel rightT = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            rightT.setOpaque(false);
            ratioLbl.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 8.5f));
            ratioLbl.setForeground(Theme.GAIN);
            spreadLbl.setFont(Theme.MONO_TINY.deriveFont(Font.PLAIN, 8.5f));
            spreadLbl.setForeground(Theme.TXT_MUTED);
            rightT.add(ratioLbl);
            rightT.add(spreadLbl);
            top.add(rightT, BorderLayout.EAST);

            // Ratio Bar
            JPanel ratioBar = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int w = getWidth();
                    int h = getHeight();
                    int bidW = (int) (w * bidRatio);
                    g2.setColor(Theme.GAIN);
                    g2.fillRoundRect(0, 0, bidW, h, 2, 2);
                    g2.setColor(Theme.LOSS);
                    g2.fillRoundRect(bidW, 0, w - bidW, h, 2, 2);
                    g2.dispose();
                }
            };
            ratioBar.setPreferredSize(new Dimension(100, 3));
            ratioBar.setOpaque(false);

            JPanel headerWrapper = new JPanel(new BorderLayout(0, 3));
            headerWrapper.setOpaque(false);
            headerWrapper.add(top, BorderLayout.NORTH);
            headerWrapper.add(ratioBar, BorderLayout.SOUTH);
            add(headerWrapper, BorderLayout.NORTH);

            // Ladder Grid (Bids on Left, Asks on Right)
            JPanel ladder = new JPanel(new GridLayout(1, 2, 8, 0));
            ladder.setOpaque(false);
            ladder.add(bidsTable);
            ladder.add(asksTable);
            add(ladder, BorderLayout.CENTER);
        }

        void updateForAsset(Asset a) {
            if (a == null) return;
            boolean isIndian = List.of("RELIANCE", "TCS", "INFY", "HDFCBANK").contains(a.getSymbol().toUpperCase());
            String cur = isIndian ? "₹" : "$";
            // Built around the REAL bid/ask of the asset (see OrderBookSimulator)
            OrderBookSimulator.OrderBook book = orderBook.simulate(a, 5);
            double spread = book.spread();
            spreadLbl.setText(String.format(Locale.US, "Spread: %s%,.4f (%.3f%%)", cur, spread, spread / a.getPrice() * 100));

            double bidTotal = 0;
            double askTotal = 0;
            double maxSize = 0;
            for (OrderBookSimulator.Level l : book.bids()) { bidTotal += l.size(); maxSize = Math.max(maxSize, l.size()); }
            for (OrderBookSimulator.Level l : book.asks()) { askTotal += l.size(); maxSize = Math.max(maxSize, l.size()); }
            bidRatio = bidTotal / (bidTotal + askTotal);
            ratioLbl.setText(String.format(Locale.US, "Bids: %.1f%%  vs  Asks: %.1f%%", bidRatio * 100, 100 - bidRatio * 100));
            bidsTable.repopulate(book.bids(), maxSize, cur);
            asksTable.repopulate(book.asks(), maxSize, cur);
            repaint();
        }
    }

    private static class DepthLadderTable extends JPanel {
        private final boolean isBids;

        DepthLadderTable(boolean isBids) {
            this.isBids = isBids;
            setLayout(new GridLayout(6, 1, 0, 1));
            setOpaque(false);
        }

        private static String qty(double size) {
            return size < 100 ? String.format(Locale.US, "%,.3f", size) : String.format(Locale.US, "%,.0f", size);
        }

        void repopulate(List<OrderBookSimulator.Level> levels, double maxSize, String cur) {
            removeAll();

            // Header Row
            JPanel hRow = new JPanel(new GridLayout(1, 3));
            hRow.setOpaque(false);
            JLabel h1 = new JLabel(isBids ? "Qty" : "Ask Price (" + cur + ")", SwingConstants.LEFT);
            JLabel h2 = new JLabel("Total", SwingConstants.CENTER);
            JLabel h3 = new JLabel(isBids ? "Bid Price (" + cur + ")" : "Qty", SwingConstants.RIGHT);
            for (JLabel l : List.of(h1, h2, h3)) {
                l.setFont(Theme.MICRO.deriveFont(Font.BOLD, 8f));
                l.setForeground(Theme.TXT_MUTED);
                hRow.add(l);
            }
            add(hRow);

            double runningTotal = 0;
            for (OrderBookSimulator.Level level : levels) {
                runningTotal += level.size();
                double frac = maxSize > 0 ? level.size() / maxSize : 0;
                String price = String.format(Locale.US, "%,.2f", level.price());

                JPanel rowPanel = new JPanel(new GridLayout(1, 3)) {
                    @Override
                    protected void paintComponent(Graphics g) {
                        super.paintComponent(g);
                        Graphics2D g2 = (Graphics2D) g.create();
                        int w = getWidth();
                        int h = getHeight();
                        int barW = (int) (w * frac);
                        g2.setColor(isBids ? new Color(34, 197, 94, 25) : new Color(239, 68, 68, 25));
                        if (isBids) {
                            g2.fillRect(w - barW, 0, barW, h);
                        } else {
                            g2.fillRect(0, 0, barW, h);
                        }
                        g2.dispose();
                    }
                };
                rowPanel.setOpaque(false);

                JLabel l1 = new JLabel(isBids ? qty(level.size()) : price);
                l1.setFont(Theme.MONO_TINY.deriveFont(8.5f));
                l1.setForeground(isBids ? Color.WHITE : Theme.LOSS);

                JLabel l2 = new JLabel(qty(runningTotal), SwingConstants.CENTER);
                l2.setFont(Theme.MONO_TINY.deriveFont(8.5f));
                l2.setForeground(Theme.TXT_MUTED);

                JLabel l3 = new JLabel(isBids ? price : qty(level.size()), SwingConstants.RIGHT);
                l3.setFont(Theme.MONO_TINY.deriveFont(8.5f));
                l3.setForeground(isBids ? Theme.GAIN : Color.WHITE);

                rowPanel.add(l1);
                rowPanel.add(l2);
                rowPanel.add(l3);
                add(rowPanel);
            }
            revalidate();
            repaint();
        }
    }

    // -------------------------------------------------------------------------
    // CUSTOM PROGRESS BAR
    // -------------------------------------------------------------------------
    private static class JProgressBarCustom extends JPanel {
        private int value = 0;
        private final Color barColor;

        JProgressBarCustom(Color barColor) {
            this.barColor = barColor;
            setPreferredSize(new Dimension(100, 4));
            setOpaque(false);
        }

        void setValue(int val) {
            this.value = Math.max(0, Math.min(100, val));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            g2.setColor(Theme.APP_BG);
            g2.fillRoundRect(0, 0, w, h, 2, 2);
            int fillW = (int) (w * (value / 100.0));
            g2.setColor(barColor);
            g2.fillRoundRect(0, 0, fillW, h, 2, 2);
            g2.dispose();
        }
    }

    // -------------------------------------------------------------------------
    // TABLE MODELS
    // -------------------------------------------------------------------------
    private static class OpenOrdersTableModel extends AbstractTableModel {
        private final String[] cols = {"Time / ID", "Symbol", "Side", "Product", "Qty", "Trigger", "LTP", "Status", "Action"};
        private final List<Order> orders = new ArrayList<>();

        void setOrders(List<Order> list) {
            orders.clear();
            if (list != null) orders.addAll(list);
            fireTableDataChanged();
        }

        Order getOrderAt(int row) {
            return (row >= 0 && row < orders.size()) ? orders.get(row) : null;
        }

        public int getRowCount() { return orders.size(); }
        public int getColumnCount() { return cols.length; }
        public String getColumnName(int col) { return cols[col]; }

        public Object getValueAt(int row, int col) {
            Order o = orders.get(row);
            return switch (col) {
                case 0 -> "#ORD-" + o.getId();
                case 1 -> o.getAsset().getSymbol();
                case 2 -> o.getSide();
                case 3 -> "CNC";
                case 4 -> o.getQuantity();
                case 5 -> o.getConditionText();
                case 6 -> Formatter.money(o.getAsset().getPrice());
                case 7 -> "Waiting (" + o.getClass().getSimpleName().replace("Order", "") + ")";
                case 8 -> "Cancel";
                default -> "";
            };
        }
    }

    private static class ExecHistoryTableModel extends AbstractTableModel {
        private final String[] cols = {"Fill Time", "Symbol", "Side", "Filled Qty", "Avg Exec Price", "Total Value", "Realized P&L"};
        private final List<Transaction> txs = new ArrayList<>();
        private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss");

        void setTransactions(List<Transaction> list) {
            txs.clear();
            if (list != null) txs.addAll(list);
            fireTableDataChanged();
        }

        public int getRowCount() { return txs.size(); }
        public int getColumnCount() { return cols.length; }
        public String getColumnName(int col) { return cols[col]; }

        public Object getValueAt(int row, int col) {
            Transaction t = txs.get(row);
            double total = t.getQuantity() * t.getPrice() + t.getFee();
            return switch (col) {
                case 0 -> t.getTimestamp().format(dtf);
                case 1 -> t.getSymbol();
                case 2 -> t.getSide();
                case 3 -> t.getQuantity();
                case 4 -> Formatter.money(t.getPrice());
                case 5 -> Formatter.money(total);
                case 6 -> t.getSide() == OrderSide.SELL ? Formatter.signedMoney(t.getRealizedPnl()) : "-";
                default -> "";
            };
        }
    }
}
