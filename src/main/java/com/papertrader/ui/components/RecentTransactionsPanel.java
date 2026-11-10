package com.papertrader.ui.components;

import com.papertrader.model.OrderSide;
import com.papertrader.model.Transaction;
import com.papertrader.model.User;
import com.papertrader.service.TradingService;
import com.papertrader.ui.Theme;
import com.papertrader.util.Formatter;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Premium Recent Activity panel.
 * Displays rich transaction cards with buy/sell status pills,
 * execution price, volume, and total trade amounts.
 */
public class RecentTransactionsPanel extends JPanel {

    private final TradingService tradingService;
    private final Runnable onViewAll;
    private final JPanel listContainer = new JPanel();

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM dd");

    public RecentTransactionsPanel(TradingService tradingService, Runnable onViewAll) {
        this.tradingService = tradingService;
        this.onViewAll = onViewAll;

        setLayout(new BorderLayout(0, 10));
        setOpaque(false);
        setBackground(Theme.CARD);
        setBorder(Theme.cardBorder());
        setPreferredSize(new Dimension(330, 290));
        setMinimumSize(new Dimension(280, 230));

        // Header with "View All" link
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel titleLabel = new JLabel("RECENT ACTIVITY");
        titleLabel.setFont(Theme.SMALL.deriveFont(Font.BOLD, 11f));
        titleLabel.setForeground(Theme.MUTED);
        header.add(titleLabel, BorderLayout.WEST);

        if (onViewAll != null) {
            JButton viewAllBtn = new JButton("View All →");
            viewAllBtn.setFont(Theme.SMALL.deriveFont(Font.BOLD, 11f));
            viewAllBtn.setForeground(Theme.ACCENT);
            viewAllBtn.setOpaque(false);
            viewAllBtn.setContentAreaFilled(false);
            viewAllBtn.setBorderPainted(false);
            viewAllBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            viewAllBtn.addActionListener(e -> onViewAll.run());
            header.add(viewAllBtn, BorderLayout.EAST);
        }
        add(header, BorderLayout.NORTH);

        // Transaction list container
        listContainer.setLayout(new GridLayout(0, 1, 0, 6));
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
            listContainer.revalidate();
            listContainer.repaint();
            return;
        }

        List<Transaction> history = tradingService.getTransactionHistory(user);
        if (history.isEmpty()) {
            JLabel emptyLabel = new JLabel("No trades yet. Head to Trade to execute orders.", JLabel.CENTER);
            emptyLabel.setFont(Theme.SMALL);
            emptyLabel.setForeground(Theme.MUTED);
            listContainer.add(emptyLabel);
        } else {
            // Show latest 10 transactions in reverse chronological order
            int count = 0;
            for (int i = history.size() - 1; i >= 0 && count < 10; i--, count++) {
                Transaction t = history.get(i);
                listContainer.add(new TransactionItem(t));
            }
        }

        listContainer.revalidate();
        listContainer.repaint();
    }

    private static class TransactionItem extends JPanel {
        private final Transaction t;
        private boolean hovered = false;

        TransactionItem(Transaction t) {
            this.t = t;
            setLayout(new BorderLayout());
            setOpaque(false);
            setPreferredSize(new Dimension(100, 48));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hovered = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hovered = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            // Background card
            g2.setColor(hovered ? Theme.CARD_HOVER : Theme.CARD_HEADER);
            g2.fillRoundRect(0, 0, w - 1, h - 1, 8, 8);
            g2.setColor(hovered ? Theme.BORDER_LIGHT : Theme.BORDER_SUBTLE);
            g2.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);

            boolean isBuy = t.getSide() == OrderSide.BUY;
            int curX = 10;

            // Side Badge (BUY / SELL)
            int badgeW = 40;
            int badgeH = 20;
            int badgeY = (h - badgeH) / 2;

            g2.setColor(isBuy ? Theme.GREEN_BG : Theme.RED_BG);
            g2.fillRoundRect(curX, badgeY, badgeW, badgeH, 6, 6);
            g2.setFont(Theme.TINY.deriveFont(Font.BOLD, 10f));
            g2.setColor(isBuy ? Theme.GREEN : Theme.RED);
            String sideStr = t.getSide().name();
            FontMetrics bfm = g2.getFontMetrics();
            g2.drawString(sideStr, curX + (badgeW - bfm.stringWidth(sideStr)) / 2, badgeY + 14);

            curX += badgeW + 10;

            // Symbol + Order Type (Top line)
            g2.setFont(Theme.BODY.deriveFont(Font.BOLD, 12f));
            g2.setColor(Theme.TEXT);
            g2.drawString(t.getSymbol(), curX, 20);

            int symW = g2.getFontMetrics().stringWidth(t.getSymbol());
            g2.setFont(Theme.TINY);
            g2.setColor(Theme.MUTED_DARK);
            g2.drawString("• " + t.getOrderType(), curX + symW + 5, 20);

            // Quantity @ Price (Bottom line)
            g2.setFont(Theme.SMALL);
            g2.setColor(Theme.MUTED);
            String qtyPriceStr = t.getQuantity() + " @ " + Formatter.money(t.getPrice());
            g2.drawString(qtyPriceStr, curX, 38);

            // Right side: Total Amount (Top line) + Timestamp (Bottom line)
            String totalStr = Formatter.money(t.getTotal());
            g2.setFont(Theme.BODY.deriveFont(Font.BOLD, 12f));
            g2.setColor(Theme.TEXT);
            FontMetrics vfm = g2.getFontMetrics();
            g2.drawString(totalStr, w - 12 - vfm.stringWidth(totalStr), 20);

            String timeStr = t.getTimestamp().format(TIME_FMT);
            g2.setFont(Theme.TINY);
            g2.setColor(Theme.MUTED_DARK);
            FontMetrics tfm = g2.getFontMetrics();
            g2.drawString(timeStr, w - 12 - tfm.stringWidth(timeStr), 38);

            g2.dispose();
        }
    }
}
