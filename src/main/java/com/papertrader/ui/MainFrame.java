package com.papertrader.ui;

import com.papertrader.service.AuthService;
import com.papertrader.service.MarketService;
import com.papertrader.service.PortfolioService;
import com.papertrader.service.TradingService;

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
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The main window. It shows either the login screen or the app (sidebar + pages),
 * using a CardLayout to switch between them like a deck of cards.
 *
 * LOW COUPLING: MainFrame only talks to services. It never touches files or the price feed.
 */
public class MainFrame extends JFrame {

    private static final String LOGIN = "login";
    private static final String APP = "app";

    private final AuthService authService;
    private final CardLayout rootCards = new CardLayout();
    private final JPanel root = new JPanel(rootCards);
    private final CardLayout pageCards = new CardLayout();
    private final JPanel pageArea = new JPanel(pageCards);
    private final Map<String, BasePanel> pages = new LinkedHashMap<>();
    private final Map<String, JToggleButton> navButtons = new LinkedHashMap<>();
    private final JLabel userLabel = new JLabel();
    private BasePanel currentPage;

    public MainFrame(AuthService authService, MarketService marketService, TradingService tradingService,
                     PortfolioService portfolioService, double startingCash) {
        super("PaperTrader 3D - Finance Paper Trading Simulator");
        this.authService = authService;

        // Every page is stored as a BasePanel (the parent type). See refreshCurrentPage().
        pages.put("Dashboard", new DashboardPanel(authService, marketService, portfolioService, tradingService, startingCash));
        pages.put("Market", new MarketPanel(marketService));
        pages.put("Trade", new TradePanel(authService, marketService, tradingService));
        pages.put("Portfolio", new PortfolioPanel(authService, marketService, portfolioService));
        pages.put("History", new HistoryPanel(authService, tradingService));

        root.add(new LoginPanel(authService, this::showApp), LOGIN);
        root.add(buildAppShell(), APP);
        setContentPane(root);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1150, 720));
        setSize(1320, 820);
        setLocationRelativeTo(null);

        // Refresh the visible page once per second so numbers stay live.
        new Timer(1000, e -> refreshCurrentPage()).start();
    }

    private JPanel buildAppShell() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(Theme.SIDEBAR);
        sidebar.setBorder(BorderFactory.createEmptyBorder(24, 16, 20, 16));
        sidebar.setPreferredSize(new Dimension(210, 100));

        JLabel logo = new JLabel("PaperTrader 3D");
        logo.setFont(Theme.HEADING.deriveFont(19f));
        logo.setForeground(Theme.TEXT);
        logo.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(logo);
        sidebar.add(Box.createVerticalStrut(28));

        ButtonGroup group = new ButtonGroup();
        for (String name : pages.keySet()) {
            pageArea.add(pages.get(name), name);
            JToggleButton button = new JToggleButton(name);
            button.setHorizontalAlignment(SwingConstants.LEFT);
            button.setFont(Theme.BODY);
            button.setFocusPainted(false);
            button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
            button.setAlignmentX(Component.LEFT_ALIGNMENT);
            button.putClientProperty("JButton.buttonType", "borderless");
            button.addActionListener(e -> showPage(name));
            group.add(button);
            navButtons.put(name, button);
            sidebar.add(button);
            sidebar.add(Box.createVerticalStrut(6));
        }

        sidebar.add(Box.createVerticalGlue());
        userLabel.setForeground(Theme.MUTED);
        userLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(userLabel);
        sidebar.add(Box.createVerticalStrut(8));
        JButton logoutButton = new JButton("Log out");
        logoutButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        logoutButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        logoutButton.addActionListener(e -> logout());
        sidebar.add(logoutButton);

        JPanel shell = new JPanel(new BorderLayout());
        shell.add(sidebar, BorderLayout.WEST);
        shell.add(pageArea, BorderLayout.CENTER);
        return shell;
    }

    private void showApp() {
        userLabel.setText("Signed in as " + authService.getCurrentUser().getUsername());
        rootCards.show(root, APP);
        showPage("Dashboard");
    }

    public void showPage(String name) {
        pageCards.show(pageArea, name);
        navButtons.get(name).setSelected(true);
        currentPage = pages.get(name);
        currentPage.refresh();
    }

    private void refreshCurrentPage() {
        if (authService.isLoggedIn() && currentPage != null) {
            currentPage.refresh();   // RUNTIME POLYMORPHISM: the real page's refresh() runs
        }
    }

    private void logout() {
        authService.logout();
        currentPage = null;
        rootCards.show(root, LOGIN);
    }
}
