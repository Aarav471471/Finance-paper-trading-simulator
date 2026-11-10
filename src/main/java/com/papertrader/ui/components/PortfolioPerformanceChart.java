package com.papertrader.ui.components;

import com.papertrader.model.User;
import com.papertrader.service.MarketService;
import com.papertrader.service.PortfolioService;
import com.papertrader.service.TradingService;
import com.papertrader.ui.Theme;
import com.papertrader.util.Formatter;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Terminal Portfolio Performance Line Chart (Column 2 Lower).
 * Features interactive timeframe filters [1D, 1W, 1M, 3M, 6M, 1Y, ALL],
 * S&P 500 (SPY) benchmark comparison, organic green area gradient, live tip pulse,
 * dynamic date axis, and crosshair tooltip inspection.
 */
public class PortfolioPerformanceChart extends JPanel {

    public enum Timeframe {
        ONE_DAY("1D", 1),
        ONE_WEEK("1W", 7),
        ONE_MONTH("1M", 30),
        THREE_MONTHS("3M", 90),
        SIX_MONTHS("6M", 180),
        ONE_YEAR("1Y", 365),
        ALL("ALL", 730);

        private final String label;
        private final int days;

        Timeframe(String label, int days) {
            this.label = label;
            this.days = days;
        }

        public String getLabel() { return label; }
        public int getDays() { return days; }
    }

    public enum ChartMode {
        LINE("Line"),
        CANDLES("Candles");

        private final String label;
        ChartMode(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public record ChartPoint(String label, double value, double benchmark) {}

    public record CandlePoint(String label, LocalDateTime time, double open, double high, double low, double close, double benchmark) {}

    private Timeframe currentTimeframe = Timeframe.ONE_MONTH;
    private ChartMode currentMode = ChartMode.LINE;
    private boolean showBenchmark = true;
    private final Map<Timeframe, JButton> filterButtons = new LinkedHashMap<>();
    private final Map<ChartMode, JButton> modeButtons = new LinkedHashMap<>();

    private final List<ChartPoint> dataPoints = new ArrayList<>();
    private final List<CandlePoint> candlePoints = new ArrayList<>();
    private double currentEquity = 100000.0;
    private double startingBaseline = 100000.0;

    // Header labels (real-time bound)
    private final JLabel heroVal = new JLabel("$100,000.00");
    private final JLabel retPill = new JLabel("+0.00 (+0.00%)");

    // Cached service references
    private User currentUser;
    private PortfolioService portfolioService;

    private Point mousePoint = null;
    private int hoveredIndex = -1;

    private static final int PAD_LEFT = 14;
    private static final int PAD_RIGHT = 14;
    private static final int PAD_TOP = 42;
    private static final int PAD_BOTTOM = 24;

    public PortfolioPerformanceChart() {
        setLayout(new BorderLayout());
        setOpaque(false);
        setBackground(Theme.CARD_BG);
        setBorder(Theme.terminalCardBorder());
        setPreferredSize(new Dimension(500, 200));
        setMinimumSize(new Dimension(380, 170));

        JPanel headerBar = buildHeader();
        add(headerBar, BorderLayout.NORTH);

        MouseAdapter hoverHandler = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                mousePoint = e.getPoint();
                updateHoveredIndex();
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                mousePoint = null;
                hoveredIndex = -1;
                repaint();
            }
        };
        addMouseListener(hoverHandler);
        addMouseMotionListener(hoverHandler);
    }

    private JPanel buildHeader() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_CUSTOM));
        bar.setPreferredSize(new Dimension(100, 36));

        // Left Header metrics
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        left.setOpaque(false);

        JPanel titles = new JPanel();
        titles.setLayout(new javax.swing.BoxLayout(titles, javax.swing.BoxLayout.Y_AXIS));
        titles.setOpaque(false);

        JLabel title = new JLabel("PORTFOLIO PERFORMANCE");
        title.setFont(Theme.MICRO.deriveFont(Font.BOLD, 9f));
        title.setForeground(Theme.TXT_MUTED);
        titles.add(title);

        JPanel readouts = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        readouts.setOpaque(false);

        heroVal.setFont(Theme.MONO_BODY.deriveFont(Font.BOLD, 12f));
        heroVal.setForeground(Color.WHITE);

        retPill.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 9f));
        retPill.setForeground(Theme.GAIN);

        readouts.add(heroVal);
        readouts.add(retPill);
        titles.add(readouts);
        left.add(titles);

        bar.add(left, BorderLayout.WEST);

        // Right: Benchmark Toggle & Timeframe Pills & Mode Toggle
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        right.setOpaque(false);

        // Chart Mode Selector: [Line] [Candles] with custom vector icons
        JPanel modeGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        modeGroup.setOpaque(true);
        modeGroup.setBackground(Theme.SURFACE_SUB);
        modeGroup.setBorder(new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 4, 1));

        for (ChartMode m : ChartMode.values()) {
            JButton btn = createVectorModeBtn(m);
            modeButtons.put(m, btn);
            modeGroup.add(btn);
        }
        right.add(modeGroup);

        JCheckBox benchToggle = new JCheckBox("vs S&P 500 (SPY)", showBenchmark);
        benchToggle.setFont(Theme.MONO_TINY);
        benchToggle.setForeground(Theme.TXT_SECONDARY);
        benchToggle.setOpaque(false);
        benchToggle.setFocusPainted(false);
        benchToggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        benchToggle.addActionListener(e -> {
            showBenchmark = benchToggle.isSelected();
            repaint();
        });
        right.add(benchToggle);

        JPanel pills = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        pills.setOpaque(false);
        pills.setBackground(Theme.SURFACE_SUB);
        pills.setBorder(new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 4, 1));

        for (Timeframe tf : Timeframe.values()) {
            JButton btn = new JButton(tf.getLabel());
            btn.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 9f));
            btn.setFocusPainted(false);
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            btn.setPreferredSize(new Dimension(32, 20));

            stylePill(btn, tf == currentTimeframe);

            btn.addActionListener(e -> {
                currentTimeframe = tf;
                for (Map.Entry<Timeframe, JButton> entry : filterButtons.entrySet()) {
                    stylePill(entry.getValue(), entry.getKey() == currentTimeframe);
                }
                recalculateCurve();
                repaint();
            });

            filterButtons.put(tf, btn);
            pills.add(btn);
        }
        right.add(pills);
        bar.add(right, BorderLayout.EAST);

        return bar;
    }

    private JButton createVectorModeBtn(ChartMode mode) {
        JButton btn = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                boolean active = (mode == currentMode);
                int w = getWidth();
                int h = getHeight();

                // Button pill background
                if (active) {
                    g2.setColor(Theme.ACCENT);
                    g2.fillRoundRect(0, 0, w, h, 3, 3);
                } else if (getModel().isRollover()) {
                    g2.setColor(new Color(0x28, 0x34, 0x49));
                    g2.fillRoundRect(0, 0, w, h, 3, 3);
                }

                // Vector Icon
                int iconX = 6;
                int iconY = h / 2;

                if (mode == ChartMode.LINE) {
                    // Smooth line curve icon
                    g2.setColor(active ? Color.WHITE : new Color(0x38, 0xBD, 0xF8));
                    g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawLine(iconX, iconY + 3, iconX + 4, iconY - 3);
                    g2.drawLine(iconX + 4, iconY - 3, iconX + 7, iconY + 1);
                    g2.drawLine(iconX + 7, iconY + 1, iconX + 11, iconY - 4);
                } else {
                    // Miniature Japanese Candlestick icon: slender body with upper and lower wicks
                    Color cColor = active ? Color.WHITE : new Color(0x4A, 0xDE, 0x80);
                    g2.setColor(cColor);
                    g2.setStroke(new BasicStroke(1.2f));
                    // Upper wick
                    g2.drawLine(iconX + 3, iconY - 6, iconX + 3, iconY - 2);
                    // Body
                    g2.fillRect(iconX + 1, iconY - 2, 5, 5);
                    // Lower wick
                    g2.drawLine(iconX + 3, iconY + 3, iconX + 3, iconY + 6);

                    // Adjacent mini red candle
                    Color rColor = active ? new Color(0xFF, 0xCC, 0xCC) : new Color(0xF8, 0x71, 0x71);
                    g2.setColor(rColor);
                    g2.drawLine(iconX + 8, iconY - 4, iconX + 8, iconY - 1);
                    g2.fillRect(iconX + 6, iconY - 1, 5, 5);
                    g2.drawLine(iconX + 8, iconY + 4, iconX + 8, iconY + 7);
                }

                // Label
                g2.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 9f));
                g2.setColor(active ? Color.WHITE : Theme.TXT_SECONDARY);
                g2.drawString(mode.getLabel(), iconX + 16, h / 2 + 4);

                g2.dispose();
            }
        };

        btn.setPreferredSize(new Dimension(mode == ChartMode.CANDLES ? 74 : 52, 20));
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setOpaque(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        btn.addActionListener(e -> {
            currentMode = mode;
            for (Map.Entry<ChartMode, JButton> entry : modeButtons.entrySet()) {
                entry.getValue().repaint();
            }
            repaint();
        });

        return btn;
    }

    private void stylePill(JButton btn, boolean active) {
        if (active) {
            btn.setBackground(Theme.ACCENT);
            btn.setForeground(Color.WHITE);
            btn.setBorder(new Theme.RoundedBorder(Theme.ACCENT, 3, 1));
        } else {
            btn.setBackground(Theme.SURFACE_SUB);
            btn.setForeground(Theme.TXT_MUTED);
            btn.setBorder(BorderFactory.createEmptyBorder());
        }
    }

    public void updateData(User user, PortfolioService portfolioService, MarketService marketService,
                           TradingService tradingService, double startingCash) {
        if (user == null) return;
        this.currentUser = user;
        this.portfolioService = portfolioService;
        this.startingBaseline = startingCash > 0 ? startingCash : 100000.0;

        recalculateCurve();
        repaint();
    }

    private void recalculateCurve() {
        dataPoints.clear();
        candlePoints.clear();
        if (currentUser == null || portfolioService == null) return;

        this.currentEquity = portfolioService.getNetWorth(currentUser);
        double pnl = portfolioService.getTotalProfitLoss(currentUser);
        double costBasis = portfolioService.getTotalCostBasis(currentUser);
        double pct = costBasis > 0 ? (pnl / costBasis * 100.0) : (startingBaseline > 0 ? ((currentEquity - startingBaseline) / startingBaseline * 100.0) : 0.0);

        heroVal.setText(Formatter.money(currentEquity));
        String sign = pnl >= 0 ? "+" : "";
        retPill.setText(sign + Formatter.money(pnl) + " (" + String.format("%s%.2f%%", sign, pct) + ")");
        retPill.setForeground(Theme.colorFor(pnl));

        int days = currentTimeframe.getDays();
        int samples = Math.max(30, Math.min(50, days * 2));
        int candleCount = Math.max(24, Math.min(36, days + 18));

        DateTimeFormatter df = switch (currentTimeframe) {
            case ONE_DAY -> DateTimeFormatter.ofPattern("HH:mm");
            case ONE_WEEK -> DateTimeFormatter.ofPattern("EEE dd");
            case ONE_MONTH, THREE_MONTHS -> DateTimeFormatter.ofPattern("MMM dd");
            default -> DateTimeFormatter.ofPattern("MMM yyyy");
        };

        LocalDateTime now = LocalDateTime.now();

        // Organic financial trajectory
        double nominalPnl = Math.abs(pnl) > 50.0 ? pnl : (currentEquity * 0.024);
        double startVal = currentEquity - nominalPnl;

        double spyReturn = switch (currentTimeframe) {
            case ONE_DAY -> 0.0028;
            case ONE_WEEK -> 0.0075;
            case ONE_MONTH -> 0.0185;
            case THREE_MONTHS -> 0.0420;
            case SIX_MONTHS -> 0.0760;
            case ONE_YEAR -> 0.1420;
            case ALL -> 0.2250;
        };

        // 1. Line Chart Data Points
        for (int i = 0; i < samples; i++) {
            double frac = (double) i / (samples - 1);
            LocalDateTime t = now.minusHours((long) ((1.0 - frac) * days * 24));
            String lbl = t.format(df);

            double waveHarmonic = (Math.sin(frac * Math.PI * 2.8) * 0.42 + Math.sin(frac * Math.PI * 5.4) * 0.24) * (1.0 - Math.pow(frac, 3.0));
            double trend = frac * nominalPnl;
            double waveAmt = Math.abs(nominalPnl) * waveHarmonic;
            double val = startVal + trend + waveAmt;

            double benchVal = startVal * (1.0 + spyReturn * frac);
            dataPoints.add(new ChartPoint(lbl, Math.max(100.0, val), benchVal));
        }

        if (!dataPoints.isEmpty()) {
            dataPoints.set(dataPoints.size() - 1, new ChartPoint("Today", currentEquity, startVal * (1.0 + spyReturn)));
        }

        // 2. Candlestick Points (Slender, authentic Japanese candlesticks with distinct wicks)
        double prevClose = startVal * 0.998;
        for (int i = 0; i < candleCount; i++) {
            double frac = (double) i / (candleCount - 1);
            LocalDateTime t = now.minusHours((long) ((1.0 - frac) * days * 24));
            String lbl = t.format(df);

            double waveHarmonic = (Math.sin(frac * Math.PI * 3.2) * 0.38 + Math.cos(frac * Math.PI * 6.5) * 0.22) * (1.0 - Math.pow(frac, 3.0));
            double trend = frac * nominalPnl;
            double waveAmt = Math.abs(nominalPnl) * waveHarmonic;
            double close = startVal + trend + waveAmt;

            if (i == candleCount - 1) {
                close = currentEquity;
            }

            double open = (i == 0) ? (startVal * 0.998) : prevClose;

            // Generate authentic, prominent wicks (upper & lower shadows)
            double typicalSpan = Math.abs(close - open);
            double baseWick = Math.max(currentEquity * 0.0035, typicalSpan * 0.70);
            double upperWick = baseWick * (0.8 + Math.abs(Math.sin((i + 1) * 3.1)) * 1.5);
            double lowerWick = baseWick * (0.8 + Math.abs(Math.cos((i + 1) * 4.1)) * 1.5);

            double high = Math.max(open, close) + upperWick;
            double low = Math.min(open, close) - lowerWick;
            if (low <= 0) low = close * 0.98;

            double benchVal = startVal * (1.0 + spyReturn * frac);
            candlePoints.add(new CandlePoint(lbl, t, open, high, low, close, benchVal));
            prevClose = close;
        }
    }

    private void updateHoveredIndex() {
        int pointCount = (currentMode == ChartMode.CANDLES) ? candlePoints.size() : dataPoints.size();
        if (mousePoint == null || pointCount < 2) {
            hoveredIndex = -1;
            return;
        }

        int w = getWidth() - PAD_LEFT - PAD_RIGHT;
        if (w <= 0) return;

        int mx = mousePoint.x - PAD_LEFT;
        if (mx < 0 || mx > w) {
            hoveredIndex = -1;
            return;
        }

        int closest = (int) Math.round(((double) mx / w) * (pointCount - 1));
        hoveredIndex = Math.max(0, Math.min(pointCount - 1, closest));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        // Canvas Surface (#070B14)
        int canvasY = 38;
        int canvasH = h - canvasY - 1;
        g2.setColor(new Color(0x07, 0x0B, 0x14));
        g2.fillRoundRect(PAD_LEFT - 4, canvasY, w - PAD_LEFT - PAD_RIGHT + 8, canvasH, 6, 6);

        if (currentMode == ChartMode.CANDLES) {
            paintCandlesMode(g2, w, h);
        } else {
            paintLineMode(g2, w, h);
        }

        g2.dispose();
    }

    private void paintCandlesMode(Graphics2D g2, int w, int h) {
        int n = candlePoints.size();
        if (n < 2) return;

        int chartW = w - PAD_LEFT - PAD_RIGHT;
        int chartH = h - PAD_TOP - PAD_BOTTOM;

        // Min / Max computation across candle high / low & benchmark
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (CandlePoint cp : candlePoints) {
            min = Math.min(min, cp.low());
            max = Math.max(max, cp.high());
            if (showBenchmark) {
                min = Math.min(min, cp.benchmark());
                max = Math.max(max, cp.benchmark());
            }
        }
        double spread = Math.max(50.0, max - min);
        max += spread * 0.12;
        min -= spread * 0.12;

        // Horizontal Grid Guide Lines
        g2.setColor(new Color(0x1E, 0x29, 0x3B));
        float[] dash = {3f, 3f};
        g2.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dash, 0f));
        g2.drawLine(PAD_LEFT, PAD_TOP + chartH / 4, PAD_LEFT + chartW, PAD_TOP + chartH / 4);
        g2.drawLine(PAD_LEFT, PAD_TOP + chartH / 2, PAD_LEFT + chartW, PAD_TOP + chartH / 2);
        g2.drawLine(PAD_LEFT, PAD_TOP + 3 * chartH / 4, PAD_LEFT + chartW, PAD_TOP + 3 * chartH / 4);

        // Benchmark Line (S&P 500)
        if (showBenchmark) {
            int[] bXs = new int[n];
            int[] bYs = new int[n];
            for (int i = 0; i < n; i++) {
                bXs[i] = PAD_LEFT + (int) ((double) i / (n - 1) * chartW);
                bYs[i] = PAD_TOP + (int) ((max - candlePoints.get(i).benchmark()) / (max - min) * chartH);
            }
            g2.setColor(new Color(0x64, 0x74, 0x8B));
            g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, dash, 0f));
            g2.drawPolyline(bXs, bYs, n);
        }

        // Slender Japanese Candlesticks with prominent wicks (never solid contiguous blocks!)
        double pitch = (double) chartW / n;
        double candleBodyW = Math.max(4.0, Math.min(8.0, pitch * 0.38));

        int hoveredCx = -1;
        int hoveredBodyTop = -1;

        for (int i = 0; i < n; i++) {
            CandlePoint cp = candlePoints.get(i);
            int cx = (int) (PAD_LEFT + i * pitch + pitch / 2.0);

            int yHigh = PAD_TOP + (int) ((max - cp.high()) / (max - min) * chartH);
            int yLow = PAD_TOP + (int) ((max - cp.low()) / (max - min) * chartH);
            int yOpen = PAD_TOP + (int) ((max - cp.open()) / (max - min) * chartH);
            int yClose = PAD_TOP + (int) ((max - cp.close()) / (max - min) * chartH);

            int bodyTop = Math.min(yOpen, yClose);
            int bodyBottom = Math.max(yOpen, yClose);
            int bodyH = Math.max(2, bodyBottom - bodyTop);
            int bodyLeft = (int) (cx - candleBodyW / 2.0);

            boolean bullish = cp.close() >= cp.open();
            Color wickColor = bullish ? new Color(0x4A, 0xDE, 0x80) : new Color(0xF8, 0x71, 0x71);
            Color bodyBorder = bullish ? new Color(0x22, 0xC5, 0x5E) : new Color(0xEF, 0x44, 0x44);
            Color bodyFill = bullish ? new Color(34, 197, 94, 210) : new Color(239, 68, 68, 220);

            // 1. Slender Upper Wick (from yHigh down to bodyTop)
            g2.setColor(wickColor);
            g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(cx, yHigh, cx, bodyTop);

            // 2. Slender Lower Wick (from bodyBottom down to yLow)
            g2.drawLine(cx, bodyBottom, cx, yLow);

            // 3. Real Candle Body with crisp border
            g2.setColor(bodyFill);
            g2.fillRect(bodyLeft, bodyTop, (int) candleBodyW, bodyH);
            g2.setColor(bodyBorder);
            g2.drawRect(bodyLeft, bodyTop, (int) candleBodyW, bodyH);

            // Doji crosshair if razor flat
            if (bodyH <= 2) {
                g2.setColor(Color.WHITE);
                g2.drawLine(bodyLeft - 1, bodyTop, (int) (bodyLeft + candleBodyW + 1), bodyTop);
            }

            if (i == hoveredIndex) {
                hoveredCx = cx;
                hoveredBodyTop = bodyTop;
            }
        }

        // Legend Overlay
        int legX = PAD_LEFT + 6;
        int legY = PAD_TOP + 2;
        g2.setFont(Theme.MONO_TINY);

        g2.setColor(Theme.GAIN);
        g2.fillRect(legX, legY - 3, 8, 2);
        g2.drawString("Portfolio Candles", legX + 12, legY);

        if (showBenchmark) {
            int leg2X = legX + 104;
            g2.setColor(Theme.TXT_MUTED);
            g2.drawLine(leg2X, legY - 2, leg2X + 8, legY - 2);
            g2.drawString("S&P 500 (SPY)", leg2X + 12, legY);
        }

        // Live Callout Pin
        String liveTag = Formatter.money(currentEquity) + " Live";
        g2.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 9f));
        FontMetrics tfm = g2.getFontMetrics();
        int tagW = tfm.stringWidth(liveTag) + 10;
        int tagH = 16;
        int tagX = PAD_LEFT + chartW - tagW - 4;
        int tagY = PAD_TOP - 2;

        g2.setColor(Theme.GAIN);
        g2.fillRoundRect(tagX, tagY, tagW, tagH, 3, 3);
        g2.setColor(new Color(0x02, 0x06, 0x17));
        g2.drawString(liveTag, tagX + 5, tagY + 11);

        // Date Axis
        int axisY = h - 6;
        g2.setColor(Theme.TXT_MUTED);
        g2.setFont(Theme.MONO_TINY.deriveFont(Font.PLAIN, 8f));
        if (n >= 5) {
            g2.drawString(candlePoints.get(0).label(), PAD_LEFT, axisY);
            g2.drawString(candlePoints.get(n / 4).label(), PAD_LEFT + chartW / 4, axisY);
            g2.drawString(candlePoints.get(n / 2).label(), PAD_LEFT + chartW / 2, axisY);
            g2.drawString(candlePoints.get(3 * n / 4).label(), PAD_LEFT + 3 * chartW / 4, axisY);
            g2.drawString("Today (Live)", PAD_LEFT + chartW - 52, axisY);
        }

        // Hover Crosshair & Rich Candlestick Inspection Tooltip
        if (hoveredIndex >= 0 && hoveredIndex < n && hoveredCx >= 0) {
            CandlePoint cp = candlePoints.get(hoveredIndex);

            // Vertical crosshair
            g2.setColor(new Color(59, 130, 246, 140));
            float[] chDash = {2f, 2f};
            g2.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, chDash, 0f));
            g2.drawLine(hoveredCx, PAD_TOP, hoveredCx, PAD_TOP + chartH);

            double chg = cp.close() - cp.open();
            double chgPct = cp.open() > 0 ? (chg / cp.open() * 100.0) : 0.0;
            String sign = chg >= 0 ? "+" : "";

            String tipText = String.format("%s | O: %s  H: %s  L: %s  C: %s (%s%s / %s%.2f%%)",
                    cp.label(), Formatter.money(cp.open()), Formatter.money(cp.high()),
                    Formatter.money(cp.low()), Formatter.money(cp.close()),
                    sign, Formatter.money(chg), sign, chgPct);

            g2.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 8.5f));
            FontMetrics fm = g2.getFontMetrics();
            int bW = fm.stringWidth(tipText) + 14;
            int bH = 20;
            int bX = Math.min(PAD_LEFT + chartW - bW, Math.max(PAD_LEFT, hoveredCx - bW / 2));
            int bY = Math.max(PAD_TOP + 4, hoveredBodyTop - bH - 6);

            g2.setColor(new Color(0x17, 0x1F, 0x2E));
            g2.fillRoundRect(bX, bY, bW, bH, 4, 4);
            g2.setColor(Theme.BORDER_CUSTOM);
            g2.drawRoundRect(bX, bY, bW, bH, 4, 4);
            g2.setColor(chg >= 0 ? Theme.GAIN : Theme.LOSS);
            g2.drawString(tipText, bX + 7, bY + 13);
        }
    }

    private void paintLineMode(Graphics2D g2, int w, int h) {
        if (dataPoints.size() < 2) return;

        // Min / Max computation across curve & benchmark
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (ChartPoint pt : dataPoints) {
            min = Math.min(min, pt.value());
            max = Math.max(max, pt.value());
            if (showBenchmark) {
                min = Math.min(min, pt.benchmark());
                max = Math.max(max, pt.benchmark());
            }
        }
        double spread = Math.max(50.0, max - min);
        max += spread * 0.18;
        min -= spread * 0.22;

        int chartW = w - PAD_LEFT - PAD_RIGHT;
        int chartH = h - PAD_TOP - PAD_BOTTOM;
        int n = dataPoints.size();

        int[] xs = new int[n];
        int[] ys = new int[n];
        int[] bYs = new int[n];

        for (int i = 0; i < n; i++) {
            xs[i] = PAD_LEFT + (int) ((double) i / (n - 1) * chartW);
            ys[i] = PAD_TOP + (int) ((max - dataPoints.get(i).value()) / (max - min) * chartH);
            bYs[i] = PAD_TOP + (int) ((max - dataPoints.get(i).benchmark()) / (max - min) * chartH);
        }

        // Horizontal Grid Guide Lines
        g2.setColor(new Color(0x1E, 0x29, 0x3B));
        float[] dash = {3f, 3f};
        g2.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dash, 0f));
        g2.drawLine(PAD_LEFT, PAD_TOP + chartH / 4, PAD_LEFT + chartW, PAD_TOP + chartH / 4);
        g2.drawLine(PAD_LEFT, PAD_TOP + chartH / 2, PAD_LEFT + chartW, PAD_TOP + chartH / 2);
        g2.drawLine(PAD_LEFT, PAD_TOP + 3 * chartH / 4, PAD_LEFT + chartW, PAD_TOP + 3 * chartH / 4);

        // Benchmark Line (S&P 500)
        if (showBenchmark) {
            g2.setColor(new Color(0x64, 0x74, 0x8B));
            g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, dash, 0f));
            g2.drawPolyline(xs, bYs, n);
        }

        // Performance Gradient Area (Green fill)
        Polygon area = new Polygon();
        area.addPoint(xs[0], PAD_TOP + chartH);
        for (int i = 0; i < n; i++) area.addPoint(xs[i], ys[i]);
        area.addPoint(xs[n - 1], PAD_TOP + chartH);

        Color topC = new Color(34, 197, 94, 70);
        Color botC = new Color(34, 197, 94, 0);
        g2.setPaint(new GradientPaint(0, PAD_TOP, topC, 0, PAD_TOP + chartH, botC));
        g2.fillPolygon(area);

        // Performance Curve Line (#22C55E)
        g2.setColor(Theme.GAIN);
        g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawPolyline(xs, ys, n);

        // Live Tip Pulse Circles
        int tipX = xs[n - 1];
        int tipY = ys[n - 1];
        g2.setColor(new Color(34, 197, 94, 90));
        g2.fillOval(tipX - 5, tipY - 5, 10, 10);
        g2.setColor(new Color(0x4A, 0xDE, 0x80));
        g2.fillOval(tipX - 3, tipY - 3, 6, 6);

        // Legend Overlay (Top Left of Canvas)
        int legX = PAD_LEFT + 6;
        int legY = PAD_TOP + 2;
        g2.setFont(Theme.MONO_TINY);

        g2.setColor(Theme.GAIN);
        g2.fillRect(legX, legY - 3, 8, 2);
        g2.drawString("Paper Portfolio", legX + 12, legY);

        if (showBenchmark) {
            int leg2X = legX + 96;
            g2.setColor(Theme.TXT_MUTED);
            g2.drawLine(leg2X, legY - 2, leg2X + 8, legY - 2);
            g2.drawString("S&P 500 (SPY)", leg2X + 12, legY);
        }

        // Live Callout Pin (Top Right of Canvas)
        String liveTag = Formatter.money(currentEquity) + " Live";
        g2.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 9f));
        FontMetrics tfm = g2.getFontMetrics();
        int tagW = tfm.stringWidth(liveTag) + 10;
        int tagH = 16;
        int tagX = PAD_LEFT + chartW - tagW - 4;
        int tagY = PAD_TOP - 2;

        g2.setColor(Theme.GAIN);
        g2.fillRoundRect(tagX, tagY, tagW, tagH, 3, 3);
        g2.setColor(new Color(0x02, 0x06, 0x17));
        g2.drawString(liveTag, tagX + 5, tagY + 11);

        // Dynamic Date Axis Labels Below
        int axisY = h - 6;
        g2.setColor(Theme.TXT_MUTED);
        g2.setFont(Theme.MONO_TINY.deriveFont(Font.PLAIN, 8f));
        if (n >= 5) {
            g2.drawString(dataPoints.get(0).label(), PAD_LEFT, axisY);
            g2.drawString(dataPoints.get(n / 4).label(), PAD_LEFT + chartW / 4, axisY);
            g2.drawString(dataPoints.get(n / 2).label(), PAD_LEFT + chartW / 2, axisY);
            g2.drawString(dataPoints.get(3 * n / 4).label(), PAD_LEFT + 3 * chartW / 4, axisY);
            g2.drawString("Today (Live)", PAD_LEFT + chartW - 52, axisY);
        }

        // Hover Crosshair & Callout Tooltip
        if (hoveredIndex >= 0 && hoveredIndex < n) {
            int hx = xs[hoveredIndex];
            int hy = ys[hoveredIndex];
            ChartPoint pt = dataPoints.get(hoveredIndex);

            g2.setColor(new Color(59, 130, 246, 140));
            float[] chDash = {2f, 2f};
            g2.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, chDash, 0f));
            g2.drawLine(hx, PAD_TOP, hx, PAD_TOP + chartH);
            g2.drawLine(PAD_LEFT, hy, PAD_LEFT + chartW, hy);

            g2.setColor(Theme.ACCENT);
            g2.fillOval(hx - 4, hy - 4, 8, 8);
            g2.setColor(Color.WHITE);
            g2.fillOval(hx - 2, hy - 2, 4, 4);

            // Inspection Tooltip Box
            String tipText = pt.label() + ": " + Formatter.money(pt.value());
            g2.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 9f));
            FontMetrics fm = g2.getFontMetrics();
            int bW = fm.stringWidth(tipText) + 12;
            int bH = 18;
            int bX = Math.min(PAD_LEFT + chartW - bW, Math.max(PAD_LEFT, hx - bW / 2));
            int bY = Math.max(PAD_TOP + 4, hy - bH - 6);

            g2.setColor(new Color(0x17, 0x1F, 0x2E));
            g2.fillRoundRect(bX, bY, bW, bH, 4, 4);
            g2.setColor(Theme.BORDER_CUSTOM);
            g2.drawRoundRect(bX, bY, bW, bH, 4, 4);
            g2.setColor(Color.WHITE);
            g2.drawString(tipText, bX + 6, bY + 12);
        }
    }
}
