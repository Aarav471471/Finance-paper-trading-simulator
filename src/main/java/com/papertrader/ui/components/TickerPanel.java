package com.papertrader.ui.components;

import com.papertrader.model.Asset;
import com.papertrader.service.MarketService;
import com.papertrader.ui.Theme;
import com.papertrader.util.Formatter;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;

/** A stock-exchange style strip of prices that scrolls from right to left. */
public class TickerPanel extends JPanel {

    private final MarketService marketService;
    private int offset = 0;

    public TickerPanel(MarketService marketService) {
        this.marketService = marketService;
        setBackground(Theme.SIDEBAR);
        setPreferredSize(new Dimension(100, 38));
        new Timer(30, e -> {   // move 1 pixel every 30 ms for a smooth scroll
            offset++;
            repaint();
        }).start();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setFont(Theme.BODY);
        FontMetrics fm = g2.getFontMetrics();
        int baseline = (getHeight() + fm.getAscent()) / 2 - 2;

        List<Asset> assets = marketService.getAllAssets();
        int stripWidth = 0;
        for (Asset a : assets) {
            stripWidth += itemWidth(fm, a);
        }
        if (stripWidth == 0) {
            return;
        }
        int x = -(offset % stripWidth);
        while (x < getWidth()) {          // repeat the strip until the panel is full
            for (Asset a : assets) {
                x = drawItem(g2, fm, a, x, baseline);
            }
        }
    }

    private int itemWidth(FontMetrics fm, Asset a) {
        return fm.stringWidth(a.getSymbol() + "  " + Formatter.money(a.getPrice()) + "  "
                + arrow(a) + Formatter.percent(a.getChangePercent())) + 40;
    }

    private int drawItem(Graphics2D g2, FontMetrics fm, Asset a, int x, int baseline) {
        String symbol = a.getSymbol() + "  ";
        String price = Formatter.money(a.getPrice()) + "  ";
        String change = arrow(a) + Formatter.percent(a.getChangePercent());
        g2.setColor(Theme.TEXT);
        g2.drawString(symbol, x, baseline);
        x += fm.stringWidth(symbol);
        g2.setColor(Theme.MUTED);
        g2.drawString(price, x, baseline);
        x += fm.stringWidth(price);
        g2.setColor(Theme.colorFor(a.getChangePercent()));
        g2.drawString(change, x, baseline);
        return x + fm.stringWidth(change) + 40;
    }

    private String arrow(Asset a) {
        return a.getChangePercent() >= 0 ? "▲ " : "▼ ";
    }
}
