package com.papertrader.ui;

import com.papertrader.analytics.EquityPoint;
import com.papertrader.analytics.PerformanceAnalyzer;
import com.papertrader.analytics.PerformanceReport;
import com.papertrader.backtest.BacktestEngine;
import com.papertrader.interfaces.FeeCalculator;
import com.papertrader.model.Asset;
import com.papertrader.model.Timeframe;
import com.papertrader.service.ChartService;
import com.papertrader.service.MarketService;
import com.papertrader.strategy.TradingStrategy;
import com.papertrader.ui.components.CandleChartPanel;
import com.papertrader.ui.components.LineChartPanel;
import com.papertrader.ui.components.StatCard;
import com.papertrader.ui.components.StrategyPicker;
import com.papertrader.util.Formatter;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * Backtesting & historical replay: run a strategy over the last year of REAL daily candles,
 * watching it trade candle by candle at 1x / 2x / 5x speed (or instantly).
 */
public class BacktestPanel extends BasePanel {

    private static final double STARTING_CASH = 100_000;
    private static final int BASE_DELAY_MS = 120;   // one candle every 120 ms at 1x

    private final ChartService chartService;
    private final FeeCalculator fees;

    private final JComboBox<Asset> assetBox;
    private final StrategyPicker strategyPicker = new StrategyPicker();
    private final JComboBox<String> speedBox = new JComboBox<>(new String[]{"1x", "2x", "5x", "Instant"});
    private final JButton startButton = new JButton("Start");
    private final JButton pauseButton = new JButton("Pause");
    private final JButton resetButton = new JButton("Reset");
    private final JLabel statusLabel = new JLabel(" ");

    private final CandleChartPanel chart = new CandleChartPanel();
    private final LineChartPanel equityChart = new LineChartPanel();
    private final StatCard returnCard = new StatCard("Return");
    private final StatCard benchmarkCard = new StatCard("Buy & hold");
    private final StatCard sharpeCard = new StatCard("Sharpe/Sortino");
    private final StatCard drawdownCard = new StatCard("Max drawdown");
    private final StatCard winCard = new StatCard("Win % / PF");

    private final Timer replayTimer = new Timer(BASE_DELAY_MS, e -> replayStep());
    private BacktestEngine engine;
    private String engineTitle = "";

    public BacktestPanel(MarketService marketService, ChartService chartService, FeeCalculator fees) {
        super("Backtest", "Replay a year of real daily prices and see how a strategy would have done. "
                + "Uses virtual cash, so your account is not touched.");
        this.chartService = chartService;
        this.fees = fees;
        this.assetBox = new JComboBox<>(marketService.getAllAssets().toArray(new Asset[0]));

        JPanel cards = new JPanel(new GridLayout(1, 5, 12, 0));
        cards.setOpaque(false);
        for (StatCard c : new StatCard[]{returnCard, benchmarkCard, sharpeCard, drawdownCard, winCard}) {
            cards.add(c);
        }
        chart.setBorder(Theme.cardBorder());
        equityChart.setBorder(Theme.cardBorder());
        JSplitPane charts = new JSplitPane(JSplitPane.VERTICAL_SPLIT, chart, equityChart);
        charts.setResizeWeight(0.65);
        charts.setBorder(null);

        JPanel center = new JPanel(new BorderLayout(0, 12));
        center.setOpaque(false);
        center.add(cards, BorderLayout.NORTH);
        center.add(charts, BorderLayout.CENTER);

        JPanel content = new JPanel(new BorderLayout(14, 0));
        content.setOpaque(false);
        content.add(buildControls(), BorderLayout.WEST);
        content.add(center, BorderLayout.CENTER);
        add(content, BorderLayout.CENTER);

        assetBox.addActionListener(e -> strategyPicker.setReferencePrice(((Asset) assetBox.getSelectedItem()).getPrice()));
        strategyPicker.setReferencePrice(((Asset) assetBox.getSelectedItem()).getPrice());
        speedBox.addActionListener(e -> applySpeed());
        startButton.addActionListener(e -> start());
        pauseButton.addActionListener(e -> replayTimer.stop());
        resetButton.addActionListener(e -> reset());
        reset();
    }

    private JPanel buildControls() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(Theme.CARD);
        panel.setBorder(Theme.cardBorder());
        panel.setPreferredSize(new Dimension(290, 100));

        JLabel heading = new JLabel("Replay settings");
        heading.setFont(Theme.HEADING);
        heading.setForeground(Theme.TEXT);
        addLeft(panel, heading);
        panel.add(Box.createVerticalStrut(12));
        addLabeled(panel, "Asset", assetBox);
        strategyPicker.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(strategyPicker);
        panel.add(Box.createVerticalStrut(8));
        addLabeled(panel, "Replay speed", speedBox);
        panel.add(Box.createVerticalStrut(10));

        JPanel buttons = new JPanel(new GridLayout(1, 3, 6, 0));
        buttons.setOpaque(false);
        startButton.putClientProperty("JButton.buttonType", "default");
        startButton.setBackground(Theme.ACCENT);
        startButton.setForeground(java.awt.Color.WHITE);
        buttons.add(startButton);
        buttons.add(pauseButton);
        buttons.add(resetButton);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        panel.add(buttons);
        panel.add(Box.createVerticalStrut(12));
        statusLabel.setForeground(Theme.MUTED);
        statusLabel.setFont(Theme.SMALL);
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(statusLabel);
        panel.add(Box.createVerticalGlue());
        JLabel note = new JLabel("<html><body style='width:200px'>Rules: all-in on BUY, sell everything on SELL. "
                + "Starts with " + Formatter.money(STARTING_CASH) + ", pays the normal fee and 0.05% slippage per trade. "
                + "Green arrows = buys, gold arrows = sells.</body></html>");
        note.setFont(Theme.SMALL);
        note.setForeground(Theme.MUTED);
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(note);
        return panel;
    }

    private static void addLeft(JPanel panel, JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(c);
    }

    private void addLabeled(JPanel panel, String label, JComponent field) {
        JLabel l = new JLabel(label);
        l.setForeground(Theme.MUTED);
        l.setFont(Theme.SMALL);
        addLeft(panel, l);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        addLeft(panel, field);
        panel.add(Box.createVerticalStrut(8));
    }

    private void applySpeed() {
        String speed = (String) speedBox.getSelectedItem();
        int factor = "2x".equals(speed) ? 2 : "5x".equals(speed) ? 5 : 1;
        replayTimer.setDelay(BASE_DELAY_MS / factor);
    }

    private void start() {
        if (engine != null && engine.hasNext()) {   // resume after pause
            applySpeed();
            replayTimer.start();
            return;
        }
        TradingStrategy strategy;
        try {
            strategy = strategyPicker.createStrategy();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Check the rule", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Asset asset = (Asset) assetBox.getSelectedItem();
        statusLabel.setText("Downloading a year of " + asset.getSymbol() + " daily candles...");
        startButton.setEnabled(false);
        chartService.loadAsync(asset, Timeframe.ONE_DAY, candles -> {
            startButton.setEnabled(true);
            if (candles.size() < 40) {
                statusLabel.setText("Not enough history for " + asset.getSymbol());
                return;
            }
            engine = new BacktestEngine(candles, strategy, STARTING_CASH, fees, 0.05);
            engineTitle = asset.getSymbol() + "  -  " + strategy.getName() + "   (source: "
                    + chartService.getSourceName(asset, Timeframe.ONE_DAY) + ")";
            applySpeed();
            replayTimer.start();
        });
    }

    private void replayStep() {
        if (engine == null) {
            replayTimer.stop();
            return;
        }
        if ("Instant".equals(speedBox.getSelectedItem())) {
            engine.runToEnd();
        } else if (engine.hasNext()) {
            engine.step();
        }
        if (!engine.hasNext()) {
            replayTimer.stop();
        }
        updateView();
    }

    private void reset() {
        replayTimer.stop();
        engine = null;
        chart.setData("Choose an asset and strategy, then press Start replay", new ArrayList<>(), true);
        equityChart.setData("Strategy equity", new ArrayList<>());
        statusLabel.setText(" ");
        for (StatCard c : new StatCard[]{returnCard, benchmarkCard, sharpeCard, drawdownCard, winCard}) {
            c.setValue("-", Theme.TEXT);
            c.setNote(" ", Theme.MUTED);
        }
    }

    private void updateView() {
        chart.setData(engineTitle, engine.getCandlesSoFar(), engine.getMarkers(), true);
        List<Double> values = new ArrayList<>();
        for (EquityPoint p : engine.getEquityCurve()) {
            values.add(p.value());
        }
        equityChart.setData("Strategy equity: " + Formatter.money(engine.getCurrentEquity()), values);

        PerformanceReport r = engine.getReport();
        double benchmark = engine.getBuyAndHoldReturnPercent();
        returnCard.setValue(Formatter.percent(r.totalReturnPercent()), Theme.colorFor(r.totalReturnPercent()));
        returnCard.setNote(engine.isHolding() ? "Holding" : "In cash", Theme.MUTED);
        benchmarkCard.setValue(Formatter.percent(benchmark), Theme.colorFor(benchmark));
        boolean beat = r.totalReturnPercent() > benchmark;
        benchmarkCard.setNote(beat ? "Strategy wins" : "Strategy behind", beat ? Theme.GREEN : Theme.RED);
        sharpeCard.setValue(PerformanceAnalyzer.format(r.sharpeRatio(), "") + " / "
                + PerformanceAnalyzer.format(r.sortinoRatio(), ""), Theme.TEXT);
        sharpeCard.setNote("Annualised", Theme.MUTED);
        drawdownCard.setValue("-" + PerformanceAnalyzer.format(r.maxDrawdownPercent(), "%"), Theme.RED);
        drawdownCard.setNote("Peak to low", Theme.MUTED);
        winCard.setValue((Double.isNaN(r.winRatePercent()) ? "n/a" : String.format("%.0f%%", r.winRatePercent()))
                + " / " + (Double.isNaN(r.profitFactor()) || Double.isInfinite(r.profitFactor())
                ? PerformanceAnalyzer.format(r.profitFactor(), "") : String.format("%.1f", r.profitFactor())), Theme.TEXT);
        winCard.setNote(r.closedTrades() + " trade(s)", Theme.MUTED);

        statusLabel.setText("Candle " + engine.getProgress() + " of " + engine.getTotalCandles()
                + (engine.hasNext() ? "" : "  -  finished"));
    }

    @Override
    public void refresh() {
        // the replay timer drives this page; nothing to do on the 1-second refresh
    }
}
