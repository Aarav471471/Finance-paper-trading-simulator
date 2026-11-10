package com.papertrader.ui.components;

import com.papertrader.ui.Theme;
import com.papertrader.util.Formatter;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;

/**
 * A price line chart drawn by hand with Graphics2D (no chart library needed).
 * METHOD OVERRIDING: Swing calls paintComponent() whenever the panel must be drawn;
 * we override it to draw our own chart.
 */
public class LineChartPanel extends JPanel {

    private static final int PAD_LEFT = 16;
    private static final int PAD_RIGHT = 90;
    private static final int PAD_TOP = 40;
    private static final int PAD_BOTTOM = 20;

    private String title = "";
    private List<Double> prices = new ArrayList<>();

    public LineChartPanel() {
        setBackground(Theme.CARD);
    }

    public void setData(String title, List<Double> prices) {
        this.title = title;
        this.prices = prices;
        repaint();   // ask Swing to call paintComponent again
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setColor(Theme.TEXT);
        g2.setFont(Theme.HEADING);
        g2.drawString(title, PAD_LEFT, 26);

        if (prices.size() < 2) {
            g2.setColor(Theme.MUTED);
            g2.setFont(Theme.BODY);
            g2.drawString("Collecting prices...", PAD_LEFT, PAD_TOP + 30);
            return;
        }

        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (double p : prices) {
            min = Math.min(min, p);
            max = Math.max(max, p);
        }
        if (max - min < 0.0001) {   // flat line: add a little room so we do not divide by zero
            max += 1;
            min -= 1;
        }

        int width = getWidth() - PAD_LEFT - PAD_RIGHT;
        int height = getHeight() - PAD_TOP - PAD_BOTTOM;
        int n = prices.size();
        int[] xs = new int[n];
        int[] ys = new int[n];
        for (int i = 0; i < n; i++) {
            xs[i] = PAD_LEFT + (int) ((double) i / (n - 1) * width);
            ys[i] = PAD_TOP + (int) ((max - prices.get(i)) / (max - min) * height);
        }

        boolean up = prices.get(n - 1) >= prices.get(0);
        Color lineColor = up ? Theme.GREEN : Theme.RED;

        // light grid lines + price labels on the right
        g2.setFont(Theme.SMALL);
        for (int i = 0; i <= 4; i++) {
            int y = PAD_TOP + i * height / 4;
            g2.setColor(Theme.BORDER);
            g2.drawLine(PAD_LEFT, y, PAD_LEFT + width, y);
            g2.setColor(Theme.MUTED);
            g2.drawString(Formatter.money(max - i * (max - min) / 4), PAD_LEFT + width + 8, y + 4);
        }

        // gradient fill under the line
        Polygon area = new Polygon();
        area.addPoint(xs[0], PAD_TOP + height);
        for (int i = 0; i < n; i++) {
            area.addPoint(xs[i], ys[i]);
        }
        area.addPoint(xs[n - 1], PAD_TOP + height);
        Color top = new Color(lineColor.getRed(), lineColor.getGreen(), lineColor.getBlue(), 90);
        Color bottom = new Color(lineColor.getRed(), lineColor.getGreen(), lineColor.getBlue(), 0);
        g2.setPaint(new GradientPaint(0, PAD_TOP, top, 0, PAD_TOP + height, bottom));
        g2.fillPolygon(area);

        // the price line itself
        g2.setColor(lineColor);
        g2.setStroke(new BasicStroke(2.2f));
        g2.drawPolyline(xs, ys, n);

        // dot on the latest price
        g2.fillOval(xs[n - 1] - 5, ys[n - 1] - 5, 10, 10);
    }
}
