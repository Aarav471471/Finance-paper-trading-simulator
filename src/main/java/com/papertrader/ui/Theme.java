package com.papertrader.ui;

import javax.swing.BorderFactory;
import javax.swing.UIManager;
import javax.swing.border.Border;
import java.awt.Color;
import java.awt.Font;

/** All colours and fonts in one place, so the whole app has one consistent dark look. */
public final class Theme {

    public static final Color BACKGROUND = new Color(0x0E1117);
    public static final Color SIDEBAR = new Color(0x131722);
    public static final Color CARD = new Color(0x1A1F2C);
    public static final Color BORDER = new Color(0x2A3142);
    public static final Color TEXT = new Color(0xE6E9EF);
    public static final Color MUTED = new Color(0x8A93A6);
    public static final Color ACCENT = new Color(0x4F8CFF);
    public static final Color GREEN = new Color(0x22C55E);
    public static final Color RED = new Color(0xEF4444);
    public static final Color GOLD = new Color(0xF5B942);

    public static final Font TITLE = new Font(Font.SANS_SERIF, Font.BOLD, 24);
    public static final Font HEADING = new Font(Font.SANS_SERIF, Font.BOLD, 16);
    public static final Font BODY = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
    public static final Font SMALL = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    public static final Font BIG_NUMBER = new Font(Font.SANS_SERIF, Font.BOLD, 22);

    private Theme() {
    }

    /** Green for gains, red for losses. */
    public static Color colorFor(double change) {
        if (change > 0) {
            return GREEN;
        }
        if (change < 0) {
            return RED;
        }
        return TEXT;
    }

    public static Border cardBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1, true),
                BorderFactory.createEmptyBorder(14, 16, 14, 16));
    }

    /** Extra FlatLaf settings (rounded corners, table look). Called once at start-up. */
    public static void applyDefaults() {
        UIManager.put("Component.arc", 10);
        UIManager.put("Button.arc", 10);
        UIManager.put("TextComponent.arc", 10);
        UIManager.put("Panel.background", BACKGROUND);
        UIManager.put("Table.background", CARD);
        UIManager.put("Table.rowHeight", 30);
        UIManager.put("Table.showHorizontalLines", true);
        UIManager.put("Table.gridColor", BORDER);
        UIManager.put("Table.selectionBackground", new Color(0x24324F));
        UIManager.put("TableHeader.background", SIDEBAR);
        UIManager.put("ScrollPane.background", CARD);
        UIManager.put("Button.default.background", ACCENT);
        UIManager.put("Button.default.foreground", Color.WHITE);
    }
}
