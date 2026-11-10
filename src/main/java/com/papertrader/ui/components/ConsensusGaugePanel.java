package com.papertrader.ui.components;

import com.papertrader.ui.Theme;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;

/**
 * Institutional Consensus & Technical Analysis Speedometer Gauge.
 * Renders a semi-circular speedometer arc with Strong Sell, Neutral, and Strong Buy segments,
 * plus a responsive needle pointer, verdict badge, and oscillator consensus counts.
 */
public class ConsensusGaugePanel extends JPanel {

    private double score = 0.82; // 0.0 (Strong Sell) to 1.0 (Strong Buy)
    private String verdictText = "STRONG BUY";
    private String countsText = "14 BUY  •  4 NEUTRAL  •  2 SELL";

    private final JLabel verdictBadge = new JLabel("STRONG BUY");
    private final JLabel countsLabel = new JLabel("14 BUY  •  4 NEUTRAL  •  2 SELL");

    public ConsensusGaugePanel() {
        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(new Color(0x14, 0x1B, 0x2B));
        setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 6, 1),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        setPreferredSize(new Dimension(280, 180));

        // Header
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);

        JLabel title = new JLabel("TECHNICAL VERDICT");
        title.setFont(Theme.MICRO.deriveFont(Font.BOLD, 9f));
        title.setForeground(Theme.TXT_MUTED);
        top.add(title, BorderLayout.WEST);

        verdictBadge.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 9f));
        verdictBadge.setForeground(new Color(0x4A, 0xE1, 0x76));
        verdictBadge.setOpaque(true);
        verdictBadge.setBackground(new Color(0, 185, 84, 40));
        verdictBadge.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(new Color(74, 225, 118, 100), 3, 1),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)));
        top.add(verdictBadge, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        // Center Gauge Canvas
        GaugeCanvas canvas = new GaugeCanvas();
        add(canvas, BorderLayout.CENTER);

        // Bottom sub-labels
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        JLabel lLeft = new JLabel("Oversold / Bear");
        lLeft.setFont(Theme.MICRO);
        lLeft.setForeground(Theme.TXT_MUTED);

        countsLabel.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 9f));
        countsLabel.setForeground(new Color(0x4A, 0xE1, 0x76));
        countsLabel.setHorizontalAlignment(JLabel.CENTER);

        JLabel lRight = new JLabel("Overbought / Bull");
        lRight.setFont(Theme.MICRO);
        lRight.setForeground(Theme.TXT_MUTED);

        bottom.add(lLeft, BorderLayout.WEST);
        bottom.add(countsLabel, BorderLayout.CENTER);
        bottom.add(lRight, BorderLayout.EAST);

        add(bottom, BorderLayout.SOUTH);
    }

    public void setTechnicalRating(double rating, String verdict, int buyCount, int neutralCount, int sellCount) {
        this.score = Math.max(0.0, Math.min(1.0, rating));
        this.verdictText = verdict;
        this.countsText = buyCount + " BUY  •  " + neutralCount + " NEUTRAL  •  " + sellCount + " SELL";

        verdictBadge.setText(verdictText);
        countsLabel.setText(countsText);

        Color col;
        if (score >= 0.65) {
            col = new Color(0x4A, 0xE1, 0x76);
            verdictBadge.setBackground(new Color(0, 185, 84, 40));
        } else if (score <= 0.35) {
            col = new Color(0xFF, 0x54, 0x51);
            verdictBadge.setBackground(new Color(147, 0, 10, 40));
        } else {
            col = new Color(0x8C, 0x90, 0x9F);
            verdictBadge.setBackground(new Color(40, 52, 73, 60));
        }
        verdictBadge.setForeground(col);
        countsLabel.setForeground(col);

        repaint();
    }

    private class GaugeCanvas extends JPanel {
        GaugeCanvas() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            int cx = w / 2;
            int cy = h - 8;
            int radius = Math.min(cx - 24, cy - 10);
            if (radius < 40) radius = 40;

            int arcThickness = 12;
            int arcX = cx - radius;
            int arcY = cy - radius;
            int arcDiameter = radius * 2;

            g2.setStroke(new BasicStroke(arcThickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            // Background Track (#232A3A)
            g2.setColor(new Color(0x23, 0x2A, 0x3A));
            g2.draw(new Arc2D.Double(arcX, arcY, arcDiameter, arcDiameter, 0, 180, Arc2D.OPEN));

            // Strong Sell Segment (180 to 125 deg)
            g2.setColor(new Color(0xFF, 0x54, 0x51));
            g2.draw(new Arc2D.Double(arcX, arcY, arcDiameter, arcDiameter, 130, 50, Arc2D.OPEN));

            // Neutral Segment (120 to 60 deg)
            g2.setStroke(new BasicStroke(arcThickness, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
            g2.setColor(new Color(0x8C, 0x90, 0x9F));
            g2.draw(new Arc2D.Double(arcX, arcY, arcDiameter, arcDiameter, 55, 70, Arc2D.OPEN));

            // Buy / Strong Buy Segment (50 to 0 deg)
            g2.setStroke(new BasicStroke(arcThickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(0x4A, 0xE1, 0x76));
            g2.draw(new Arc2D.Double(arcX, arcY, arcDiameter, arcDiameter, 0, 50, Arc2D.OPEN));

            // Needle angle: score 0.0 -> 180 deg (left), score 1.0 -> 0 deg (right)
            // (1.0 - score) * 180 degrees
            double needleAngleDeg = 180.0 - (score * 180.0);
            double needleAngleRad = Math.toRadians(needleAngleDeg);

            int needleLen = radius - 8;
            int tipX = cx + (int) (needleLen * Math.cos(needleAngleRad));
            int tipY = cy - (int) (needleLen * Math.sin(needleAngleRad));

            // Needle Line
            g2.setColor(new Color(0xDC, 0xE2, 0xF7));
            g2.setStroke(new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(cx, cy, tipX, tipY);

            // Pivot Center Circle
            g2.setColor(new Color(0xAD, 0xC6, 0xFF));
            g2.fillOval(cx - 5, cy - 5, 10, 10);
            g2.setColor(new Color(0x14, 0x1B, 0x2B));
            g2.fillOval(cx - 2, cy - 2, 4, 4);

            g2.dispose();
        }
    }
}
