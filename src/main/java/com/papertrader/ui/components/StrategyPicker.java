package com.papertrader.ui.components;

import com.papertrader.strategy.BuyAndHoldStrategy;
import com.papertrader.strategy.PriceRuleStrategy;
import com.papertrader.strategy.RsiStrategy;
import com.papertrader.strategy.SmaCrossoverStrategy;
import com.papertrader.strategy.TradingStrategy;
import com.papertrader.ui.Theme;

import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.Component;
import java.awt.Dimension;

/**
 * Lets the user choose a strategy (and the prices for a custom rule).
 * Shared by the Backtest page and the Auto-Trade page (code reuse).
 */
public class StrategyPicker extends JPanel {

    private static final String SMA = "SMA Crossover (10/30)";
    private static final String RSI = "RSI (14, 30/70)";
    private static final String HOLD = "Buy & Hold";
    private static final String RULE = "Custom price rule";

    private final JComboBox<String> strategyBox = new JComboBox<>(new String[]{SMA, RSI, HOLD, RULE});
    private final JSpinner buyBelow = new JSpinner(new SpinnerNumberModel(100.0, 0.0001, 1e9, 1.0));
    private final JSpinner sellAbove = new JSpinner(new SpinnerNumberModel(110.0, 0.0001, 1e9, 1.0));
    private final JLabel description = new JLabel();
    private final JPanel ruleFields = new JPanel();

    public StrategyPicker() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        buyBelow.setEditor(new JSpinner.NumberEditor(buyBelow, "0.00"));
        sellAbove.setEditor(new JSpinner.NumberEditor(sellAbove, "0.00"));

        ruleFields.setLayout(new BoxLayout(ruleFields, BoxLayout.Y_AXIS));
        ruleFields.setOpaque(false);
        addRow(ruleFields, "IF price <=  ...  THEN BUY", buyBelow);
        addRow(ruleFields, "IF price >=  ...  THEN SELL", sellAbove);

        addRow(this, "Strategy", strategyBox);
        add(ruleFields);
        description.setForeground(Theme.MUTED);
        description.setFont(Theme.SMALL);
        description.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(description);

        strategyBox.addActionListener(e -> updateVisibility());
        updateVisibility();
    }

    private void addRow(JPanel panel, String label, JComponent field) {
        JLabel l = new JLabel(label);
        l.setForeground(Theme.MUTED);
        l.setFont(Theme.SMALL);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        panel.add(l);
        panel.add(field);
        panel.add(javax.swing.Box.createVerticalStrut(8));
    }

    private void updateVisibility() {
        ruleFields.setVisible(RULE.equals(strategyBox.getSelectedItem()));
        try {
            description.setText("<html><body style='width:200px'>" + createStrategy().getDescription() + "</body></html>");
        } catch (IllegalArgumentException e) {
            description.setText(e.getMessage());
        }
        revalidate();
    }

    /** Pre-fills the custom rule around the current price (buy 2% lower, sell 2% higher). */
    public void setReferencePrice(double price) {
        buyBelow.setValue(Math.round(price * 0.98 * 100) / 100.0);
        sellAbove.setValue(Math.round(price * 1.02 * 100) / 100.0);
        updateVisibility();
    }

    /** Builds the chosen strategy object (a FACTORY method). */
    public TradingStrategy createStrategy() {
        String choice = (String) strategyBox.getSelectedItem();
        if (SMA.equals(choice)) {
            return new SmaCrossoverStrategy(10, 30);
        }
        if (RSI.equals(choice)) {
            return new RsiStrategy(14, 30, 70);
        }
        if (HOLD.equals(choice)) {
            return new BuyAndHoldStrategy();
        }
        return new PriceRuleStrategy((Double) buyBelow.getValue(), (Double) sellAbove.getValue());
    }
}
