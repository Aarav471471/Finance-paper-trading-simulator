package com.papertrader.ui.components;

import com.papertrader.ui.Theme;
import com.papertrader.util.Formatter;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * A price line chart drawn with Graphics2D.
 * Enhanced with subtle grid lines, clean gradient fills,
 * interactive map-style zoom in / zoom out, click-drag pan, and crosshair tooltip.
 */
public class LineChartPanel extends JPanel {

    private static final int PAD_LEFT = 20;
    private static final int PAD_RIGHT = 90;
    private static final int PAD_TOP = 44;
    private static final int PAD_BOTTOM = 24;

    private String title = "";
    private List<Double> prices = new ArrayList<>();

    // Map-style Zoom & Pan
    private double zoomLevel = 1.0;
    private double panOffset = 0.0;
    private final JLabel lblZoomBadge = new JLabel("100%");
    private Point mousePoint = null;
    private int hoveredIndex = -1;

    public LineChartPanel() {
        setLayout(new BorderLayout());
        setOpaque(false);
        setBackground(Theme.CARD);

        // Header controls (top right map zoom buttons)
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 4));
        topBar.setOpaque(false);

        JPanel zoomGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        zoomGroup.setOpaque(true);
        zoomGroup.setBackground(new Color(0x10, 0x16, 0x22));
        zoomGroup.setBorder(new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 4, 1));

        JButton btnZoomOut = createZoomBtn("−", "Zoom Out (or Scroll Down)", e -> zoomOut());
        lblZoomBadge.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 8.5f));
        lblZoomBadge.setForeground(Theme.TXT_MUTED);
        lblZoomBadge.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        JButton btnZoomIn = createZoomBtn("+", "Zoom In (or Scroll Up)", e -> zoomIn());
        JButton btnReset = createZoomBtn("⟲", "Reset Zoom / Pan (or Double Click)", e -> resetZoom());

        zoomGroup.add(btnZoomOut);
        zoomGroup.add(lblZoomBadge);
        zoomGroup.add(btnZoomIn);
        zoomGroup.add(btnReset);

        topBar.add(zoomGroup);
        add(topBar, BorderLayout.NORTH);

        MouseAdapter mouse = new MouseAdapter() {
            private Point dragStart = null;
            private double panStart = 0.0;

            @Override
            public void mouseMoved(MouseEvent e) {
                mousePoint = e.getPoint();
                updateHoveredIndex();
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    dragStart = e.getPoint();
                    panStart = panOffset;
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragStart = null;
                setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragStart != null && prices.size() > 1) {
                    int totalN = prices.size();
                    int width = getWidth() - PAD_LEFT - PAD_RIGHT;
                    int visibleN = Math.max(5, (int) Math.round(totalN / zoomLevel));
                    if (width > 0 && totalN > visibleN) {
                        double pitch = (double) width / visibleN;
                        int dx = e.getX() - dragStart.x;
                        double delta = dx / pitch;
                        int maxPan = totalN - visibleN;
                        panOffset = Math.max(0.0, Math.min(maxPan, panStart + delta));
                        mousePoint = e.getPoint();
                        updateHoveredIndex();
                        repaint();
                    }
                }
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                if (e.getWheelRotation() < 0) {
                    setZoom(zoomLevel * 1.18);
                } else {
                    setZoom(zoomLevel / 1.18);
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    resetZoom();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (dragStart == null) {
                    mousePoint = null;
                    hoveredIndex = -1;
                    repaint();
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
    }

    public void zoomIn() {
        setZoom(zoomLevel * 1.25);
    }

    public void zoomOut() {
        setZoom(zoomLevel / 1.25);
    }

    public void resetZoom() {
        zoomLevel = 1.0;
        panOffset = 0.0;
        updateZoomBadge();
        repaint();
    }

    public void setZoom(double newZoom) {
        zoomLevel = Math.max(1.0, Math.min(10.0, newZoom));
        if (zoomLevel <= 1.001) {
            zoomLevel = 1.0;
            panOffset = 0.0;
        }
        updateZoomBadge();
        repaint();
    }

    private void updateZoomBadge() {
        lblZoomBadge.setText(String.format(java.util.Locale.US, "%d%%", (int) Math.round(zoomLevel * 100)));
        lblZoomBadge.setForeground(zoomLevel > 1.01 ? Theme.ACCENT : Theme.TXT_MUTED);
    }

    private JButton createZoomBtn(String text, String tooltip, java.awt.event.ActionListener al) {
        JButton b = new JButton(text);
        b.setFont(new Font("SansSerif", Font.BOLD, 11));
        b.setForeground(Theme.TXT_SECONDARY);
        b.setBackground(Theme.SURFACE_SUB);
        b.setBorder(BorderFactory.createEmptyBorder(1, 6, 1, 6));
        b.setFocusPainted(false);
        b.setToolTipText(tooltip);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(al);
        return b;
    }

    public void setData(String title, List<Double> prices) {
        this.title = title;
        this.prices = prices;
        resetZoom();
        repaint();
    }

    private void updateHoveredIndex() {
        if (mousePoint == null || prices.size() < 2) {
            hoveredIndex = -1;
            return;
        }

        int width = getWidth() - PAD_LEFT - PAD_RIGHT;
        if (width <= 0) return;

        int mx = mousePoint.x - PAD_LEFT;
        if (mx < 0 || mx > width) {
            hoveredIndex = -1;
            return;
        }

        int totalN = prices.size();
        int visibleN = Math.max(5, (int) Math.round(totalN / zoomLevel));
        int maxPan = totalN - visibleN;
        int pan = (int) Math.round(Math.max(0, Math.min(maxPan, panOffset)));
        int startIdx = Math.max(0, totalN - visibleN - pan);

        int localIdx = (int) Math.round(((double) mx / width) * (visibleN - 1));
        hoveredIndex = Math.max(0, Math.min(totalN - 1, startIdx + localIdx));
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

        int totalN = prices.size();
        int visibleN = Math.max(5, (int) Math.round(totalN / zoomLevel));
        int maxPan = totalN - visibleN;
        int pan = (int) Math.round(Math.max(0, Math.min(maxPan, panOffset)));
        int startIdx = Math.max(0, totalN - visibleN - pan);
        int endIdx = Math.min(totalN, startIdx + visibleN);
        int n = endIdx - startIdx;

        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (int i = startIdx; i < endIdx; i++) {
            double p = prices.get(i);
            min = Math.min(min, p);
            max = Math.max(max, p);
        }
        if (max - min < 0.0001) {
            max += 1;
            min -= 1;
        }

        int width = w - PAD_LEFT - PAD_RIGHT;
        int height = h - PAD_TOP - PAD_BOTTOM;
        int[] xs = new int[n];
        int[] ys = new int[n];
        for (int i = 0; i < n; i++) {
            xs[i] = PAD_LEFT + (int) ((double) i / (n - 1) * width);
            ys[i] = PAD_TOP + (int) ((max - prices.get(startIdx + i)) / (max - min) * height);
        }

        boolean up = prices.get(endIdx - 1) >= prices.get(startIdx);
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

        // Minimap overview trackbar (when zoomed in)
        if (zoomLevel > 1.05 && totalN > n) {
            int mmW = 90;
            int mmH = 5;
            int mmX = PAD_LEFT + width - mmW - 8;
            int mmY = 8;
            g2.setColor(new Color(0x10, 0x16, 0x22, 180));
            g2.fillRoundRect(mmX, mmY, mmW, mmH, 3, 3);
            g2.setColor(Theme.BORDER);
            g2.drawRoundRect(mmX, mmY, mmW, mmH, 3, 3);

            double startFrac = (double) startIdx / totalN;
            double visFrac = (double) n / totalN;
            int thumbX = mmX + (int) (startFrac * mmW);
            int thumbW = Math.max(6, (int) (visFrac * mmW));
            g2.setColor(new Color(0x3B, 0x82, 0xF6, 200));
            g2.fillRoundRect(thumbX, mmY, thumbW, mmH, 3, 3);
        }

        // Hover Crosshair & Callout Tooltip
        if (hoveredIndex >= startIdx && hoveredIndex < endIdx) {
            int localI = hoveredIndex - startIdx;
            int hx = xs[localI];
            int hy = ys[localI];
            double pVal = prices.get(hoveredIndex);

            g2.setColor(new Color(59, 130, 246, 140));
            float[] chDash = {2f, 2f};
            g2.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, chDash, 0f));
            g2.drawLine(hx, PAD_TOP, hx, PAD_TOP + height);
            g2.drawLine(PAD_LEFT, hy, PAD_LEFT + width, hy);

            g2.setColor(Theme.ACCENT);
            g2.fillOval(hx - 4, hy - 4, 8, 8);
            g2.setColor(Color.WHITE);
            g2.fillOval(hx - 2, hy - 2, 4, 4);

            // Inspection Tooltip Box
            String tipText = Formatter.money(pVal);
            g2.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 9f));
            FontMetrics fm = g2.getFontMetrics();
            int bW = fm.stringWidth(tipText) + 12;
            int bH = 18;
            int bX = Math.min(PAD_LEFT + width - bW, Math.max(PAD_LEFT, hx - bW / 2));
            int bY = Math.max(PAD_TOP + 4, hy - bH - 6);

            g2.setColor(new Color(0x17, 0x1F, 0x2E));
            g2.fillRoundRect(bX, bY, bW, bH, 4, 4);
            g2.setColor(Theme.BORDER);
            g2.drawRoundRect(bX, bY, bW, bH, 4, 4);
            g2.setColor(Color.WHITE);
            g2.drawString(tipText, bX + 6, bY + 12);
        }

        g2.dispose();
    }
}
