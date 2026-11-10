package com.papertrader.ui.components;

import com.papertrader.ui.Theme;
import com.papertrader.util.Formatter;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;

/**
 * A price line chart drawn with Graphics2D.
 * Enhanced with subtle grid lines, clean gradient fills,
 * and high-contrast typography.
 */
public class LineChartPanel extends JPanel {

    private static final int PAD_LEFT = 20;
    private static final int PAD_RIGHT = 90;
    private static final int PAD_TOP = 44;
    private static final int PAD_BOTTOM = 24;

    private String title = "";
    private List<Double> prices = new ArrayList<>();

    public LineChartPanel() {
        setOpaque(false);
        setBackground(Theme.CARD);
    }

    public void setData(String title, List<Double> prices) {
        this.title = title;
        this.prices = prices;
        repaint();
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

        g2.setColor(Theme.TEXT);
        g2.setFont(Theme.HEADING);
        g2.drawString(title, PAD_LEFT, 28);

        if (prices.size() < 2) {
            g2.setColor(Theme.MUTED);
            g2.setFont(Theme.BODY);
            g2.drawString("Collecting live prices...", PAD_LEFT, PAD_TOP + 30);
            g2.dispose();
            return;
        }

        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (double p : prices) {
            min = Math.min(min, p);
            max = Math.max(max, p);
        }
        if (max - min < 0.0001) {
            max += 1;
            min -= 1;
        }

        int width = w - PAD_LEFT - PAD_RIGHT;
        int height = h - PAD_TOP - PAD_BOTTOM;
        int n = prices.size();
        int[] xs = new int[n];
        int[] ys = new int[n];
        for (int i = 0; i < n; i++) {
            xs[i] = PAD_LEFT + (int) ((double) i / (n - 1) * width);
            ys[i] = PAD_TOP + (int) ((max - prices.get(i)) / (max - min) * height);
        }

        boolean up = prices.get(n - 1) >= prices.get(0);
        Color lineColor = up ? Theme.GREEN : Theme.RED;

        // Subtle grid lines + price labels on the right
        g2.setFont(Theme.TINY);
        for (int i = 0; i <= 4; i++) {
            int y = PAD_TOP + i * height / 4;
            g2.setColor(Theme.BORDER_SUBTLE);
            g2.drawLine(PAD_LEFT, y, PAD_LEFT + width, y);
            g2.setColor(Theme.MUTED_DARK);
            g2.drawString(Formatter.money(max - i * (max - min) / 4), PAD_LEFT + width + 8, y + 4);
        }

        // Gradient fill under the line
        Polygon area = new Polygon();
        area.addPoint(xs[0], PAD_TOP + height);
        for (int i = 0; i < n; i++) {
            area.addPoint(xs[i], ys[i]);
        }
        area.addPoint(xs[n - 1], PAD_TOP + height);
        Color top = new Color(lineColor.getRed(), lineColor.getGreen(), lineColor.getBlue(), 60);
        Color bottom = new Color(lineColor.getRed(), lineColor.getGreen(), lineColor.getBlue(), 0);
        g2.setPaint(new GradientPaint(0, PAD_TOP, top, 0, PAD_TOP + height, bottom));
        g2.fillPolygon(area);

        // Price line
        g2.setColor(lineColor);
        g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawPolyline(xs, ys, n);

        // Terminal dot
        g2.fillOval(xs[n - 1] - 4, ys[n - 1] - 4, 8, 8);
        g2.setColor(Color.WHITE);
        g2.fillOval(xs[n - 1] - 2, ys[n - 1] - 2, 4, 4);

        g2.dispose();
    }
}
