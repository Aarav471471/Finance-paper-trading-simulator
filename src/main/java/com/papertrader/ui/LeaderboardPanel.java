package com.papertrader.ui;

import com.papertrader.model.User;
import com.papertrader.service.AnalyticsService;
import com.papertrader.service.AuthService;
import com.papertrader.service.LeaderboardService;
import com.papertrader.service.TradingService;
import com.papertrader.bot.BotService;
import com.papertrader.ui.components.ColoredNumberRenderer;
import com.papertrader.ui.components.MoneyRenderer;
import com.papertrader.util.Formatter;

import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

/** Ranking of every trader on this computer, plus "Reset my account". */
public class LeaderboardPanel extends BasePanel {

    private final AuthService authService;
    private final LeaderboardService leaderboardService;
    private final TradingService tradingService;
    private final AnalyticsService analyticsService;
    private final BotService botService;
    private final double startingCash;
    private final RankingModel model = new RankingModel();

    public LeaderboardPanel(AuthService authService, LeaderboardService leaderboardService,
                            TradingService tradingService, AnalyticsService analyticsService,
                            BotService botService, double startingCash) {
        super("Leaderboard", "Every trader starts with " + Formatter.money(startingCash) + ". Highest net worth wins.");
        this.authService = authService;
        this.leaderboardService = leaderboardService;
        this.tradingService = tradingService;
        this.analyticsService = analyticsService;
        this.botService = botService;
        this.startingCash = startingCash;

        JTable table = new JTable(model);
        table.getColumnModel().getColumn(0).setMaxWidth(80);
        table.getColumnModel().getColumn(2).setCellRenderer(new MoneyRenderer());
        table.getColumnModel().getColumn(3).setCellRenderer(new ColoredNumberRenderer(ColoredNumberRenderer.Style.PERCENT));
        table.getColumnModel().getColumn(4).setCellRenderer(new ColoredNumberRenderer(ColoredNumberRenderer.Style.MONEY));
        table.getColumnModel().getColumn(1).setCellRenderer(new CurrentUserRenderer());

        JButton resetButton = new JButton("Reset my account");
        resetButton.setForeground(Theme.RED);
        resetButton.addActionListener(e -> resetAccount());
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        toolbar.setOpaque(false);
        toolbar.add(resetButton);

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setOpaque(false);
        content.add(toolbar, BorderLayout.NORTH);
        content.add(new JScrollPane(table), BorderLayout.CENTER);
        add(content, BorderLayout.CENTER);
    }

    private void resetAccount() {
        User user = authService.getCurrentUser();
        int answer = JOptionPane.showConfirmDialog(this,
                "This removes all your holdings, trades, bots and history and gives you "
                        + Formatter.money(startingCash) + " again. Continue?",
                "Reset account", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer == JOptionPane.YES_OPTION) {
            botService.stopAll(user);
            tradingService.resetAccount(user, startingCash);
            analyticsService.clearHistory(user);
            refresh();
        }
    }

    @Override
    public void refresh() {
        model.rows = leaderboardService.getRanking();
        model.fireTableDataChanged();
    }

    private class RankingModel extends AbstractTableModel {
        private final String[] columns = {"Rank", "Trader", "Net worth", "Return", "Realized P&L", "Trades"};
        private List<LeaderboardService.Entry> rows = new ArrayList<>();

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
            LeaderboardService.Entry e = rows.get(row);
            switch (column) {
                case 0: return e.rank() == 1 ? "1st" : e.rank() == 2 ? "2nd" : e.rank() == 3 ? "3rd" : e.rank() + "th";
                case 1: return e.username();
                case 2: return e.netWorth();
                case 3: return e.returnPercent();
                case 4: return e.realizedProfit();
                default: return e.trades();
            }
        }
    }

    /** Shows the logged-in user's name in bold blue so they can find themselves. */
    private class CurrentUserRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus,
                                                       int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focus, row, column);
            User user = authService.getCurrentUser();
            boolean me = user != null && user.getUsername().equals(value);
            setFont(getFont().deriveFont(me ? Font.BOLD : Font.PLAIN));
            setForeground(me ? Theme.ACCENT : Theme.TEXT);
            setText(me ? value + "  (you)" : String.valueOf(value));
            return this;
        }
    }
}
