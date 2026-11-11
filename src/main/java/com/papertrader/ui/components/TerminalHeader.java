package com.papertrader.ui.components;

import com.papertrader.model.Asset;
import com.papertrader.service.MarketService;
import com.papertrader.ui.Theme;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/**
 * Compact Top Terminal Header Bar.
 * Matches HTML: Title + Breadcrumbs (left), Global search with ⌘K badge (center),
 * and LIVE status, live UTC clock, notification & avatar buttons (right).
 */
public class TerminalHeader extends JPanel {

    private final JLabel clockLabel = new JLabel("14:32:08 UTC");
    private final JTextField searchInput = new JTextField();
    private final JLabel avatarLabel = new JLabel("AD", JLabel.CENTER);
    private final JLabel liveText = new JLabel("LIVE");
    private JPanel liveBadge;
    private boolean feedLive = true;

    private static final DateTimeFormatter UTC_FMT = DateTimeFormatter.ofPattern("HH:mm:ss 'UTC'");

    public TerminalHeader(Consumer<String> onSearch) {
        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(Theme.APP_BG);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_CUSTOM),
                BorderFactory.createEmptyBorder(0, 12, 0, 12)));
        setPreferredSize(new Dimension(100, 36));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        // 1. Left: Title + Breadcrumbs
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        left.setOpaque(false);

        JLabel title = new JLabel("Dashboard");
        title.setFont(Theme.HEADING.deriveFont(Font.BOLD, 13f));
        title.setForeground(Color.WHITE);

        JLabel divider = new JLabel("│");
        divider.setFont(Theme.SMALL);
        divider.setForeground(Theme.BORDER_CUSTOM);

        JLabel breadcrumb = new JLabel("Home  ›  Dashboard");
        breadcrumb.setFont(Theme.TINY);
        breadcrumb.setForeground(Theme.TXT_MUTED);

        left.add(title);
        left.add(divider);
        left.add(breadcrumb);
        add(left, BorderLayout.WEST);

        // 2. Center: Global Search Input with ⌘K
        JPanel center = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 5));
        center.setOpaque(false);

        JPanel searchBox = new JPanel(new BorderLayout(4, 0));
        searchBox.setBackground(Theme.CARD_BG);
        searchBox.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 6, 1),
                BorderFactory.createEmptyBorder(2, 8, 2, 8)));
        searchBox.setPreferredSize(new Dimension(320, 26));

        JLabel searchIcon = new JLabel("⌕");
        searchIcon.setFont(new Font("SansSerif", Font.BOLD, 13));
        searchIcon.setForeground(Theme.TXT_MUTED);

        searchInput.setBackground(Theme.CARD_BG);
        searchInput.setForeground(Color.WHITE);
        searchInput.setCaretColor(Color.WHITE);
        searchInput.setFont(Theme.BODY.deriveFont(Font.PLAIN, 11f));
        searchInput.setBorder(null);
        searchInput.putClientProperty("JTextField.placeholderText", "Search stocks, crypto or symbols...");
        if (onSearch != null) {
            searchInput.addActionListener(e -> onSearch.accept(searchInput.getText().trim()));
        }

        JLabel kbd = new JLabel("⌘K");
        kbd.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 8f));
        kbd.setForeground(Theme.TXT_MUTED);
        kbd.setOpaque(true);
        kbd.setBackground(Theme.SURFACE_SUB);
        kbd.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 3, 1),
                BorderFactory.createEmptyBorder(1, 4, 1, 4)));

        searchBox.add(searchIcon, BorderLayout.WEST);
        searchBox.add(searchInput, BorderLayout.CENTER);
        searchBox.add(kbd, BorderLayout.EAST);
        center.add(searchBox);
        add(center, BorderLayout.CENTER);

        // 3. Right: Live Badge, UTC Clock, Bell, Avatar
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 5));
        right.setOpaque(false);

        // LIVE badge
        liveBadge = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(feedLive ? Theme.GAIN : Theme.WARNING);
                g2.fillOval(4, 7, 5, 5);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        liveBadge.setOpaque(true);
        liveBadge.setBackground(Theme.GREEN_BG);
        liveBadge.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(new Color(34, 197, 94, 80), 4, 1),
                BorderFactory.createEmptyBorder(1, 12, 1, 5)));

        liveText.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 9f));
        liveText.setForeground(Theme.GAIN);
        liveBadge.add(liveText);
        right.add(liveBadge);

        // Clock Display
        clockLabel.setFont(Theme.MONO_TINY);
        clockLabel.setForeground(Theme.TXT_SECONDARY);
        right.add(clockLabel);

        // Notification Bell Button
        JButton bellBtn = createIconButton("🔔");
        right.add(bellBtn);

        // User Avatar Circle
        JPanel avatar = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(59, 130, 246, 50));
                g2.fillOval(0, 0, 24, 24);
                g2.setColor(new Color(59, 130, 246, 120));
                g2.drawOval(0, 0, 23, 23);
                g2.dispose();
            }
        };
        avatar.setPreferredSize(new Dimension(24, 24));
        avatar.setOpaque(false);
        avatarLabel.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 10f));
        avatarLabel.setForeground(Theme.ACCENT);
        avatar.add(avatarLabel, BorderLayout.CENTER);
        right.add(avatar);

        add(right, BorderLayout.EAST);

        // Live Clock Timer (updates every 1s)
        Timer clockTimer = new Timer(1000, e -> updateClock());
        clockTimer.start();
        updateClock();
    }

    /** Green "LIVE" when at least one real price source answers, amber "SIMULATED" otherwise. */
    public void setFeedLive(boolean live) {
        if (live == feedLive) {
            return;
        }
        feedLive = live;
        liveText.setText(live ? "LIVE" : "SIMULATED");
        liveText.setForeground(live ? Theme.GAIN : Theme.WARNING);
        liveBadge.setBackground(live ? Theme.GREEN_BG : new Color(245, 158, 11, 30));
        liveBadge.revalidate();
        liveBadge.repaint();
    }

    private void updateClock() {
        clockLabel.setText(ZonedDateTime.now(ZoneOffset.UTC).format(UTC_FMT));
    }

    public void setUsername(String username) {
        if (username != null && !username.isEmpty()) {
            String initial = username.length() >= 2 ? username.substring(0, 2).toUpperCase() : username.toUpperCase();
            avatarLabel.setText(initial);
        }
    }

    private JButton createIconButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("SansSerif", Font.PLAIN, 12));
        btn.setForeground(Theme.TXT_SECONDARY);
        btn.setBackground(Theme.CARD_BG);
        btn.setBorder(new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 4, 1));
        btn.setPreferredSize(new Dimension(24, 24));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }
}
