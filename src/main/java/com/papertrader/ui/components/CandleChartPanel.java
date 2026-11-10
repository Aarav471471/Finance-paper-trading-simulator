package com.papertrader.ui.components;

import com.papertrader.model.Candle;
import com.papertrader.model.OrderSide;
import com.papertrader.model.TradeMarker;
import com.papertrader.ui.Theme;
import com.papertrader.util.Formatter;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Interactive candlestick chart drawn with Graphics2D.
 *  - green candle = price went up in that period, red = down
 *  - thin line (wick) = highest and lowest price, thick body = open to close
 *  - volume bars at the bottom
 *  - move the mouse to see a crosshair with the candle's open/high/low/close
 *  - scroll the mouse wheel to zoom in and out
 *  - optional BUY/SELL arrows (used by the backtest replay)
 */
public class CandleChartPanel extends JPanel {

    private static final int PAD_LEFT = 12;
    private static final int PAD_RIGHT = 84;
    private static final int PAD_TOP = 44;
    private static final int PAD_BOTTOM = 26;

    private String title = "";
    private List<Candle> candles = new ArrayList<>();
    private List<TradeMarker> markers = new ArrayList<>();
    private boolean dailyLabels = false;
    private int visibleCount = 80;
    private int hoverX = -1;
    private int hoverY = -1;

    public CandleChartPanel() {
        setBackground(Theme.CARD);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                hoverX = e.getX();
                hoverY = e.getY();
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hoverX = -1;
                repaint();
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                visibleCount = Math.max(15, Math.min(400, visibleCount + e.getWheelRotation() * 8));
                repaint();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
    }

    public void setData(String title, List<Candle> candles, boolean dailyLabels) {
        setData(title, candles, new ArrayList<>(), dailyLabels);
    }

    public void setData(String title, List<Candle> candles, List<TradeMarker> markers, boolean dailyLabels) {
        this.title = title;
        this.candles = candles;
        this.markers = markers;
        this.dailyLabels = dailyLabels;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(Theme.TEXT);
        g2.setFont(Theme.HEADING);
        g2.drawString(title, PAD_LEFT, 24);

        if (candles.isEmpty()) {
            g2.setColor(Theme.MUTED);
            g2.setFont(Theme.BODY);
            g2.drawString("Loading price history...", PAD_LEFT, PAD_TOP + 30);
            return;
        }

        int width = getWidth() - PAD_LEFT - PAD_RIGHT;
        int fullHeight = getHeight() - PAD_TOP - PAD_BOTTOM;
        int priceHeight = (int) (fullHeight * 0.80);
        int volumeTop = PAD_TOP + priceHeight + 8;
        int volumeHeight = fullHeight - priceHeight - 8;

        int count = Math.min(visibleCount, candles.size());
        List<Candle> view = candles.subList(candles.size() - count, candles.size());
        double slot = (double) width / count;
        double bodyWidth = Math.max(1, slot * 0.65);

        double max = -Double.MAX_VALUE;
        double min = Double.MAX_VALUE;
        double maxVolume = 1;
        for (Candle c : view) {
            max = Math.max(max, c.high());
            min = Math.min(min, c.low());
            maxVolume = Math.max(maxVolume, c.volume());
        }
        if (max - min < 1e-9) {
            max += 1;
            min -= 1;
        }
        final double top = max;
        final double range = max - min;

        // grid + price labels
        g2.setFont(Theme.SMALL);
        for (int i = 0; i <= 4; i++) {
            int y = PAD_TOP + i * priceHeight / 4;
            g2.setColor(Theme.BORDER);
            g2.drawLine(PAD_LEFT, y, PAD_LEFT + width, y);
            g2.setColor(Theme.MUTED);
            g2.drawString(Formatter.money(top - i * range / 4), PAD_LEFT + width + 6, y + 4);
        }

        // candles + volume
        for (int i = 0; i < count; i++) {
            Candle c = view.get(i);
            int centerX = (int) (PAD_LEFT + slot * i + slot / 2);
            Color color = c.isGreen() ? Theme.GREEN : Theme.RED;
            g2.setColor(color);
            g2.drawLine(centerX, yFor(c.high(), top, range, priceHeight), centerX, yFor(c.low(), top, range, priceHeight));
            int yOpen = yFor(c.open(), top, range, priceHeight);
            int yClose = yFor(c.close(), top, range, priceHeight);
            int bodyTop = Math.min(yOpen, yClose);
            int bodyHeight = Math.max(1, Math.abs(yOpen - yClose));
            g2.fillRect((int) (centerX - bodyWidth / 2), bodyTop, (int) Math.ceil(bodyWidth), bodyHeight);

            int vh = (int) (c.volume() / maxVolume * volumeHeight);
            g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 70));
            g2.fillRect((int) (centerX - bodyWidth / 2), volumeTop + volumeHeight - vh, (int) Math.ceil(bodyWidth), vh);
        }

        // buy / sell arrows
        for (TradeMarker m : markers) {
            for (int i = 0; i < count; i++) {
                if (view.get(i).time() == m.time()) {
                    int x = (int) (PAD_LEFT + slot * i + slot / 2);
                    drawMarker(g2, m.side(), x, view.get(i), top, range, priceHeight);
                }
            }
        }

        // time labels
        g2.setColor(Theme.MUTED);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern(dailyLabels ? "dd MMM yy" : "HH:mm");
        for (int i = 0; i < count; i += Math.max(1, count / 6)) {
            String label = fmt.format(Instant.ofEpochMilli(view.get(i).time()).atZone(ZoneId.systemDefault()));
            g2.drawString(label, (int) (PAD_LEFT + slot * i), getHeight() - 8);
        }

        // last price line
        Candle last = view.get(count - 1);
        int yLast = yFor(last.close(), top, range, priceHeight);
        g2.setColor(last.isGreen() ? Theme.GREEN : Theme.RED);
        g2.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 1, new float[]{4, 4}, 0));
        g2.drawLine(PAD_LEFT, yLast, PAD_LEFT + width, yLast);
        g2.setStroke(new BasicStroke(1));
        g2.fillRoundRect(PAD_LEFT + width + 2, yLast - 9, PAD_RIGHT - 4, 18, 6, 6);
        g2.setColor(Color.WHITE);
        g2.drawString(Formatter.money(last.close()), PAD_LEFT + width + 6, yLast + 4);

        // crosshair + OHLC of the candle under the mouse
        if (hoverX >= PAD_LEFT && hoverX <= PAD_LEFT + width) {
            int index = Math.min(count - 1, (int) ((hoverX - PAD_LEFT) / slot));
            Candle c = view.get(index);
            g2.setColor(new Color(255, 255, 255, 60));
            g2.drawLine(hoverX, PAD_TOP, hoverX, volumeTop + volumeHeight);
            if (hoverY >= PAD_TOP && hoverY <= PAD_TOP + priceHeight) {
                g2.drawLine(PAD_LEFT, hoverY, PAD_LEFT + width, hoverY);
            }
            String when = DateTimeFormatter.ofPattern(dailyLabels ? "dd MMM yyyy" : "dd MMM HH:mm")
                    .format(Instant.ofEpochMilli(c.time()).atZone(ZoneId.systemDefault()));
            String text = when + "   O " + Formatter.money(c.open()) + "   H " + Formatter.money(c.high())
                    + "   L " + Formatter.money(c.low()) + "   C " + Formatter.money(c.close());
            g2.setColor(c.isGreen() ? Theme.GREEN : Theme.RED);
            g2.drawString(text, PAD_LEFT, 40);
        }
    }

    private int yFor(double price, double top, double range, int priceHeight) {
        return PAD_TOP + (int) ((top - price) / range * priceHeight);
    }

    private void drawMarker(Graphics2D g2, OrderSide side, int x, Candle c, double top, double range, int h) {
        boolean buy = side == OrderSide.BUY;
        int y = buy ? yFor(c.low(), top, range, h) + 6 : yFor(c.high(), top, range, h) - 6;
        int dir = buy ? 1 : -1;   // buy arrow below the candle pointing up, sell above pointing down
        int[] xs = {x, x - 6, x + 6};
        int[] ys = {y, y + dir * 10, y + dir * 10};
        g2.setColor(buy ? Theme.GREEN : Theme.GOLD);
        g2.fillPolygon(xs, ys, 3);
    }
}
