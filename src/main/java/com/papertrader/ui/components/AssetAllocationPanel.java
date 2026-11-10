package com.papertrader.ui.components;

import com.papertrader.model.Holding;
import com.papertrader.model.User;
import com.papertrader.service.MarketService;
import com.papertrader.service.PortfolioService;
import com.papertrader.ui.Theme;
import com.papertrader.util.Formatter;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Premium Asset Allocation visualization.
 * Features an interactive Donut ring chart and breakdown progress bars.
 * Derived entirely from actual user holdings and cash balance.
 */
public class AssetAllocationPanel extends JPanel {

    public record Slice(String label, String type, double value, double percentage, Color color) {
    }

    private static final Color[] PALETTE = {
            new Color(0x3B, 0x82, 0xF6), // Blue
            new Color(0x10, 0xB9, 0x81), // Emerald
            new Color(0x8B, 0x5C, 0xF6), // Violet
            new Color(0xF5, 0x9E, 0x0B), // Amber
            new Color(0x06, 0xB6, 0xD4), // Cyan
            new Color(0xEC, 0x48, 0x99), // Pink
            new Color(0x64, 0x74, 0x8B)  // Slate (Cash)
    };

    private final List<Slice> slices = new ArrayList<>();
    private double totalPortfolioValue = 0.0;
    private int hoveredSlice = -1;

    public AssetAllocationPanel() {
        setLayout(new BorderLayout());
        setOpaque(false);
        setBackground(Theme.CARD);
        setBorder(Theme.cardBorder());
        setPreferredSize(new Dimension(320, 310));
        setMinimumSize(new Dimension(280, 270));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                checkHover(e.getX(), e.getY());
            }
        });

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                hoveredSlice = -1;
                repaint();
            }
        });
    }

    public void updateData(User user, PortfolioService portfolioService, MarketService marketService) {
        if (user == null) {
            return;
        }

        slices.clear();
        double cash = user.getAccount().getBalance();
        List<Holding> holdings = user.getPortfolio().getHoldings();

        double total = cash;
        for (Holding h : holdings) {
            total += portfolioService.getMarketValue(h);
        }
        this.totalPortfolioValue = total;

        if (total <= 0) {
            repaint();
            return;
        }

        // Add holdings sorted by value descending
        List<Holding> sortedHoldings = new ArrayList<>(holdings);
        sortedHoldings.sort(Comparator.comparingDouble(h -> -portfolioService.getMarketValue(h)));

        int colorIdx = 0;
        for (Holding h : sortedHoldings) {
            double val = portfolioService.getMarketValue(h);
            if (val > 0) {
                double pct = val / total * 100.0;
                String type = marketService.findBySymbol(h.getSymbol()).map(a -> a.getType()).orElse("Asset");
                Color c = PALETTE[colorIdx % (PALETTE.length - 1)];
                colorIdx++;
                slices.add(new Slice(h.getSymbol(), type, val, pct, c));
            }
        }

        // Add Cash slice
        if (cash > 0) {
            double cashPct = cash / total * 100.0;
            slices.add(new Slice("CASH", "Cash", cash, cashPct, PALETTE[PALETTE.length - 1]));
        }

        repaint();
    }

    private void checkHover(int mx, int my) {
        if (slices.isEmpty()) return;

        int donutD = 140;
        int cx = 86;
        int cy = 110;

        double dx = mx - cx;
        double dy = my - cy;
        double dist = Math.sqrt(dx * dx + dy * dy);

        int outerR = donutD / 2;
        int innerR = outerR - 22;

        if (dist >= innerR && dist <= outerR + 6) {
            double angle = Math.toDegrees(Math.atan2(-dy, dx));
            if (angle < 0) {
                angle += 360.0;
            }
            // Standard chart angles start at 90 (top) and run clockwise
            double chartAngle = (450.0 - angle) % 360.0;

            double curAngle = 0.0;
            int found = -1;
            for (int i = 0; i < slices.size(); i++) {
                double extent = slices.get(i).percentage() * 3.6;
                if (chartAngle >= curAngle && chartAngle < curAngle + extent) {
                    found = i;
                    break;
                }
                curAngle += extent;
            }
            if (found != hoveredSlice) {
                hoveredSlice = found;
                repaint();
            }
        } else {
            if (hoveredSlice != -1) {
                hoveredSlice = -1;
                repaint();
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        // Card container
        g2.setColor(Theme.CARD);
        g2.fillRoundRect(0, 0, w - 1, h - 1, 14, 14);
        g2.setColor(Theme.BORDER);
        g2.drawRoundRect(0, 0, w - 1, h - 1, 14, 14);

        // Header Title
        g2.setFont(Theme.SMALL.deriveFont(Font.BOLD, 11f));
        g2.setColor(Theme.MUTED);
        g2.drawString("ASSET ALLOCATION", 18, 24);

        if (slices.isEmpty() || totalPortfolioValue <= 0) {
            g2.setFont(Theme.BODY);
            g2.setColor(Theme.MUTED);
            g2.drawString("No assets held yet", 18, 60);
            g2.dispose();
            return;
        }

        // Donut Geometry
        int donutSize = 130;
        int cx = 86;
        int cy = 112;
        int outerR = donutSize / 2;
        int strokeW = 20;

        double curAngle = 90.0;
        for (int i = 0; i < slices.size(); i++) {
            Slice s = slices.get(i);
            double extent = s.percentage() * 3.6;
            boolean hovered = (i == hoveredSlice);

            int curR = hovered ? outerR + 3 : outerR;
            int curStroke = hovered ? strokeW + 4 : strokeW;

            g2.setColor(s.color());
            g2.setStroke(new BasicStroke(curStroke, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
            Arc2D.Double arc = new Arc2D.Double(cx - curR + curStroke / 2.0, cy - curR + curStroke / 2.0,
                    (curR - curStroke / 2.0) * 2, (curR - curStroke / 2.0) * 2,
                    curAngle, -extent, Arc2D.OPEN);
            g2.draw(arc);

            curAngle -= extent;
        }

        // Center cutout text
        g2.setFont(Theme.TINY.deriveFont(Font.BOLD, 9f));
        g2.setColor(Theme.MUTED);
        String centerTitle = hoveredSlice >= 0 ? slices.get(hoveredSlice).label() : "NET WORTH";
        FontMetrics tfm = g2.getFontMetrics();
        g2.drawString(centerTitle, cx - tfm.stringWidth(centerTitle) / 2, cy - 4);

        g2.setFont(Theme.BODY.deriveFont(Font.BOLD, 12f));
        g2.setColor(Theme.TEXT);
        String centerVal = hoveredSlice >= 0 ? String.format("%.1f%%", slices.get(hoveredSlice).percentage())
                : Formatter.money(totalPortfolioValue);
        FontMetrics vfm = g2.getFontMetrics();
        g2.drawString(centerVal, cx - vfm.stringWidth(centerVal) / 2, cy + 12);

        // Right side: Top allocation list with progress bars
        int listX = 168;
        int listY = 46;
        int maxItems = Math.min(4, slices.size());

        for (int i = 0; i < maxItems; i++) {
            Slice s = slices.get(i);
            boolean isHov = (i == hoveredSlice);

            // Color indicator dot
            g2.setColor(s.color());
            g2.fillOval(listX, listY + 3, 8, 8);

            // Symbol
            g2.setFont(Theme.SMALL.deriveFont(Font.BOLD, 11f));
            g2.setColor(isHov ? Color.WHITE : Theme.TEXT);
            g2.drawString(s.label(), listX + 13, listY + 10);

            // Percentage
            String pctStr = String.format("%.1f%%", s.percentage());
            g2.setColor(isHov ? Theme.TEXT : Theme.MUTED);
            FontMetrics pfm = g2.getFontMetrics();
            g2.drawString(pctStr, w - 18 - pfm.stringWidth(pctStr), listY + 10);

            // Progress bar
            int barY = listY + 15;
            int barW = w - listX - 18;
            g2.setColor(Theme.BORDER_SUBTLE);
            g2.fillRoundRect(listX, barY, barW, 4, 2, 2);
            g2.setColor(s.color());
            int fillW = (int) (barW * (s.percentage() / 100.0));
            g2.fillRoundRect(listX, barY, Math.max(3, fillW), 4, 2, 2);

            listY += 28;
        }

        // Bottom breakdown chips
        int chipY = h - 34;
        int chipX = 18;
        g2.setFont(Theme.TINY);
        for (int i = 0; i < Math.min(3, slices.size()); i++) {
            Slice s = slices.get(i);
            String label = s.label() + ": " + Formatter.money(s.value());
            FontMetrics cfm = g2.getFontMetrics();
            int cW = cfm.stringWidth(label) + 14;

            g2.setColor(Theme.CARD_HEADER);
            g2.fillRoundRect(chipX, chipY, cW, 20, 6, 6);
            g2.setColor(Theme.BORDER_SUBTLE);
            g2.drawRoundRect(chipX, chipY, cW, 20, 6, 6);

            g2.setColor(s.color());
            g2.fillOval(chipX + 5, chipY + 7, 5, 5);

            g2.setColor(Theme.TEXT_SECONDARY);
            g2.drawString(label, chipX + 14, chipY + 14);

            chipX += cW + 6;
            if (chipX > w - 80) break;
        }

        g2.dispose();
    }
}
