package com.papertrader.ui;

import com.papertrader.exception.SimulatorException;
import com.papertrader.model.Asset;
import com.papertrader.model.Order;
import com.papertrader.model.OrderSide;
import com.papertrader.model.OrderStatus;
import com.papertrader.model.StopLossOrder;
import com.papertrader.model.User;
import com.papertrader.service.AuthService;
import com.papertrader.service.MarketService;
import com.papertrader.service.TradingService;
import com.papertrader.ui.components.MoneyRenderer;
import com.papertrader.util.Formatter;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;

/** Place Market, Limit and Stop-Loss orders, preview the cost, and manage waiting orders. */
public class TradePanel extends BasePanel {

    private static final String MARKET = "Market";
    private static final String LIMIT = "Limit";
    private static final String STOP_LOSS = "Stop-Loss";

    private final AuthService authService;
    private final TradingService tradingService;

    private final JComboBox<Asset> assetBox;
    private final JComboBox<OrderSide> sideBox = new JComboBox<>(OrderSide.values());
    private final JComboBox<String> typeBox = new JComboBox<>(new String[]{MARKET, LIMIT, STOP_LOSS});
    private final JSpinner quantitySpinner = new JSpinner(new SpinnerNumberModel(1, 1, 1_000_000, 1));
    private final JSpinner priceSpinner = new JSpinner(new SpinnerNumberModel(100.0, 0.01, 100_000_000.0, 0.5));
    private final JLabel priceLabel = new JLabel("Trigger price");

    private final JLabel currentPriceValue = previewValue();
    private final JLabel ownedValue = previewValue();
    private final JLabel tradeValue = previewValue();
    private final JLabel feeValue = previewValue();
    private final JLabel totalValue = previewValue();
    private final JLabel cashAfterValue = previewValue();

    private final PendingTableModel pendingModel = new PendingTableModel();
    private final JTable pendingTable = new JTable(pendingModel);

    public TradePanel(AuthService authService, MarketService marketService, TradingService tradingService) {
        super("Trade", "Buy or sell with Market, Limit or Stop-Loss orders.");
        this.authService = authService;
        this.tradingService = tradingService;
        this.assetBox = new JComboBox<>(marketService.getAllAssets().toArray(new Asset[0]));

        JPanel left = buildOrderForm();
        JPanel right = new JPanel(new BorderLayout(0, 14));
        right.setOpaque(false);
        right.add(buildPreviewCard(), BorderLayout.NORTH);
        right.add(buildPendingCard(), BorderLayout.CENTER);

        JPanel content = new JPanel(new GridLayout(1, 2, 14, 0));
        content.setOpaque(false);
        content.add(left);
        content.add(right);
        add(content, BorderLayout.CENTER);

        priceSpinner.setEditor(new JSpinner.NumberEditor(priceSpinner, "0.00"));
        assetBox.addActionListener(e -> resetTriggerPrice());
        typeBox.addActionListener(e -> resetTriggerPrice());
        sideBox.addActionListener(e -> refresh());
        quantitySpinner.addChangeListener(e -> refresh());
        resetTriggerPrice();
    }

    // ---------------- building the screen ----------------

    private JPanel buildOrderForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Theme.CARD);
        form.setBorder(Theme.cardBorder());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(8, 4, 8, 4);
        c.weightx = 1;
        c.gridx = 0;

        JLabel heading = new JLabel("New order");
        heading.setFont(Theme.HEADING);
        heading.setForeground(Theme.TEXT);
        form.add(heading, c);
        addRow(form, c, new JLabel("Asset"), assetBox);
        addRow(form, c, new JLabel("Buy or sell"), sideBox);
        addRow(form, c, new JLabel("Order type"), typeBox);
        addRow(form, c, new JLabel("Quantity"), quantitySpinner);
        addRow(form, c, priceLabel, priceSpinner);

        JLabel help = new JLabel("<html><body style='width:300px'>"
                + "<b>Market</b>: executes now at the current price.<br>"
                + "<b>Limit</b>: buy at or below / sell at or above your price.<br>"
                + "<b>Stop-Loss</b>: sell if the price falls to your price (or buy if it rises to it)."
                + "</body></html>");
        help.setFont(Theme.SMALL);
        help.setForeground(Theme.MUTED);
        form.add(help, c);

        JButton placeButton = new JButton("Place order");
        placeButton.putClientProperty("JButton.buttonType", "default");
        placeButton.setBackground(Theme.ACCENT);
        placeButton.setForeground(java.awt.Color.WHITE);
        placeButton.setFont(Theme.HEADING);
        placeButton.addActionListener(e -> placeOrder());
        c.insets = new Insets(18, 4, 8, 4);
        form.add(placeButton, c);

        c.weighty = 1;
        form.add(new JLabel(), c);   // pushes everything to the top
        return form;
    }

    private void addRow(JPanel form, GridBagConstraints c, JLabel label, JComponent field) {
        label.setForeground(Theme.MUTED);
        label.setFont(Theme.SMALL);
        c.insets = new Insets(8, 4, 2, 4);
        form.add(label, c);
        c.insets = new Insets(0, 4, 4, 4);
        form.add(field, c);
    }

    private JPanel buildPreviewCard() {
        JPanel card = new JPanel(new GridLayout(0, 2, 8, 8));
        card.setBackground(Theme.CARD);
        card.setBorder(BorderFactory.createTitledBorder(Theme.cardBorder(), "Order preview", 0, 0, Theme.HEADING, Theme.TEXT));
        card.add(previewLabel("Current price"));
        card.add(currentPriceValue);
        card.add(previewLabel("You own"));
        card.add(ownedValue);
        card.add(previewLabel("Trade value"));
        card.add(tradeValue);
        card.add(previewLabel("Brokerage fee"));
        card.add(feeValue);
        card.add(previewLabel("Total"));
        card.add(totalValue);
        card.add(previewLabel("Cash after trade"));
        card.add(cashAfterValue);
        return card;
    }

    private JPanel buildPendingCard() {
        JPanel card = new JPanel(new BorderLayout(0, 10));
        card.setBackground(Theme.CARD);
        card.setBorder(Theme.cardBorder());
        JLabel heading = new JLabel("Waiting orders");
        heading.setFont(Theme.HEADING);
        heading.setForeground(Theme.TEXT);
        JButton cancelButton = new JButton("Cancel selected");
        cancelButton.addActionListener(e -> cancelSelected());
        pendingTable.getColumnModel().getColumn(5).setCellRenderer(new MoneyRenderer());

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(heading, BorderLayout.WEST);
        header.add(cancelButton, BorderLayout.EAST);
        card.add(header, BorderLayout.NORTH);
        card.add(new JScrollPane(pendingTable), BorderLayout.CENTER);
        return card;
    }

    private static JLabel previewLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(Theme.MUTED);
        return label;
    }

    private static JLabel previewValue() {
        JLabel label = new JLabel("-");
        label.setForeground(Theme.TEXT);
        label.setFont(Theme.HEADING);
        return label;
    }

    // ---------------- actions ----------------

    private void resetTriggerPrice() {
        Asset asset = (Asset) assetBox.getSelectedItem();
        boolean needsPrice = !MARKET.equals(typeBox.getSelectedItem());
        priceSpinner.setEnabled(needsPrice);
        if (!needsPrice) {
            priceLabel.setText("Trigger price (not needed for a Market order)");
        } else {
            priceLabel.setText(LIMIT.equals(typeBox.getSelectedItem()) ? "Limit price" : "Stop price");
        }
        if (asset != null) {
            priceSpinner.setValue(asset.getPrice());
        }
        refresh();
    }

    private void placeOrder() {
        User user = authService.getCurrentUser();
        Asset asset = (Asset) assetBox.getSelectedItem();
        OrderSide side = (OrderSide) sideBox.getSelectedItem();
        int quantity = (Integer) quantitySpinner.getValue();
        double price = (Double) priceSpinner.getValue();
        String type = (String) typeBox.getSelectedItem();

        try {
            Order order;
            // The three calls below use the three OVERLOADED versions of placeOrder().
            if (MARKET.equals(type)) {
                order = tradingService.placeOrder(user, asset, side, quantity);
            } else if (LIMIT.equals(type)) {
                order = tradingService.placeOrder(user, asset, side, quantity, price);
            } else {
                order = tradingService.placeOrder(user, new StopLossOrder(asset, side, quantity, price));
            }

            if (order.getStatus() == OrderStatus.FILLED) {
                JOptionPane.showMessageDialog(this, side + " " + quantity + " " + asset.getSymbol() + " filled at "
                        + Formatter.money(order.getFilledPrice()), "Order filled", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "Order #" + order.getId() + " is waiting and will execute "
                        + order.getConditionText() + ".", "Order placed", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (SimulatorException | IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Order not placed", JOptionPane.ERROR_MESSAGE);
        }
        refresh();
    }

    private void cancelSelected() {
        int row = pendingTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a waiting order first.");
            return;
        }
        tradingService.cancelOrder(pendingModel.orders.get(row));
        refresh();
    }

    // ---------------- refresh ----------------

    @Override
    public void refresh() {
        User user = authService.getCurrentUser();
        Asset asset = (Asset) assetBox.getSelectedItem();
        if (user == null || asset == null) {
            return;
        }
        int quantity = (Integer) quantitySpinner.getValue();
        boolean isBuy = sideBox.getSelectedItem() == OrderSide.BUY;
        double price = MARKET.equals(typeBox.getSelectedItem()) ? asset.getPrice() : (Double) priceSpinner.getValue();
        double value = price * quantity;
        double fee = tradingService.calculateFee(value);
        double total = isBuy ? value + fee : value - fee;
        double cash = user.getAccount().getBalance();
        double cashAfter = isBuy ? cash - total : cash + total;

        currentPriceValue.setText(Formatter.money(asset.getPrice()));
        ownedValue.setText(user.getPortfolio().getQuantity(asset.getSymbol()) + " unit(s)");
        tradeValue.setText(Formatter.money(value));
        feeValue.setText(Formatter.money(fee));
        totalValue.setText((isBuy ? "Pay " : "Receive ") + Formatter.money(total));
        cashAfterValue.setText(Formatter.money(cashAfter));
        cashAfterValue.setForeground(cashAfter < 0 ? Theme.RED : Theme.TEXT);

        pendingModel.setOrders(tradingService.getPendingOrders(user));
    }

    /** Rows of the "Waiting orders" table. */
    private class PendingTableModel extends AbstractTableModel {
        private final String[] columns = {"#", "Type", "Side", "Qty", "Symbol", "Now", "Condition"};
        private List<Order> orders = new ArrayList<>();

        void setOrders(List<Order> newOrders) {
            boolean sameRows = newOrders.equals(orders);
            orders = newOrders;
            if (sameRows) {
                fireTableRowsUpdated(0, Math.max(0, orders.size() - 1));   // keep selection
            } else {
                fireTableDataChanged();
            }
        }

        @Override
        public int getRowCount() {
            return orders.size();
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
            Order o = orders.get(row);
            switch (column) {
                case 0: return o.getId();
                case 1: return o.getOrderType();
                case 2: return o.getSide();
                case 3: return o.getQuantity();
                case 4: return o.getAsset().getSymbol();
                case 5: return o.getAsset().getPrice();
                default: return o.getConditionText();
            }
        }
    }
}
