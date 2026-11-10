package com.papertrader.ui;

import com.papertrader.model.Holding;
import com.papertrader.model.User;
import com.papertrader.service.AuthService;
import com.papertrader.service.MarketService;
import com.papertrader.service.PortfolioService;
import com.papertrader.ui.components.ColoredNumberRenderer;
import com.papertrader.ui.components.MoneyRenderer;
import com.papertrader.ui.components.StatCard;
import com.papertrader.util.Formatter;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;

/** Everything the user owns, with average cost and live profit / loss. */
public class PortfolioPanel extends BasePanel {

    private final AuthService authService;
    private final MarketService marketService;
    private final PortfolioService portfolioService;

    private final StatCard costCard = new StatCard("Total invested");
    private final StatCard valueCard = new StatCard("Current value");
    private final StatCard pnlCard = new StatCard("Unrealised P&L");
    private final HoldingsTableModel model = new HoldingsTableModel();
    private final JTable table = new JTable(model);

    public PortfolioPanel(AuthService authService, MarketService marketService, PortfolioService portfolioService) {
        super("Portfolio", "Your holdings, valued at live prices.");
        this.authService = authService;
        this.marketService = marketService;
        this.portfolioService = portfolioService;

        MoneyRenderer money = new MoneyRenderer();
        for (int col = 3; col <= 5; col++) {
            table.getColumnModel().getColumn(col).setCellRenderer(money);
        }
        table.getColumnModel().getColumn(6).setCellRenderer(new ColoredNumberRenderer(ColoredNumberRenderer.Style.MONEY));
        table.getColumnModel().getColumn(7).setCellRenderer(new ColoredNumberRenderer(ColoredNumberRenderer.Style.PERCENT));

        JPanel cards = new JPanel(new GridLayout(1, 3, 14, 0));
        cards.setOpaque(false);
        cards.add(costCard);
        cards.add(valueCard);
        cards.add(pnlCard);

        JPanel content = new JPanel(new BorderLayout(0, 14));
        content.setOpaque(false);
        content.add(cards, BorderLayout.NORTH);
        content.add(new JScrollPane(table), BorderLayout.CENTER);
        add(content, BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        User user = authService.getCurrentUser();
        if (user == null) {
            return;
        }
        double cost = portfolioService.getTotalCostBasis(user);
        double value = portfolioService.getTotalMarketValue(user);
        double pnl = value - cost;
        costCard.setValue(Formatter.money(cost), Theme.TEXT);
        valueCard.setValue(Formatter.money(value), Theme.TEXT);
        pnlCard.setValue(Formatter.signedMoney(pnl), Theme.colorFor(pnl));
        pnlCard.setNote(cost == 0 ? " " : Formatter.percent(pnl / cost * 100), Theme.colorFor(pnl));

        int selected = table.getSelectedRow();
        model.holdings = user.getPortfolio().getHoldings();
        model.fireTableDataChanged();
        if (selected >= 0 && selected < model.getRowCount()) {
            table.setRowSelectionInterval(selected, selected);
        }
    }

    private class HoldingsTableModel extends AbstractTableModel {
        private final String[] columns = {"Symbol", "Name", "Quantity", "Avg cost", "Price", "Market value", "P&L", "P&L %"};
        private List<Holding> holdings = new ArrayList<>();

        @Override
        public int getRowCount() {
            return holdings.size();
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
            Holding h = holdings.get(row);
            switch (column) {
                case 0: return h.getSymbol();
                case 1: return marketService.findBySymbol(h.getSymbol()).map(a -> a.getName()).orElse("?");
                case 2: return h.getQuantity();
                case 3: return h.getAverageCost();
                case 4: return portfolioService.getCurrentPrice(h);
                case 5: return portfolioService.getMarketValue(h);
                case 6: return portfolioService.getProfitLoss(h);
                default: return portfolioService.getProfitLossPercent(h);
            }
        }
    }
}
