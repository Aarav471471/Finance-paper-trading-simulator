package com.papertrader.ui.components;

import com.papertrader.model.OrderSide;
import com.papertrader.model.Transaction;
import com.papertrader.model.User;
import com.papertrader.service.TradingService;
import com.papertrader.ui.Theme;
import com.papertrader.util.CsvExporter;
import com.papertrader.util.Formatter;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Terminal Execution Ledger Panel (Column 3 Lower).
 * Features filled trade stream, status badges, CSV download, and View All link.
 */
public class ExecutionLedgerPanel extends JPanel {

    private final TradingService tradingService;
    private final Runnable onViewAll;
    private final JPanel listContainer = new JPanel();
    private List<Transaction> currentHistory = new java.util.ArrayList<>();

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss 'UTC'");

    public ExecutionLedgerPanel(TradingService tradingService, Runnable onViewAll) {
        this.tradingService = tradingService;
        this.onViewAll = onViewAll;

        setLayout(new BorderLayout(0, 4));
        setOpaque(false);
        setBackground(Theme.CARD_BG);
        setBorder(Theme.terminalCardBorder());
        setPreferredSize(new Dimension(260, 180));
        setMinimumSize(new Dimension(220, 150));

        // 1. Header (Title, CSV Audit Download, View All)
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_CUSTOM));
        header.setPreferredSize(new Dimension(100, 22));

        JLabel title = new JLabel("EXECUTION LEDGER");
        title.setFont(Theme.TINY.deriveFont(Font.BOLD, 10f));
        title.setForeground(Color.WHITE);
        header.add(title, BorderLayout.WEST);

        JPanel links = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        links.setOpaque(false);

        JButton csvBtn = new JButton("CSV ↓");
        csvBtn.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 9f));
        csvBtn.setForeground(Theme.ACCENT);
        csvBtn.setContentAreaFilled(false);
        csvBtn.setBorderPainted(false);
        csvBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        csvBtn.addActionListener(e -> exportCsv());

        JButton viewAllBtn = new JButton("View All →");
        viewAllBtn.setFont(Theme.MONO_TINY.deriveFont(Font.PLAIN, 9f));
        viewAllBtn.setForeground(Theme.TXT_MUTED);
        viewAllBtn.setContentAreaFilled(false);
        viewAllBtn.setBorderPainted(false);
        viewAllBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (onViewAll != null) {
            viewAllBtn.addActionListener(e -> onViewAll.run());
        }

        links.add(csvBtn);
        links.add(viewAllBtn);
        header.add(links, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // 2. Stream List
        listContainer.setLayout(new GridLayout(0, 1, 0, 2));
        listContainer.setOpaque(false);

        JScrollPane scroll = new JScrollPane(listContainer);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(14);
        add(scroll, BorderLayout.CENTER);
    }

    public void updateData(User user) {
        listContainer.removeAll();
        if (user == null) {
            currentHistory.clear();
            listContainer.revalidate();
            listContainer.repaint();
            return;
        }

        List<Transaction> history = tradingService.getTransactionHistory(user);
        this.currentHistory = history != null ? history : new java.util.ArrayList<>();
        if (this.currentHistory.isEmpty()) {
            JLabel empty = new JLabel("No executions logged in session", JLabel.CENTER);
            empty.setFont(Theme.MONO_TINY);
            empty.setForeground(Theme.TXT_MUTED);
            listContainer.add(empty);
        } else {
            // Show latest executions
            int limit = 12;
            int count = 0;
            for (int i = this.currentHistory.size() - 1; i >= 0 && count < limit; i--, count++) {
                Transaction t = this.currentHistory.get(i);
                listContainer.add(createRow(t));
            }
        }

        listContainer.revalidate();
        listContainer.repaint();
    }

    private JPanel createRow(Transaction t) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(40, 52, 73, 100)),
                BorderFactory.createEmptyBorder(3, 2, 3, 2)));

        boolean isBuy = t.getSide() == OrderSide.BUY;

        // Left: Side Badge + Title
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        left.setOpaque(false);

        JLabel badge = new JLabel(t.getSide().name());
        badge.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 8f));
        badge.setForeground(isBuy ? Theme.GAIN : Theme.LOSS);
        badge.setOpaque(true);
        badge.setBackground(isBuy ? Theme.GREEN_BG : Theme.RED_BG);
        badge.setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 4));
        left.add(badge);

        JPanel details = new JPanel();
        details.setLayout(new javax.swing.BoxLayout(details, javax.swing.BoxLayout.Y_AXIS));
        details.setOpaque(false);

        JLabel topLabel = new JLabel(t.getQuantity() + " " + t.getSymbol() + " @ " + Formatter.money(t.getPrice()));
        topLabel.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 9f));
        topLabel.setForeground(Color.WHITE);

        JLabel timeLabel = new JLabel(t.getTimestamp().format(TIME_FMT));
        timeLabel.setFont(Theme.MICRO.deriveFont(Font.PLAIN, 8f));
        timeLabel.setForeground(Theme.TXT_MUTED);

        details.add(topLabel);
        details.add(timeLabel);
        left.add(details);
        row.add(left, BorderLayout.WEST);

        // Right: Amount + FILLED
        JPanel right = new JPanel();
        right.setLayout(new javax.swing.BoxLayout(right, javax.swing.BoxLayout.Y_AXIS));
        right.setOpaque(false);

        JLabel amt = new JLabel(Formatter.money(t.getTotal()));
        amt.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 9f));
        amt.setForeground(new Color(0xE2, 0xE8, 0xF0));
        amt.setAlignmentX(JLabel.RIGHT_ALIGNMENT);

        JLabel filled = new JLabel("FILLED");
        filled.setFont(Theme.MICRO.deriveFont(Font.BOLD, 8f));
        filled.setForeground(isBuy ? Theme.GAIN : Theme.LOSS);
        filled.setAlignmentX(JLabel.RIGHT_ALIGNMENT);

        right.add(amt);
        right.add(filled);
        row.add(right, BorderLayout.EAST);

        return row;
    }

    private void exportCsv() {
        if (currentHistory == null || currentHistory.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No transactions available to export.", "Audit Export", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("PaperTrader_Audit_" + System.currentTimeMillis() + ".csv"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                CsvExporter.export(currentHistory, "ID,User,Timestamp,Symbol,Side,Type,Quantity,Price,Fee", chooser.getSelectedFile());
                JOptionPane.showMessageDialog(this, "Exported " + currentHistory.size() + " transactions to CSV.", "Audit Export", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
