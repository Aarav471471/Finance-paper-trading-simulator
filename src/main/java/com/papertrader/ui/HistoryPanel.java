package com.papertrader.ui;

import com.papertrader.model.Transaction;
import com.papertrader.model.User;
import com.papertrader.service.AuthService;
import com.papertrader.service.TradingService;
import com.papertrader.ui.components.MoneyRenderer;
import com.papertrader.util.CsvExporter;
import com.papertrader.util.Formatter;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Every completed trade, newest first, with CSV export. */
public class HistoryPanel extends BasePanel {

    private final AuthService authService;
    private final TradingService tradingService;
    private final HistoryTableModel model = new HistoryTableModel();

    public HistoryPanel(AuthService authService, TradingService tradingService) {
        super("History", "All your completed trades.");
        this.authService = authService;
        this.tradingService = tradingService;

        JTable table = new JTable(model);
        MoneyRenderer money = new MoneyRenderer();
        for (int col = 6; col <= 8; col++) {
            table.getColumnModel().getColumn(col).setCellRenderer(money);
        }
        table.getColumnModel().getColumn(0).setMaxWidth(60);

        JButton exportButton = new JButton("Export to CSV");
        exportButton.addActionListener(e -> exportCsv());
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        toolbar.setOpaque(false);
        toolbar.add(exportButton);

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setOpaque(false);
        content.add(toolbar, BorderLayout.NORTH);
        content.add(new JScrollPane(table), BorderLayout.CENTER);
        add(content, BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        User user = authService.getCurrentUser();
        if (user == null) {
            return;
        }
        List<Transaction> list = tradingService.getTransactionHistory(user);
        if (list.size() != model.rows.size()) {     // only redraw when a new trade happened
            Collections.reverse(list);
            model.rows = list;
            model.fireTableDataChanged();
        }
    }

    private void exportCsv() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("transactions.csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            CsvExporter.export(model.rows, Transaction.CSV_HEADER, chooser.getSelectedFile());
            JOptionPane.showMessageDialog(this, "Saved " + model.rows.size() + " trades to "
                    + chooser.getSelectedFile().getName());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not save: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static class HistoryTableModel extends AbstractTableModel {
        private final String[] columns = {"#", "Time", "Symbol", "Side", "Type", "Qty", "Price", "Fee", "Total"};
        private List<Transaction> rows = new ArrayList<>();

        @Override
        public int getRowCount() {
            return rows.size();
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
            Transaction t = rows.get(row);
            switch (column) {
                case 0: return t.getId();
                case 1: return Formatter.dateTime(t.getTimestamp());
                case 2: return t.getSymbol();
                case 3: return t.getSide();
                case 4: return t.getOrderType();
                case 5: return t.getQuantity();
                case 6: return t.getPrice();
                case 7: return t.getFee();
                default: return t.getTotal();
            }
        }
    }
}
