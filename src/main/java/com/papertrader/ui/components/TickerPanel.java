package com.papertrader.ui.components;

import com.papertrader.model.Asset;
import com.papertrader.service.MarketService;
import com.papertrader.ui.Theme;
import com.papertrader.util.Formatter;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Terminal Market Ticker Tape marquee.
 * Matches HTML: fixed leading "Ticker" badge and smooth scrolling symbol stream.
 */
public class TickerPanel extends JPanel {

    private final MarketService marketService;
    private double offset = 0.0;
    private boolean paused = false;
    private final Timer timer;

    public TickerPanel(MarketService marketService) {
        this.marketService = marketService;
        setOpaque(false);
        setPreferredSize(new Dimension(100, 26));
        setMinimumSize(new Dimension(100, 26));

        this.timer = new Timer(18, e -> {
            if (!paused) {
                offset += 0.8;
                repaint();
            }
        });
        timer.start();

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                paused = true;
            }

            @Override
            public void mouseExited(MouseEvent e) {
                paused = false;
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        // Background container
        g2.setColor(Theme.CARD_BG);
        g2.fillRoundRect(0, 0, w - 1, h - 1, 4, 4);
        g2.setColor(Theme.BORDER_CUSTOM);
        g2.drawRoundRect(0, 0, w - 1, h - 1, 4, 4);

        int badgeW = 68;

        // Render Scrolling Stream
        List<Asset> assets = marketService.getAllAssets();
        if (!assets.isEmpty()) {
            Font fontSym = Theme.MONO_TINY.deriveFont(Font.BOLD, 10f);
            Font fontPrice = Theme.MONO_TINY.deriveFont(Font.PLAIN, 10f);
            Font fontChange = Theme.MONO_TINY.deriveFont(Font.PLAIN, 10f);

            int itemGap = 24;
            int stripW = 0;
            for (Asset a : assets) {
                stripW += measureItem(g2, a, fontSym, fontPrice, fontChange) + itemGap;
            }

            if (stripW > 0) {
                // Clip stream to area right of badge
                g2.clipRect(badgeW + 2, 1, w - badgeW - 3, h - 2);

                int startX = badgeW + 8 - (int) (offset % stripW);
                int curX = startX;

                while (curX < w) {
                    for (Asset a : assets) {
                        int itemWidth = measureItem(g2, a, fontSym, fontPrice, fontChange);
                        if (curX + itemWidth > badgeW && curX < w) {
                            drawItem(g2, a, curX, h, fontSym, fontPrice, fontChange);
                        }
                        curX += itemWidth + itemGap;
                    }
                }
                g2.setClip(null);
            }
        }

        // Left Fixed "🟢 Ticker" Badge
        g2.setColor(Theme.SURFACE_SUB);
        g2.fillRoundRect(0, 0, badgeW, h - 1, 4, 4);
        g2.setColor(Theme.BORDER_CUSTOM);
        g2.drawLine(badgeW, 0, badgeW, h - 1);

        // Green dot
        g2.setColor(Theme.GAIN);
        g2.fillOval(8, h / 2 - 3, 6, 6);

        // Label
        g2.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 9f));
        g2.setColor(Theme.TXT_MUTED);
        g2.drawString("TICKER", 18, h / 2 + 3);

        g2.dispose();
    }

    private int measureItem(Graphics2D g2, Asset a, Font fontSym, Font fontPrice, Font fontChange) {
        FontMetrics fmSym = g2.getFontMetrics(fontSym);
        FontMetrics fmPrice = g2.getFontMetrics(fontPrice);
        FontMetrics fmChange = g2.getFontMetrics(fontChange);

        String chg = (a.getChangePercent() >= 0 ? "+" : "") + String.format("%.2f%% ", a.getChangePercent())
                + (a.getChangePercent() >= 0 ? "↑" : "↓");

        return fmSym.stringWidth(a.getSymbol()) + 6 + fmPrice.stringWidth(Formatter.money(a.getPrice())) + 6
                + fmChange.stringWidth(chg);
    }

    private void drawItem(Graphics2D g2, Asset a, int x, int h, Font fontSym, Font fontPrice, Font fontChange) {
        int baseline = h / 2 + 4;
        int curX = x;

        // Symbol (white bold)
        g2.setFont(fontSym);
        g2.setColor(Color.WHITE);
        g2.drawString(a.getSymbol(), curX, baseline);
        curX += g2.getFontMetrics().stringWidth(a.getSymbol()) + 6;

        // Price (slate-300)
        g2.setFont(fontPrice);
        g2.setColor(new Color(0xCB, 0xD5, 0xE1));
        String priceStr = Formatter.money(a.getPrice());
        g2.drawString(priceStr, curX, baseline);
        curX += g2.getFontMetrics().stringWidth(priceStr) + 6;

        // Change % with arrow
        boolean up = a.getChangePercent() >= 0;
        String chgStr = (up ? "+" : "") + String.format("%.2f%% ", a.getChangePercent()) + (up ? "↑" : "↓");
        g2.setFont(fontChange);
        g2.setColor(up ? Theme.GAIN : Theme.LOSS);
        g2.drawString(chgStr, curX, baseline);
    }
}
