package com.papertrader.ui.components;

import com.papertrader.ui.Theme;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Terminal Financial Summary Card.
 * Conforms precisely to the terminal mockup with icon badges, mono values,
 * percentage indicators, contextual descriptions, and mini sparkline curves.
 */
public class StatCard extends JPanel {

    public enum IconType {
        CASH,
        HOLDINGS,
        NET_WORTH,
        PROFIT_LOSS,
        GENERIC
    }

    private final String title;
    private IconType iconType = IconType.GENERIC;
    private String value = "-";
    private Color valueColor = Theme.TXT_PRIMARY;
    private String note = "";
    private Color noteColor = Theme.TXT_MUTED;
    private String changePill = "";
    private Color pillTextColor = Theme.GAIN;
    private Color pillBgColor = new Color(0, 0, 0, 0);
    private boolean isFeatured = false; // Blue ring highlight for Net Worth
    private boolean hovered = false;

    // Mini sparkline (relative Y offsets 0..10). Empty = not drawn; only real values are shown.
    private int[] sparklineY = new int[0];

    public StatCard(String title) {
        this(title, IconType.GENERIC);
    }

    public StatCard(String title, IconType iconType) {
        this.title = title;
        this.iconType = iconType;
        this.isFeatured = (iconType == IconType.NET_WORTH);
        setLayout(new BorderLayout());
        setOpaque(false);
        setPreferredSize(new Dimension(210, 84));
        setMinimumSize(new Dimension(170, 78));

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

    public void setFeatured(boolean featured) {
        this.isFeatured = featured;
        repaint();
    }

    public void setIconType(IconType iconType) {
        this.iconType = iconType;
        this.isFeatured = (iconType == IconType.NET_WORTH);
        repaint();
    }

    public void setValue(String value, Color color) {
        this.value = value;
        this.valueColor = color;
        repaint();
    }

    public void setNote(String note, Color color) {
        this.note = note;
        this.noteColor = color;
        repaint();
    }

    public void setChangePill(String changeText, double changeValue) {
        this.changePill = changeText;
        if (changeValue > 0) {
            this.pillTextColor = Theme.GAIN;
            this.pillBgColor = isFeatured ? Theme.ACCENT_BG : Theme.GREEN_BG;
        } else if (changeValue < 0) {
            this.pillTextColor = Theme.LOSS;
            this.pillBgColor = Theme.RED_BG;
        } else {
            this.pillTextColor = Theme.TXT_MUTED;
            this.pillBgColor = new Color(100, 116, 139, 25);
        }
        repaint();
    }

    /** Draws the small trend line from real values (e.g. the last account values). */
    public void setSparkline(java.util.List<Double> values) {
        if (values == null || values.size() < 2) {
            sparklineY = new int[0];
        } else {
            int n = Math.min(24, values.size());
            java.util.List<Double> last = values.subList(values.size() - n, values.size());
            double min = java.util.Collections.min(last);
            double max = java.util.Collections.max(last);
            sparklineY = new int[n];
            for (int i = 0; i < n; i++) {
                sparklineY[i] = max == min ? 6 : (int) Math.round(11 - (last.get(i) - min) / (max - min) * 10);
            }
        }
        repaint();
    }

    public void clearChangePill() {
        this.changePill = "";
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        int r = 8;

        // Card Surface
        g2.setColor(hovered ? Theme.SURFACE_SUB : Theme.CARD_BG);
        g2.fillRoundRect(0, 0, w - 1, h - 1, r, r);

        // Border / Featured Ring
        if (isFeatured) {
            g2.setColor(new Color(59, 130, 246, 120)); // accent/50
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRoundRect(0, 0, w - 1, h - 1, r, r);
        } else {
            g2.setColor(hovered ? Theme.BORDER_LIGHT : Theme.BORDER_CUSTOM);
            g2.setStroke(new BasicStroke(1.0f));
            g2.drawRoundRect(0, 0, w - 1, h - 1, r, r);
        }

        // Top Row: Title (left) & Icon Box (right)
        int padX = 12;
        int topY = 18;

        g2.setFont(Theme.TINY.deriveFont(Font.BOLD, 10f));
        g2.setColor(isFeatured ? Theme.ACCENT : Theme.TXT_MUTED);
        g2.drawString(title.toUpperCase(), padX, topY);

        int iconBoxSize = 20;
        int iconBoxX = w - padX - iconBoxSize;
        int iconBoxY = 8;
        paintIconBox(g2, iconBoxX, iconBoxY, iconBoxSize);

        // Middle Row: Big Mono Value (left) & Percentage (right)
        int midY = 46;
        g2.setFont(Theme.STAT_NUMBER);
        g2.setColor(valueColor);
        g2.drawString(value, padX, midY);

        if (changePill != null && !changePill.isEmpty()) {
            g2.setFont(Theme.MONO_SMALL.deriveFont(Font.BOLD, 10f));
            FontMetrics pfm = g2.getFontMetrics();
            int pillW = pfm.stringWidth(changePill) + (iconType == IconType.PROFIT_LOSS ? 8 : 2);
            int pillH = 16;
            int pillX = w - padX - pillW;
            int pillY = midY - 12;

            if (iconType == IconType.PROFIT_LOSS && pillBgColor.getAlpha() > 0) {
                g2.setColor(pillBgColor);
                g2.fillRoundRect(pillX, pillY, pillW, pillH, 4, 4);
                g2.setColor(pillTextColor);
                g2.drawString(changePill, pillX + 4, pillY + 12);
            } else {
                g2.setColor(isFeatured ? Theme.ACCENT : pillTextColor);
                g2.drawString(changePill, pillX, pillY + 12);
            }
        }

        // Bottom Row: Description Note (left) & Mini Sparkline (right)
        int bottomY = h - 11;
        g2.setFont(Theme.TINY);
        g2.setColor(isFeatured ? Theme.TXT_SECONDARY : noteColor);
        String noteStr = note;
        if (g2.getFontMetrics().stringWidth(noteStr) > w - padX - 60) {
            noteStr = noteStr.substring(0, Math.min(noteStr.length(), 18)) + "...";
        }
        g2.drawString(noteStr, padX, bottomY);

        // Draw Mini Sparkline SVG equivalent in bottom right
        int sparkW = 44;
        int sparkH = 12;
        int sparkX = w - padX - sparkW;
        int sparkY = h - 20;

        Color sparkColor = isFeatured ? Theme.ACCENT : (pillTextColor != null ? pillTextColor : Theme.GAIN);
        g2.setColor(sparkColor);
        g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        int n = sparklineY.length;
        if (n < 2) {
            g2.dispose();
            return;
        }
        int[] xs = new int[n];
        int[] ys = new int[n];
        for (int i = 0; i < n; i++) {
            xs[i] = sparkX + i * sparkW / (n - 1);
            ys[i] = sparkY + sparklineY[i];
        }
        g2.drawPolyline(xs, ys, n);

        g2.dispose();
    }

    private void paintIconBox(Graphics2D g2, int x, int y, int size) {
        // Rounded box in surfaceSub
        g2.setColor(isFeatured ? Theme.ACCENT_BG : Theme.SURFACE_SUB);
        g2.fillRoundRect(x, y, size, size, 4, 4);
        g2.setColor(isFeatured ? new Color(59, 130, 246, 80) : Theme.BORDER_CUSTOM);
        g2.drawRoundRect(x, y, size, size, 4, 4);

        int cx = x + size / 2;
        int cy = y + size / 2;
        Color iconColor = isFeatured ? Theme.ACCENT : (iconType == IconType.PROFIT_LOSS ? Theme.GAIN : Theme.ACCENT);
        g2.setColor(iconColor);
        g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        switch (iconType) {
            case CASH -> {
                // Wallet
                g2.drawRoundRect(cx - 5, cy - 4, 10, 8, 2, 2);
                g2.drawOval(cx + 1, cy - 1, 2, 2);
            }
            case HOLDINGS -> {
                // Pie chart icon
                g2.drawOval(cx - 5, cy - 5, 10, 10);
                g2.drawLine(cx, cy - 5, cx, cy);
                g2.drawLine(cx, cy, cx + 4, cy + 2);
            }
            case NET_WORTH -> {
                // Shield
                int[] px = {cx, cx + 4, cx + 4, cx, cx - 4, cx - 4};
                int[] py = {cy - 5, cy - 3, cy + 1, cy + 5, cy + 1, cy - 3};
                g2.drawPolygon(px, py, 6);
            }
            case PROFIT_LOSS -> {
                // Trending arrow up
                g2.drawLine(cx - 5, cy + 3, cx - 1, cy - 1);
                g2.drawLine(cx - 1, cy - 1, cx + 1, cy + 1);
                g2.drawLine(cx + 1, cy + 1, cx + 5, cy - 4);
                g2.drawLine(cx + 2, cy - 4, cx + 5, cy - 4);
                g2.drawLine(cx + 5, cy - 4, cx + 5, cy - 1);
            }
            default -> g2.drawOval(cx - 3, cy - 3, 6, 6);
        }
    }
}
