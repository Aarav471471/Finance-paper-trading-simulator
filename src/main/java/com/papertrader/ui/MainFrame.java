package com.papertrader.ui;

import com.papertrader.interfaces.PriceFeed;
import com.papertrader.service.AppContext;
import com.papertrader.service.AuthService;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Terminal Main Window Shell.
 * Features fixed 220px sidebar matching the production terminal mockup:
 * - Brand header with view_in_ar glyph & v4.2 PRO badge
 * - Navigation items (Dashboard, Markets, Trade, Portfolio, History,
 *   Analytics, Backtest, Auto-Trade, Leaderboard)
 * - Live Feeds connection card (Binance & Yahoo Fin connected)
 * - Settings & Help links
 * - User Profile with SIM ACTIVE & PAPER badges
 */
public class MainFrame extends JFrame {

    private static final String LOGIN = "login";
    private static final String APP = "app";

    private final AppContext ctx;
    private final AuthService authService;
    private int secondsSinceSnapshot = 0;
    private final CardLayout rootCards = new CardLayout();
    private final JPanel root = new JPanel(rootCards);
    private final CardLayout pageCards = new CardLayout();
    private final JPanel pageArea = new JPanel(pageCards);
    private final Map<String, BasePanel> pages = new LinkedHashMap<>();
    private final Map<String, SidebarNavButton> navButtons = new LinkedHashMap<>();

    private final JLabel userAvatarLabel = new JLabel("AD", SwingConstants.CENTER);
    private final JLabel usernameLabel = new JLabel("Admin");
    private final JLabel binanceStatus = new JLabel("Connected");
    private final JLabel yahooStatus = new JLabel("Connected");
    private final PriceFeed priceFeed;
    private BasePanel currentPage;

    /** @param ctx all the services, built once in App (the composition root) */
    public MainFrame(AppContext ctx) {
        super("PaperTrader 3D - Production Simulation Terminal");
        this.ctx = ctx;
        this.authService = ctx.auth();
        this.priceFeed = ctx.priceFeed();
        AuthService authService = ctx.auth();
        var marketService = ctx.market();
        var tradingService = ctx.trading();
        var portfolioService = ctx.portfolio();
        double startingCash = ctx.startingCash();

        TradePanel tradePanel = new TradePanel(authService, marketService, tradingService, portfolioService);
        tradePanel.setChartService(ctx.charts());
        tradePanel.setMarketData(ctx.marketData());
        StockDetailsPanel stockDetailsPanel = new StockDetailsPanel(authService, marketService,
                tradingService, portfolioService, target -> handleNavigation(target, tradePanel, null));
        stockDetailsPanel.setChartService(ctx.charts());
        stockDetailsPanel.setMarketData(ctx.marketData());

        Consumer<String> navHandler = target -> handleNavigation(target, tradePanel, stockDetailsPanel);

        DashboardPanel dashboardPanel = new DashboardPanel(authService, marketService, portfolioService,
                tradingService, startingCash, navHandler);
        dashboardPanel.setDataSources(ctx.analytics(), ctx.charts());

        pages.put("Dashboard", dashboardPanel);
        MarketPanel marketPanel = new MarketPanel(marketService, navHandler);
        marketPanel.setMarketData(ctx.marketData());
        pages.put("Markets", marketPanel);
        pages.put("Stock Details", stockDetailsPanel);
        pages.put("Trade", tradePanel);
        PortfolioPanel portfolioPanel = new PortfolioPanel(authService, marketService, portfolioService, tradingService, navHandler);
        portfolioPanel.setDataSources(ctx.analytics(), ctx.charts(), startingCash);
        pages.put("Portfolio", portfolioPanel);
        pages.put("History", new HistoryPanel(authService, tradingService, marketService, portfolioService, navHandler));
        pages.put("Analytics", new AnalyticsPanel(authService, portfolioService, tradingService, ctx.analytics(), startingCash));
        pages.put("Backtest", new BacktestPanel(marketService, ctx.charts(), ctx.fees()));
        pages.put("Auto-Trade", new AutoTradePanel(authService, marketService, ctx.bots()));
        pages.put("Leaderboard", new LeaderboardPanel(authService, ctx.leaderboard(), tradingService, ctx.analytics(),
                ctx.bots(), startingCash));

        root.add(new LoginPanel(authService, this::showApp), LOGIN);
        root.add(buildAppShell(), APP);
        setContentPane(root);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1200, 750));
        setSize(1380, 840);
        setLocationRelativeTo(null);

        new Timer(1000, e -> refreshCurrentPage()).start();
    }

    private JPanel buildAppShell() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(Theme.SIDEBAR_BG);
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER_CUSTOM));
        sidebar.setPreferredSize(new Dimension(220, 100));

        // 1. Top Logo Header (h-12 / 48px)
        JPanel logoBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 10));
        logoBar.setOpaque(false);
        logoBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_CUSTOM));
        logoBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));

        // Icon Box (w-7 h-7)
        JPanel iconBox = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(59, 130, 246, 35));
                g2.fillRoundRect(0, 0, 26, 26, 4, 4);
                g2.setColor(new Color(59, 130, 246, 100));
                g2.drawRoundRect(0, 0, 25, 25, 4, 4);

                // view_in_ar 3D isometric cube
                int cx = 13, cy = 13;
                g2.setColor(Theme.ACCENT);
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRect(cx - 5, cy - 5, 10, 10);
                g2.drawLine(cx - 5, cy - 5, cx + 5, cy + 5);
                g2.dispose();
            }
        };
        iconBox.setPreferredSize(new Dimension(26, 26));
        iconBox.setOpaque(false);
        logoBar.add(iconBox);

        JLabel logoText = new JLabel("PaperTrader");
        logoText.setFont(Theme.HEADING.deriveFont(Font.BOLD, 13f));
        logoText.setForeground(Color.WHITE);
        logoBar.add(logoText);

        JLabel proBadge = new JLabel("v4.2 PRO");
        proBadge.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 8f));
        proBadge.setForeground(Theme.ACCENT);
        proBadge.setOpaque(true);
        proBadge.setBackground(Theme.ACCENT_BG);
        proBadge.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(new Color(59, 130, 246, 80), 3, 1),
                BorderFactory.createEmptyBorder(1, 4, 1, 4)));
        logoBar.add(proBadge);
        sidebar.add(logoBar);
        sidebar.add(Box.createVerticalStrut(6));

        // 2. Navigation Items
        ButtonGroup group = new ButtonGroup();
        for (String name : pages.keySet()) {
            pageArea.add(pages.get(name), name);
            SidebarNavButton btn = new SidebarNavButton(name);
            btn.setAlignmentX(Component.LEFT_ALIGNMENT);
            btn.addActionListener(e -> showPage(name));
            group.add(btn);
            navButtons.put(name, btn);
            sidebar.add(btn);
            sidebar.add(Box.createVerticalStrut(2));
        }

        // 3. Live Feeds Section
        sidebar.add(Box.createVerticalStrut(10));
        JPanel feedsBox = buildLiveFeedsSection();
        sidebar.add(feedsBox);

        sidebar.add(Box.createVerticalGlue());

        // 4. Sidebar Bottom Section: Settings + Help + User Profile
        JPanel bottomSection = buildSidebarBottom();
        sidebar.add(bottomSection);

        JPanel shell = new JPanel(new BorderLayout());
        shell.add(sidebar, BorderLayout.WEST);
        shell.add(pageArea, BorderLayout.CENTER);
        return shell;
    }

    private JPanel buildLiveFeedsSection() {
        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setOpaque(false);
        container.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(40, 52, 73, 140)),
                BorderFactory.createEmptyBorder(8, 12, 4, 12)));
        container.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));

        JLabel title = new JLabel("LIVE FEEDS");
        title.setFont(Theme.MICRO.deriveFont(Font.BOLD, 8f));
        title.setForeground(Theme.TXT_MUTED);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        container.add(title);
        container.add(Box.createVerticalStrut(5));

        container.add(createFeedRow("BINANCE", binanceStatus));
        container.add(Box.createVerticalStrut(3));
        container.add(createFeedRow("YAHOO FIN", yahooStatus));

        return container;
    }

    private JPanel createFeedRow(String source, JLabel status) {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(Theme.SURFACE_SUB);
        row.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 4, 1),
                BorderFactory.createEmptyBorder(3, 6, 3, 6)));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));

        JLabel src = new JLabel(source);
        src.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 9f));
        src.setForeground(Theme.TXT_SECONDARY);

        JPanel statP = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        statP.setOpaque(false);

        JLabel dot = new JLabel("●");
        dot.setFont(new Font("SansSerif", Font.BOLD, 8));
        dot.setForeground(Theme.GAIN);

        status.setFont(Theme.MONO_TINY.deriveFont(Font.PLAIN, 8f));
        status.setForeground(Theme.GAIN);

        statP.add(dot);
        statP.add(status);

        row.add(src, BorderLayout.WEST);
        row.add(statP, BorderLayout.EAST);
        return row;
    }

    private JPanel buildSidebarBottom() {
        JPanel bottom = new JPanel();
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.setOpaque(false);
        bottom.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER_CUSTOM),
                BorderFactory.createEmptyBorder(6, 8, 8, 8)));
        bottom.setMaximumSize(new Dimension(Integer.MAX_VALUE, 134));

        // Settings link
        bottom.add(createBottomLink("⚙ Settings", () -> {}));
        bottom.add(Box.createVerticalStrut(2));
        bottom.add(createBottomLink("? Help & Support", () -> {}));
        bottom.add(Box.createVerticalStrut(2));
        bottom.add(createBottomLink("↪ Log out", this::logout));
        bottom.add(Box.createVerticalStrut(6));

        // User Profile Block
        JPanel userBox = new JPanel(new BorderLayout(8, 0));
        userBox.setOpaque(false);
        userBox.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(40, 52, 73, 140)),
                BorderFactory.createEmptyBorder(6, 4, 2, 4)));

        // Avatar
        JPanel av = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(59, 130, 246, 45));
                g2.fillOval(0, 0, 26, 26);
                g2.setColor(new Color(59, 130, 246, 120));
                g2.drawOval(0, 0, 25, 25);
                g2.dispose();
            }
        };
        av.setPreferredSize(new Dimension(26, 26));
        av.setOpaque(false);
        userAvatarLabel.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 10f));
        userAvatarLabel.setForeground(Theme.ACCENT);
        av.add(userAvatarLabel, BorderLayout.CENTER);
        userBox.add(av, BorderLayout.WEST);

        // Name + Sim Active
        JPanel nameBox = new JPanel();
        nameBox.setLayout(new BoxLayout(nameBox, BoxLayout.Y_AXIS));
        nameBox.setOpaque(false);

        usernameLabel.setFont(Theme.BODY.deriveFont(Font.BOLD, 11f));
        usernameLabel.setForeground(Color.WHITE);

        JLabel statusLbl = new JLabel("SIM ACTIVE");
        statusLbl.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 8f));
        statusLbl.setForeground(Theme.GAIN);

        nameBox.add(usernameLabel);
        nameBox.add(statusLbl);
        userBox.add(nameBox, BorderLayout.CENTER);

        // PAPER badge
        JLabel paperBadge = new JLabel("PAPER");
        paperBadge.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 8f));
        paperBadge.setForeground(Theme.GAIN);
        paperBadge.setOpaque(true);
        paperBadge.setBackground(Theme.GREEN_BG);
        paperBadge.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(new Color(34, 197, 94, 80), 3, 1),
                BorderFactory.createEmptyBorder(1, 4, 1, 4)));
        userBox.add(paperBadge, BorderLayout.EAST);

        bottom.add(userBox);
        return bottom;
    }

    private JButton createBottomLink(String text, Runnable action) {
        JButton btn = new JButton(text);
        btn.setFont(Theme.BODY.deriveFont(Font.PLAIN, 11f));
        btn.setForeground(Theme.TXT_SECONDARY);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        btn.addActionListener(e -> action.run());
        return btn;
    }

    private void showApp() {
        String uname = authService.getCurrentUser().getUsername();
        usernameLabel.setText(uname);
        userAvatarLabel.setText(uname.length() >= 2 ? uname.substring(0, 2).toUpperCase() : uname.toUpperCase());
        ctx.analytics().recordSnapshot(authService.getCurrentUser());
        rootCards.show(root, APP);
        showPage("Dashboard");
    }

    public void showPage(String name) {
        if ("StockDetails".equals(name)) {
            name = "Stock Details";
        }
        pageCards.show(pageArea, name);
        SidebarNavButton btn = navButtons.get(name);
        if (btn != null) {
            btn.setSelected(true);
        }
        currentPage = pages.get(name);
        if (currentPage != null) {
            currentPage.refresh();
        }
    }

    private void handleNavigation(String target, TradePanel tradePanel, StockDetailsPanel stockDetailsPanel) {
        if (target == null) return;
        if (target.startsWith("Trade:")) {
            String symbol = target.substring(6);
            showPage("Trade");
            tradePanel.selectAssetBySymbol(symbol);
        } else if (target.startsWith("StockDetails:")) {
            String symbol = target.substring(13);
            if (stockDetailsPanel != null) {
                stockDetailsPanel.setAssetBySymbol(symbol);
            }
            showPage("Stock Details");
        } else if ("StockDetails".equals(target) || "Stock Details".equals(target)) {
            showPage("Stock Details");
        } else {
            showPage(target);
        }
    }

    private void refreshCurrentPage() {
        String st = priceFeed.getStatusText();
        boolean hasBinance = st.toLowerCase().contains("binance: live");
        boolean hasYahoo = st.toLowerCase().contains("yahoo finance: live");

        binanceStatus.setText(hasBinance ? "Connected" : "Simulated");
        binanceStatus.setForeground(hasBinance ? Theme.GAIN : Theme.WARNING);

        yahooStatus.setText(hasYahoo ? "Connected" : "Simulated");
        yahooStatus.setForeground(hasYahoo ? Theme.GAIN : Theme.WARNING);

        // every 15 seconds, save the account value for the Analytics page
        if (authService.isLoggedIn() && ++secondsSinceSnapshot >= 15) {
            secondsSinceSnapshot = 0;
            ctx.analytics().recordSnapshot(authService.getCurrentUser());
        }

        if (authService.isLoggedIn() && currentPage != null) {
            currentPage.refresh();
        }
    }

    private void logout() {
        ctx.bots().stopAll(authService.getCurrentUser());
        authService.logout();
        currentPage = null;
        rootCards.show(root, LOGIN);
    }

    private static class SidebarNavButton extends JToggleButton {
        private final String label;
        private boolean hovered = false;

        SidebarNavButton(String text) {
            super(text);
            this.label = text;
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(204, 32));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hovered = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hovered = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            boolean active = isSelected();

            if (active) {
                g2.setColor(new Color(59, 130, 246, 38)); // accent/15
                g2.fillRoundRect(8, 2, w - 16, h - 4, 6, 6);
                g2.setColor(new Color(59, 130, 246, 100));
                g2.drawRoundRect(8, 2, w - 16, h - 4, 6, 6);
            } else if (hovered) {
                g2.setColor(new Color(28, 38, 56, 160)); // surfaceSub/70
                g2.fillRoundRect(8, 2, w - 16, h - 4, 6, 6);
            }

            int iconX = 20;
            int iconY = h / 2;
            Color iconColor = active ? Theme.ACCENT : (hovered ? Color.WHITE : Theme.TXT_SECONDARY);
            paintNavIcon(g2, label, iconX, iconY, iconColor);

            g2.setFont(Theme.BODY.deriveFont(active ? Font.BOLD : Font.PLAIN, 12f));
            g2.setColor(active ? Color.WHITE : (hovered ? Color.WHITE : Theme.TXT_SECONDARY));
            g2.drawString(label, 42, h / 2 + 4);

            g2.dispose();
        }

        private void paintNavIcon(Graphics2D g2, String name, int cx, int cy, Color c) {
            g2.setColor(c);
            g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            switch (name) {
                case "Dashboard" -> {
                    g2.drawRoundRect(cx - 6, cy - 6, 4, 4, 1, 1);
                    g2.drawRoundRect(cx + 1, cy - 6, 4, 4, 1, 1);
                    g2.drawRoundRect(cx - 6, cy + 1, 4, 4, 1, 1);
                    g2.drawRoundRect(cx + 1, cy + 1, 4, 4, 1, 1);
                }
                case "Markets" -> {
                    g2.drawLine(cx - 6, cy + 3, cx - 2, cy - 1);
                    g2.drawLine(cx - 2, cy - 1, cx + 1, cy + 1);
                    g2.drawLine(cx + 1, cy + 1, cx + 6, cy - 5);
                }
                case "Stock Details" -> {
                    g2.drawRect(cx - 3, cy - 4, 6, 8);
                    g2.drawLine(cx, cy - 7, cx, cy - 4);
                    g2.drawLine(cx, cy + 4, cx, cy + 7);
                }
                case "Trade" -> {
                    g2.drawLine(cx - 5, cy - 2, cx + 5, cy - 2);
                    g2.drawLine(cx + 2, cy - 5, cx + 5, cy - 2);
                    g2.drawLine(cx - 5, cy + 2, cx + 5, cy + 2);
                    g2.drawLine(cx - 2, cy + 5, cx - 5, cy + 2);
                }
                case "Portfolio" -> {
                    g2.drawRoundRect(cx - 6, cy - 2, 12, 8, 2, 2);
                    g2.drawArc(cx - 3, cy - 5, 6, 5, 0, 180);
                }
                case "History" -> {
                    g2.drawOval(cx - 6, cy - 6, 12, 12);
                    g2.drawLine(cx, cy - 3, cx, cy);
                    g2.drawLine(cx, cy, cx + 3, cy + 1);
                }
                case "Analytics" -> {
                    g2.drawLine(cx - 6, cy + 6, cx - 6, cy + 1);
                    g2.drawLine(cx - 2, cy + 6, cx - 2, cy - 3);
                    g2.drawLine(cx + 2, cy + 6, cx + 2, cy - 1);
                    g2.drawLine(cx + 6, cy + 6, cx + 6, cy - 6);
                }
                case "Backtest" -> {
                    g2.drawArc(cx - 6, cy - 6, 12, 12, 60, 270);
                    g2.drawLine(cx + 3, cy - 6, cx + 4, cy - 2);
                    g2.drawLine(cx + 4, cy - 2, cx + 0, cy - 2);
                }
                case "Auto-Trade" -> {
                    g2.drawRoundRect(cx - 6, cy - 3, 12, 9, 3, 3);
                    g2.drawLine(cx, cy - 3, cx, cy - 6);
                    g2.fillOval(cx - 3, cy, 2, 2);
                    g2.fillOval(cx + 2, cy, 2, 2);
                }
                case "Leaderboard" -> {
                    g2.drawRect(cx - 6, cy, 4, 6);
                    g2.drawRect(cx - 2, cy - 5, 4, 11);
                    g2.drawRect(cx + 2, cy - 2, 4, 8);
                }
                default -> g2.drawOval(cx - 3, cy - 3, 6, 6);
            }
        }
    }
}
