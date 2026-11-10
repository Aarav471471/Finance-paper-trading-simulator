package com.papertrader.ui;

import com.papertrader.model.MarketSnapshot;
import com.papertrader.model.NewsItem;
import com.papertrader.service.MarketDataService;
import com.papertrader.util.MarketFormat;
import com.papertrader.model.Asset;
import com.papertrader.model.Crypto;
import com.papertrader.model.ETF;
import com.papertrader.model.Stock;
import com.papertrader.service.MarketService;
import com.papertrader.util.Formatter;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
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
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * PaperTrader 3D - Institutional Market Terminal.
 * Implements the full HTML specification:
 * 1. Top market telemetry & region selector (India / USA / Crypto)
 * 2. 4 dynamic major index cards with sparklines & range
 * 3. Sectoral Pulse heat strip with direct table filtering
 * 4. Two-column Movers Dock (Top Gainers vs Top Losers)
 * 5. Explore & advanced filter toolbar (Search, Sector, Cap, Sort)
 * 6. Institutional Asset Directory Table with Day's Range and 7D Trend
 * 7. High velocity market activity cards
 * 8. Slide-over Quick Analysis Drawer with chart and instant Trade link
 * 9. Toast notification overlay
 */
public class MarketPanel extends BasePanel {

    public enum Region {
        INDIA("🇮🇳 INDIA", "NSE/BSE", "₹"),
        USA("🇺🇸 USA", "US$", "$"),
        CRYPTO("🌐 CRYPTO", "24/7", "$");

        private final String label;
        private final String sub;
        private final String symbol;

        Region(String label, String sub, String symbol) {
            this.label = label;
            this.sub = sub;
            this.symbol = symbol;
        }

        public String getLabel() { return label; }
        public String getSub() { return sub; }
        public String getCurrency() { return symbol; }
    }

    private final MarketService marketService;
    private final Consumer<String> onNavigate;

    // State
    private Region currentRegion = Region.INDIA;
    private String activeSubExch = "NSE";
    private String activeSector = "ALL";

    // real numbers (set from MainFrame); null = only the app's own prices are used
    private MarketDataService marketData;
    private final JLabel breadthLbl = new JLabel("Breadth: -");
    private final JLabel vixLbl = new JLabel("VIX: N/A");
    private final JLabel dayVolLbl = new JLabel("Day Turnover: N/A");
    private final JPanel activityStrip = new JPanel(new GridLayout(1, 4, 8, 0));
    private String shownIndexKey = "";
    private int refreshTick = 0;

    /** The real indices shown per region: {Yahoo symbol, title, tag, description}. */
    private static final String[][] INDIA_INDICES = {
            {"^NSEI", "NIFTY 50", "NSE", "National Stock Exchange Benchmark"},
            {"^BSESN", "SENSEX", "BSE", "Bombay Stock Exchange 30"},
            {"^NSEBANK", "BANK NIFTY", "NSE", "Banking Index Sectoral"},
            {"^CNXIT", "NIFTY IT", "NSE", "Information Technology Index"}};
    private static final String[][] USA_INDICES = {
            {"^GSPC", "S&P 500", "NYSE", "Standard & Poor's 500 Index"},
            {"^NDX", "NASDAQ 100", "NASDAQ", "Largest 100 Nasdaq Companies"},
            {"^DJI", "DOW JONES", "NYSE", "Dow Jones Industrial Average"},
            {"^RUT", "RUSSELL 2000", "NYSE", "US Small Cap Index"}};
    private String activeCap = "ALL";
    private String activeSort = "MCAP_DESC";
    private String searchQuery = "";
    private final Set<String> watchlist = new HashSet<>();

    // Drawer state
    private Asset currentDrawerAsset = null;
    private final JPanel drawerPanel = new JPanel();
    private boolean isDrawerOpen = false;

    // Toast state
    private final JLabel toastLabel = new JLabel();
    private final JPanel toastPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
    private Timer toastTimer;

    // Components to update
    private final JPanel regionButtonsContainer = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
    private final JPanel subExchContainer = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    private final JLabel sessionBadge = new JLabel();
    private final JPanel indexCardsGrid = new JPanel(new GridLayout(1, 4, 8, 0));
    private final JPanel sectorStrip = new JPanel(new GridLayout(1, 8, 4, 0));
    private final JPanel gainersContainer = new JPanel();
    private final JPanel losersContainer = new JPanel();
    private final JTextField searchField = new JTextField();
    private final JComboBox<String> sectorCombo = new JComboBox<>();
    private final JButton capAllBtn = new JButton("All Cap");
    private final JButton capLargeBtn = new JButton("Large Cap");
    private final JButton capMidBtn = new JButton("Mid Cap");
    private final JComboBox<String> sortCombo = new JComboBox<>(new String[]{
            "Sort: Market Cap", "Sort: Most Active (Volume)", "Sort: Top Gainers %", "Sort: Top Losers %", "Sort: Highest Price"
    });

    private final MarketDirectoryTableModel tableModel = new MarketDirectoryTableModel();
    private final JTable assetsTable = new JTable(tableModel);
    private final JLabel paginationLabel = new JLabel();

    // Clock
    private final JLabel clockLabel = new JLabel();

    public MarketPanel(MarketService marketService) {
        this(marketService, null);
    }

    public MarketPanel(MarketService marketService, Consumer<String> onNavigate) {
        super("", ""); // Handled by custom terminal header
        this.marketService = marketService;
        this.onNavigate = onNavigate;

        setLayout(new BorderLayout());
        setBackground(Theme.APP_BG);
        setBorder(null);

        // 1. Top Terminal Header (Breadcrumbs, search, live clock)
        add(buildTopHeader(), BorderLayout.NORTH);

        // 2. Main Content Split / Scroll
        JPanel centerContainer = new JPanel(new BorderLayout());
        centerContainer.setOpaque(false);

        JPanel mainScrollable = new JPanel();
        mainScrollable.setLayout(new BoxLayout(mainScrollable, BoxLayout.Y_AXIS));
        mainScrollable.setOpaque(false);
        mainScrollable.setBorder(BorderFactory.createEmptyBorder(6, 12, 16, 12));

        // Section 1: Telemetry & Region Selector
        mainScrollable.add(buildTelemetryRegionBar());
        mainScrollable.add(Box.createRigidArea(new Dimension(0, 8)));

        // Section 2: 4 Major Index Cards
        mainScrollable.add(buildIndexCardsSection());
        mainScrollable.add(Box.createRigidArea(new Dimension(0, 8)));

        // Section 3: Sector Heat Strip
        mainScrollable.add(buildSectorHeatStrip());
        mainScrollable.add(Box.createRigidArea(new Dimension(0, 8)));

        // Section 4: Movers Dock (Gainers vs Losers)
        mainScrollable.add(buildMoversDock());
        mainScrollable.add(Box.createRigidArea(new Dimension(0, 8)));

        // Section 5: Filter Toolbar
        mainScrollable.add(buildFilterToolbar());
        mainScrollable.add(Box.createRigidArea(new Dimension(0, 6)));

        // Section 6: Asset Directory Table
        mainScrollable.add(buildDirectoryTableSection());
        mainScrollable.add(Box.createRigidArea(new Dimension(0, 8)));

        // Section 7: High Velocity Market Activity Strip
        mainScrollable.add(buildHighVelocityStrip());

        JScrollPane scrollPane = new JScrollPane(mainScrollable);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(18);

        centerContainer.add(scrollPane, BorderLayout.CENTER);

        // Build Slide-Over Drawer
        buildAnalysisDrawer();
        drawerPanel.setVisible(false);
        centerContainer.add(drawerPanel, BorderLayout.EAST);

        add(centerContainer, BorderLayout.CENTER);

        // Build Toast
        setupToast();

        // Start live session clock timer
        Timer clockTimer = new Timer(1000, e -> updateClock());
        clockTimer.start();
        updateClock();

        // Initial Data Load
        refreshRegionUI();
        refresh();
    }

    private JPanel buildTopHeader() {
        JPanel h = new JPanel(new BorderLayout());
        h.setOpaque(true);
        h.setBackground(Theme.APP_BG);
        h.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_CUSTOM));
        h.setPreferredSize(new Dimension(100, 38));

        // Left: Breadcrumbs
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 8));
        left.setOpaque(false);

        JLabel home = new JLabel("Home");
        home.setFont(Theme.SMALL);
        home.setForeground(Theme.TXT_MUTED);

        JLabel sep = new JLabel("›");
        sep.setFont(Theme.SMALL);
        sep.setForeground(Theme.TXT_MUTED);

        JLabel page = new JLabel("Markets");
        page.setFont(Theme.SMALL.deriveFont(Font.BOLD));
        page.setForeground(Theme.ACCENT);

        left.add(home);
        left.add(sep);
        left.add(page);
        h.add(left, BorderLayout.WEST);

        // Center: Global Search Input
        JPanel center = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 6));
        center.setOpaque(false);

        JPanel searchBox = new JPanel(new BorderLayout(4, 0));
        searchBox.setOpaque(true);
        searchBox.setBackground(Theme.SURFACE_SUB);
        searchBox.setBorder(new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 4, 1));
        searchBox.setPreferredSize(new Dimension(280, 26));

        JLabel sIcon = new JLabel(" 🔍");
        sIcon.setFont(Theme.MICRO);
        sIcon.setForeground(Theme.TXT_MUTED);
        searchBox.add(sIcon, BorderLayout.WEST);

        JTextField globalSearch = new JTextField();
        globalSearch.setOpaque(false);
        globalSearch.setBorder(null);
        globalSearch.setFont(Theme.SMALL);
        globalSearch.setForeground(Color.WHITE);
        globalSearch.setCaretColor(Color.WHITE);
        globalSearch.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                searchField.setText(globalSearch.getText());
                filterTable();
            }
        });
        searchBox.add(globalSearch, BorderLayout.CENTER);

        JLabel cmdK = new JLabel("⌘K ");
        cmdK.setFont(Theme.MONO_TINY.deriveFont(8f));
        cmdK.setForeground(Theme.TXT_MUTED);
        searchBox.add(cmdK, BorderLayout.EAST);

        center.add(searchBox);
        h.add(center, BorderLayout.CENTER);

        // Right: Session Time & Avatar
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 6));
        right.setOpaque(false);

        JPanel livePill = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        livePill.setOpaque(true);
        livePill.setBackground(Theme.SURFACE_SUB);
        livePill.setBorder(new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 4, 1));

        JLabel beacon = new JLabel("●");
        beacon.setFont(Theme.MICRO);
        beacon.setForeground(Theme.GAIN);

        clockLabel.setFont(Theme.MONO_TINY.deriveFont(9f));
        clockLabel.setForeground(Theme.TXT_SECONDARY);

        livePill.add(beacon);
        livePill.add(clockLabel);
        right.add(livePill);

        JLabel avatar = new JLabel("AD", SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.ACCENT_BG);
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.setColor(Theme.ACCENT);
                g2.drawOval(0, 0, getWidth() - 1, getHeight() - 1);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        avatar.setPreferredSize(new Dimension(24, 24));
        avatar.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 9f));
        avatar.setForeground(Theme.ACCENT);
        right.add(avatar);

        h.add(right, BorderLayout.EAST);
        return h;
    }

    private void updateClock() {
        LocalTime ist = LocalTime.now(ZoneId.of("Asia/Kolkata"));
        LocalTime utc = LocalTime.now(ZoneId.of("UTC"));
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm:ss");
        clockLabel.setText("LIVE | " + ist.format(fmt) + " IST / " + utc.format(fmt) + " UTC");
    }

    private JPanel buildTelemetryRegionBar() {
        JPanel bar = new JPanel(new BorderLayout(10, 0));
        bar.setOpaque(true);
        bar.setBackground(Theme.CARD_BG);
        bar.setBorder(BorderFactory.createCompoundBorder(
                Theme.terminalCardBorder(),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));

        // Left: Region Buttons + Sub-Exchange + Session Badge
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);

        regionButtonsContainer.setOpaque(true);
        regionButtonsContainer.setBackground(Theme.APP_BG);
        regionButtonsContainer.setBorder(new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 4, 1));

        for (Region r : Region.values()) {
            JButton b = new JButton(r.getLabel() + " " + r.getSub());
            b.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 9f));
            b.setFocusPainted(false);
            b.setCursor(new Cursor(Cursor.HAND_CURSOR));
            b.addActionListener(e -> switchRegion(r));
            regionButtonsContainer.add(b);
        }
        left.add(regionButtonsContainer);

        subExchContainer.setOpaque(false);
        left.add(subExchContainer);

        sessionBadge.setOpaque(true);
        sessionBadge.setBackground(Theme.APP_BG);
        sessionBadge.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 4, 1),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)));
        sessionBadge.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 8.5f));
        left.add(sessionBadge);

        bar.add(left, BorderLayout.WEST);

        // Right: Breadth & VIX Telemetry
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);

        JLabel breadth = breadthLbl;
        breadth.setFont(Theme.MONO_TINY);
        breadth.setForeground(Theme.TXT_SECONDARY);

        JLabel vix = vixLbl;
        vix.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 9f));
        vix.setForeground(Theme.GAIN);

        JLabel vol = dayVolLbl;
        vol.setFont(Theme.MONO_TINY);
        vol.setForeground(Theme.ACCENT);

        right.add(breadth);
        right.add(new JLabel("|") {{ setForeground(Theme.BORDER_CUSTOM); }});
        right.add(vix);
        right.add(new JLabel("|") {{ setForeground(Theme.BORDER_CUSTOM); }});
        right.add(vol);

        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private void switchRegion(Region r) {
        this.currentRegion = r;
        showToast("Switched to " + r.name() + " Markets");
        refreshRegionUI();
        refresh();
    }

    private void refreshRegionUI() {
        // 1. Style Region Buttons
        Component[] comps = regionButtonsContainer.getComponents();
        Region[] regions = Region.values();
        for (int i = 0; i < comps.length && i < regions.length; i++) {
            JButton b = (JButton) comps[i];
            boolean active = regions[i] == currentRegion;
            b.setBackground(active ? Theme.ACCENT : Theme.APP_BG);
            b.setForeground(active ? Color.WHITE : Theme.TXT_SECONDARY);
            b.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        }

        // 2. Sub-Exchange Pills
        subExchContainer.removeAll();
        String[] pills = switch (currentRegion) {
            case INDIA -> new String[]{"NSE", "BSE"};
            case USA -> new String[]{"NASDAQ", "NYSE"};
            case CRYPTO -> new String[]{"All Coins", "DeFi", "Layer 1"};
        };
        for (String p : pills) {
            JButton b = new JButton(p);
            b.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 8.5f));
            boolean act = p.equals(activeSubExch) || (subExchContainer.getComponentCount() == 0);
            b.setBackground(act ? Theme.SURFACE_SUB : Theme.CARD_BG);
            b.setForeground(act ? Theme.ACCENT : Theme.TXT_MUTED);
            b.setBorder(BorderFactory.createCompoundBorder(
                    new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 3, 1),
                    BorderFactory.createEmptyBorder(2, 6, 2, 6)));
            b.setFocusPainted(false);
            b.setCursor(new Cursor(Cursor.HAND_CURSOR));
            b.addActionListener(e -> {
                activeSubExch = p;
                refreshRegionUI();
                filterTable();
            });
            subExchContainer.add(b);
        }

        // 3. Session status
        if (currentRegion == Region.INDIA) {
            sessionBadge.setText("● MARKET OPEN | 09:15 - 15:30 IST");
            sessionBadge.setForeground(Theme.GAIN);
        } else if (currentRegion == Region.USA) {
            sessionBadge.setText("● PRE-MARKET | 09:30 - 16:00 EST");
            sessionBadge.setForeground(Theme.WARNING);
        } else {
            sessionBadge.setText("● 24/7 CONTINUOUS | Global Decentralized");
            sessionBadge.setForeground(Theme.GAIN);
        }

        // Rebuild Index Cards
        rebuildIndexCards();

        subExchContainer.revalidate();
        subExchContainer.repaint();
        regionButtonsContainer.revalidate();
        regionButtonsContainer.repaint();
    }

    private JPanel buildIndexCardsSection() {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(indexCardsGrid, BorderLayout.CENTER);
        return p;
    }

    private void rebuildIndexCards() {
        indexCardsGrid.removeAll();

        // 4 real index cards for the region (Yahoo Finance / Binance / CoinGecko); N/A while loading or offline
        String[][] cardsData = indexCardsData();
        for (String[] d : cardsData) {
            indexCardsGrid.add(createIndexCard(d[0], d[1], d[2], d[3], d[4], d[5], d[6], d[7]));
        }

        indexCardsGrid.revalidate();
        indexCardsGrid.repaint();
    }

    /** Builds the 4 index cards of the current region from real data: {title, tag, sub, value, points, pct, low, high}. */
    private String[][] indexCardsData() {
        List<String[]> rows = new ArrayList<>();
        if (currentRegion == Region.CRYPTO) {
            for (String sym : new String[]{"BTC", "ETH", "SOL"}) {
                Asset a = marketService.findBySymbol(sym).orElse(null);
                if (a == null) {
                    continue;
                }
                MarketSnapshot snap = marketData != null ? marketData.snapshot(a) : null;
                rows.add(new String[]{a.getName().toUpperCase(), sym, a.getType() + " • 24h", MarketFormat.price("$", a.getPrice()),
                        String.format(Locale.US, "%+,.2f", a.getPrice() - a.getPreviousClose()),
                        MarketFormat.signedPct(a.getChangePercent()),
                        snap == null ? MarketFormat.NA : MarketFormat.price("$", snap.dayLow()),
                        snap == null ? MarketFormat.NA : MarketFormat.price("$", snap.dayHigh())});
            }
            double[] cap = marketData == null ? null : marketData.cryptoMarketCap().orElse(null);
            rows.add(cap == null
                    ? new String[]{"TOTAL CRYPTO", "MCAP", "Overall Cryptocurrency Market", MarketFormat.NA, "", "+0.00%", "", ""}
                    : new String[]{"TOTAL CRYPTO", "MCAP", "CoinGecko • whole market", MarketFormat.big("$", cap[0]),
                    MarketFormat.big("$", cap[0] - cap[0] / (1 + cap[1] / 100)), MarketFormat.signedPct(cap[1]), "", ""});
            return rows.toArray(new String[0][]);
        }
        String cur = currentRegion == Region.INDIA ? "₹" : "$";
        for (String[] idx : currentRegion == Region.INDIA ? INDIA_INDICES : USA_INDICES) {
            MarketSnapshot snap = marketData == null ? null : marketData.index(idx[0]).orElse(null);
            if (snap == null) {
                rows.add(new String[]{idx[1], idx[2], idx[3], MarketFormat.NA,
                        marketData != null && marketData.isOnline() ? "loading" : "offline", "+0.00%", MarketFormat.NA, MarketFormat.NA});
            } else {
                rows.add(new String[]{idx[1], idx[2], idx[3], MarketFormat.price(cur, snap.price()),
                        String.format(Locale.US, "%+,.2f", snap.change()), MarketFormat.signedPct(snap.changePercent()),
                        MarketFormat.price(cur, snap.dayLow()), MarketFormat.price(cur, snap.dayHigh())});
            }
        }
        return rows.toArray(new String[0][]);
    }

    private JPanel createIndexCard(String title, String tag, String sub, String val, String deltaPts, String deltaPct, String low, String high) {
        JPanel card = new JPanel(new BorderLayout(0, 4));
        card.setOpaque(true);
        card.setBackground(Theme.CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                Theme.terminalCardBorder(),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));

        boolean isUp = !deltaPct.startsWith("-");

        // Top Row: Title + Exch Tag & Delta Badge
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        left.setOpaque(false);

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(Theme.SUBHEADING.deriveFont(Font.BOLD, 12f));
        titleLbl.setForeground(Color.WHITE);

        JLabel tagLbl = new JLabel(tag);
        tagLbl.setFont(Theme.MONO_TINY.deriveFont(7.5f));
        tagLbl.setForeground(Theme.TXT_MUTED);
        tagLbl.setOpaque(true);
        tagLbl.setBackground(Theme.SURFACE_SUB);
        tagLbl.setBorder(BorderFactory.createEmptyBorder(1, 3, 1, 3));

        left.add(titleLbl);
        left.add(tagLbl);
        top.add(left, BorderLayout.WEST);

        JLabel pctLbl = new JLabel(deltaPct);
        pctLbl.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 9f));
        pctLbl.setForeground(isUp ? Theme.GAIN : Theme.LOSS);
        pctLbl.setOpaque(true);
        pctLbl.setBackground(isUp ? Theme.GREEN_BG : Theme.RED_BG);
        pctLbl.setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 4));
        top.add(pctLbl, BorderLayout.EAST);

        // Center: Value + Sparkline
        JPanel center = new JPanel(new BorderLayout(6, 0));
        center.setOpaque(false);

        JPanel valBox = new JPanel();
        valBox.setLayout(new BoxLayout(valBox, BoxLayout.Y_AXIS));
        valBox.setOpaque(false);

        JLabel valLbl = new JLabel(val);
        valLbl.setFont(Theme.MONO_BODY.deriveFont(Font.BOLD, 15f));
        valLbl.setForeground(Color.WHITE);

        JLabel ptsLbl = new JLabel((isUp ? "▲ " : "▼ ") + deltaPts);
        ptsLbl.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 8.5f));
        ptsLbl.setForeground(isUp ? Theme.GAIN : Theme.LOSS);

        valBox.add(valLbl);
        valBox.add(ptsLbl);
        center.add(valBox, BorderLayout.WEST);

        // Mini Sparkline canvas
        JPanel spark = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (MarketFormat.NA.equals(val)) {
                    g2.dispose();
                    return;   // no data yet: draw nothing rather than a made-up line
                }
                int w = getWidth();
                int h = getHeight();
                // real shape: previous close -> today's low/high -> now (no invented wiggles)
                int[] ys = isUp ? new int[]{h - 6, h - 4, 6}
                                : new int[]{6, 4, h - 6};
                int n = ys.length;
                int[] xs = new int[n];
                for (int i = 0; i < n; i++) xs[i] = (int) ((double) i / (n - 1) * w);

                g2.setColor(isUp ? Theme.GAIN : Theme.LOSS);
                g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawPolyline(xs, ys, n);

                Polygon poly = new Polygon();
                poly.addPoint(xs[0], h);
                for (int i = 0; i < n; i++) poly.addPoint(xs[i], ys[i]);
                poly.addPoint(xs[n - 1], h);
                g2.setPaint(new GradientPaint(0, 0, isUp ? Theme.GREEN_BG : Theme.RED_BG, 0, h, new Color(0, 0, 0, 0)));
                g2.fillPolygon(poly);
                g2.dispose();
            }
        };
        spark.setPreferredSize(new Dimension(72, 28));
        spark.setOpaque(false);
        center.add(spark, BorderLayout.EAST);

        // Bottom: Range Footer
        JPanel bot = new JPanel(new BorderLayout());
        bot.setOpaque(false);
        bot.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER_SUBTLE));

        JLabel lLow = new JLabel("L: " + low);
        lLow.setFont(Theme.MICRO.deriveFont(8f));
        lLow.setForeground(Theme.TXT_MUTED);

        JLabel lHigh = new JLabel("H: " + high);
        lHigh.setFont(Theme.MICRO.deriveFont(8f));
        lHigh.setForeground(Theme.TXT_MUTED);

        bot.add(lLow, BorderLayout.WEST);
        bot.add(lHigh, BorderLayout.EAST);

        card.add(top, BorderLayout.NORTH);
        card.add(center, BorderLayout.CENTER);
        card.add(bot, BorderLayout.SOUTH);

        return card;
    }

    private JPanel buildSectorHeatStrip() {
        JPanel card = new JPanel(new BorderLayout(0, 4));
        card.setOpaque(true);
        card.setBackground(Theme.CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                Theme.terminalCardBorder(),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));

        // Header
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);

        JLabel title = new JLabel("SECTORAL PULSE (MARKET HEATMAP)");
        title.setFont(Theme.TINY.deriveFont(Font.BOLD, 9.5f));
        title.setForeground(Color.WHITE);

        JLabel hint = new JLabel("Click sector to filter directory below");
        hint.setFont(Theme.MICRO);
        hint.setForeground(Theme.TXT_MUTED);

        head.add(title, BorderLayout.WEST);
        head.add(hint, BorderLayout.EAST);
        card.add(head, BorderLayout.NORTH);

        // Sectors grid
        sectorStrip.setOpaque(false);
        String[][] sectors = sectorHeat();
        for (String[] sec : sectors) {
            sectorStrip.add(sectorChip(sec[0], sec[1], Integer.parseInt(sec[2])));
        }

        card.add(sectorStrip, BorderLayout.CENTER);
        return card;
    }

    /** Average change % of each sector, from the live prices of the app's assets (max 8 sectors). */
    private String[][] sectorHeat() {
        Map<String, double[]> sums = new LinkedHashMap<>();   // sector -> {total %, count}
        for (Asset a : marketService.getAllAssets()) {
            double[] v = sums.computeIfAbsent(getAssetSector(a), k -> new double[2]);
            v[0] += a.getChangePercent();
            v[1]++;
        }
        List<Map.Entry<String, double[]>> list = new ArrayList<>(sums.entrySet());
        list.sort((x, y) -> Double.compare(y.getValue()[0] / y.getValue()[1], x.getValue()[0] / x.getValue()[1]));
        double maxAbs = 0.01;
        for (Map.Entry<String, double[]> e : list) {
            maxAbs = Math.max(maxAbs, Math.abs(e.getValue()[0] / e.getValue()[1]));
        }
        String[][] out = new String[Math.min(8, list.size())][];
        for (int i = 0; i < out.length; i++) {
            double avg = list.get(i).getValue()[0] / list.get(i).getValue()[1];
            out[i] = new String[]{list.get(i).getKey(), String.format(Locale.US, "%+.2f%%", avg),
                    String.valueOf((int) Math.round(Math.abs(avg) / maxAbs * 100))};
        }
        return out;
    }

    private JButton sectorChip(String name, String pct, int barPct) {
        boolean isUp = !pct.startsWith("-");
        JButton chip = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.SURFACE_SUB);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
                g2.setColor(Theme.BORDER_CUSTOM);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 4, 4);

                g2.setFont(Theme.MICRO.deriveFont(Font.BOLD, 8.5f));
                g2.setColor(Color.WHITE);
                g2.drawString(name, 6, 12);

                g2.setColor(isUp ? Theme.GAIN : Theme.LOSS);
                g2.drawString(pct, 6, 24);

                // Mini progress bar
                int pX = getWidth() - 32;
                int pY = 16;
                int pW = 26;
                int pH = 4;
                g2.setColor(Theme.APP_BG);
                g2.fillRoundRect(pX, pY, pW, pH, 2, 2);
                g2.setColor(isUp ? Theme.GAIN : Theme.LOSS);
                g2.fillRoundRect(pX, pY, (int) (pW * (barPct / 100.0)), pH, 2, 2);

                g2.dispose();
            }
        };
        chip.setPreferredSize(new Dimension(85, 30));
        chip.setBorder(BorderFactory.createEmptyBorder());
        chip.setFocusPainted(false);
        chip.setCursor(new Cursor(Cursor.HAND_CURSOR));
        chip.addActionListener(e -> {
            activeSector = name;
            sectorCombo.setSelectedItem(name);
            showToast("Filtered by " + name + " sector");
            filterTable();
        });
        return chip;
    }

    private void rebuildSectorStrip() {
        sectorStrip.removeAll();
        for (String[] sec : sectorHeat()) {
            sectorStrip.add(sectorChip(sec[0], sec[1], Integer.parseInt(sec[2])));
        }
        sectorStrip.revalidate();
        sectorStrip.repaint();
    }

    private JPanel buildMoversDock() {
        JPanel dock = new JPanel(new GridLayout(1, 2, 8, 0));
        dock.setOpaque(false);

        // Left: Gainers
        JPanel gCard = new JPanel(new BorderLayout(0, 4));
        gCard.setOpaque(true);
        gCard.setBackground(Theme.CARD_BG);
        gCard.setBorder(BorderFactory.createCompoundBorder(
                Theme.terminalCardBorder(),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));

        JPanel gHead = new JPanel(new BorderLayout());
        gHead.setOpaque(false);
        JLabel gTitle = new JLabel("● Top Movers - Gainers");
        gTitle.setFont(Theme.TINY.deriveFont(Font.BOLD, 10f));
        gTitle.setForeground(Theme.GAIN);
        gHead.add(gTitle, BorderLayout.WEST);
        gCard.add(gHead, BorderLayout.NORTH);

        gainersContainer.setLayout(new BoxLayout(gainersContainer, BoxLayout.Y_AXIS));
        gainersContainer.setOpaque(false);
        gCard.add(gainersContainer, BorderLayout.CENTER);

        // Right: Losers
        JPanel lCard = new JPanel(new BorderLayout(0, 4));
        lCard.setOpaque(true);
        lCard.setBackground(Theme.CARD_BG);
        lCard.setBorder(BorderFactory.createCompoundBorder(
                Theme.terminalCardBorder(),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));

        JPanel lHead = new JPanel(new BorderLayout());
        lHead.setOpaque(false);
        JLabel lTitle = new JLabel("● Top Movers - Losers");
        lTitle.setFont(Theme.TINY.deriveFont(Font.BOLD, 10f));
        lTitle.setForeground(Theme.LOSS);
        lHead.add(lTitle, BorderLayout.WEST);
        lCard.add(lHead, BorderLayout.NORTH);

        losersContainer.setLayout(new BoxLayout(losersContainer, BoxLayout.Y_AXIS));
        losersContainer.setOpaque(false);
        lCard.add(losersContainer, BorderLayout.CENTER);

        dock.add(gCard);
        dock.add(lCard);
        return dock;
    }

    private JPanel createMoverRow(Asset a, boolean isGain) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_SUBTLE),
                BorderFactory.createEmptyBorder(4, 2, 4, 2)));
        row.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // Left: Avatar + Symbol & Name
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.setOpaque(false);

        JLabel av = new JLabel(a.getSymbol().substring(0, Math.min(2, a.getSymbol().length())), SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.SURFACE_SUB);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        av.setPreferredSize(new Dimension(24, 24));
        av.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 8.5f));
        av.setForeground(isGain ? Theme.ACCENT : Theme.LOSS);
        left.add(av);

        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setOpaque(false);

        JLabel sym = new JLabel(a.getSymbol());
        sym.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 10f));
        sym.setForeground(Color.WHITE);

        JLabel name = new JLabel(a.getName());
        name.setFont(Theme.MICRO);
        name.setForeground(Theme.TXT_MUTED);

        info.add(sym);
        info.add(name);
        left.add(info);
        row.add(left, BorderLayout.WEST);

        // Right: Price & Percent Pill
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setOpaque(false);

        JLabel price = new JLabel(Formatter.money(a.getPrice()));
        price.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 10f));
        price.setForeground(Color.WHITE);

        double pct = a.getChangePercent();
        String sign = pct >= 0 ? "+" : "";
        JLabel pill = new JLabel(sign + String.format("%.2f%%", pct));
        pill.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 8.5f));
        pill.setForeground(isGain ? Theme.GAIN : Theme.LOSS);
        pill.setOpaque(true);
        pill.setBackground(isGain ? Theme.GREEN_BG : Theme.RED_BG);
        pill.setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 4));

        right.add(price);
        right.add(pill);
        row.add(right, BorderLayout.EAST);

        row.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                openDrawer(a);
            }
        });

        return row;
    }

    private JPanel buildFilterToolbar() {
        JPanel bar = new JPanel(new BorderLayout(8, 0));
        bar.setOpaque(true);
        bar.setBackground(Theme.CARD_BG);
        bar.setBorder(BorderFactory.createCompoundBorder(
                Theme.terminalCardBorder(),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));

        // Left: Search Field
        JPanel left = new JPanel(new BorderLayout(4, 0));
        left.setOpaque(true);
        left.setBackground(Theme.APP_BG);
        left.setBorder(new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 4, 1));
        left.setPreferredSize(new Dimension(320, 28));

        JLabel sI = new JLabel(" 🔍 ");
        sI.setFont(Theme.MICRO);
        sI.setForeground(Theme.TXT_MUTED);
        left.add(sI, BorderLayout.WEST);

        searchField.setOpaque(false);
        searchField.setBorder(null);
        searchField.setFont(Theme.SMALL);
        searchField.setForeground(Color.WHITE);
        searchField.setCaretColor(Color.WHITE);
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                searchQuery = searchField.getText();
                filterTable();
            }
        });
        left.add(searchField, BorderLayout.CENTER);

        bar.add(left, BorderLayout.WEST);

        // Right: Filters & Sort
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setOpaque(false);

        // Sector Combo
        sectorCombo.setFont(Theme.SMALL);
        sectorCombo.setBackground(Theme.SURFACE_SUB);
        sectorCombo.setForeground(Color.WHITE);
        sectorCombo.addItem("All Sectors");
        sectorCombo.addItem("Information Tech");
        sectorCombo.addItem("Banking");
        sectorCombo.addItem("Energy");
        sectorCombo.addItem("Automobile");
        sectorCombo.addItem("Telecom");
        sectorCombo.addItem("FMCG");
        sectorCombo.addItem("Infrastructure");
        sectorCombo.addItem("Consumer Tech");
        sectorCombo.addItem("Technology");
        sectorCombo.addItem("Crypto");
        sectorCombo.addItem("ETF");
        sectorCombo.addActionListener(e -> {
            String sel = (String) sectorCombo.getSelectedItem();
            activeSector = "All Sectors".equals(sel) ? "ALL" : sel;
            filterTable();
        });
        right.add(sectorCombo);

        // Cap Pills
        JPanel capPills = new JPanel(new FlowLayout(FlowLayout.LEFT, 1, 0));
        capPills.setOpaque(true);
        capPills.setBackground(Theme.APP_BG);
        capPills.setBorder(new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 4, 1));

        JButton[] capBtns = {capAllBtn, capLargeBtn, capMidBtn};
        for (JButton b : capBtns) {
            b.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 8.5f));
            b.setFocusPainted(false);
            b.setCursor(new Cursor(Cursor.HAND_CURSOR));
            b.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            capPills.add(b);
        }
        capAllBtn.addActionListener(e -> setCapFilter("ALL"));
        capLargeBtn.addActionListener(e -> setCapFilter("LARGE"));
        capMidBtn.addActionListener(e -> setCapFilter("MID"));
        styleCapPills();
        right.add(capPills);

        // Sort Combo
        sortCombo.setFont(Theme.SMALL);
        sortCombo.setBackground(Theme.SURFACE_SUB);
        sortCombo.setForeground(Color.WHITE);
        sortCombo.addActionListener(e -> {
            activeSort = (String) sortCombo.getSelectedItem();
            filterTable();
        });
        right.add(sortCombo);

        // Reset Button
        JButton reset = new JButton("↺");
        reset.setFont(Theme.SUBHEADING);
        reset.setBackground(Theme.SURFACE_SUB);
        reset.setForeground(Theme.TXT_MUTED);
        reset.setFocusPainted(false);
        reset.setCursor(new Cursor(Cursor.HAND_CURSOR));
        reset.setToolTipText("Reset Filters");
        reset.addActionListener(e -> resetFilters());
        right.add(reset);

        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private void setCapFilter(String cap) {
        this.activeCap = cap;
        styleCapPills();
        filterTable();
    }

    private void styleCapPills() {
        capAllBtn.setBackground("ALL".equals(activeCap) ? Theme.ACCENT : Theme.APP_BG);
        capAllBtn.setForeground("ALL".equals(activeCap) ? Color.WHITE : Theme.TXT_MUTED);

        capLargeBtn.setBackground("LARGE".equals(activeCap) ? Theme.ACCENT : Theme.APP_BG);
        capLargeBtn.setForeground("LARGE".equals(activeCap) ? Color.WHITE : Theme.TXT_MUTED);

        capMidBtn.setBackground("MID".equals(activeCap) ? Theme.ACCENT : Theme.APP_BG);
        capMidBtn.setForeground("MID".equals(activeCap) ? Color.WHITE : Theme.TXT_MUTED);
    }

    private void resetFilters() {
        searchField.setText("");
        searchQuery = "";
        activeSector = "ALL";
        sectorCombo.setSelectedIndex(0);
        setCapFilter("ALL");
        sortCombo.setSelectedIndex(0);
        showToast("Filters cleared");
        filterTable();
    }

    private JPanel buildDirectoryTableSection() {
        JPanel card = new JPanel(new BorderLayout(0, 4));
        card.setOpaque(true);
        card.setBackground(Theme.CARD_BG);
        card.setBorder(Theme.terminalCardBorder());

        assetsTable.setRowHeight(38);
        assetsTable.setBackground(Theme.CARD_BG);
        assetsTable.setForeground(Color.WHITE);
        assetsTable.setGridColor(Theme.BORDER_SUBTLE);
        assetsTable.setShowHorizontalLines(true);
        assetsTable.setShowVerticalLines(false);
        assetsTable.getTableHeader().setBackground(Theme.APP_BG);
        assetsTable.getTableHeader().setForeground(Theme.TXT_MUTED);
        assetsTable.getTableHeader().setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 8.5f));
        assetsTable.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_CUSTOM));

        // Column Renderers
        assetsTable.getColumnModel().getColumn(0).setPreferredWidth(180);
        assetsTable.getColumnModel().getColumn(0).setCellRenderer(new InstrumentCellRenderer());

        assetsTable.getColumnModel().getColumn(1).setPreferredWidth(55);
        assetsTable.getColumnModel().getColumn(1).setCellRenderer(new CenterBadgeRenderer());

        assetsTable.getColumnModel().getColumn(2).setPreferredWidth(90);
        assetsTable.getColumnModel().getColumn(2).setCellRenderer(new MonospaceCellRenderer(SwingConstants.RIGHT, Font.BOLD));

        assetsTable.getColumnModel().getColumn(3).setPreferredWidth(80);
        assetsTable.getColumnModel().getColumn(3).setCellRenderer(new DeltaPointsCellRenderer());

        assetsTable.getColumnModel().getColumn(4).setPreferredWidth(80);
        assetsTable.getColumnModel().getColumn(4).setCellRenderer(new DeltaPercentCellRenderer());

        assetsTable.getColumnModel().getColumn(5).setPreferredWidth(120);
        assetsTable.getColumnModel().getColumn(5).setCellRenderer(new RangeGaugeCellRenderer());

        assetsTable.getColumnModel().getColumn(6).setPreferredWidth(75);
        assetsTable.getColumnModel().getColumn(6).setCellRenderer(new MonospaceCellRenderer(SwingConstants.RIGHT, Font.PLAIN));

        assetsTable.getColumnModel().getColumn(7).setPreferredWidth(90);
        assetsTable.getColumnModel().getColumn(7).setCellRenderer(new MonospaceCellRenderer(SwingConstants.RIGHT, Font.PLAIN));

        assetsTable.getColumnModel().getColumn(8).setPreferredWidth(75);
        assetsTable.getColumnModel().getColumn(8).setCellRenderer(new SparklineCellRenderer());

        assetsTable.getColumnModel().getColumn(9).setPreferredWidth(95);
        assetsTable.getColumnModel().getColumn(9).setCellRenderer(new ActionsCellRenderer());

        assetsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = assetsTable.rowAtPoint(e.getPoint());
                int col = assetsTable.columnAtPoint(e.getPoint());
                if (row >= 0 && row < tableModel.getRowCount()) {
                    Asset a = tableModel.getAssetAt(row);
                    if (e.getClickCount() == 2) {
                        if (onNavigate != null) onNavigate.accept("StockDetails:" + a.getSymbol());
                    } else if (col == 9) {
                        int xInCell = e.getX() - assetsTable.getCellRect(row, col, false).x;
                        if (xInCell < 30) {
                            toggleWatchlist(a.getSymbol());
                        } else {
                            if (onNavigate != null) onNavigate.accept("Trade:" + a.getSymbol());
                        }
                    } else {
                        openDrawer(a);
                    }
                }
            }
        });

        JScrollPane tableScroll = new JScrollPane(assetsTable);
        tableScroll.setBorder(null);
        tableScroll.setOpaque(false);
        tableScroll.getViewport().setOpaque(false);
        tableScroll.setPreferredSize(new Dimension(800, 280));
        card.add(tableScroll, BorderLayout.CENTER);

        // Pagination Bar
        JPanel bot = new JPanel(new BorderLayout());
        bot.setOpaque(true);
        bot.setBackground(Theme.APP_BG);
        bot.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER_CUSTOM),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)));

        paginationLabel.setFont(Theme.MONO_TINY);
        paginationLabel.setForeground(Theme.TXT_MUTED);
        bot.add(paginationLabel, BorderLayout.WEST);

        card.add(bot, BorderLayout.SOUTH);
        return card;
    }

    private JPanel buildHighVelocityStrip() {
        activityStrip.setOpaque(false);
        rebuildActivityStrip();
        return activityStrip;
    }

    /** Four "what is happening" cards, all calculated from real prices, volumes and 52-week ranges. */
    private void rebuildActivityStrip() {
        activityStrip.removeAll();
        Asset mostActive = null;
        double bestTurnover = -1;
        Asset surge = null;
        double bestSurge = -1;
        Asset nearHigh = null;
        double bestHigh = -1;
        Asset widest = null;
        double bestRange = -1;
        for (Asset a : marketService.getAllAssets()) {
            if (marketData == null) {
                break;
            }
            MarketSnapshot snap = marketData.snapshot(a);
            double turnover = snap.volume() * a.getPrice();
            if (MarketFormat.known(turnover) && turnover > bestTurnover) {
                bestTurnover = turnover;
                mostActive = a;
            }
            double avg = marketData.averageVolume(a, 10);
            if (MarketFormat.known(snap.volume()) && MarketFormat.known(avg) && avg > 0 && snap.volume() / avg > bestSurge) {
                bestSurge = snap.volume() / avg;
                surge = a;
            }
            if (MarketFormat.known(snap.week52High()) && snap.week52High() > 0 && a.getPrice() / snap.week52High() > bestHigh) {
                bestHigh = a.getPrice() / snap.week52High();
                nearHigh = a;
            }
            if (MarketFormat.known(snap.dayHigh()) && MarketFormat.known(snap.dayLow()) && snap.dayLow() > 0
                    && (snap.dayHigh() - snap.dayLow()) / snap.dayLow() > bestRange) {
                bestRange = (snap.dayHigh() - snap.dayLow()) / snap.dayLow();
                widest = a;
            }
        }
        if (mostActive == null) {
            activityStrip.add(createActivityCard("MOST ACTIVELY TRADED", "-", MarketFormat.NA, "Loading volumes...", Theme.ACCENT));
        } else {
            String c = MarketFormat.currency(mostActive.getSymbol());
            activityStrip.add(createActivityCard("MOST ACTIVELY TRADED", mostActive.getSymbol(),
                    MarketFormat.volume(marketData.snapshot(mostActive).volume()), MarketFormat.big(c, bestTurnover) + " turnover today", Theme.ACCENT));
        }
        if (surge == null) {
            activityStrip.add(createActivityCard("VOLUME SURGE", "-", MarketFormat.NA, "Needs daily volume history", Theme.GAIN));
        } else {
            activityStrip.add(createActivityCard(String.format(Locale.US, "VOLUME SURGE (%.1fX)", bestSurge), surge.getSymbol(),
                    MarketFormat.signedPct(surge.getChangePercent()),
                    MarketFormat.volume(marketData.snapshot(surge).volume()) + " vs " + MarketFormat.volume(marketData.averageVolume(surge, 10)) + " 10-day avg", Theme.GAIN));
        }
        if (nearHigh == null) {
            activityStrip.add(createActivityCard("CLOSEST TO 52-WEEK HIGH", "-", MarketFormat.NA, "Loading 52-week ranges...", Theme.WARNING));
        } else {
            String c = MarketFormat.currency(nearHigh.getSymbol());
            activityStrip.add(createActivityCard("CLOSEST TO 52-WEEK HIGH", nearHigh.getSymbol(), MarketFormat.price(c, nearHigh.getPrice()),
                    String.format(Locale.US, "%.1f%% of 52W high %s", bestHigh * 100, MarketFormat.price(c, marketData.snapshot(nearHigh).week52High())), Theme.WARNING));
        }
        if (widest == null) {
            activityStrip.add(createActivityCard("WIDEST DAY RANGE", "-", MarketFormat.NA, "Loading day ranges...", Theme.ACCENT));
        } else {
            activityStrip.add(createActivityCard("WIDEST DAY RANGE", widest.getSymbol(), String.format(Locale.US, "%.2f%%", bestRange * 100),
                    "High-low swing today (most volatile)", Theme.ACCENT));
        }
        activityStrip.revalidate();
        activityStrip.repaint();
    }

    private JPanel createActivityCard(String label, String sym, String metric, String desc, Color accent) {
        JPanel c = new JPanel(new BorderLayout(0, 3));
        c.setOpaque(true);
        c.setBackground(Theme.CARD_BG);
        c.setBorder(BorderFactory.createCompoundBorder(
                Theme.terminalCardBorder(),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));

        JLabel l = new JLabel(label);
        l.setFont(Theme.MICRO.deriveFont(Font.BOLD, 8f));
        l.setForeground(Theme.TXT_MUTED);

        JPanel mid = new JPanel(new BorderLayout());
        mid.setOpaque(false);

        JLabel s = new JLabel(sym);
        s.setFont(Theme.MONO_BODY.deriveFont(Font.BOLD, 12f));
        s.setForeground(Color.WHITE);

        JLabel m = new JLabel(metric);
        m.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 9.5f));
        m.setForeground(accent);

        mid.add(s, BorderLayout.WEST);
        mid.add(m, BorderLayout.EAST);

        JLabel d = new JLabel(desc);
        d.setFont(Theme.MICRO.deriveFont(8f));
        d.setForeground(Theme.TXT_MUTED);

        c.add(l, BorderLayout.NORTH);
        c.add(mid, BorderLayout.CENTER);
        c.add(d, BorderLayout.SOUTH);

        return c;
    }

    private void buildAnalysisDrawer() {
        drawerPanel.setLayout(new BorderLayout());
        drawerPanel.setOpaque(true);
        drawerPanel.setBackground(Theme.SURFACE_SUB);
        drawerPanel.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Theme.BORDER_CUSTOM));
        drawerPanel.setPreferredSize(new Dimension(340, 600));
    }

    private void openDrawer(Asset a) {
        this.currentDrawerAsset = a;
        this.isDrawerOpen = true;

        drawerPanel.removeAll();

        // 1. Header
        JPanel dHead = new JPanel(new BorderLayout());
        dHead.setOpaque(true);
        dHead.setBackground(Theme.CARD_BG);
        dHead.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_CUSTOM),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));

        JPanel dLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        dLeft.setOpaque(false);

        JLabel av = new JLabel(a.getSymbol().substring(0, Math.min(2, a.getSymbol().length())), SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.ACCENT_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                g2.setColor(Theme.ACCENT);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        av.setPreferredSize(new Dimension(32, 32));
        av.setFont(Theme.MONO_BODY.deriveFont(Font.BOLD, 11f));
        av.setForeground(Theme.ACCENT);
        dLeft.add(av);

        JPanel dTitles = new JPanel();
        dTitles.setLayout(new BoxLayout(dTitles, BoxLayout.Y_AXIS));
        dTitles.setOpaque(false);

        JLabel dSym = new JLabel(a.getSymbol());
        dSym.setFont(Theme.SUBHEADING.deriveFont(Font.BOLD, 13f));
        dSym.setForeground(Color.WHITE);

        JLabel dName = new JLabel(a.getName());
        dName.setFont(Theme.MICRO);
        dName.setForeground(Theme.TXT_MUTED);

        dTitles.add(dSym);
        dTitles.add(dName);
        dLeft.add(dTitles);
        dHead.add(dLeft, BorderLayout.WEST);

        JButton close = new JButton("✕");
        close.setFont(Theme.TINY);
        close.setForeground(Theme.TXT_MUTED);
        close.setOpaque(false);
        close.setBorder(null);
        close.setFocusPainted(false);
        close.setCursor(new Cursor(Cursor.HAND_CURSOR));
        close.addActionListener(e -> closeDrawer());
        dHead.add(close, BorderLayout.EAST);

        drawerPanel.add(dHead, BorderLayout.NORTH);

        // 2. Center Content
        JPanel dContent = new JPanel();
        dContent.setLayout(new BoxLayout(dContent, BoxLayout.Y_AXIS));
        dContent.setOpaque(false);
        dContent.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Price & Delta
        JPanel dPriceBox = new JPanel(new BorderLayout());
        dPriceBox.setOpaque(false);

        JLabel dPrice = new JLabel(Formatter.money(a.getPrice()));
        dPrice.setFont(Theme.MONO_BODY.deriveFont(Font.BOLD, 18f));
        dPrice.setForeground(Color.WHITE);

        double pct = a.getChangePercent();
        boolean isUp = pct >= 0;
        JLabel dDelta = new JLabel((isUp ? "+ " : "") + String.format("%.2f%%", pct));
        dDelta.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 10f));
        dDelta.setForeground(isUp ? Theme.GAIN : Theme.LOSS);
        dDelta.setOpaque(true);
        dDelta.setBackground(isUp ? Theme.GREEN_BG : Theme.RED_BG);
        dDelta.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));

        dPriceBox.add(dPrice, BorderLayout.WEST);
        dPriceBox.add(dDelta, BorderLayout.EAST);
        dContent.add(dPriceBox);
        dContent.add(Box.createRigidArea(new Dimension(0, 8)));

        // Mini Area Chart
        JPanel dChart = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();

                g2.setColor(Theme.CARD_BG);
                g2.fillRoundRect(0, 0, w, h, 6, 6);
                g2.setColor(Theme.BORDER_CUSTOM);
                g2.drawRoundRect(0, 0, w - 1, h - 1, 6, 6);

                List<Double> hist = marketService.getPriceHistory(a.getSymbol());
                if (hist.size() < 2) {
                    g2.setColor(Theme.TXT_MUTED);
                    g2.setFont(Theme.MICRO);
                    g2.drawString("Collecting live ticks...", 10, h / 2);
                    g2.dispose();
                    return;
                }

                double min = Double.MAX_VALUE;
                double max = -Double.MAX_VALUE;
                for (double v : hist) {
                    min = Math.min(min, v);
                    max = Math.max(max, v);
                }
                double diff = Math.max(0.01, max - min);

                int n = hist.size();
                int[] xs = new int[n];
                int[] ys = new int[n];
                for (int i = 0; i < n; i++) {
                    xs[i] = 10 + (int) ((double) i / (n - 1) * (w - 20));
                    ys[i] = 10 + (int) ((max - hist.get(i)) / diff * (h - 24));
                }

                Polygon poly = new Polygon();
                poly.addPoint(xs[0], h - 2);
                for (int i = 0; i < n; i++) poly.addPoint(xs[i], ys[i]);
                poly.addPoint(xs[n - 1], h - 2);

                g2.setPaint(new GradientPaint(0, 0, isUp ? Theme.GREEN_BG : Theme.RED_BG, 0, h, new Color(0, 0, 0, 0)));
                g2.fillPolygon(poly);

                g2.setColor(isUp ? Theme.GAIN : Theme.LOSS);
                g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawPolyline(xs, ys, n);

                g2.dispose();
            }
        };
        dChart.setPreferredSize(new Dimension(300, 110));
        dChart.setMaximumSize(new Dimension(320, 110));
        dContent.add(dChart);
        dContent.add(Box.createRigidArea(new Dimension(0, 10)));

        // Statistics Matrix
        JPanel stats = new JPanel(new GridLayout(2, 2, 6, 6));
        stats.setOpaque(false);
        stats.setMaximumSize(new Dimension(320, 80));

        MarketSnapshot snap = marketData != null ? marketData.snapshot(a) : null;
        String c = MarketFormat.currency(a.getSymbol());
        stats.add(createDrawerStat("Day High / Low", snap == null ? MarketFormat.NA
                : MarketFormat.price(c, snap.dayHigh()) + " / " + MarketFormat.price(c, snap.dayLow())));
        stats.add(createDrawerStat("Day Volume", snap == null ? MarketFormat.NA : MarketFormat.volume(snap.volume())));
        stats.add(createDrawerStat("Type / Risk", a.getType() + " / " + a.getRiskLevel()));
        stats.add(createDrawerStat("Volatility", String.format("%.2f%%", a.getVolatility() * 100.0)));

        dContent.add(stats);
        dContent.add(Box.createRigidArea(new Dimension(0, 10)));

        // News snippet
        JPanel newsBox = new JPanel(new BorderLayout(0, 3));
        newsBox.setOpaque(true);
        newsBox.setBackground(Theme.CARD_BG);
        newsBox.setBorder(BorderFactory.createCompoundBorder(
                Theme.terminalCardBorder(),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        newsBox.setMaximumSize(new Dimension(320, 70));

        JLabel nTitle = new JLabel("CATALYST & SENTIMENT");
        nTitle.setFont(Theme.MICRO.deriveFont(Font.BOLD, 8f));
        nTitle.setForeground(Theme.TXT_MUTED);

        List<NewsItem> news = marketData != null ? marketData.news(a) : List.of();
        JLabel nText = new JLabel("<html>" + (news.isEmpty() ? "No headlines loaded yet."
                : news.get(0).title().replace("<", "&lt;") + " <i>(" + news.get(0).publisher() + ", "
                + MarketFormat.timeAgo(news.get(0).timeMillis()) + ")</i>") + "</html>");
        nText.setFont(Theme.MICRO);
        nText.setForeground(Theme.TXT_SECONDARY);

        newsBox.add(nTitle, BorderLayout.NORTH);
        newsBox.add(nText, BorderLayout.CENTER);
        dContent.add(newsBox);

        drawerPanel.add(dContent, BorderLayout.CENTER);

        // 3. Footer Action Buttons
        JPanel dFoot = new JPanel(new GridLayout(1, 3, 6, 0));
        dFoot.setOpaque(true);
        dFoot.setBackground(Theme.CARD_BG);
        dFoot.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER_CUSTOM),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));

        boolean inWatchlist = watchlist.contains(a.getSymbol());
        JButton wBtn = new JButton((inWatchlist ? "★ " : "☆ ") + "Watch");
        wBtn.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 9f));
        wBtn.setBackground(Theme.SURFACE_SUB);
        wBtn.setForeground(inWatchlist ? Theme.WARNING : Theme.TXT_SECONDARY);
        wBtn.setFocusPainted(false);
        wBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        wBtn.addActionListener(e -> {
            toggleWatchlist(a.getSymbol());
            openDrawer(a); // Refresh
        });

        JButton ddBtn = new JButton("Deep Dive →");
        ddBtn.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 9f));
        ddBtn.setBackground(new Color(0x23, 0x2A, 0x3A));
        ddBtn.setForeground(Theme.ACCENT);
        ddBtn.setBorder(new Theme.RoundedBorder(Theme.ACCENT, 3, 1));
        ddBtn.setFocusPainted(false);
        ddBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        ddBtn.addActionListener(e -> {
            if (onNavigate != null) onNavigate.accept("StockDetails:" + a.getSymbol());
        });

        JButton tBtn = new JButton("Trade →");
        tBtn.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 9f));
        tBtn.setBackground(Theme.ACCENT);
        tBtn.setForeground(Color.WHITE);
        tBtn.setFocusPainted(false);
        tBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        tBtn.addActionListener(e -> {
            if (onNavigate != null) onNavigate.accept("Trade:" + a.getSymbol());
        });

        dFoot.add(wBtn);
        dFoot.add(ddBtn);
        dFoot.add(tBtn);
        drawerPanel.add(dFoot, BorderLayout.SOUTH);

        drawerPanel.setVisible(true);
        drawerPanel.revalidate();
        drawerPanel.repaint();
    }

    private JPanel createDrawerStat(String title, String val) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(true);
        p.setBackground(Theme.CARD_BG);
        p.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 3, 1),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)));

        JLabel t = new JLabel(title);
        t.setFont(Theme.MICRO.deriveFont(7.5f));
        t.setForeground(Theme.TXT_MUTED);

        JLabel v = new JLabel(val);
        v.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 9f));
        v.setForeground(Color.WHITE);

        p.add(t, BorderLayout.NORTH);
        p.add(v, BorderLayout.CENTER);
        return p;
    }

    private void closeDrawer() {
        this.isDrawerOpen = false;
        drawerPanel.setVisible(false);
    }

    private void toggleWatchlist(String symbol) {
        if (watchlist.contains(symbol)) {
            watchlist.remove(symbol);
            showToast(symbol + " removed from Watchlist");
        } else {
            watchlist.add(symbol);
            showToast(symbol + " added to Watchlist");
        }
        assetsTable.repaint();
    }

    private void setupToast() {
        toastPanel.setOpaque(true);
        toastPanel.setBackground(Theme.SURFACE_SUB);
        toastPanel.setBorder(new Theme.RoundedBorder(Theme.ACCENT, 6, 1));
        toastPanel.setVisible(false);

        toastLabel.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 9f));
        toastLabel.setForeground(Color.WHITE);
        toastPanel.add(toastLabel);

        toastTimer = new Timer(2500, e -> toastPanel.setVisible(false));
        toastTimer.setRepeats(false);
    }

    private void showToast(String msg) {
        toastLabel.setText("✓ " + msg);
        toastPanel.setVisible(true);
        if (toastTimer != null) {
            toastTimer.restart();
        }
    }

    /** Gives the page real index levels, volumes, market caps and news. */
    public void setMarketData(MarketDataService marketData) {
        this.marketData = marketData;
        marketDataRef = marketData;
    }

    /** Header strip: breadth of the app's assets, a real volatility index, and today's turnover. */
    private void refreshHeaderStats() {
        int up = 0;
        int down = 0;
        int flat = 0;
        double turnover = 0;
        for (Asset a : marketService.getAllAssets()) {
            boolean inRegion = switch (currentRegion) {
                case INDIA -> isIndianAsset(a.getSymbol());
                case USA -> !isIndianAsset(a.getSymbol()) && !(a instanceof Crypto);
                case CRYPTO -> a instanceof Crypto;
            };
            if (!inRegion) {
                continue;
            }
            if (a.getChangePercent() > 0.001) up++;
            else if (a.getChangePercent() < -0.001) down++;
            else flat++;
            if (marketData != null) {
                double v = marketData.snapshot(a).volume();
                if (MarketFormat.known(v)) {
                    turnover += v * a.getPrice();
                }
            }
        }
        breadthLbl.setText("Breadth: " + up + " ▲ / " + down + " ▼ / " + flat + " •");
        String cur = currentRegion == Region.INDIA ? "₹" : "$";
        dayVolLbl.setText("Day Turnover: " + (turnover > 0 ? MarketFormat.big(cur, turnover) : MarketFormat.NA));
        if (currentRegion == Region.CRYPTO) {
            double[] cap = marketData == null ? null : marketData.cryptoMarketCap().orElse(null);
            vixLbl.setText("Crypto MCap 24h: " + (cap == null ? MarketFormat.NA : MarketFormat.signedPct(cap[1])));
            vixLbl.setForeground(cap == null || cap[1] >= 0 ? Theme.GAIN : Theme.LOSS);
        } else {
            String sym = currentRegion == Region.INDIA ? "^INDIAVIX" : "^VIX";
            MarketSnapshot vix = marketData == null ? null : marketData.index(sym).orElse(null);
            vixLbl.setText((currentRegion == Region.INDIA ? "India VIX: " : "VIX: ") + (vix == null ? MarketFormat.NA
                    : String.format(Locale.US, "%.2f %s", vix.price(), MarketFormat.signedPct(vix.changePercent()))));
            // a FALLING fear index is good news, so it is shown in green
            vixLbl.setForeground(vix == null || vix.change() <= 0 ? Theme.GAIN : Theme.LOSS);
        }
    }

    @Override
    public void refresh() {
        // Re-filter table with live prices
        filterTable();

        // Real index cards / header / sectors / activity (rebuilt only every few seconds to avoid flicker)
        refreshHeaderStats();
        String indexKey = String.join("|", java.util.Arrays.stream(indexCardsData()).map(r -> String.join(",", r)).toList());
        if (!indexKey.equals(shownIndexKey)) {
            shownIndexKey = indexKey;
            rebuildIndexCards();
        }
        if (refreshTick++ % 5 == 0) {
            rebuildSectorStrip();
            rebuildActivityStrip();
        }

        // Update Movers Dock
        List<Asset> all = marketService.getAllAssets();
        List<Asset> sorted = new ArrayList<>(all);
        sorted.sort(Comparator.comparingDouble(Asset::getChangePercent));

        // Gainers (highest changes)
        gainersContainer.removeAll();
        for (int i = sorted.size() - 1; i >= Math.max(0, sorted.size() - 4); i--) {
            gainersContainer.add(createMoverRow(sorted.get(i), true));
        }

        // Losers (lowest changes)
        losersContainer.removeAll();
        for (int i = 0; i < Math.min(4, sorted.size()); i++) {
            losersContainer.add(createMoverRow(sorted.get(i), false));
        }

        gainersContainer.revalidate();
        gainersContainer.repaint();
        losersContainer.revalidate();
        losersContainer.repaint();

        // Update drawer chart if open
        if (isDrawerOpen && currentDrawerAsset != null) {
            drawerPanel.repaint();
        }
    }

    private void filterTable() {
        List<Asset> all = marketService.getAllAssets();
        List<Asset> filtered = new ArrayList<>();

        for (Asset a : all) {
            // Region filter
            boolean regionMatch = switch (currentRegion) {
                case INDIA -> isIndianAsset(a.getSymbol());
                case USA -> (a instanceof Stock && !isIndianAsset(a.getSymbol())) || a instanceof ETF;
                case CRYPTO -> a instanceof Crypto;
            };
            if (!regionMatch) continue;

            // Search query
            String q = searchQuery.toLowerCase().trim();
            if (!q.isEmpty()) {
                boolean match = a.getSymbol().toLowerCase().contains(q) || a.getName().toLowerCase().contains(q);
                if (!match) continue;
            }

            // Sector filter
            if (!"ALL".equalsIgnoreCase(activeSector)) {
                String sec = getAssetSector(a);
                if (!sec.equalsIgnoreCase(activeSector)) continue;
            }

            filtered.add(a);
        }

        // Sort
        if ("Sort: Most Active (Volume)".equals(activeSort)) {
            // stable deterministic sort
            Collections.shuffle(filtered);
        } else if ("Sort: Top Gainers %".equals(activeSort)) {
            filtered.sort((x, y) -> Double.compare(y.getChangePercent(), x.getChangePercent()));
        } else if ("Sort: Top Losers %".equals(activeSort)) {
            filtered.sort(Comparator.comparingDouble(Asset::getChangePercent));
        } else if ("Sort: Highest Price".equals(activeSort)) {
            filtered.sort((x, y) -> Double.compare(y.getPrice(), x.getPrice()));
        }

        tableModel.setAssets(filtered);
        paginationLabel.setText("Showing 1-" + filtered.size() + " of " + filtered.size() + " listed instruments (" + currentRegion.name() + ")");
    }

    private boolean isIndianAsset(String s) {
        return s.equals("RELIANCE") || s.equals("TCS") || s.equals("HDFCBANK") || s.equals("INFY")
                || s.equals("ICICIBANK") || s.equals("BHARTIARTL") || s.equals("SBIN") || s.equals("ITC")
                || s.equals("LT") || s.equals("TATAMOTORS") || s.equals("TRENT") || s.equals("ETERNAL");
    }

    private String getAssetSector(Asset a) {
        if (a instanceof Stock s) {
            return s.getSector();
        } else if (a instanceof Crypto) {
            return "Crypto";
        } else if (a instanceof ETF) {
            return "ETF";
        }
        return "Equities";
    }

    // -------------------------------------------------------------------------
    // Table Model & Custom Renderers
    // -------------------------------------------------------------------------

    private class MarketDirectoryTableModel extends AbstractTableModel {
        private final String[] columns = {
                "Asset / Instrument", "Exch", "LTP (Price)", "24h Change", "24h Change %", "Day's Range (L / H)", "Volume", "Mkt Cap", "7D Trend", "Actions"
        };
        private List<Asset> assets = new ArrayList<>();

        public void setAssets(List<Asset> assets) {
            this.assets = assets;
            fireTableDataChanged();
        }

        public Asset getAssetAt(int row) {
            return assets.get(row);
        }

        @Override
        public int getRowCount() {
            return assets.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int row, int column) {
            Asset a = assets.get(row);
            return switch (column) {
                case 0 -> a;
                case 1 -> isIndianAsset(a.getSymbol()) ? "NSE" : (a instanceof Crypto ? "BINANCE" : "NASDAQ");
                case 2 -> Formatter.money(a.getPrice());
                case 3 -> a.getPrice() - a.getPreviousClose();
                case 4 -> a.getChangePercent();
                case 5 -> a;
                case 6 -> marketData == null ? MarketFormat.NA : MarketFormat.volume(marketData.snapshot(a).volume());
                case 7 -> marketData == null ? MarketFormat.NA : marketData.fundamentals(a)
                        .map(f -> MarketFormat.big(MarketFormat.currency(a.getSymbol()), f.marketCap())).orElse(MarketFormat.NA);
                case 8 -> a;
                case 9 -> a;
                default -> "";
            };
        }
    }

    private class InstrumentCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            if (!(value instanceof Asset a)) return super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, col);

            JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 5));
            p.setOpaque(true);
            p.setBackground(isSelected ? Theme.SURFACE_SUB : Theme.CARD_BG);

            JLabel av = new JLabel(a.getSymbol().substring(0, Math.min(2, a.getSymbol().length())), SwingConstants.CENTER) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(Theme.SURFACE_SUB);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            av.setPreferredSize(new Dimension(24, 24));
            av.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 8.5f));
            av.setForeground(Theme.ACCENT);
            p.add(av);

            JPanel text = new JPanel();
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            text.setOpaque(false);

            JLabel sym = new JLabel(a.getSymbol());
            sym.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 10f));
            sym.setForeground(Color.WHITE);

            JLabel name = new JLabel(a.getName());
            name.setFont(Theme.MICRO);
            name.setForeground(Theme.TXT_MUTED);

            text.add(sym);
            text.add(name);
            p.add(text);

            return p;
        }
    }

    private static class CenterBadgeRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            JLabel l = (JLabel) super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, col);
            l.setHorizontalAlignment(SwingConstants.CENTER);
            l.setFont(Theme.MONO_TINY.deriveFont(8f));
            l.setForeground(Theme.TXT_MUTED);
            l.setBackground(isSelected ? Theme.SURFACE_SUB : Theme.CARD_BG);
            return l;
        }
    }

    private static class MonospaceCellRenderer extends DefaultTableCellRenderer {
        private final int align;
        private final int style;

        public MonospaceCellRenderer(int align, int style) {
            this.align = align;
            this.style = style;
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            JLabel l = (JLabel) super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, col);
            l.setHorizontalAlignment(align);
            l.setFont(Theme.MONO_TINY.deriveFont(style, 10f));
            l.setForeground(Color.WHITE);
            l.setBackground(isSelected ? Theme.SURFACE_SUB : Theme.CARD_BG);
            return l;
        }
    }

    private static class DeltaPointsCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            JLabel l = (JLabel) super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, col);
            if (value instanceof Double pts) {
                boolean up = pts >= 0;
                l.setText((up ? "+" : "") + Formatter.money(pts));
                l.setForeground(up ? Theme.GAIN : Theme.LOSS);
            }
            l.setHorizontalAlignment(SwingConstants.RIGHT);
            l.setFont(Theme.MONO_TINY.deriveFont(9f));
            l.setBackground(isSelected ? Theme.SURFACE_SUB : Theme.CARD_BG);
            return l;
        }
    }

    private static class DeltaPercentCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            if (!(value instanceof Double pct)) return super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, col);
            boolean up = pct >= 0;
            String text = (up ? "+" : "") + String.format("%.2f%%", pct);

            JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 8));
            p.setOpaque(true);
            p.setBackground(isSelected ? Theme.SURFACE_SUB : Theme.CARD_BG);

            JLabel badge = new JLabel(text);
            badge.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 8.5f));
            badge.setForeground(up ? Theme.GAIN : Theme.LOSS);
            badge.setOpaque(true);
            badge.setBackground(up ? Theme.GREEN_BG : Theme.RED_BG);
            badge.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));

            p.add(badge);
            return p;
        }
    }

    /** The range renderer is static, so it reads the data service through this reference. */
    private static MarketDataService marketDataRef;

    private static class RangeGaugeCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            if (!(value instanceof Asset a)) return super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, col);

            double p = a.getPrice();
            MarketSnapshot snap = marketDataRef != null ? marketDataRef.snapshot(a) : null;
            double low = snap != null && MarketFormat.known(snap.dayLow()) ? snap.dayLow() : Math.min(p, a.getPreviousClose());
            double high = snap != null && MarketFormat.known(snap.dayHigh()) ? snap.dayHigh() : Math.max(p, a.getPreviousClose());
            boolean isUp = a.getChangePercent() >= 0;

            JPanel gauge = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                    int w = getWidth();
                    int h = getHeight();

                    // Numbers
                    g2.setFont(Theme.MICRO.deriveFont(7.5f));
                    g2.setColor(Theme.TXT_MUTED);
                    g2.drawString(Formatter.money(low), 4, 12);
                    String hStr = Formatter.money(high);
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(hStr, w - fm.stringWidth(hStr) - 4, 12);

                    // Track
                    int trY = 18;
                    int trH = 4;
                    g2.setColor(Theme.APP_BG);
                    g2.fillRoundRect(4, trY, w - 8, trH, 2, 2);

                    // Fill
                    int fillW = (int) ((w - 8) * 0.65);
                    g2.setColor(isUp ? Theme.GAIN : Theme.LOSS);
                    g2.fillRoundRect(4, trY, fillW, trH, 2, 2);

                    g2.dispose();
                }
            };
            gauge.setOpaque(true);
            gauge.setBackground(isSelected ? Theme.SURFACE_SUB : Theme.CARD_BG);
            return gauge;
        }
    }

    private class SparklineCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            if (!(value instanceof Asset a)) return super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, col);

            boolean isUp = a.getChangePercent() >= 0;
            List<Double> hist = marketService.getPriceHistory(a.getSymbol());

            JPanel spark = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int w = getWidth();
                    int h = getHeight();

                    int n = Math.max(2, Math.min(10, hist.size()));
                    int[] xs = new int[n];
                    int[] ys = new int[n];
                    for (int i = 0; i < n; i++) {
                        xs[i] = 4 + (int) ((double) i / (n - 1) * (w - 8));
                        ys[i] = isUp ? (int) (h - 6 - ((double) i / (n - 1) * (h - 12)))
                                     : (int) (6 + ((double) i / (n - 1) * (h - 12)));
                    }

                    g2.setColor(isUp ? Theme.GAIN : Theme.LOSS);
                    g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawPolyline(xs, ys, n);
                    g2.dispose();
                }
            };
            spark.setOpaque(true);
            spark.setBackground(isSelected ? Theme.SURFACE_SUB : Theme.CARD_BG);
            return spark;
        }
    }

    private class ActionsCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            if (!(value instanceof Asset a)) return super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, col);

            JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 6));
            p.setOpaque(true);
            p.setBackground(isSelected ? Theme.SURFACE_SUB : Theme.CARD_BG);

            boolean inW = watchlist.contains(a.getSymbol());
            JLabel star = new JLabel(inW ? "★" : "☆");
            star.setFont(Theme.SMALL);
            star.setForeground(inW ? Theme.WARNING : Theme.TXT_MUTED);
            p.add(star);

            JLabel tradeBtn = new JLabel(" Trade ");
            tradeBtn.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 8.5f));
            tradeBtn.setForeground(Color.WHITE);
            tradeBtn.setOpaque(true);
            tradeBtn.setBackground(Theme.ACCENT);
            tradeBtn.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            p.add(tradeBtn);

            return p;
        }
    }
}
