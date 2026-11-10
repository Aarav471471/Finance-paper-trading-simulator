package com.papertrader.ui.components;

import com.papertrader.model.Asset;
import com.papertrader.service.MarketService;
import com.papertrader.ui.Theme;
import com.papertrader.util.Formatter;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Terminal Market Watchlist (Column 1 - 22%).
 * Features tabs [Watchlist, Gainers, Losers], symbol search filter,
 * sparkline curves, alert toggles, and simulated allocation ratio.
 */
public class WatchlistPanel extends JPanel {

    private final MarketService marketService;
    private final Consumer<Asset> onSelectAsset;

    private String activeTab = "ALL"; // ALL, GAINERS, LOSERS
    private String searchQuery = "";
    private String selectedSymbol = "NVDA";
    private final Set<String> alertSymbols = new HashSet<>();

    private final JPanel listContainer = new JPanel();
    private final JTextField searchField = new JTextField();
    private final JButton tabAll = new JButton("Watchlist");
    private final JButton tabGainers = new JButton("Gainers");
    private final JButton tabLosers = new JButton("Losers");

    public WatchlistPanel(MarketService marketService, Consumer<Asset> onSelectAsset) {
        this.marketService = marketService;
        this.onSelectAsset = onSelectAsset;

        setLayout(new BorderLayout(0, 6));
        setOpaque(false);
        setBackground(Theme.CARD_BG);
        setBorder(Theme.terminalCardBorder());

        // Select first asset by default
        List<Asset> all = marketService.getAllAssets();
        if (!all.isEmpty()) {
            selectedSymbol = all.get(0).getSymbol();
        }

        // 1. Header (Title + Add Button)
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_CUSTOM));
        header.setPreferredSize(new Dimension(100, 24));

        JLabel titleLabel = new JLabel("MARKET WATCHLIST");
        titleLabel.setFont(Theme.TINY.deriveFont(Font.BOLD, 10f));
        titleLabel.setForeground(Color.WHITE);
        header.add(titleLabel, BorderLayout.WEST);

        JButton addBtn = new JButton("+");
        addBtn.setFont(Theme.BODY.deriveFont(Font.BOLD, 12f));
        addBtn.setForeground(Theme.TXT_SECONDARY);
        addBtn.setBackground(Theme.SURFACE_SUB);
        addBtn.setBorder(new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 4, 1));
        addBtn.setPreferredSize(new Dimension(20, 18));
        addBtn.setFocusPainted(false);
        addBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addBtn.addActionListener(e -> {
            String input = JOptionPane.showInputDialog(this, "Enter ticker symbol to track:", "Add Asset", JOptionPane.PLAIN_MESSAGE);
            if (input != null && !input.trim().isEmpty()) {
                refreshList();
            }
        });
        header.add(addBtn, BorderLayout.EAST);

        // 2. Tabs + Search Field Container
        JPanel controls = new JPanel(new BorderLayout(0, 4));
        controls.setOpaque(false);

        // Tabs: Watchlist, Gainers, Losers
        JPanel tabBox = new JPanel(new GridLayout(1, 3, 2, 0));
        tabBox.setOpaque(false);
        tabBox.setBackground(Theme.SURFACE_SUB);
        tabBox.setBorder(new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 4, 1));

        styleTabBtn(tabAll, true);
        styleTabBtn(tabGainers, false);
        styleTabBtn(tabLosers, false);

        tabAll.addActionListener(e -> setTab("ALL"));
        tabGainers.addActionListener(e -> setTab("GAINERS"));
        tabLosers.addActionListener(e -> setTab("LOSERS"));

        tabBox.add(tabAll);
        tabBox.add(tabGainers);
        tabBox.add(tabLosers);
        controls.add(tabBox, BorderLayout.NORTH);

        // Search Input
        searchField.setBackground(Theme.SURFACE_SUB);
        searchField.setForeground(Theme.TXT_PRIMARY);
        searchField.setCaretColor(Color.WHITE);
        searchField.setFont(Theme.MONO_TINY);
        searchField.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 4, 1),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)));
        searchField.setPreferredSize(new Dimension(100, 22));
        searchField.putClientProperty("JTextField.placeholderText", "Filter symbols...");

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { updateSearch(); }
            public void removeUpdate(DocumentEvent e) { updateSearch(); }
            public void changedUpdate(DocumentEvent e) { updateSearch(); }
            private void updateSearch() {
                searchQuery = searchField.getText().trim().toLowerCase();
                refreshList();
            }
        });
        controls.add(searchField, BorderLayout.SOUTH);

        JPanel topContainer = new JPanel(new BorderLayout(0, 5));
        topContainer.setOpaque(false);
        topContainer.add(header, BorderLayout.NORTH);
        topContainer.add(controls, BorderLayout.SOUTH);
        add(topContainer, BorderLayout.NORTH);

        // 3. Asset Rows List
        listContainer.setLayout(new GridLayout(0, 1, 0, 2));
        listContainer.setOpaque(false);

        JScrollPane scroll = new JScrollPane(listContainer);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(14);
        add(scroll, BorderLayout.CENTER);

        // 4. Mini Allocation Summary at bottom
        JPanel footer = buildMiniAllocationFooter();
        add(footer, BorderLayout.SOUTH);

        refreshList();
    }

    private void styleTabBtn(JButton btn, boolean active) {
        btn.setFont(Theme.TINY.deriveFont(Font.BOLD, 9f));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (active) {
            btn.setBackground(Theme.ACCENT);
            btn.setForeground(Color.WHITE);
            btn.setBorder(new Theme.RoundedBorder(Theme.ACCENT, 4, 1));
        } else {
            btn.setBackground(Theme.SURFACE_SUB);
            btn.setForeground(Theme.TXT_MUTED);
            btn.setBorder(BorderFactory.createEmptyBorder());
        }
    }

    private void setTab(String tab) {
        this.activeTab = tab;
        styleTabBtn(tabAll, "ALL".equals(tab));
        styleTabBtn(tabGainers, "GAINERS".equals(tab));
        styleTabBtn(tabLosers, "LOSERS".equals(tab));
        refreshList();
    }

    private JPanel buildMiniAllocationFooter() {
        JPanel footer = new JPanel(new BorderLayout(0, 3));
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER_CUSTOM));
        footer.setPreferredSize(new Dimension(100, 32));

        JPanel labelRow = new JPanel(new BorderLayout());
        labelRow.setOpaque(false);

        JLabel left = new JLabel("SIMULATED RATIO");
        left.setFont(Theme.MICRO.deriveFont(Font.BOLD, 9f));
        left.setForeground(Theme.TXT_MUTED);

        JLabel right = new JLabel("62% EQ · 38% ALT");
        right.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 9f));
        right.setForeground(Color.WHITE);

        labelRow.add(left, BorderLayout.WEST);
        labelRow.add(right, BorderLayout.EAST);

        // Segmented Progress Bar
        JPanel bar = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();

                g2.setColor(Theme.SURFACE_SUB);
                g2.fillRoundRect(0, 0, w, h, 3, 3);

                int w1 = (int) (w * 0.624);
                int w2 = (int) (w * 0.248);
                int w3 = w - w1 - w2;

                g2.setColor(Theme.ACCENT);
                g2.fillRoundRect(0, 0, w1, h, 3, 3);
                g2.setColor(Theme.GAIN);
                g2.fillRect(w1, 0, w2, h);
                g2.setColor(Theme.TXT_MUTED);
                g2.fillRoundRect(w1 + w2, 0, w3, h, 3, 3);

                g2.dispose();
            }
        };
        bar.setPreferredSize(new Dimension(100, 5));
        bar.setOpaque(false);

        footer.add(labelRow, BorderLayout.NORTH);
        footer.add(bar, BorderLayout.SOUTH);
        return footer;
    }

    public void selectSymbol(String symbol) {
        this.selectedSymbol = symbol;
        marketService.findBySymbol(symbol).ifPresent(asset -> {
            if (onSelectAsset != null) {
                onSelectAsset.accept(asset);
            }
        });
        refreshList();
    }

    public void refreshList() {
        listContainer.removeAll();

        List<Asset> all = marketService.getAllAssets();
        for (Asset a : all) {
            boolean isGainer = a.getChangePercent() >= 0;
            if ("GAINERS".equals(activeTab) && !isGainer) continue;
            if ("LOSERS".equals(activeTab) && isGainer) continue;

            if (!searchQuery.isEmpty()) {
                String sym = a.getSymbol().toLowerCase();
                String nm = a.getName().toLowerCase();
                if (!sym.contains(searchQuery) && !nm.contains(searchQuery)) continue;
            }

            WatchlistRow row = new WatchlistRow(a);
            listContainer.add(row);
        }

        listContainer.revalidate();
        listContainer.repaint();
    }

    private class WatchlistRow extends JPanel {
        private final Asset asset;
        private boolean hovered = false;

        WatchlistRow(Asset asset) {
            this.asset = asset;
            setLayout(new BorderLayout());
            setOpaque(false);
            setPreferredSize(new Dimension(100, 36));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

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

                @Override
                public void mouseClicked(MouseEvent e) {
                    selectedSymbol = asset.getSymbol();
                    if (onSelectAsset != null) {
                        onSelectAsset.accept(asset);
                    }
                    WatchlistPanel.this.refreshList();
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
            boolean isSelected = asset.getSymbol().equalsIgnoreCase(selectedSymbol);

            // Row Highlight
            if (isSelected) {
                g2.setColor(new Color(28, 38, 56, 180)); // surfaceSub/30
                g2.fillRoundRect(0, 0, w, h, 4, 4);
                // Left accent border line
                g2.setColor(Theme.ACCENT);
                g2.fillRect(0, 3, 2, h - 6);
            } else if (hovered) {
                g2.setColor(Theme.SURFACE_SUB);
                g2.fillRoundRect(0, 0, w, h, 4, 4);
            }

            int curX = 6;
            int baseline = 16;

            // Symbol + Dot
            g2.setFont(Theme.MONO_BODY.deriveFont(Font.BOLD, 11f));
            g2.setColor(isSelected ? Theme.ACCENT : Color.WHITE);
            g2.drawString(asset.getSymbol(), curX, baseline);
            curX += g2.getFontMetrics().stringWidth(asset.getSymbol()) + 4;

            boolean isCrypto = "Crypto".equalsIgnoreCase(asset.getType());
            if (isCrypto) {
                // 24/7 badge
                g2.setColor(new Color(245, 158, 11, 25));
                g2.fillRoundRect(curX, baseline - 8, 22, 10, 2, 2);
                g2.setColor(Theme.WARNING);
                g2.drawRoundRect(curX, baseline - 8, 22, 10, 2, 2);
                g2.setFont(Theme.MONO_TINY.deriveFont(Font.PLAIN, 7f));
                g2.drawString("24/7", curX + 3, baseline);
                curX += 26;
            } else {
                // Green live dot
                g2.setColor(Theme.GAIN);
                g2.fillOval(curX, baseline - 5, 4, 4);
                curX += 8;
            }

            // Company Name (subtext)
            g2.setFont(Theme.MICRO);
            g2.setColor(Theme.TXT_MUTED);
            String nameStr = asset.getName();
            if (nameStr.length() > 10) nameStr = nameStr.substring(0, 10) + "...";
            g2.drawString(nameStr, 6, 29);

            // Mini Sparkline in Center
            int sparkW = 32;
            int sparkH = 10;
            int sparkX = (w - sparkW) / 2 + 10;
            int sparkY = (h - sparkH) / 2;
            boolean up = asset.getChangePercent() >= 0;

            g2.setColor(up ? Theme.GAIN : Theme.LOSS);
            g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            if (up) {
                g2.drawLine(sparkX, sparkY + 8, sparkX + 8, sparkY + 7);
                g2.drawLine(sparkX + 8, sparkY + 7, sparkX + 16, sparkY + 8);
                g2.drawLine(sparkX + 16, sparkY + 8, sparkX + 24, sparkY + 4);
                g2.drawLine(sparkX + 24, sparkY + 4, sparkX + 32, sparkY + 1);
            } else {
                g2.drawLine(sparkX, sparkY + 2, sparkX + 8, sparkY + 3);
                g2.drawLine(sparkX + 8, sparkY + 3, sparkX + 16, sparkY + 6);
                g2.drawLine(sparkX + 16, sparkY + 6, sparkX + 24, sparkY + 5);
                g2.drawLine(sparkX + 24, sparkY + 5, sparkX + 32, sparkY + 9);
            }

            // Right side: Price + Change % + Alert Icon
            int rightX = w - 6;

            // Alert bell icon
            boolean hasAlert = alertSymbols.contains(asset.getSymbol());
            g2.setColor(hasAlert ? Theme.WARNING : Theme.TXT_MUTED);
            int bellX = rightX - 10;
            g2.drawOval(bellX, 10, 8, 8);
            if (hasAlert) {
                g2.fillOval(bellX + 2, 12, 4, 4);
            }

            // Price & Change
            String priceStr = Formatter.money(asset.getPrice());
            String chgStr = (up ? "+" : "") + String.format("%.2f%%", asset.getChangePercent());

            g2.setFont(Theme.MONO_BODY.deriveFont(Font.BOLD, 10f));
            g2.setColor(Color.WHITE);
            FontMetrics pfm = g2.getFontMetrics();
            g2.drawString(priceStr, bellX - 6 - pfm.stringWidth(priceStr), baseline);

            g2.setFont(Theme.MONO_TINY);
            g2.setColor(up ? Theme.GAIN : Theme.LOSS);
            FontMetrics cfm = g2.getFontMetrics();
            g2.drawString(chgStr, bellX - 6 - cfm.stringWidth(chgStr), 29);

            g2.dispose();
        }
    }
}
