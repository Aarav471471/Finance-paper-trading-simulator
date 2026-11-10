package com.papertrader.ui;

import com.papertrader.bot.BotService;
import com.papertrader.bot.TradingBot;
import com.papertrader.model.Asset;
import com.papertrader.model.User;
import com.papertrader.service.AuthService;
import com.papertrader.service.MarketService;
import com.papertrader.strategy.TradingStrategy;
import com.papertrader.ui.components.StrategyPicker;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Algorithmic trading sandbox: start bots that trade automatically by a strategy or a custom rule. */
public class AutoTradePanel extends BasePanel {

    private final AuthService authService;
    private final BotService botService;

    private final JComboBox<Asset> assetBox;
    private final StrategyPicker strategyPicker = new StrategyPicker();
    private final JSpinner quantitySpinner = new JSpinner(new SpinnerNumberModel(1, 1, 100_000, 1));
    private final BotTableModel model = new BotTableModel();
    private final JTable table = new JTable(model);
    private final JTextArea logArea = new JTextArea();

    public AutoTradePanel(AuthService authService, MarketService marketService, BotService botService) {
        super("Auto-Trade", "Bots check 1-minute candles every 5 seconds and place real paper orders on your account.");
        this.authService = authService;
        this.botService = botService;
        this.assetBox = new JComboBox<>(marketService.getAllAssets().toArray(new Asset[0]));

        logArea.setEditable(false);
        logArea.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
        logArea.setBackground(Theme.CARD);

        JButton stopButton = new JButton("Stop selected bot");
        stopButton.addActionListener(e -> stopSelected());
        JPanel tableCard = new JPanel(new BorderLayout(0, 8));
        tableCard.setBackground(Theme.CARD);
        tableCard.setBorder(Theme.cardBorder());
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("Your bots");
        title.setFont(Theme.HEADING);
        title.setForeground(Theme.TEXT);
        header.add(title, BorderLayout.WEST);
        header.add(stopButton, BorderLayout.EAST);
        tableCard.add(header, BorderLayout.NORTH);
        tableCard.add(new JScrollPane(table), BorderLayout.CENTER);

        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(Theme.cardBorder());
        JSplitPane right = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableCard, logScroll);
        right.setResizeWeight(0.5);
        right.setBorder(null);

        JPanel content = new JPanel(new BorderLayout(14, 0));
        content.setOpaque(false);
        content.add(buildForm(), BorderLayout.WEST);
        content.add(right, BorderLayout.CENTER);
        add(content, BorderLayout.CENTER);

        assetBox.addActionListener(e -> strategyPicker.setReferencePrice(((Asset) assetBox.getSelectedItem()).getPrice()));
        strategyPicker.setReferencePrice(((Asset) assetBox.getSelectedItem()).getPrice());
    }

    private JPanel buildForm() {
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBackground(Theme.CARD);
        form.setBorder(Theme.cardBorder());
        form.setPreferredSize(new Dimension(290, 100));

        JLabel heading = new JLabel("New bot");
        heading.setFont(Theme.HEADING);
        heading.setForeground(Theme.TEXT);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(heading);
        form.add(Box.createVerticalStrut(12));
        addLabeled(form, "Asset", assetBox);
        strategyPicker.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(strategyPicker);
        form.add(Box.createVerticalStrut(8));
        addLabeled(form, "Units per trade", quantitySpinner);

        JButton startButton = new JButton("Start bot");
        startButton.putClientProperty("JButton.buttonType", "default");
        startButton.setBackground(Theme.ACCENT);
        startButton.setForeground(java.awt.Color.WHITE);
        startButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        startButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        startButton.addActionListener(e -> startBot());
        form.add(Box.createVerticalStrut(6));
        form.add(startButton);
        form.add(Box.createVerticalGlue());
        JLabel note = new JLabel("<html><body style='width:200px'>Tip: test a strategy on the Backtest page first. "
                + "Bots use the same order engine as you (fees, spread, slippage) and stop when you log out.</body></html>");
        note.setFont(Theme.SMALL);
        note.setForeground(Theme.MUTED);
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(note);
        return form;
    }

    private void addLabeled(JPanel panel, String label, JComponent field) {
        JLabel l = new JLabel(label);
        l.setForeground(Theme.MUTED);
        l.setFont(Theme.SMALL);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        panel.add(l);
        panel.add(field);
        panel.add(Box.createVerticalStrut(8));
    }

    private void startBot() {
        try {
            TradingStrategy strategy = strategyPicker.createStrategy();
            botService.addBot(new TradingBot(authService.getCurrentUser(), (Asset) assetBox.getSelectedItem(),
                    strategy, (Integer) quantitySpinner.getValue()));
            refresh();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Bot not started", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void stopSelected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a bot first.");
            return;
        }
        TradingBot bot = model.bots.get(row);
        if (bot.isRunning()) {
            bot.stop();
        }
        refresh();
    }

    @Override
    public void refresh() {
        User user = authService.getCurrentUser();
        if (user == null) {
            return;
        }
        int selected = table.getSelectedRow();
        model.bots = botService.getBots(user);
        model.fireTableDataChanged();
        if (selected >= 0 && selected < model.getRowCount()) {
            table.setRowSelectionInterval(selected, selected);
        }

        List<String> lines = new ArrayList<>();
        for (TradingBot bot : model.bots) {
            lines.addAll(bot.getLog());
        }
        Collections.sort(lines);           // lines start with the time, so this sorts by time
        Collections.reverse(lines);        // newest first
        String text = lines.isEmpty() ? "No bots yet. Choose a strategy on the left and press Start bot." : String.join("\n", lines);
        if (!text.equals(logArea.getText())) {
            logArea.setText(text);
            logArea.setCaretPosition(0);
        }
    }

    private static class BotTableModel extends AbstractTableModel {
        private final String[] columns = {"#", "Asset", "Strategy", "Units", "Last signal", "Status"};
        private List<TradingBot> bots = new ArrayList<>();

        @Override
        public int getRowCount() {
            return bots.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int row, int column) {
            TradingBot b = bots.get(row);
            switch (column) {
                case 0: return b.getId();
                case 1: return b.getAsset().getSymbol();
                case 2: return b.getStrategy().getName();
                case 3: return b.getQuantity();
                case 4: return b.getLastSignal();
                default: return b.isRunning() ? "Running" : "Stopped";
            }
        }
    }
}
