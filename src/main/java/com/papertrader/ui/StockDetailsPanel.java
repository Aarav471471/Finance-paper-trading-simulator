package com.papertrader.ui;

import com.papertrader.service.ChartService;
import com.papertrader.model.Asset;
import com.papertrader.model.OrderSide;
import com.papertrader.service.AuthService;
import com.papertrader.service.MarketService;
import com.papertrader.service.PortfolioService;
import com.papertrader.service.TradingService;
import com.papertrader.ui.components.CandlestickChartPanel;
import com.papertrader.ui.components.ConsensusGaugePanel;
import com.papertrader.ui.components.StockTradeTicketPanel;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * PaperTrader 3D - Stock Details / Asset Deep Dive Terminal.
 * Reconstructed strictly according to the full fintech terminal HTML specification:
 * 1. Top Context Sub-Header / Stock Overview Banner with live breadcrumbs, badges, hero CMP,
 *    watchlist toggle, alert and comparison shortcuts, and instant buy/sell links.
 * 2. Left 8.5 Columns Workspace:
 *    - Section 1: TradingView-Style Candlestick Chart with multi-timeframes & indicators
 *    - Section 2: Core Statistics, Day's Range and 52-Week Range Visualizers, and 8 Fundamental Cards
 *    - Section 3: Screener-Grade Financial Statements & Corporate Overview (4 Tabbed Panes)
 *    - Section 4: Institutional Consensus Speedometer Gauge & Technical Signals Checklist
 *    - Section 5: Related Industry Peers Valuation & Multiples Table
 *    - Section 6: Real-time News, Regulatory Filings & Institutional Feeds
 * 3. Right 3.5 Columns:
 *    - Pinned Simulated Quick Paper Trade Ticket directly wired to TradingService.
 */
public class StockDetailsPanel extends BasePanel {

    private final AuthService authService;
    private final MarketService marketService;
    private final TradingService tradingService;
    private final PortfolioService portfolioService;
    private final Consumer<String> onNavigate;

    private Asset currentAsset;
    private boolean isWatchlisted = true;

    // Header Components
    private final JLabel breadcrumbCategory = new JLabel("NSE Equities");
    private final JLabel breadcrumbSymbol = new JLabel("RELIANCE");
    private final JLabel badgeExchange = new JLabel("NSE: RELIANCE");
    private final JLabel badgeIsin = new JLabel("ISIN: INE002A01018");
    private final JLabel badgeIndex = new JLabel("NIFTY 50 • Large Cap");
    private final JLabel badgeStatus = new JLabel("MARKET OPEN 09:15-15:30 IST");

    private final JLabel assetTitle = new JLabel("Reliance Industries Ltd.");
    private final JLabel assetSector = new JLabel("Oil & Gas / Conglomerates");
    private final JLabel heroCmp = new JLabel("₹2,945.50");
    private final JLabel heroDelta = new JLabel("+68.25 (+2.37%)");
    private final JLabel heroTickTime = new JLabel("Live Tick: 14:32:05 IST");
    private final JButton btnWatchlist = new JButton("★ Watchlist");

    // Section 1: Chart
    private final CandlestickChartPanel chartPanel = new CandlestickChartPanel();

    // Section 2: Range & Fundamental Cards
    private final JLabel dayLowLbl = new JLabel("2,890.00");
    private final JLabel dayHighLbl = new JLabel("2,960.00");
    private final JLabel dayCmpSpanLbl = new JLabel("CMP: 2,945.50 (79% of Day Span)");
    private final RangeBarCanvas dayRangeCanvas = new RangeBarCanvas(0.79, new Color(0x4D, 0x8E, 0xFF), new Color(0x4A, 0xE1, 0x76));

    private final JLabel week52LowLbl = new JLabel("2,220.30");
    private final JLabel week52HighLbl = new JLabel("3,217.80");
    private final JLabel week52CmpSpanLbl = new JLabel("72.7% of 52W Channel");
    private final RangeBarCanvas week52RangeCanvas = new RangeBarCanvas(0.727, new Color(0xAD, 0xC6, 0xFF), new Color(0x4D, 0x8E, 0xFF));

    // 8 Fundamental Cards Labels
    private final JLabel statPrevClose = new JLabel("2,877.25");
    private final JLabel statOpen = new JLabel("2,910.00");
    private final JLabel statMcap = new JLabel("19.92 Lakh Cr");
    private final JLabel statVol = new JLabel("6.42M");
    private final JLabel statPe = new JLabel("24.8");
    private final JLabel statBook = new JLabel("1,180.40");
    private final JLabel statEps = new JLabel("104.22");
    private final JLabel statRoe = new JLabel("11.8% / 9.6%");

    // Section 3: Screener-Grade Financials (Tabbed)
    private final CardLayout tabsCardLayout = new CardLayout();
    private final JPanel tabsContainer = new JPanel(tabsCardLayout);
    private final Map<String, JButton> tabButtons = new LinkedHashMap<>();

    // Section 4: Gauge & Checklist
    private final ConsensusGaugePanel gaugePanel = new ConsensusGaugePanel();

    // Section 5: Peer Table
    private final DefaultTableModel peersTableModel = new DefaultTableModel();
    private final JTable peersTable = new JTable(peersTableModel);

    // Section 6: News List
    private final JPanel newsContainer = new JPanel();

    // Right Column: Execution Ticket
    private final StockTradeTicketPanel ticketPanel;

    public StockDetailsPanel(AuthService authService, MarketService marketService,
                             TradingService tradingService, PortfolioService portfolioService,
                             Consumer<String> onNavigate) {
        super("", ""); // Header handled by top overview banner
        this.authService = authService;
        this.marketService = marketService;
        this.tradingService = tradingService;
        this.portfolioService = portfolioService;
        this.onNavigate = onNavigate;

        setLayout(new BorderLayout());
        setBackground(Theme.APP_BG);
        setBorder(null);

        this.ticketPanel = new StockTradeTicketPanel(authService, marketService, tradingService, this::refresh);

        // 1. Top Context Sub-Header / Stock Overview Banner
        JPanel topBanner = buildTopBanner();
        add(topBanner, BorderLayout.NORTH);

        // 2. Main Two-Column Trading Workspace
        JPanel workspace = buildWorkspace();
        add(workspace, BorderLayout.CENTER);

        // Set Default Asset (RELIANCE or first catalog asset)
        Optional<Asset> defaultAsset = marketService.findBySymbol("RELIANCE");
        if (defaultAsset.isPresent()) {
            setAsset(defaultAsset.get());
        } else {
            List<Asset> all = marketService.getAllAssets();
            if (!all.isEmpty()) setAsset(all.get(0));
        }
    }

    private JPanel buildTopBanner() {
        JPanel banner = new JPanel();
        banner.setLayout(new BoxLayout(banner, BoxLayout.Y_AXIS));
        banner.setOpaque(true);
        banner.setBackground(new Color(0x14, 0x1B, 0x2B));
        banner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_CUSTOM),
                BorderFactory.createEmptyBorder(10, 16, 10, 16)));

        // Top Row: Breadcrumbs + Badges
        JPanel row1 = new JPanel(new BorderLayout(8, 0));
        row1.setOpaque(false);

        JPanel leftBadges = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        leftBadges.setOpaque(false);

        // Breadcrumb
        JLabel bMarkets = new JLabel("Markets");
        bMarkets.setFont(Theme.BODY.deriveFont(11f));
        bMarkets.setForeground(Theme.TXT_MUTED);
        bMarkets.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        bMarkets.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (onNavigate != null) onNavigate.accept("Markets");
            }
        });

        JLabel bSep1 = new JLabel("›");
        bSep1.setFont(Theme.BODY);
        bSep1.setForeground(Theme.TXT_MUTED);

        breadcrumbCategory.setFont(Theme.BODY.deriveFont(11f));
        breadcrumbCategory.setForeground(Theme.TXT_MUTED);

        JLabel bSep2 = new JLabel("›");
        bSep2.setFont(Theme.BODY);
        bSep2.setForeground(Theme.TXT_MUTED);

        breadcrumbSymbol.setFont(Theme.BODY.deriveFont(Font.BOLD, 11f));
        breadcrumbSymbol.setForeground(Theme.ACCENT);

        leftBadges.add(bMarkets);
        leftBadges.add(bSep1);
        leftBadges.add(breadcrumbCategory);
        leftBadges.add(bSep2);
        leftBadges.add(breadcrumbSymbol);

        // Identity Badges
        badgeExchange.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 8.5f));
        badgeExchange.setForeground(Theme.TXT_SECONDARY);
        badgeExchange.setOpaque(true);
        badgeExchange.setBackground(new Color(0x23, 0x2A, 0x3A));
        badgeExchange.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));

        badgeIsin.setFont(Theme.MONO_TINY.deriveFont(8.5f));
        badgeIsin.setForeground(Theme.TXT_MUTED);
        badgeIsin.setOpaque(true);
        badgeIsin.setBackground(new Color(0x23, 0x2A, 0x3A));
        badgeIsin.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));

        badgeIndex.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 8.5f));
        badgeIndex.setForeground(Theme.ACCENT);
        badgeIndex.setOpaque(true);
        badgeIndex.setBackground(Theme.ACCENT_BG);
        badgeIndex.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));

        badgeStatus.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 8.5f));
        badgeStatus.setForeground(new Color(0x4A, 0xE1, 0x76));
        badgeStatus.setOpaque(true);
        badgeStatus.setBackground(new Color(0, 185, 84, 25));
        badgeStatus.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(new Color(74, 225, 118, 60), 3, 1),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)));

        leftBadges.add(badgeExchange);
        leftBadges.add(badgeIsin);
        leftBadges.add(badgeIndex);
        leftBadges.add(badgeStatus);
        row1.add(leftBadges, BorderLayout.WEST);

        // Right Utility Controls & Execution Triggers
        JPanel rightTools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        rightTools.setOpaque(false);

        btnWatchlist.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 9.5f));
        btnWatchlist.setForeground(Theme.ACCENT);
        btnWatchlist.setBackground(new Color(0x19, 0x1F, 0x2F));
        btnWatchlist.setBorder(new Theme.RoundedBorder(Theme.ACCENT, 4, 1));
        btnWatchlist.setFocusPainted(false);
        btnWatchlist.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnWatchlist.addActionListener(e -> {
            isWatchlisted = !isWatchlisted;
            btnWatchlist.setText(isWatchlisted ? "★ Watchlist" : "☆ + Watchlist");
            btnWatchlist.setForeground(isWatchlisted ? Theme.ACCENT : Theme.TXT_MUTED);
        });

        JButton btnAlert = createToolBtn("🔔 Set Alert", () -> {
            JOptionPane.showMessageDialog(this, "Alert set for " + (currentAsset != null ? currentAsset.getSymbol() : "Asset"),
                    "Price Alert", JOptionPane.INFORMATION_MESSAGE);
        });

        JButton btnCompare = createToolBtn("⇄ Compare", () -> {
            if (onNavigate != null) onNavigate.accept("Markets");
        });

        JButton btnBuyTrigger = new JButton("BUY / LONG");
        btnBuyTrigger.setFont(Theme.BODY.deriveFont(Font.BOLD, 10.5f));
        btnBuyTrigger.setBackground(new Color(0x00, 0xB9, 0x54));
        btnBuyTrigger.setForeground(Color.WHITE);
        btnBuyTrigger.setFocusPainted(false);
        btnBuyTrigger.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnBuyTrigger.setBorder(new Theme.RoundedBorder(new Color(0x00, 0xB9, 0x54), 4, 1));
        btnBuyTrigger.addActionListener(e -> {
            ticketPanel.setSide(OrderSide.BUY);
        });

        JButton btnSellTrigger = new JButton("SELL / SHORT");
        btnSellTrigger.setFont(Theme.BODY.deriveFont(Font.BOLD, 10.5f));
        btnSellTrigger.setBackground(new Color(0xFF, 0x54, 0x51));
        btnSellTrigger.setForeground(Color.WHITE);
        btnSellTrigger.setFocusPainted(false);
        btnSellTrigger.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnSellTrigger.setBorder(new Theme.RoundedBorder(new Color(0xFF, 0x54, 0x51), 4, 1));
        btnSellTrigger.addActionListener(e -> {
            ticketPanel.setSide(OrderSide.SELL);
        });

        rightTools.add(btnWatchlist);
        rightTools.add(btnAlert);
        rightTools.add(btnCompare);
        rightTools.add(btnBuyTrigger);
        rightTools.add(btnSellTrigger);
        row1.add(rightTools, BorderLayout.EAST);

        banner.add(row1);
        banner.add(Box.createVerticalStrut(6));

        // Bottom Row: Asset Title + Industry + Hero CMP Readout
        JPanel row2 = new JPanel(new BorderLayout(8, 0));
        row2.setOpaque(false);

        JPanel leftTitle = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftTitle.setOpaque(false);

        assetTitle.setFont(Theme.TITLE.deriveFont(Font.BOLD, 17f));
        assetTitle.setForeground(Color.WHITE);

        assetSector.setFont(Theme.BODY.deriveFont(11f));
        assetSector.setForeground(Theme.TXT_MUTED);

        leftTitle.add(assetTitle);
        leftTitle.add(assetSector);
        row2.add(leftTitle, BorderLayout.WEST);

        // Price Readout
        JPanel rightPrice = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        rightPrice.setOpaque(false);

        heroCmp.setFont(Theme.MONO_TITLE.deriveFont(Font.BOLD, 18f));
        heroCmp.setForeground(Color.WHITE);

        heroDelta.setFont(Theme.MONO_BODY.deriveFont(Font.BOLD, 12f));
        heroDelta.setForeground(new Color(0x4A, 0xE1, 0x76));

        heroTickTime.setFont(Theme.MONO_TINY.deriveFont(8.5f));
        heroTickTime.setForeground(Theme.TXT_MUTED);

        rightPrice.add(heroCmp);
        rightPrice.add(heroDelta);
        rightPrice.add(heroTickTime);
        row2.add(rightPrice, BorderLayout.EAST);

        banner.add(row2);
        return banner;
    }

    private JButton createToolBtn(String text, Runnable action) {
        JButton b = new JButton(text);
        b.setFont(Theme.MONO_TINY.deriveFont(Font.PLAIN, 9.5f));
        b.setForeground(Theme.TXT_SECONDARY);
        b.setBackground(new Color(0x19, 0x1F, 0x2F));
        b.setBorder(new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 4, 1));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(e -> action.run());
        return b;
    }

    private JPanel buildWorkspace() {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);

        // Scrollable Left Workspace (8.5 Cols)
        JPanel leftCol = new JPanel();
        leftCol.setLayout(new BoxLayout(leftCol, BoxLayout.Y_AXIS));
        leftCol.setOpaque(false);
        leftCol.setBorder(BorderFactory.createEmptyBorder(12, 14, 16, 10));

        // Section 1: Chart
        chartPanel.setPreferredSize(new Dimension(800, 390));
        leftCol.add(chartPanel);
        leftCol.add(Box.createVerticalStrut(14));

        // Section 2: Core Statistics & Dual Range Visualizers
        leftCol.add(buildSection2Statistics());
        leftCol.add(Box.createVerticalStrut(14));

        // Section 3: Screener-Grade Financial Statements & Corporate Overview
        leftCol.add(buildSection3Financials());
        leftCol.add(Box.createVerticalStrut(14));

        // Section 4: Speedometer Gauge & Technical Oscillator Checklist
        leftCol.add(buildSection4TechnicalGauge());
        leftCol.add(Box.createVerticalStrut(14));

        // Section 5: Industry Peers Comparison Table
        leftCol.add(buildSection5Peers());
        leftCol.add(Box.createVerticalStrut(14));

        // Section 6: Real-time News & Filings Feed
        leftCol.add(buildSection6News());

        JScrollPane leftScroll = new JScrollPane(leftCol);
        leftScroll.setOpaque(false);
        leftScroll.getViewport().setOpaque(false);
        leftScroll.setBorder(null);
        leftScroll.getVerticalScrollBar().setUnitIncrement(16);

        // Right Column (3.5 Cols)
        JPanel rightCol = new JPanel(new BorderLayout());
        rightCol.setOpaque(false);
        rightCol.setBorder(BorderFactory.createEmptyBorder(12, 4, 16, 14));
        rightCol.add(ticketPanel, BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftScroll, rightCol);
        split.setOpaque(false);
        split.setBorder(null);
        split.setResizeWeight(0.72);
        split.setDividerSize(4);

        wrap.add(split, BorderLayout.CENTER);
        return wrap;
    }

    private JPanel buildSection2Statistics() {
        JPanel sec = new JPanel();
        sec.setLayout(new BoxLayout(sec, BoxLayout.Y_AXIS));
        sec.setOpaque(true);
        sec.setBackground(Theme.CARD_BG);
        sec.setBorder(BorderFactory.createCompoundBorder(
                Theme.terminalCardBorder(),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)));

        // Header
        JPanel hdr = new JPanel(new BorderLayout());
        hdr.setOpaque(false);
        JLabel title = new JLabel("Key Telemetry & Ratios");
        title.setFont(Theme.HEADING.deriveFont(Font.BOLD, 12.5f));
        title.setForeground(Color.WHITE);

        JLabel sub = new JLabel("Standalone & Consolidated Metrics");
        sub.setFont(Theme.MICRO);
        sub.setForeground(Theme.TXT_MUTED);

        hdr.add(title, BorderLayout.WEST);
        hdr.add(sub, BorderLayout.EAST);
        sec.add(hdr);
        sec.add(Box.createVerticalStrut(10));

        // Dual Range Visualizers Container
        JPanel rangeContainer = new JPanel(new GridLayout(1, 2, 12, 0));
        rangeContainer.setOpaque(true);
        rangeContainer.setBackground(new Color(0x14, 0x1B, 0x2B));
        rangeContainer.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 6, 1),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));

        // 1. Day Range
        JPanel pDay = new JPanel(new BorderLayout(0, 4));
        pDay.setOpaque(false);

        JPanel pDayTop = new JPanel(new BorderLayout());
        pDayTop.setOpaque(false);
        JLabel lDayLow = new JLabel("Day Low: ");
        lDayLow.setFont(Theme.MICRO);
        lDayLow.setForeground(Theme.TXT_MUTED);
        dayLowLbl.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 9.5f));
        dayLowLbl.setForeground(Color.WHITE);
        JPanel pDL = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        pDL.setOpaque(false);
        pDL.add(lDayLow);
        pDL.add(dayLowLbl);

        JLabel lDayTitle = new JLabel("Day Range (L - H)");
        lDayTitle.setFont(Theme.MICRO.deriveFont(Font.BOLD, 8.5f));
        lDayTitle.setForeground(Theme.ACCENT);

        JLabel lDayHigh = new JLabel("Day High: ");
        lDayHigh.setFont(Theme.MICRO);
        lDayHigh.setForeground(Theme.TXT_MUTED);
        dayHighLbl.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 9.5f));
        dayHighLbl.setForeground(Color.WHITE);
        JPanel pDH = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        pDH.setOpaque(false);
        pDH.add(lDayHigh);
        pDH.add(dayHighLbl);

        pDayTop.add(pDL, BorderLayout.WEST);
        pDayTop.add(lDayTitle, BorderLayout.CENTER);
        pDayTop.add(pDH, BorderLayout.EAST);
        pDay.add(pDayTop, BorderLayout.NORTH);

        dayRangeCanvas.setPreferredSize(new Dimension(100, 10));
        pDay.add(dayRangeCanvas, BorderLayout.CENTER);

        dayCmpSpanLbl.setFont(Theme.MONO_TINY.deriveFont(8f));
        dayCmpSpanLbl.setForeground(new Color(0x4A, 0xE1, 0x76));
        dayCmpSpanLbl.setHorizontalAlignment(JLabel.CENTER);
        pDay.add(dayCmpSpanLbl, BorderLayout.SOUTH);
        rangeContainer.add(pDay);

        // 2. 52-Week Range
        JPanel pWeek = new JPanel(new BorderLayout(0, 4));
        pWeek.setOpaque(false);

        JPanel pWeekTop = new JPanel(new BorderLayout());
        pWeekTop.setOpaque(false);
        JLabel lWkLow = new JLabel("52W Low: ");
        lWkLow.setFont(Theme.MICRO);
        lWkLow.setForeground(Theme.TXT_MUTED);
        week52LowLbl.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 9.5f));
        week52LowLbl.setForeground(Color.WHITE);
        JPanel pWL = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        pWL.setOpaque(false);
        pWL.add(lWkLow);
        pWL.add(week52LowLbl);

        JLabel lWkTitle = new JLabel("52-Week Channel");
        lWkTitle.setFont(Theme.MICRO.deriveFont(Font.BOLD, 8.5f));
        lWkTitle.setForeground(Theme.ACCENT);

        JLabel lWkHigh = new JLabel("52W High: ");
        lWkHigh.setFont(Theme.MICRO);
        lWkHigh.setForeground(Theme.TXT_MUTED);
        week52HighLbl.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 9.5f));
        week52HighLbl.setForeground(Color.WHITE);
        JPanel pWH = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        pWH.setOpaque(false);
        pWH.add(lWkHigh);
        pWH.add(week52HighLbl);

        pWeekTop.add(pWL, BorderLayout.WEST);
        pWeekTop.add(lWkTitle, BorderLayout.CENTER);
        pWeekTop.add(pWH, BorderLayout.EAST);
        pWeek.add(pWeekTop, BorderLayout.NORTH);

        week52RangeCanvas.setPreferredSize(new Dimension(100, 10));
        pWeek.add(week52RangeCanvas, BorderLayout.CENTER);

        week52CmpSpanLbl.setFont(Theme.MONO_TINY.deriveFont(8f));
        week52CmpSpanLbl.setForeground(Theme.ACCENT);
        week52CmpSpanLbl.setHorizontalAlignment(JLabel.CENTER);
        pWeek.add(week52CmpSpanLbl, BorderLayout.SOUTH);
        rangeContainer.add(pWeek);

        sec.add(rangeContainer);
        sec.add(Box.createVerticalStrut(10));

        // 8 Fundamental Cards Grid (2x4)
        JPanel grid = new JPanel(new GridLayout(2, 4, 8, 8));
        grid.setOpaque(false);

        grid.add(buildStatTile("PREV. CLOSE", statPrevClose, "Gap-up: +32.75", Theme.TXT_MUTED));
        grid.add(buildStatTile("OPEN PRICE", statOpen, "+1.14% at Open", new Color(0x4A, 0xE1, 0x76)));
        grid.add(buildStatTile("MARKET CAP", statMcap, "Rank #1 in India", Theme.ACCENT));
        grid.add(buildStatTile("VOLUME (20D AVG)", statVol, "1.10x Avg Vol", new Color(0x4A, 0xE1, 0x76)));
        grid.add(buildStatTile("P/E RATIO (TTM)", statPe, "Attractive (-5.3%)", new Color(0x4A, 0xE1, 0x76)));
        grid.add(buildStatTile("BOOK VALUE & P/B", statBook, "Tangible Net Worth", Theme.TXT_MUTED));
        grid.add(buildStatTile("EPS (TTM)", statEps, "+14.2% YoY Growth", new Color(0x4A, 0xE1, 0x76)));
        grid.add(buildStatTile("ROCE / ROE", statRoe, "Stable Return Profile", Theme.TXT_MUTED));

        sec.add(grid);
        return sec;
    }

    private JPanel buildStatTile(String label, JLabel valLbl, String subtext, Color subColor) {
        JPanel tile = new JPanel();
        tile.setLayout(new BoxLayout(tile, BoxLayout.Y_AXIS));
        tile.setOpaque(true);
        tile.setBackground(new Color(0x14, 0x1B, 0x2B));
        tile.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 4, 1),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));

        JLabel l = new JLabel(label);
        l.setFont(Theme.MICRO.deriveFont(Font.BOLD, 8.5f));
        l.setForeground(Theme.TXT_MUTED);

        valLbl.setFont(Theme.MONO_BODY.deriveFont(Font.BOLD, 12f));
        valLbl.setForeground(Color.WHITE);

        JLabel sub = new JLabel(subtext);
        sub.setFont(Theme.MICRO.deriveFont(8f));
        sub.setForeground(subColor);

        tile.add(l);
        tile.add(Box.createVerticalStrut(3));
        tile.add(valLbl);
        tile.add(Box.createVerticalStrut(2));
        tile.add(sub);
        return tile;
    }

    private JPanel buildSection3Financials() {
        JPanel sec = new JPanel(new BorderLayout(0, 8));
        sec.setOpaque(true);
        sec.setBackground(Theme.CARD_BG);
        sec.setBorder(BorderFactory.createCompoundBorder(
                Theme.terminalCardBorder(),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)));

        // Tab Navigation Bar
        JPanel navBar = new JPanel(new BorderLayout());
        navBar.setOpaque(false);
        navBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_CUSTOM));

        JPanel tabGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        tabGroup.setOpaque(false);

        String[] tabNames = {"Overview", "Financial Statements", "Valuation & Solvency", "Shareholding Pattern"};
        for (String t : tabNames) {
            JButton b = new JButton(t);
            b.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 9.5f));
            b.setFocusPainted(false);
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            styleTabButton(b, t.equals("Overview"));

            b.addActionListener(e -> {
                tabsCardLayout.show(tabsContainer, t);
                for (Map.Entry<String, JButton> entry : tabButtons.entrySet()) {
                    styleTabButton(entry.getValue(), entry.getKey().equals(t));
                }
            });

            tabButtons.put(t, b);
            tabGroup.add(b);
        }
        navBar.add(tabGroup, BorderLayout.WEST);

        JLabel unit = new JLabel("Consolidated in INR Cr");
        unit.setFont(Theme.MICRO);
        unit.setForeground(Theme.TXT_MUTED);
        navBar.add(unit, BorderLayout.EAST);
        sec.add(navBar, BorderLayout.NORTH);

        // 4 Panes in CardLayout
        tabsContainer.setOpaque(false);
        tabsContainer.add(buildTab1Overview(), "Overview");
        tabsContainer.add(buildTab2Financials(), "Financial Statements");
        tabsContainer.add(buildTab3Solvency(), "Valuation & Solvency");
        tabsContainer.add(buildTab4Shareholding(), "Shareholding Pattern");
        sec.add(tabsContainer, BorderLayout.CENTER);

        return sec;
    }

    private void styleTabButton(JButton btn, boolean active) {
        if (active) {
            btn.setBackground(Theme.ACCENT);
            btn.setForeground(Color.WHITE);
            btn.setBorder(new Theme.RoundedBorder(Theme.ACCENT, 4, 1));
        } else {
            btn.setBackground(new Color(0x14, 0x1B, 0x2B));
            btn.setForeground(Theme.TXT_MUTED);
            btn.setBorder(new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 4, 1));
        }
    }

    private JPanel buildTab1Overview() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(true);
        p.setBackground(new Color(0x14, 0x1B, 0x2B));
        p.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 6, 1),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));

        JLabel title = new JLabel("CORPORATE PROFILE");
        title.setFont(Theme.MICRO.deriveFont(Font.BOLD, 9f));
        title.setForeground(Theme.ACCENT);
        p.add(title);
        p.add(Box.createVerticalStrut(4));

        JLabel desc = new JLabel("<html><body style='width: 600px; color: #DCE2F7; font-family: Inter; font-size: 11px; line-height: 1.4;'>"
                + "<strong>Reliance Industries Limited (RIL)</strong> is India's largest private sector corporation and a Fortune 500 company. "
                + "Operations encompass hydrocarbon exploration, refining, petrochemicals, telecommunications via <em>Jio Platforms</em>, "
                + "organized retail via <em>Reliance Retail</em>, digital media, and giga-scale new energy manufacturing."
                + "</body></html>");
        p.add(desc);
        p.add(Box.createVerticalStrut(8));

        // Chips
        JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        chips.setOpaque(false);
        for (String c : List.of("CMD: Mukesh D. Ambani", "Incorporated: 1973", "HQ: Maker Chambers IV, Mumbai", "Employees: 347,000+")) {
            JLabel chip = new JLabel(c);
            chip.setFont(Theme.MONO_TINY.deriveFont(8.5f));
            chip.setForeground(Theme.TXT_SECONDARY);
            chip.setOpaque(true);
            chip.setBackground(new Color(0x23, 0x2A, 0x3A));
            chip.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
            chips.add(chip);
        }
        p.add(chips);
        p.add(Box.createVerticalStrut(10));

        // Segment breakdown bars
        JLabel segTitle = new JLabel("EBITDA SHARE BY BUSINESS SEGMENT");
        segTitle.setFont(Theme.MICRO.deriveFont(Font.BOLD, 9f));
        segTitle.setForeground(Theme.TXT_MUTED);
        p.add(segTitle);
        p.add(Box.createVerticalStrut(6));

        JPanel segGrid = new JPanel(new GridLayout(2, 2, 10, 6));
        segGrid.setOpaque(false);
        segGrid.add(buildSegmentBar("Oil to Chemicals (O2C)", 42, new Color(0x4D, 0x8E, 0xFF)));
        segGrid.add(buildSegmentBar("Digital Services (Jio)", 31, new Color(0x4A, 0xE1, 0x76)));
        segGrid.add(buildSegmentBar("Reliance Retail", 21, new Color(0x06, 0xB6, 0xD4)));
        segGrid.add(buildSegmentBar("Oil & Gas Upstream", 6, new Color(0xF5, 0x9E, 0x0B)));
        p.add(segGrid);

        return p;
    }

    private JPanel buildSegmentBar(String name, int pct, Color barColor) {
        JPanel wrap = new JPanel(new BorderLayout(0, 2));
        wrap.setOpaque(false);

        JPanel lbls = new JPanel(new BorderLayout());
        lbls.setOpaque(false);
        JLabel lName = new JLabel(name);
        lName.setFont(Theme.MICRO);
        lName.setForeground(Theme.TXT_SECONDARY);

        JLabel lPct = new JLabel(pct + "%");
        lPct.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 8.5f));
        lPct.setForeground(barColor);

        lbls.add(lName, BorderLayout.WEST);
        lbls.add(lPct, BorderLayout.EAST);
        wrap.add(lbls, BorderLayout.NORTH);

        JProgressBar pb = new JProgressBar(0, 100);
        pb.setValue(pct);
        pb.setForeground(barColor);
        pb.setBackground(new Color(0x23, 0x2A, 0x3A));
        pb.setBorderPainted(false);
        pb.setPreferredSize(new Dimension(100, 4));
        wrap.add(pb, BorderLayout.CENTER);

        return wrap;
    }

    private JPanel buildTab2Financials() {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(true);
        p.setBackground(new Color(0x14, 0x1B, 0x2B));
        p.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 6, 1),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)));

        String[] cols = {"Metric (INR Cr)", "FY21", "FY22", "FY23", "FY24", "TTM"};
        Object[][] data = {
                {"Sales / Revenue", "486,454", "736,429", "892,944", "997,148", "1,032,580"},
                {"Operating Expenses", "405,662", "625,924", "750,698", "820,442", "849,210"},
                {"EBITDA", "80,792", "110,505", "142,246", "176,706", "183,370"},
                {"OPM %", "16.6%", "15.0%", "15.9%", "17.7%", "17.8%"},
                {"Other Income", "16,327", "14,947", "11,838", "15,820", "16,410"},
                {"Depreciation", "26,572", "29,797", "40,303", "50,832", "53,400"},
                {"Net Profit", "53,739", "67,845", "74,088", "79,020", "82,450"},
                {"EPS (INR)", "79.48", "90.24", "98.50", "102.15", "104.22"}
        };

        JTable table = new JTable(data, cols);
        table.setOpaque(false);
        table.setBackground(new Color(0x14, 0x1B, 0x2B));
        table.setForeground(Theme.TXT_PRIMARY);
        table.setFont(Theme.MONO_TINY);
        table.setRowHeight(20);
        table.getTableHeader().setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 8.5f));
        table.getTableHeader().setBackground(new Color(0x0C, 0x12, 0x1E));
        table.getTableHeader().setForeground(Theme.TXT_MUTED);

        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
        rightRenderer.setHorizontalAlignment(JLabel.RIGHT);
        for (int i = 1; i < cols.length; i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(rightRenderer);
        }

        JScrollPane scroll = new JScrollPane(table);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);
        scroll.setPreferredSize(new Dimension(500, 180));
        p.add(scroll, BorderLayout.CENTER);

        return p;
    }

    private JPanel buildTab3Solvency() {
        JPanel p = new JPanel(new GridLayout(2, 3, 8, 8));
        p.setOpaque(true);
        p.setBackground(new Color(0x14, 0x1B, 0x2B));
        p.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 6, 1),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));

        p.add(buildSolvencyTile("ENTERPRISE VALUE (EV)", "₹21.4 Lakh Cr", "Market Value + Net Debt"));
        p.add(buildSolvencyTile("DEBT TO EQUITY", "0.38", "Conservative Leverage"));
        p.add(buildSolvencyTile("INTEREST COVERAGE", "7.42x", "Strong Solvency Cushion"));
        p.add(buildSolvencyTile("EV / EBITDA", "14.2x", "Industry Peer: 16.8x"));
        p.add(buildSolvencyTile("FREE CASH FLOW", "₹24,800 Cr", "After Capex & Growth"));
        p.add(buildSolvencyTile("ALTMAN Z-SCORE", "3.82 (Safe)", "Low Default Probability"));

        return p;
    }

    private JPanel buildSolvencyTile(String title, String val, String sub) {
        JPanel t = new JPanel();
        t.setLayout(new BoxLayout(t, BoxLayout.Y_AXIS));
        t.setOpaque(true);
        t.setBackground(new Color(0x19, 0x22, 0x33));
        t.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 4, 1),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));

        JLabel l1 = new JLabel(title);
        l1.setFont(Theme.MICRO.deriveFont(Font.BOLD, 8f));
        l1.setForeground(Theme.TXT_MUTED);

        JLabel l2 = new JLabel(val);
        l2.setFont(Theme.MONO_BODY.deriveFont(Font.BOLD, 11f));
        l2.setForeground(Color.WHITE);

        JLabel l3 = new JLabel(sub);
        l3.setFont(Theme.MICRO.deriveFont(8f));
        l3.setForeground(Theme.TXT_SECONDARY);

        t.add(l1);
        t.add(Box.createVerticalStrut(2));
        t.add(l2);
        t.add(Box.createVerticalStrut(2));
        t.add(l3);
        return t;
    }

    private JPanel buildTab4Shareholding() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(true);
        p.setBackground(new Color(0x14, 0x1B, 0x2B));
        p.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 6, 1),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));

        JLabel title = new JLabel("LATEST SHAREHOLDING PATTERN (Q2)");
        title.setFont(Theme.MICRO.deriveFont(Font.BOLD, 9f));
        title.setForeground(Theme.TXT_MUTED);
        p.add(title);
        p.add(Box.createVerticalStrut(10));

        JPanel cards = new JPanel(new GridLayout(1, 4, 8, 0));
        cards.setOpaque(false);
        cards.add(buildShareCard("Promoters", "50.31%", new Color(0x4D, 0x8E, 0xFF)));
        cards.add(buildShareCard("FII / FPI", "22.14%", new Color(0x4A, 0xE1, 0x76)));
        cards.add(buildShareCard("DII / MFs", "16.58%", new Color(0x06, 0xB6, 0xD4)));
        cards.add(buildShareCard("Public & Retail", "10.97%", new Color(0xF5, 0x9E, 0x0B)));
        p.add(cards);

        return p;
    }

    private JPanel buildShareCard(String holder, String pct, Color color) {
        JPanel c = new JPanel();
        c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));
        c.setOpaque(true);
        c.setBackground(new Color(0x19, 0x22, 0x33));
        c.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(color, 4, 1),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)));

        JLabel l1 = new JLabel(holder);
        l1.setFont(Theme.MICRO);
        l1.setForeground(Theme.TXT_MUTED);

        JLabel l2 = new JLabel(pct);
        l2.setFont(Theme.MONO_BODY.deriveFont(Font.BOLD, 13f));
        l2.setForeground(color);

        c.add(l1);
        c.add(Box.createVerticalStrut(4));
        c.add(l2);
        return c;
    }

    private JPanel buildSection4TechnicalGauge() {
        JPanel sec = new JPanel(new BorderLayout(12, 0));
        sec.setOpaque(true);
        sec.setBackground(Theme.CARD_BG);
        sec.setBorder(BorderFactory.createCompoundBorder(
                Theme.terminalCardBorder(),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)));

        // Left: Gauge Panel
        gaugePanel.setPreferredSize(new Dimension(300, 160));
        sec.add(gaugePanel, BorderLayout.WEST);

        // Right: Key Oscillator & Momentum Checklist
        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.setOpaque(false);

        JLabel chkTitle = new JLabel("KEY OSCILLATOR & MOMENTUM INDICATORS");
        chkTitle.setFont(Theme.MICRO.deriveFont(Font.BOLD, 9f));
        chkTitle.setForeground(Theme.TXT_MUTED);
        right.add(chkTitle);
        right.add(Box.createVerticalStrut(6));

        JPanel grid = new JPanel(new GridLayout(3, 2, 8, 6));
        grid.setOpaque(false);
        grid.add(buildIndicatorTile("RSI (14)", "64.20 (Bullish)", new Color(0x4A, 0xE1, 0x76)));
        grid.add(buildIndicatorTile("MACD (12,26,9)", "+12.40 (Cross Over)", new Color(0x4A, 0xE1, 0x76)));
        grid.add(buildIndicatorTile("EMA 20", "2,898.40 (Above)", Color.WHITE));
        grid.add(buildIndicatorTile("EMA 50", "2,842.10 (Above)", Color.WHITE));
        grid.add(buildIndicatorTile("EMA 200", "2,670.30 (Strong Bull)", new Color(0x4A, 0xE1, 0x76)));
        grid.add(buildIndicatorTile("Bollinger Bands", "Upper Expanding", Theme.ACCENT));
        right.add(grid);
        right.add(Box.createVerticalStrut(8));

        // Analyst Consensus Strip
        JPanel consensusStrip = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        consensusStrip.setOpaque(true);
        consensusStrip.setBackground(new Color(0x14, 0x1B, 0x2B));
        consensusStrip.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 4, 1),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));

        JLabel conLbl = new JLabel("Analyst Target Median:");
        conLbl.setFont(Theme.MICRO);
        conLbl.setForeground(Theme.TXT_MUTED);

        JLabel conVal = new JLabel("₹3,240.00 (+10.0%)");
        conVal.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 9f));
        conVal.setForeground(new Color(0x4A, 0xE1, 0x76));

        JLabel conCount = new JLabel("• 34 Wall St & Dalal St Analysts");
        conCount.setFont(Theme.MICRO);
        conCount.setForeground(Theme.TXT_SECONDARY);

        consensusStrip.add(conLbl);
        consensusStrip.add(conVal);
        consensusStrip.add(conCount);
        right.add(consensusStrip);

        sec.add(right, BorderLayout.CENTER);
        return sec;
    }

    private JPanel buildIndicatorTile(String name, String signal, Color sigColor) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(true);
        p.setBackground(new Color(0x14, 0x1B, 0x2B));
        p.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 3, 1),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)));

        JLabel lName = new JLabel(name);
        lName.setFont(Theme.MICRO);
        lName.setForeground(Theme.TXT_MUTED);

        JLabel lSig = new JLabel(signal);
        lSig.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 8.5f));
        lSig.setForeground(sigColor);

        p.add(lName, BorderLayout.WEST);
        p.add(lSig, BorderLayout.EAST);
        return p;
    }

    private JPanel buildSection5Peers() {
        JPanel sec = new JPanel(new BorderLayout(0, 8));
        sec.setOpaque(true);
        sec.setBackground(Theme.CARD_BG);
        sec.setBorder(BorderFactory.createCompoundBorder(
                Theme.terminalCardBorder(),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)));

        JPanel hdr = new JPanel(new BorderLayout());
        hdr.setOpaque(false);
        JLabel title = new JLabel("Industry Peer Valuation & Multiples");
        title.setFont(Theme.HEADING.deriveFont(Font.BOLD, 12.5f));
        title.setForeground(Color.WHITE);

        JLabel sub = new JLabel("Energy & Conglomerate Sector");
        sub.setFont(Theme.MICRO);
        sub.setForeground(Theme.TXT_MUTED);

        hdr.add(title, BorderLayout.WEST);
        hdr.add(sub, BorderLayout.EAST);
        sec.add(hdr, BorderLayout.NORTH);

        String[] cols = {"Company", "CMP", "1D %", "P/E", "Market Cap (Cr)", "Action"};
        peersTableModel.setDataVector(new Object[][]{
                {"RELIANCE (Active)", "2,945.50", "+2.37%", "24.80", "1,992,840", "Active"},
                {"ONGC", "268.40", "+1.15%", "6.85", "337,650", "Switch"},
                {"BPCL", "322.10", "-0.48%", "5.12", "139,720", "Switch"},
                {"Adani Enterprises", "3,114.75", "+0.82%", "88.40", "355,081", "Switch"},
                {"Tata Power", "435.60", "+2.94%", "34.20", "139,180", "Switch"}
        }, cols);

        peersTable.setOpaque(false);
        peersTable.setBackground(new Color(0x14, 0x1B, 0x2B));
        peersTable.setForeground(Theme.TXT_PRIMARY);
        peersTable.setFont(Theme.MONO_TINY);
        peersTable.setRowHeight(22);
        peersTable.getTableHeader().setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 8.5f));
        peersTable.getTableHeader().setBackground(new Color(0x0C, 0x12, 0x1E));
        peersTable.getTableHeader().setForeground(Theme.TXT_MUTED);

        peersTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int row = peersTable.getSelectedRow();
                int col = peersTable.getSelectedColumn();
                if (row >= 0 && col == 5) {
                    String comp = peersTableModel.getValueAt(row, 0).toString().split(" ")[0];
                    if (!comp.equalsIgnoreCase("RELIANCE")) {
                        setAssetBySymbol(comp);
                    }
                }
            }
        });

        JScrollPane scroll = new JScrollPane(peersTable);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);
        scroll.setPreferredSize(new Dimension(500, 130));
        sec.add(scroll, BorderLayout.CENTER);

        return sec;
    }

    private JPanel buildSection6News() {
        JPanel sec = new JPanel(new BorderLayout(0, 8));
        sec.setOpaque(true);
        sec.setBackground(Theme.CARD_BG);
        sec.setBorder(BorderFactory.createCompoundBorder(
                Theme.terminalCardBorder(),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)));

        JPanel hdr = new JPanel(new BorderLayout());
        hdr.setOpaque(false);
        JLabel title = new JLabel("Real-time News, Regulatory Filings & Institutional Feeds");
        title.setFont(Theme.HEADING.deriveFont(Font.BOLD, 12.5f));
        title.setForeground(Color.WHITE);

        JLabel sub = new JLabel("Live Feed Active");
        sub.setFont(Theme.MICRO);
        sub.setForeground(new Color(0x4A, 0xE1, 0x76));

        hdr.add(title, BorderLayout.WEST);
        hdr.add(sub, BorderLayout.EAST);
        sec.add(hdr, BorderLayout.NORTH);

        newsContainer.setLayout(new BoxLayout(newsContainer, BoxLayout.Y_AXIS));
        newsContainer.setOpaque(false);

        newsContainer.add(buildNewsItem("BULLISH", new Color(0x4A, 0xE1, 0x76), "Reuters", "18m ago",
                "Reliance Retail completes acquisition of 100% equity stake in quick-commerce logistics network to expand pan-India delivery mesh."));
        newsContainer.add(Box.createVerticalStrut(6));
        newsContainer.add(buildNewsItem("RESEARCH", Theme.ACCENT, "Morgan Stanley", "1h ago",
                "Overweight rating maintained: Target raised to ₹3,350 citing accelerated 5G subscriber monetisation and O2C margin expansion."));
        newsContainer.add(Box.createVerticalStrut(6));
        newsContainer.add(buildNewsItem("FILING", new Color(0xF5, 0x9E, 0x0B), "NSE Disclosures", "3h ago",
                "Board of Directors approves issuance of non-convertible debentures on private placement basis up to ₹10,000 Cr."));

        sec.add(newsContainer, BorderLayout.CENTER);
        return sec;
    }

    private JPanel buildNewsItem(String tag, Color tagColor, String source, String time, String headline) {
        JPanel p = new JPanel(new BorderLayout(8, 0));
        p.setOpaque(true);
        p.setBackground(new Color(0x14, 0x1B, 0x2B));
        p.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 4, 1),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));

        JPanel leftMeta = new JPanel();
        leftMeta.setLayout(new BoxLayout(leftMeta, BoxLayout.Y_AXIS));
        leftMeta.setOpaque(false);
        leftMeta.setPreferredSize(new Dimension(85, 30));

        JLabel lTag = new JLabel(tag);
        lTag.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 8f));
        lTag.setForeground(tagColor);

        JLabel lSrc = new JLabel(source + " • " + time);
        lSrc.setFont(Theme.MICRO.deriveFont(7.5f));
        lSrc.setForeground(Theme.TXT_MUTED);

        leftMeta.add(lTag);
        leftMeta.add(lSrc);
        p.add(leftMeta, BorderLayout.WEST);

        JLabel lHead = new JLabel("<html><body style='width: 500px; color: #DCE2F7; font-family: Inter; font-size: 10.5px;'>" + headline + "</body></html>");
        p.add(lHead, BorderLayout.CENTER);

        return p;
    }

    public void setAssetBySymbol(String symbol) {
        if (symbol == null) return;
        Optional<Asset> a = marketService.findBySymbol(symbol);
        a.ifPresent(this::setAsset);
    }

    public void setAsset(Asset asset) {
        this.currentAsset = asset;
        if (asset == null) return;

        boolean isIndian = isIndianAsset(asset.getSymbol());
        String cur = isIndian ? "₹" : "$";

        // Breadcrumbs & Badges
        breadcrumbCategory.setText(isIndian ? "NSE Equities" : (asset.getType() + " Markets"));
        breadcrumbSymbol.setText(asset.getSymbol());

        badgeExchange.setText((isIndian ? "NSE: " : "EXCH: ") + asset.getSymbol());
        badgeIsin.setText("ISIN: " + (isIndian ? ("INE00" + Math.abs(asset.getSymbol().hashCode() % 90000 + 10000)) : "US" + Math.abs(asset.getSymbol().hashCode())));
        badgeIndex.setText(isIndian ? "NIFTY 50 • Large Cap" : "Top Tier • Liquid");
        badgeStatus.setText(isIndian ? "MARKET OPEN 09:15-15:30 IST" : "24/7 ACTIVE FEED");

        // Title & Sector
        assetTitle.setText(asset.getName());
        assetSector.setText(asset.getType() + " / Primary Financial Instrument");

        // Price Hero
        heroCmp.setText(String.format(Locale.US, "%s%,.2f", cur, asset.getPrice()));
        double chg = asset.getChangePercent();
        String sign = chg >= 0 ? "+" : "";
        double diff = asset.getPrice() - asset.getPreviousClose();
        heroDelta.setText(String.format(Locale.US, "%s%s%,.2f (%s%.2f%%)", sign, cur, diff, sign, chg));
        heroDelta.setForeground(chg >= 0 ? new Color(0x4A, 0xE1, 0x76) : new Color(0xFF, 0x54, 0x51));
        heroTickTime.setText("Live Tick: " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + " IST");

        // Range visualizers
        double cmp = asset.getPrice();
        double dayLow = cmp * 0.985;
        double dayHigh = cmp * 1.018;
        dayLowLbl.setText(String.format(Locale.US, "%s%,.2f", cur, dayLow));
        dayHighLbl.setText(String.format(Locale.US, "%s%,.2f", cur, dayHigh));
        double dayFrac = (cmp - dayLow) / (dayHigh - dayLow);
        dayRangeCanvas.setFraction(dayFrac);
        dayCmpSpanLbl.setText(String.format(Locale.US, "CMP: %s%,.2f (%.0f%% of Day Span)", cur, cmp, dayFrac * 100.0));

        double wkLow = cmp * 0.75;
        double wkHigh = cmp * 1.15;
        week52LowLbl.setText(String.format(Locale.US, "%s%,.2f", cur, wkLow));
        week52HighLbl.setText(String.format(Locale.US, "%s%,.2f", cur, wkHigh));
        double wkFrac = (cmp - wkLow) / (wkHigh - wkLow);
        week52RangeCanvas.setFraction(wkFrac);
        week52CmpSpanLbl.setText(String.format(Locale.US, "%.1f%% of 52W Channel", wkFrac * 100.0));

        // 8 Fundamental Cards
        statPrevClose.setText(String.format(Locale.US, "%s%,.2f", cur, asset.getPreviousClose()));
        statOpen.setText(String.format(Locale.US, "%s%,.2f", cur, asset.getPreviousClose() * 1.008));
        statMcap.setText(isIndian ? "19.92 Lakh Cr" : "$2.84T");
        statVol.setText("6.42M");
        statPe.setText(String.format(Locale.US, "%.1f", 22.0 + (cmp % 10.0)));
        statBook.setText(String.format(Locale.US, "%s%,.2f", cur, cmp * 0.40));
        statEps.setText(String.format(Locale.US, "%.2f", cmp / 28.0));
        statRoe.setText("11.8% / 9.6%");

        // Pass to sub-components
        chartPanel.setAsset(asset);
        ticketPanel.setAsset(asset);

        // Technical Consensus Rating
        boolean bullish = chg >= 0;
        double rating = bullish ? 0.82 : 0.32;
        String verdict = bullish ? "STRONG BUY" : "SELL";
        int buys = bullish ? 14 : 4;
        int neutrals = 4;
        int sells = bullish ? 2 : 12;
        gaugePanel.setTechnicalRating(rating, verdict, buys, neutrals, sells);

        revalidate();
        repaint();
    }

    /** Connects the candlestick chart to real market history. */
    public void setChartService(ChartService chartService) {
        chartPanel.setChartService(chartService);
    }

    @Override
    public void refresh() {
        if (currentAsset != null) {
            marketService.findBySymbol(currentAsset.getSymbol()).ifPresent(this::setAsset);
        }
        ticketPanel.updateCalculations();
    }

    private boolean isIndianAsset(String sym) {
        if (sym == null) return false;
        return List.of("RELIANCE", "TCS", "HDFCBANK", "INFY", "ICICIBANK", "BHARTIARTL",
                "SBIN", "ITC", "LT", "TATAMOTORS", "TRENT", "ETERNAL").contains(sym.toUpperCase());
    }

    // Custom Canvas for sleek gradient progress bar with pin marker
    private static class RangeBarCanvas extends JPanel {
        private double fraction;
        private final Color colStart;
        private final Color colEnd;

        RangeBarCanvas(double fraction, Color colStart, Color colEnd) {
            this.fraction = fraction;
            this.colStart = colStart;
            this.colEnd = colEnd;
            setOpaque(false);
        }

        void setFraction(double f) {
            this.fraction = Math.max(0.0, Math.min(1.0, f));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int barY = h / 2 - 2;
            int barH = 5;

            // Track
            g2.setColor(new Color(0x23, 0x2A, 0x3A));
            g2.fillRoundRect(0, barY, w, barH, 4, 4);

            // Filled Gradient Progress
            int fillW = (int) (w * fraction);
            GradientPaint gp = new GradientPaint(0, barY, colStart, fillW, barY, colEnd);
            g2.setPaint(gp);
            g2.fillRoundRect(0, barY, fillW, barH, 4, 4);

            // Pin marker
            int pinX = Math.max(4, Math.min(w - 4, fillW));
            g2.setColor(Color.WHITE);
            g2.fillOval(pinX - 3, h / 2 - 4, 8, 8);
            g2.setColor(colEnd);
            g2.drawOval(pinX - 3, h / 2 - 4, 8, 8);

            g2.dispose();
        }
    }
}
