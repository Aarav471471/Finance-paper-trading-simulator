package com.papertrader.ui;

import javax.swing.BorderFactory;
import javax.swing.UIManager;
import javax.swing.border.Border;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

/**
 * PaperTrader 3D - Production Terminal Design System.
 * Matched precisely to the terminal design specification.
 */
public final class Theme {

    // Terminal Theme Palette
    public static final Color APP_BG = new Color(0x0B, 0x0F, 0x19);          // #0B0F19
    public static final Color SIDEBAR_BG = new Color(0x11, 0x18, 0x27);      // #111827
    public static final Color CARD_BG = new Color(0x17, 0x1F, 0x2E);         // #171F2E
    public static final Color SURFACE_SUB = new Color(0x1C, 0x26, 0x38);     // #1C2638
    public static final Color BORDER_CUSTOM = new Color(0x28, 0x34, 0x49);   // #283449
    public static final Color BORDER_SUBTLE = new Color(0x1E, 0x27, 0x38);   // subtle divider
    public static final Color BORDER_LIGHT = new Color(0x38, 0x48, 0x63);    // hover border
    public static final Color ACCENT = new Color(0x3B, 0x82, 0xF6);          // #3B82F6
    public static final Color GAIN = new Color(0x22, 0xC5, 0x5E);            // #22C55E
    public static final Color LOSS = new Color(0xEF, 0x44, 0x44);            // #EF4444
    public static final Color WARNING = new Color(0xF5, 0x9E, 0x0B);         // #F59E0B

    public static final Color TXT_PRIMARY = new Color(0xF8, 0xFA, 0xFC);     // #F8FAFC
    public static final Color TXT_SECONDARY = new Color(0x94, 0xA3, 0xB8);   // #94A3B8
    public static final Color TXT_MUTED = new Color(0x64, 0x74, 0x8B);       // #64748B

    // Compatibility Aliases
    public static final Color BACKGROUND = APP_BG;
    public static final Color SIDEBAR = SIDEBAR_BG;
    public static final Color CARD = CARD_BG;
    public static final Color CARD_HOVER = SURFACE_SUB;
    public static final Color CARD_HEADER = SURFACE_SUB;
    public static final Color BORDER = BORDER_CUSTOM;
    public static final Color TEXT = TXT_PRIMARY;
    public static final Color TEXT_PRIMARY = TXT_PRIMARY;
    public static final Color TEXT_SECONDARY = TXT_SECONDARY;
    public static final Color TEXT_MUTED = TXT_MUTED;
    public static final Color MUTED = TXT_MUTED;
    public static final Color MUTED_DARK = TXT_MUTED;
    public static final Color GREEN = GAIN;
    public static final Color RED = LOSS;
    public static final Color GOLD = WARNING;
    public static final Color CYAN = new Color(0x06, 0xB6, 0xD4);
    public static final Color PURPLE = new Color(0x8B, 0x5C, 0xF6);

    // Alpha Tint Backgrounds
    public static final Color GREEN_BG = new Color(34, 197, 94, 30);
    public static final Color RED_BG = new Color(239, 68, 68, 30);
    public static final Color ACCENT_BG = new Color(59, 130, 246, 30);
    public static final Color PURPLE_BG = new Color(139, 92, 246, 30);
    public static final Color GOLD_BG = new Color(245, 158, 11, 30);

    // Typography
    public static final Font TITLE = new Font("Inter", Font.BOLD, 18);
    public static final Font HEADING = new Font("Inter", Font.BOLD, 14);
    public static final Font SUBHEADING = new Font("Inter", Font.BOLD, 12);
    public static final Font BODY = new Font("Inter", Font.PLAIN, 12);
    public static final Font SMALL = new Font("Inter", Font.PLAIN, 11);
    public static final Font TINY = new Font("Inter", Font.PLAIN, 10);
    public static final Font MICRO = new Font("Inter", Font.PLAIN, 9);

    public static final Font MONO_TITLE = new Font("JetBrains Mono", Font.BOLD, 16);
    public static final Font MONO_BODY = new Font("JetBrains Mono", Font.PLAIN, 11);
    public static final Font MONO_SMALL = new Font("JetBrains Mono", Font.PLAIN, 10);
    public static final Font MONO_TINY = new Font("JetBrains Mono", Font.PLAIN, 9);
    public static final Font STAT_NUMBER = new Font("JetBrains Mono", Font.BOLD, 17);
    public static final Font BIG_NUMBER = new Font("JetBrains Mono", Font.BOLD, 18);

    private Theme() {
    }

    public static Color colorFor(double change) {
        if (change > 0) return GAIN;
        if (change < 0) return LOSS;
        return TXT_PRIMARY;
    }

    public static Color pillBgFor(double change) {
        if (change > 0) return GREEN_BG;
        if (change < 0) return RED_BG;
        return new Color(100, 116, 139, 25);
    }

    public static Border cardBorder() {
        return BorderFactory.createCompoundBorder(
                new RoundedBorder(BORDER_CUSTOM, 8, 1),
                BorderFactory.createEmptyBorder(10, 12, 10, 12));
    }

    public static Border cardBorder(int top, int left, int bottom, int right) {
        return BorderFactory.createCompoundBorder(
                new RoundedBorder(BORDER_CUSTOM, 8, 1),
                BorderFactory.createEmptyBorder(top, left, bottom, right));
    }

    public static Border terminalCardBorder() {
        return BorderFactory.createCompoundBorder(
                new RoundedBorder(BORDER_CUSTOM, 8, 1),
                BorderFactory.createEmptyBorder(8, 10, 8, 10));
    }

    public static void applyDefaults() {
        UIManager.put("Component.arc", 6);
        UIManager.put("Button.arc", 6);
        UIManager.put("TextComponent.arc", 6);
        UIManager.put("Panel.background", APP_BG);
        UIManager.put("Table.background", CARD_BG);
        UIManager.put("Table.rowHeight", 32);
        UIManager.put("Table.showHorizontalLines", true);
        UIManager.put("Table.gridColor", BORDER_CUSTOM);
        UIManager.put("Table.selectionBackground", SURFACE_SUB);
        UIManager.put("Table.selectionForeground", TXT_PRIMARY);
        UIManager.put("TableHeader.background", SIDEBAR_BG);
        UIManager.put("TableHeader.foreground", TXT_MUTED);
        UIManager.put("TableHeader.font", MONO_SMALL);
        UIManager.put("ScrollPane.background", CARD_BG);
        UIManager.put("ScrollPane.border", BorderFactory.createLineBorder(BORDER_CUSTOM, 1));
        UIManager.put("Button.default.background", ACCENT);
        UIManager.put("Button.default.foreground", Color.WHITE);
        UIManager.put("ScrollBar.thumbArc", 4);
        UIManager.put("ScrollBar.thumbInsets", new Insets(1, 1, 1, 1));
        UIManager.put("ScrollBar.track", new Color(0x11, 0x18, 0x27));
        UIManager.put("ScrollBar.thumb", new Color(0x28, 0x34, 0x49));
    }

    public static class RoundedBorder implements Border {
        private final Color color;
        private final int radius;
        private final int thickness;

        public RoundedBorder(Color color, int radius, int thickness) {
            this.color = color;
            this.radius = radius;
            this.thickness = thickness;
        }

        @Override
        public void paintBorder(java.awt.Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.setStroke(new java.awt.BasicStroke(thickness));
            g2.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(java.awt.Component c) {
            return new Insets(thickness, thickness, thickness, thickness);
        }

        @Override
        public boolean isBorderOpaque() {
            return false;
        }
    }
}
