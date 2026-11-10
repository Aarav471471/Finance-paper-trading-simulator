package com.papertrader.ui;

import com.papertrader.service.ChartService;
import com.papertrader.feed.YahooSymbols;
import com.papertrader.model.Asset;
import com.papertrader.model.FinancialYear;
import com.papertrader.model.Fundamentals;
import com.papertrader.model.MarketSnapshot;
import com.papertrader.model.NewsItem;
import com.papertrader.service.MarketDataService;
import com.papertrader.strategy.TechnicalAnalysis;
import com.papertrader.util.MarketFormat;
import com.papertrader.util.MarketHours;
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
    private final JLabel heroCmp = new JLabel("N/A");
    private final JLabel heroDelta = new JLabel("-");
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
    private final JLabel statVol = new JLabel("N/A");
    private final JLabel statPe = new JLabel("24.8");
    private final JLabel statBook = new JLabel("1,180.40");
    private final JLabel statEps = new JLabel("104.22");
    private final JLabel statRoe = new JLabel("11.8% / 9.6%");

    // Sub-texts under the 8 cards (filled with real numbers in setAsset)
    private final JLabel[] statSubs = new JLabel[8];

    // Real data source (day stats, fundamentals, news); set from MainFrame
    private MarketDataService marketData;
    private Fundamentals shownFundamentals;
    private List<NewsItem> shownNews;
    private String shownPeersFor;

    // Section 3 dynamic parts
    private final JLabel overviewDesc = new JLabel();
    private final JPanel overviewChips = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    private final JPanel overviewBars = new JPanel(new GridLayout(2, 2, 10, 6));
    private final JLabel unitLbl = new JLabel("Consolidated in INR Cr");
    private final DefaultTableModel financialsModel = new DefaultTableModel() {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JLabel[] solvencyVals = new JLabel[6];
    private final JLabel[] solvencySubs = new JLabel[6];
    private final JLabel[] shareVals = new JLabel[4];
    private final JLabel shareTitle = new JLabel("LATEST SHAREHOLDING PATTERN");

    // Section 4 dynamic parts
    private final JLabel[] indicatorVals = new JLabel[6];
    private final JLabel analystVal = new JLabel(MarketFormat.NA);
    private final JLabel analystCount = new JLabel("");

    // Section 3: Screener-Grade Financials (Tabbed)
    private final CardLayout tabsCardLayout = new CardLayout();
    private final JPanel tabsContainer = new JPanel(tabsCardLayout);
    private final Map<String, JButton> tabButtons = new LinkedHashMap<>();

    // Section 4: Gauge & Checklist
    private final ConsensusGaugePanel gaugePanel = new ConsensusGaugePanel();

    // Section 5: Peer Table
    private final DefaultTableModel peersTableModel = new DefaultTableModel();
    private final JTable peersTable = new JTable(peersTableModel);
    private final JLabel peersSubLbl = new JLabel("");

    // Section 6: News List
    private final JPanel newsContainer = new JPanel();
    private final JLabel newsSubLbl = new JLabel("Yahoo Finance");

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

        String[] titles = {"PREV. CLOSE", "OPEN PRICE", "MARKET CAP", "VOLUME (TODAY)",
                "P/E RATIO (TTM)", "BOOK VALUE & P/B", "EPS (TTM)", "ROE / ROA"};
        JLabel[] values = {statPrevClose, statOpen, statMcap, statVol, statPe, statBook, statEps, statRoe};
        for (int i = 0; i < 8; i++) {
            statSubs[i] = new JLabel(" ");
            grid.add(buildStatTile(titles[i], values[i], statSubs[i]));
        }

        sec.add(grid);
        return sec;
    }

    private JPanel buildStatTile(String label, JLabel valLbl, JLabel sub) {
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

        sub.setFont(Theme.MICRO.deriveFont(8f));
        sub.setForeground(Theme.TXT_MUTED);

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

        unitLbl.setFont(Theme.MICRO);
        unitLbl.setForeground(Theme.TXT_MUTED);
        navBar.add(unitLbl, BorderLayout.EAST);
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

        overviewDesc.setText(descriptionHtml("Loading company profile..."));
        p.add(overviewDesc);
        p.add(Box.createVerticalStrut(8));

        // Chips (CEO, sector, HQ, employees) - filled from Yahoo Finance
        overviewChips.setOpaque(false);
        overviewChips.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(overviewChips);
        p.add(Box.createVerticalStrut(10));

        // Profitability bars
        JLabel segTitle = new JLabel("KEY PROFITABILITY RATIOS");
        segTitle.setFont(Theme.MICRO.deriveFont(Font.BOLD, 9f));
        segTitle.setForeground(Theme.TXT_MUTED);
        p.add(segTitle);
        p.add(Box.createVerticalStrut(6));

        overviewBars.setOpaque(false);
        overviewBars.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(overviewBars);

        return p;
    }

    private static String descriptionHtml(String text) {
        String safe = text.replace("&", "&amp;").replace("<", "&lt;");
        if (safe.length() > 600) {
            safe = safe.substring(0, 600) + "...";
        }
        return "<html><body style='width: 600px; color: #DCE2F7; font-family: Inter; font-size: 11px; line-height: 1.4;'>"
                + safe + "</body></html>";
    }

    private JLabel chip(String text) {
        JLabel chip = new JLabel(text);
        chip.setFont(Theme.MONO_TINY.deriveFont(8.5f));
        chip.setForeground(Theme.TXT_SECONDARY);
        chip.setOpaque(true);
        chip.setBackground(new Color(0x23, 0x2A, 0x3A));
        chip.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
        return chip;
    }

    private JPanel buildSegmentBar(String name, int pct, Color barColor) {
        JPanel wrap = new JPanel(new BorderLayout(0, 2));
        wrap.setOpaque(false);

        JPanel lbls = new JPanel(new BorderLayout());
        lbls.setOpaque(false);
        JLabel lName = new JLabel(name);
        lName.setFont(Theme.MICRO);
        lName.setForeground(Theme.TXT_SECONDARY);

        JLabel lPct = new JLabel(pct < 0 ? MarketFormat.NA : pct + "%");
        lPct.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 8.5f));
        lPct.setForeground(barColor);

        lbls.add(lName, BorderLayout.WEST);
        lbls.add(lPct, BorderLayout.EAST);
        wrap.add(lbls, BorderLayout.NORTH);

        JProgressBar pb = new JProgressBar(0, 100);
        pb.setValue(Math.max(0, Math.min(100, pct)));
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

        // filled with the real yearly income statements in applyFundamentals()
        financialsModel.setDataVector(new Object[][]{{"Loading...", ""}}, new Object[]{"Metric", ""});
        JTable table = new JTable(financialsModel);
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
        table.setDefaultRenderer(Object.class, rightRenderer);

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

        String[] titles = {"ENTERPRISE VALUE (EV)", "DEBT TO EQUITY", "INTEREST COVERAGE",
                "EV / EBITDA", "FREE CASH FLOW", "NET PROFIT MARGIN"};
        for (int i = 0; i < 6; i++) {
            solvencyVals[i] = new JLabel(MarketFormat.NA);
            solvencySubs[i] = new JLabel(" ");
            p.add(buildSolvencyTile(titles[i], solvencyVals[i], solvencySubs[i]));
        }

        return p;
    }

    private JPanel buildSolvencyTile(String title, JLabel l2, JLabel l3) {
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

        l2.setFont(Theme.MONO_BODY.deriveFont(Font.BOLD, 11f));
        l2.setForeground(Color.WHITE);

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

        shareTitle.setFont(Theme.MICRO.deriveFont(Font.BOLD, 9f));
        shareTitle.setForeground(Theme.TXT_MUTED);
        p.add(shareTitle);
        p.add(Box.createVerticalStrut(10));

        JPanel cards = new JPanel(new GridLayout(1, 4, 8, 0));
        cards.setOpaque(false);
        String[] holders = {"Insiders / Promoters", "Institutions", "No. of Institutions", "Public & Retail"};
        Color[] colors = {new Color(0x4D, 0x8E, 0xFF), new Color(0x4A, 0xE1, 0x76),
                new Color(0x06, 0xB6, 0xD4), new Color(0xF5, 0x9E, 0x0B)};
        for (int i = 0; i < 4; i++) {
            shareVals[i] = new JLabel(MarketFormat.NA);
            cards.add(buildShareCard(holders[i], shareVals[i], colors[i]));
        }
        p.add(cards);

        return p;
    }

    private JPanel buildShareCard(String holder, JLabel l2, Color color) {
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
        String[] names = {"RSI (14)", "MACD (12,26,9)", "EMA 20", "EMA 50", "EMA 200", "Bollinger Bands"};
        for (int i = 0; i < 6; i++) {
            indicatorVals[i] = new JLabel("loading...");
            grid.add(buildIndicatorTile(names[i], indicatorVals[i]));
        }
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

        JLabel conVal = analystVal;
        conVal.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 9f));
        conVal.setForeground(new Color(0x4A, 0xE1, 0x76));

        JLabel conCount = analystCount;
        conCount.setFont(Theme.MICRO);
        conCount.setForeground(Theme.TXT_SECONDARY);

        consensusStrip.add(conLbl);
        consensusStrip.add(conVal);
        consensusStrip.add(conCount);
        right.add(consensusStrip);

        sec.add(right, BorderLayout.CENTER);
        return sec;
    }

    private JPanel buildIndicatorTile(String name, JLabel lSig) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(true);
        p.setBackground(new Color(0x14, 0x1B, 0x2B));
        p.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 3, 1),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)));

        JLabel lName = new JLabel(name);
        lName.setFont(Theme.MICRO);
        lName.setForeground(Theme.TXT_MUTED);

        lSig.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 8.5f));
        lSig.setForeground(Color.WHITE);

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

        peersSubLbl.setFont(Theme.MICRO);
        peersSubLbl.setForeground(Theme.TXT_MUTED);

        hdr.add(title, BorderLayout.WEST);
        hdr.add(peersSubLbl, BorderLayout.EAST);
        sec.add(hdr, BorderLayout.NORTH);

        peersTableModel.setColumnIdentifiers(new Object[]{"Company", "CMP", "1D %", "P/E", "Market Cap", "Action"});

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
                    if (currentAsset == null || !comp.equalsIgnoreCase(currentAsset.getSymbol())) {
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

        newsSubLbl.setFont(Theme.MICRO);
        newsSubLbl.setForeground(new Color(0x4A, 0xE1, 0x76));

        hdr.add(title, BorderLayout.WEST);
        hdr.add(newsSubLbl, BorderLayout.EAST);
        sec.add(hdr, BorderLayout.NORTH);

        newsContainer.setLayout(new BoxLayout(newsContainer, BoxLayout.Y_AXIS));
        newsContainer.setOpaque(false);

        newsContainer.add(emptyNote("Loading the latest headlines..."));

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
        badgeIsin.setText("YAHOO: " + YahooSymbols.of(asset));
        badgeIndex.setText(asset.getType());
        badgeStatus.setText(MarketHours.status(asset));

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
        heroTickTime.setText("Live Tick: " + java.time.ZonedDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss z")));

        // Pass to sub-components
        chartPanel.setAsset(asset);
        ticketPanel.setAsset(asset);

        if (marketData != null) {
            applyDayNumbers(asset, cur);
            applyTechnicals(asset, cur);
            applyFundamentals(asset, cur, marketData.fundamentals(asset).orElse(null));
            applyPeers(asset);
            applyNews(marketData.news(asset));
        }

        revalidate();
        repaint();
    }

    /** Gives the page its real market numbers (day range, P/E, news...). */
    public void setMarketData(MarketDataService marketData) {
        this.marketData = marketData;
        if (currentAsset != null) {
            setAsset(currentAsset);
        }
    }

    /** Connects the candlestick chart to real market history. */
    public void setChartService(ChartService chartService) {
        chartPanel.setChartService(chartService);
    }

    // ------------------------------------------------------------------ real numbers

    /** Day range, 52-week range, previous close, open and volume. */
    private void applyDayNumbers(Asset asset, String cur) {
        MarketSnapshot snap = marketData.snapshot(asset);
        double cmp = asset.getPrice();

        dayLowLbl.setText(MarketFormat.price(cur, snap.dayLow()));
        dayHighLbl.setText(MarketFormat.price(cur, snap.dayHigh()));
        double dayFrac = fraction(cmp, snap.dayLow(), snap.dayHigh());
        dayRangeCanvas.setFraction(MarketFormat.known(dayFrac) ? dayFrac : 0);
        dayCmpSpanLbl.setText(MarketFormat.known(dayFrac)
                ? String.format(Locale.US, "CMP: %s%,.2f (%.0f%% of Day Span)", cur, cmp, dayFrac * 100.0)
                : "Day range loading...");

        week52LowLbl.setText(MarketFormat.price(cur, snap.week52Low()));
        week52HighLbl.setText(MarketFormat.price(cur, snap.week52High()));
        double wkFrac = fraction(cmp, snap.week52Low(), snap.week52High());
        week52RangeCanvas.setFraction(MarketFormat.known(wkFrac) ? wkFrac : 0);
        week52CmpSpanLbl.setText(MarketFormat.known(wkFrac)
                ? String.format(Locale.US, "%.1f%% of 52W Channel", wkFrac * 100.0) : "52-week range loading...");

        statPrevClose.setText(MarketFormat.price(cur, asset.getPreviousClose()));
        statSubs[0].setText(String.format(Locale.US, "Change since: %+,.2f", cmp - asset.getPreviousClose()));
        statOpen.setText(MarketFormat.price(cur, snap.open()));
        statSubs[1].setText(MarketFormat.known(snap.open()) && asset.getPreviousClose() > 0
                ? String.format(Locale.US, "%+.2f%% gap at open", (snap.open() / asset.getPreviousClose() - 1) * 100)
                : "Source: " + snap.source());
        statVol.setText(MarketFormat.volume(snap.volume()));
        double avgVol = marketData.averageVolume(asset, 20);
        statSubs[3].setText(MarketFormat.known(avgVol) && avgVol > 0 && MarketFormat.known(snap.volume())
                ? String.format(Locale.US, "%.2fx 20-day avg", snap.volume() / avgVol) : "20-day avg: N/A");
    }

    private static double fraction(double value, double low, double high) {
        if (!MarketFormat.known(low) || !MarketFormat.known(high) || high <= low) {
            return Double.NaN;
        }
        return Math.max(0, Math.min(1, (value - low) / (high - low)));
    }

    /** Gauge and indicator tiles, calculated from one year of daily candles. */
    private void applyTechnicals(Asset asset, String cur) {
        TechnicalAnalysis.Report t = marketData.technicals(asset);
        if (t == null) {
            return;   // candles still loading
        }
        gaugePanel.setTechnicalRating(t.score(), t.verdict(), t.buys(), t.neutrals(), t.sells());
        double last = asset.getPrice();

        String rsiWord = !MarketFormat.known(t.rsi()) ? "" : t.rsi() > 70 ? " (Overbought)" : t.rsi() < 30 ? " (Oversold)"
                : t.rsi() >= 50 ? " (Bullish)" : " (Bearish)";
        setIndicator(0, MarketFormat.number(t.rsi(), 2) + rsiWord, t.rsi() >= 50 ? Theme.GAIN : Theme.LOSS);
        boolean macdUp = t.macd() > t.macdSignal();
        setIndicator(1, MarketFormat.known(t.macd())
                ? String.format(Locale.US, "%+.2f (%s signal)", t.macd(), macdUp ? "above" : "below") : MarketFormat.NA,
                macdUp ? Theme.GAIN : Theme.LOSS);
        setIndicator(2, emaText(cur, t.ema20(), last), last > t.ema20() ? Theme.GAIN : Theme.LOSS);
        setIndicator(3, emaText(cur, t.ema50(), last), last > t.ema50() ? Theme.GAIN : Theme.LOSS);
        setIndicator(4, emaText(cur, t.ema200(), last), last > t.ema200() ? Theme.GAIN : Theme.LOSS);
        String band = !MarketFormat.known(t.bollingerUpper()) ? MarketFormat.NA
                : last > t.bollingerUpper() ? "Above upper band"
                : last < t.bollingerLower() ? "Below lower band"
                : String.format(Locale.US, "%,.0f - %,.0f", t.bollingerLower(), t.bollingerUpper());
        setIndicator(5, band, Theme.ACCENT);
    }

    private void setIndicator(int i, String text, Color color) {
        indicatorVals[i].setText(text);
        indicatorVals[i].setForeground(text.equals(MarketFormat.NA) ? Theme.TXT_MUTED : color);
    }

    private static String emaText(String cur, double ema, double last) {
        return MarketFormat.known(ema)
                ? String.format(Locale.US, "%s%,.2f (%s)", cur, ema, last > ema ? "Above" : "Below") : MarketFormat.NA;
    }

    /** Company facts from Yahoo Finance; every missing number shows "N/A". */
    private void applyFundamentals(Asset asset, String cur, Fundamentals f) {
        double cmp = asset.getPrice();
        boolean indian = "₹".equals(cur);
        unitLbl.setText(indian ? "Figures in ₹ Crore (Yahoo Finance)" : "Figures in $ Million (Yahoo Finance)");

        if (f == null) {
            String why = marketData.isOnline() ? "Loading..." : "Offline: company data needs internet";
            statMcap.setText(MarketFormat.NA);
            statPe.setText(MarketFormat.NA);
            statBook.setText(MarketFormat.NA);
            statEps.setText(MarketFormat.NA);
            statRoe.setText(MarketFormat.NA);
            for (int i : new int[]{2, 4, 5, 6, 7}) {
                statSubs[i].setText(why);
            }
            if (shownFundamentals != null || overviewChips.getComponentCount() == 0) {
                shownFundamentals = null;
                overviewDesc.setText(descriptionHtml(why));
                overviewChips.removeAll();
                overviewChips.add(chip(asset.getType()));
                overviewBars.removeAll();
                financialsModel.setDataVector(new Object[][]{{why, ""}}, new Object[]{"Metric", ""});
                for (int i = 0; i < 6; i++) {
                    solvencyVals[i].setText(MarketFormat.NA);
                    solvencySubs[i].setText(" ");
                }
                for (JLabel l : shareVals) {
                    l.setText(MarketFormat.NA);
                }
                analystVal.setText(MarketFormat.NA);
                analystCount.setText("");
            }
            return;
        }

        // cards that depend on the live price are updated every second
        statMcap.setText(MarketFormat.big(cur, f.marketCap()));
        statSubs[2].setText(f.sector().isEmpty() ? asset.getType() : f.sector());
        statPe.setText(MarketFormat.number(f.peRatio(), 1));
        statSubs[4].setText(MarketFormat.known(f.eps()) && f.eps() > 0
                ? String.format(Locale.US, "Price %s%,.0f / EPS %,.2f", cur, cmp, f.eps()) : "Trailing 12 months");
        statBook.setText(MarketFormat.price(cur, f.bookValue()));
        statSubs[5].setText(MarketFormat.known(f.bookValue()) && f.bookValue() > 0
                ? String.format(Locale.US, "P/B %.2fx", cmp / f.bookValue()) : "P/B: N/A");
        statEps.setText(MarketFormat.number(f.eps(), 2));
        statSubs[6].setText("Earnings per share");
        statRoe.setText(MarketFormat.pct(f.returnOnEquity()) + " / " + MarketFormat.pct(f.returnOnAssets()));
        statSubs[7].setText("Return on equity / assets");
        if (MarketFormat.known(f.targetMedianPrice()) && f.targetMedianPrice() > 0) {
            analystVal.setText(String.format(Locale.US, "%s%,.2f (%+.1f%%)", cur, f.targetMedianPrice(),
                    (f.targetMedianPrice() / cmp - 1) * 100));
            analystVal.setForeground(f.targetMedianPrice() >= cmp ? Theme.GAIN : Theme.LOSS);
            analystCount.setText("• " + f.analystCount() + " analysts"
                    + (f.recommendation().isEmpty() ? "" : " • consensus: " + f.recommendation().toUpperCase()));
        } else {
            analystVal.setText(MarketFormat.NA);
            analystCount.setText("• no analyst coverage reported");
        }

        if (f == shownFundamentals) {
            return;   // the tabs below only change when new data arrives
        }
        shownFundamentals = f;

        overviewDesc.setText(descriptionHtml(f.description().isEmpty() ? "No company description available." : f.description()));
        overviewChips.removeAll();
        if (!f.ceo().isEmpty()) overviewChips.add(chip("Leader: " + f.ceo()));
        if (!f.industry().isEmpty()) overviewChips.add(chip("Industry: " + f.industry()));
        if (!f.city().isEmpty()) overviewChips.add(chip("HQ: " + f.city() + (f.country().isEmpty() ? "" : ", " + f.country())));
        if (f.employees() > 0) overviewChips.add(chip(String.format(Locale.US, "Employees: %,d", f.employees())));

        double grossMargin = Double.NaN;
        if (!f.years().isEmpty() && f.years().get(0).revenue() > 0) {
            grossMargin = f.years().get(0).grossProfit() / f.years().get(0).revenue();
        }
        overviewBars.removeAll();
        overviewBars.add(buildSegmentBar("Net Profit Margin", percentOrMinus(f.profitMargin()), new Color(0x4D, 0x8E, 0xFF)));
        overviewBars.add(buildSegmentBar("Return on Equity", percentOrMinus(f.returnOnEquity()), new Color(0x4A, 0xE1, 0x76)));
        overviewBars.add(buildSegmentBar("Return on Assets", percentOrMinus(f.returnOnAssets()), new Color(0x06, 0xB6, 0xD4)));
        overviewBars.add(buildSegmentBar("Gross Margin (latest FY)", percentOrMinus(grossMargin), new Color(0xF5, 0x9E, 0x0B)));

        fillFinancials(f, indian);

        solvencyVals[0].setText(MarketFormat.big(cur, f.enterpriseValue()));
        solvencySubs[0].setText("Market value + net debt");
        solvencyVals[1].setText(MarketFormat.number(f.debtToEquity(), 2));
        solvencySubs[1].setText(!MarketFormat.known(f.debtToEquity()) ? " " : f.debtToEquity() < 0.5 ? "Low leverage"
                : f.debtToEquity() < 1.5 ? "Moderate leverage" : "High leverage");
        double cover = f.interestCoverage();
        solvencyVals[2].setText(MarketFormat.known(cover) ? String.format(Locale.US, "%.2fx", cover) : MarketFormat.NA);
        solvencySubs[2].setText("EBIT / interest paid");
        solvencyVals[3].setText(MarketFormat.known(f.evToEbitda()) ? String.format(Locale.US, "%.1fx", f.evToEbitda()) : MarketFormat.NA);
        solvencySubs[3].setText("Years of EBITDA to buy the firm");
        solvencyVals[4].setText(MarketFormat.big(cur, f.freeCashFlow()));
        solvencySubs[4].setText("Cash left after capex");
        solvencyVals[5].setText(MarketFormat.pct(f.profitMargin()));
        solvencySubs[5].setText("Profit per 100 of revenue");

        shareTitle.setText("MAJOR HOLDERS (Yahoo Finance)");
        shareVals[0].setText(MarketFormat.pct(f.insidersPercent()));
        shareVals[1].setText(MarketFormat.pct(f.institutionsPercent()));
        shareVals[2].setText(f.institutionsCount() > 0 ? String.format(Locale.US, "%,d", f.institutionsCount()) : MarketFormat.NA);
        double pub = 1 - (MarketFormat.known(f.insidersPercent()) ? f.insidersPercent() : 0)
                - (MarketFormat.known(f.institutionsPercent()) ? f.institutionsPercent() : 0);
        shareVals[3].setText(MarketFormat.known(f.insidersPercent()) || MarketFormat.known(f.institutionsPercent())
                ? MarketFormat.pct(Math.max(0, pub)) : MarketFormat.NA);

        revalidate();
    }

    private static int percentOrMinus(double fraction) {
        return MarketFormat.known(fraction) ? (int) Math.round(fraction * 100) : -1;
    }

    /** Income statement table: one column per financial year (newest first), in crore or million. */
    private void fillFinancials(Fundamentals f, boolean indian) {
        if (f.years().isEmpty()) {
            financialsModel.setDataVector(new Object[][]{{"Yahoo reports no yearly statements", ""}}, new Object[]{"Metric", ""});
            return;
        }
        double unit = indian ? 1e7 : 1e6;
        List<FinancialYear> years = f.years();
        Object[] header = new Object[years.size() + 1];
        header[0] = indian ? "Metric (₹ Cr)" : "Metric ($ M)";
        for (int i = 0; i < years.size(); i++) {
            header[i + 1] = years.get(i).year();
        }
        String[] names = {"Revenue", "Cost of Revenue", "Gross Profit", "Operating Income", "EBIT",
                "Interest Expense", "Net Income", "Net Margin %"};
        Object[][] rows = new Object[names.length][years.size() + 1];
        for (int r = 0; r < names.length; r++) {
            rows[r][0] = names[r];
            for (int c = 0; c < years.size(); c++) {
                FinancialYear y = years.get(c);
                double v = switch (r) {
                    case 0 -> y.revenue();
                    case 1 -> y.costOfRevenue();
                    case 2 -> y.grossProfit();
                    case 3 -> y.operatingIncome();
                    case 4 -> y.ebit();
                    case 5 -> y.interestExpense();
                    case 6 -> y.netIncome();
                    default -> y.revenue() > 0 ? y.netIncome() / y.revenue() : Double.NaN;
                };
                rows[r][c + 1] = r == 7 ? MarketFormat.pct(v) : MarketFormat.number(v / unit, 0);
            }
        }
        financialsModel.setDataVector(rows, header);
    }

    /** Other assets of the same sector (or type) from the app's list, with live price and change. */
    private void applyPeers(Asset asset) {
        String group = groupOf(asset);
        List<Asset> peers = new ArrayList<>();
        peers.add(asset);
        for (Asset a : marketService.getAllAssets()) {
            if (a != asset && groupOf(a).equalsIgnoreCase(group) && a.getClass() == asset.getClass()) {
                peers.add(a);
            }
        }
        if (peers.size() < 3) {   // too few in the same sector: fill up with the same asset type
            for (Asset a : marketService.getAllAssets()) {
                if (!peers.contains(a) && a.getType().equals(asset.getType()) && peers.size() < 5) {
                    peers.add(a);
                }
            }
        }
        peersSubLbl.setText(group + " • from the app's market list");
        Object[][] rows = new Object[Math.min(5, peers.size())][];
        for (int i = 0; i < rows.length; i++) {
            Asset a = peers.get(i);
            String c = MarketFormat.currency(a.getSymbol());
            Fundamentals pf = marketData.fundamentals(a).orElse(null);
            rows[i] = new Object[]{a.getSymbol() + (a == asset ? " (Active)" : ""),
                    String.format(Locale.US, "%s%,.2f", c, a.getPrice()),
                    String.format(Locale.US, "%+.2f%%", a.getChangePercent()),
                    pf == null ? MarketFormat.NA : MarketFormat.number(pf.peRatio(), 2),
                    pf == null ? MarketFormat.NA : MarketFormat.big(c, pf.marketCap()),
                    a == asset ? "Active" : "Switch"};
        }
        String key = asset.getSymbol() + rows.length;
        if (!key.equals(shownPeersFor) || peersTableModel.getRowCount() != rows.length) {
            shownPeersFor = key;
            peersTableModel.setRowCount(0);
            for (Object[] row : rows) {
                peersTableModel.addRow(row);
            }
        } else {
            for (int i = 0; i < rows.length; i++) {
                for (int c = 1; c < 5; c++) {
                    peersTableModel.setValueAt(rows[i][c], i, c);
                }
            }
        }
    }

    private static String groupOf(Asset a) {
        return a instanceof com.papertrader.model.Stock st ? st.getSector() : a.getType();
    }

    /** Latest headlines from Yahoo Finance; rebuilt only when the list changes. */
    private void applyNews(List<NewsItem> news) {
        if (news == shownNews && newsContainer.getComponentCount() > 0) {
            return;
        }
        shownNews = news;
        newsContainer.removeAll();
        if (news.isEmpty()) {
            newsSubLbl.setText(marketData.isOnline() ? "Loading..." : "Offline");
            newsContainer.add(emptyNote(marketData.isOnline()
                    ? "Loading the latest headlines..." : "News needs an internet connection."));
        } else {
            newsSubLbl.setText("Yahoo Finance • " + news.size() + " headlines");
            for (int i = 0; i < Math.min(3, news.size()); i++) {
                NewsItem n = news.get(i);
                if (i > 0) {
                    newsContainer.add(Box.createVerticalStrut(6));
                }
                newsContainer.add(buildNewsItem("NEWS", Theme.ACCENT, n.publisher(), MarketFormat.timeAgo(n.timeMillis()),
                        n.title().replace("<", "&lt;")));
            }
        }
        newsContainer.revalidate();
        newsContainer.repaint();
    }

    private JLabel emptyNote(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.MICRO);
        l.setForeground(Theme.TXT_MUTED);
        return l;
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
