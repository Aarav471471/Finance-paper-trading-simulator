package com.papertrader.ui;

import com.papertrader.model.Asset;
import com.papertrader.service.MarketService;
import com.papertrader.ui.components.ColoredNumberRenderer;
import com.papertrader.ui.components.LineChartPanel;
import com.papertrader.ui.components.MoneyRenderer;
import com.papertrader.util.Formatter;

import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Live prices of every asset, plus a line chart of the selected one. */
public class MarketPanel extends BasePanel {

    private final MarketService marketService;
    private final MarketTableModel tableModel = new MarketTableModel();
    private final JTable table = new JTable(tableModel);
    private final LineChartPanel chart = new LineChartPanel();

    /** Last price we showed for each symbol, so we know whether to flash green or red. */
    private final Map<String, Double> lastShownPrice = new HashMap<>();
    private final Map<String, Integer> direction = new HashMap<>();

    public MarketPanel(MarketService marketService) {
        super("Market", "Live market prices. Change % is since the previous close. Click a row for its chart.");
        this.marketService = marketService;

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(4).setCellRenderer(new FlashingPriceRenderer());
        table.getColumnModel().getColumn(5).setCellRenderer(new ColoredNumberRenderer(ColoredNumberRenderer.Style.PERCENT));
        table.getSelectionModel().addListSelectionListener(e -> updateChart());
        table.setRowSelectionInterval(0, 0);

        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setPreferredSize(new java.awt.Dimension(100, 330));
        chart.setBorder(Theme.cardBorder());
        chart.setPreferredSize(new java.awt.Dimension(100, 320));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, chart);
        split.setResizeWeight(0.5);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);
    }

    private void updateChart() {
        int row = table.getSelectedRow();
        if (row < 0) {
            return;
        }
        Asset asset = tableModel.getAsset(row);
        chart.setData(asset.getSymbol() + "  " + Formatter.money(asset.getPrice()) + "   ("
                + asset.getType() + ", " + asset.getRiskLevel() + " risk)", marketService.getPriceHistory(asset.getSymbol()));
    }

    @Override
    public void refresh() {
        for (Asset a : marketService.getAllAssets()) {
            Double last = lastShownPrice.get(a.getSymbol());
            int dir = last == null ? 0 : Double.compare(a.getPrice(), last);
            direction.put(a.getSymbol(), dir);
            lastShownPrice.put(a.getSymbol(), a.getPrice());
        }
        tableModel.fireTableRowsUpdated(0, tableModel.getRowCount() - 1);   // keeps the selected row
        updateChart();
    }

    /** INNER CLASS: the table model turns the list of assets into rows and columns. */
    private class MarketTableModel extends AbstractTableModel {
        private final String[] columns = {"Symbol", "Name", "Type", "Risk", "Price", "Change"};

        private List<Asset> assets() {
            return marketService.getAllAssets();
        }

        Asset getAsset(int row) {
            return assets().get(row);
        }

        @Override
        public int getRowCount() {
            return assets().size();
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
            Asset a = getAsset(row);
            switch (column) {
                case 0: return a.getSymbol();
                case 1: return a.getName();
                case 2: return a.getType();          // polymorphic call
                case 3: return a.getRiskLevel();     // polymorphic call
                case 4: return a.getPrice();
                default: return a.getChangePercent();
            }
        }
    }

    /** Shows the price green if it just went up, red if it just went down. */
    private class FlashingPriceRenderer extends MoneyRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus,
                                                       int row, int column) {
            super.getTableCellRendererComponent(t, value, selected, focus, row, column);
            int dir = direction.getOrDefault(tableModel.getAsset(row).getSymbol(), 0);
            Color color = dir > 0 ? Theme.GREEN : dir < 0 ? Theme.RED : Theme.TEXT;
            setForeground(color);
            setHorizontalAlignment(SwingConstants.RIGHT);
            return this;
        }
    }
}
