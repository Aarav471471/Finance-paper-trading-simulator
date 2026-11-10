package com.papertrader.ui.components;

import com.papertrader.exception.SimulatorException;
import com.papertrader.model.Asset;
import com.papertrader.model.LimitOrder;
import com.papertrader.model.OrderSide;
import com.papertrader.model.StopLossOrder;
import com.papertrader.model.User;
import com.papertrader.service.AuthService;
import com.papertrader.service.MarketService;
import com.papertrader.service.TradingService;
import com.papertrader.ui.Theme;
import com.papertrader.util.Formatter;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.List;

/**
 * Terminal Order Execution Panel (Column 3 Upper).
 * Features [BUY / LONG] vs [SELL / SHORT] toggles, order type tabs,
 * quantity stepper [-] [+] with quick allocation chips [25%, 50%, 75%, MAX],
 * real-time financial calculations, validation engine, and execution CTA.
 */
public class OrderTerminalPanel extends JPanel {

    private final AuthService authService;
    private final MarketService marketService;
    private final TradingService tradingService;
    private final Runnable onOrderExecuted;

    private Asset currentAsset;
    private OrderSide currentSide = OrderSide.BUY;
    private String orderType = "Market"; // Market, Limit, Stop
    private int quantity = 50;

    // UI Elements
    private final JButton btnBuy = new JButton("BUY / LONG");
    private final JButton btnSell = new JButton("SELL / SHORT");
    private final JLabel assetTitleLabel = new JLabel("NVDA - NVIDIA Corp");
    private final JLabel assetPriceLabel = new JLabel("$138.45");

    private final JButton typeMarket = new JButton("Market");
    private final JButton typeLimit = new JButton("Limit");
    private final JButton typeStop = new JButton("Stop");

    private final JLabel maxSharesLabel = new JLabel("Max: 466");
    private final JTextField qtyField = new JTextField("50");
    private final JLabel estValLabel = new JLabel("$6,922.50");
    private final JLabel feeLabel = new JLabel("$0.00 (Zero Fee)");
    private final JLabel availCashLabel = new JLabel("$64,544.89");
    private final JLabel remCashLabel = new JLabel("$57,622.39");
    private final JLabel valMsgLabel = new JLabel("✓ Order parameters valid & within liquidity limits");
    private final JButton submitBtn = new JButton("EXECUTE PAPER BUY (50 NVDA)");

    public OrderTerminalPanel(AuthService authService, MarketService marketService, TradingService tradingService, Runnable onOrderExecuted) {
        this.authService = authService;
        this.marketService = marketService;
        this.tradingService = tradingService;
        this.onOrderExecuted = onOrderExecuted;

        setLayout(new BorderLayout(0, 5));
        setOpaque(false);
        setBackground(Theme.CARD_BG);
        setBorder(Theme.terminalCardBorder());
        setPreferredSize(new Dimension(260, 310));
        setMinimumSize(new Dimension(220, 290));

        // Default asset
        List<Asset> assets = marketService.getAllAssets();
        if (!assets.isEmpty()) {
            currentAsset = assets.get(0);
        }

        // 1. Header with Simulated / Zero Risk badge
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_CUSTOM));
        header.setPreferredSize(new Dimension(100, 22));

        JLabel title = new JLabel("ORDER EXECUTION");
        title.setFont(Theme.TINY.deriveFont(Font.BOLD, 10f));
        title.setForeground(Color.WHITE);

        JLabel simBadge = new JLabel("SIMULATED / ZERO RISK");
        simBadge.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 8f));
        simBadge.setForeground(Theme.GAIN);
        simBadge.setOpaque(true);
        simBadge.setBackground(Theme.GREEN_BG);
        simBadge.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(new Color(34, 197, 94, 80), 3, 1),
                BorderFactory.createEmptyBorder(1, 4, 1, 4)));

        header.add(title, BorderLayout.WEST);
        header.add(simBadge, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // 2. Body Form
        JPanel form = new JPanel();
        form.setLayout(new javax.swing.BoxLayout(form, javax.swing.BoxLayout.Y_AXIS));
        form.setOpaque(false);

        // BUY / SELL Toggle
        JPanel sideBox = new JPanel(new GridLayout(1, 2, 4, 0));
        sideBox.setOpaque(false);
        sideBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

        styleSideButtons();
        btnBuy.addActionListener(e -> { currentSide = OrderSide.BUY; styleSideButtons(); recalculate(); });
        btnSell.addActionListener(e -> { currentSide = OrderSide.SELL; styleSideButtons(); recalculate(); });
        sideBox.add(btnBuy);
        sideBox.add(btnSell);
        form.add(javax.swing.Box.createVerticalStrut(4));
        form.add(sideBox);

        // Selected Asset Display Banner
        JPanel assetBanner = new JPanel(new BorderLayout());
        assetBanner.setBackground(Theme.SURFACE_SUB);
        assetBanner.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 4, 1),
                BorderFactory.createEmptyBorder(3, 6, 3, 6)));
        assetBanner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        assetTitleLabel.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 10f));
        assetTitleLabel.setForeground(Color.WHITE);
        assetPriceLabel.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 10f));
        assetPriceLabel.setForeground(Theme.GAIN);

        assetBanner.add(assetTitleLabel, BorderLayout.WEST);
        assetBanner.add(assetPriceLabel, BorderLayout.EAST);
        form.add(javax.swing.Box.createVerticalStrut(4));
        form.add(assetBanner);

        // Order Type Tabs: Market, Limit, Stop
        JPanel typeRow = new JPanel(new BorderLayout());
        typeRow.setOpaque(false);
        typeRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

        JLabel otLabel = new JLabel("ORDER TYPE");
        otLabel.setFont(Theme.MICRO.deriveFont(Font.BOLD, 8f));
        otLabel.setForeground(Theme.TXT_MUTED);
        typeRow.add(otLabel, BorderLayout.WEST);

        JPanel typePills = new JPanel(new GridLayout(1, 3, 2, 0));
        typePills.setOpaque(false);

        styleTypeBtn(typeMarket, true);
        styleTypeBtn(typeLimit, false);
        styleTypeBtn(typeStop, false);

        typeMarket.addActionListener(e -> setOrderType("Market"));
        typeLimit.addActionListener(e -> setOrderType("Limit"));
        typeStop.addActionListener(e -> setOrderType("Stop"));

        typePills.add(typeMarket);
        typePills.add(typeLimit);
        typePills.add(typeStop);
        typeRow.add(typePills, BorderLayout.EAST);
        form.add(javax.swing.Box.createVerticalStrut(4));
        form.add(typeRow);

        // Quantity Stepper [-] [qty] [+]
        JPanel qtyRow = new JPanel(new BorderLayout());
        qtyRow.setOpaque(false);
        qtyRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

        JLabel qLabel = new JLabel("QUANTITY (UNITS)");
        qLabel.setFont(Theme.MICRO.deriveFont(Font.BOLD, 8f));
        qLabel.setForeground(Theme.TXT_MUTED);

        maxSharesLabel.setFont(Theme.MONO_TINY);
        maxSharesLabel.setForeground(Theme.TXT_SECONDARY);

        JPanel qtyHeader = new JPanel(new BorderLayout());
        qtyHeader.setOpaque(false);
        qtyHeader.add(qLabel, BorderLayout.WEST);
        qtyHeader.add(maxSharesLabel, BorderLayout.EAST);
        form.add(javax.swing.Box.createVerticalStrut(2));
        form.add(qtyHeader);

        JPanel stepper = new JPanel(new BorderLayout(4, 0));
        stepper.setBackground(new Color(0x07, 0x0B, 0x14));
        stepper.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 4, 1),
                BorderFactory.createEmptyBorder(2, 4, 2, 4)));
        stepper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

        JButton minusBtn = new JButton("-");
        minusBtn.setFont(Theme.MONO_BODY.deriveFont(Font.BOLD, 12f));
        minusBtn.setForeground(Theme.TXT_PRIMARY);
        minusBtn.setBackground(Theme.SURFACE_SUB);
        minusBtn.setBorder(new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 3, 1));
        minusBtn.setPreferredSize(new Dimension(20, 18));
        minusBtn.setFocusPainted(false);
        minusBtn.addActionListener(e -> stepQty(-10));

        JButton plusBtn = new JButton("+");
        plusBtn.setFont(Theme.MONO_BODY.deriveFont(Font.BOLD, 12f));
        plusBtn.setForeground(Theme.TXT_PRIMARY);
        plusBtn.setBackground(Theme.SURFACE_SUB);
        plusBtn.setBorder(new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 3, 1));
        plusBtn.setPreferredSize(new Dimension(20, 18));
        plusBtn.setFocusPainted(false);
        plusBtn.addActionListener(e -> stepQty(10));

        qtyField.setHorizontalAlignment(JTextField.CENTER);
        qtyField.setFont(Theme.MONO_BODY.deriveFont(Font.BOLD, 11f));
        qtyField.setForeground(Color.WHITE);
        qtyField.setBackground(new Color(0x07, 0x0B, 0x14));
        qtyField.setBorder(null);
        qtyField.addActionListener(e -> recalculate());

        stepper.add(minusBtn, BorderLayout.WEST);
        stepper.add(qtyField, BorderLayout.CENTER);
        stepper.add(plusBtn, BorderLayout.EAST);
        form.add(stepper);

        // Quick Allocation Chips [25%, 50%, 75%, MAX]
        JPanel chips = new JPanel(new GridLayout(1, 4, 3, 0));
        chips.setOpaque(false);
        chips.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

        String[] allocLabels = {"25%", "50%", "75%", "MAX"};
        double[] allocPcts = {0.25, 0.50, 0.75, 1.00};
        for (int i = 0; i < 4; i++) {
            double pct = allocPcts[i];
            JButton chip = new JButton(allocLabels[i]);
            chip.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 8f));
            chip.setForeground(i == 3 ? Theme.ACCENT : Theme.TXT_SECONDARY);
            chip.setBackground(Theme.SURFACE_SUB);
            chip.setBorder(new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 3, 1));
            chip.setFocusPainted(false);
            chip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            chip.addActionListener(e -> setAllocPct(pct));
            chips.add(chip);
        }
        form.add(javax.swing.Box.createVerticalStrut(3));
        form.add(chips);

        // Financial Calculation Summary Card
        JPanel calcCard = new JPanel(new GridLayout(4, 2, 2, 2));
        calcCard.setBackground(new Color(0x07, 0x0B, 0x14));
        calcCard.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 4, 1),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        calcCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 65));

        addCalcRow(calcCard, "Estimated Value:", estValLabel, Color.WHITE, true);
        addCalcRow(calcCard, "Trading Fee:", feeLabel, Theme.GAIN, false);
        addCalcRow(calcCard, "Available Cash:", availCashLabel, Theme.TXT_PRIMARY, false);
        addCalcRow(calcCard, "Remaining Balance:", remCashLabel, Theme.GAIN, true);
        form.add(javax.swing.Box.createVerticalStrut(4));
        form.add(calcCard);

        // Validation Note
        valMsgLabel.setFont(Theme.MONO_TINY.deriveFont(Font.PLAIN, 8f));
        valMsgLabel.setForeground(Theme.GAIN);
        form.add(javax.swing.Box.createVerticalStrut(2));
        form.add(valMsgLabel);

        // Primary Execution CTA Button
        submitBtn.setFont(Theme.MONO_BODY.deriveFont(Font.BOLD, 10f));
        submitBtn.setFocusPainted(false);
        submitBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        submitBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        submitBtn.addActionListener(e -> executeTrade());
        form.add(javax.swing.Box.createVerticalStrut(4));
        form.add(submitBtn);

        JLabel disclaim = new JLabel("Paper trading only. No real money involved.", JLabel.CENTER);
        disclaim.setFont(Theme.MICRO.deriveFont(Font.PLAIN, 8f));
        disclaim.setForeground(Theme.TXT_MUTED);
        disclaim.setAlignmentX(JLabel.CENTER_ALIGNMENT);
        form.add(javax.swing.Box.createVerticalStrut(2));
        form.add(disclaim);

        add(form, BorderLayout.CENTER);
        recalculate();
    }

    private void addCalcRow(JPanel p, String title, JLabel val, Color valColor, boolean bold) {
        JLabel l = new JLabel(title);
        l.setFont(Theme.MONO_TINY.deriveFont(Font.PLAIN, 8f));
        l.setForeground(Theme.TXT_MUTED);
        val.setFont(Theme.MONO_TINY.deriveFont(bold ? Font.BOLD : Font.PLAIN, 9f));
        val.setForeground(valColor);
        val.setHorizontalAlignment(JLabel.RIGHT);
        p.add(l);
        p.add(val);
    }

    private void styleSideButtons() {
        if (currentSide == OrderSide.BUY) {
            btnBuy.setBackground(Theme.GAIN);
            btnBuy.setForeground(new Color(0x02, 0x06, 0x17)); // dark text
            btnBuy.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 9f));
            btnBuy.setBorder(new Theme.RoundedBorder(Theme.GAIN, 4, 1));

            btnSell.setBackground(Theme.SURFACE_SUB);
            btnSell.setForeground(Theme.TXT_MUTED);
            btnSell.setFont(Theme.MONO_TINY.deriveFont(Font.PLAIN, 9f));
            btnSell.setBorder(new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 4, 1));

            submitBtn.setBackground(Theme.GAIN);
            submitBtn.setForeground(new Color(0x02, 0x06, 0x17));
            submitBtn.setBorder(new Theme.RoundedBorder(Theme.GAIN, 4, 1));
        } else {
            btnSell.setBackground(Theme.LOSS);
            btnSell.setForeground(Color.WHITE);
            btnSell.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 9f));
            btnSell.setBorder(new Theme.RoundedBorder(Theme.LOSS, 4, 1));

            btnBuy.setBackground(Theme.SURFACE_SUB);
            btnBuy.setForeground(Theme.TXT_MUTED);
            btnBuy.setFont(Theme.MONO_TINY.deriveFont(Font.PLAIN, 9f));
            btnBuy.setBorder(new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 4, 1));

            submitBtn.setBackground(Theme.LOSS);
            submitBtn.setForeground(Color.WHITE);
            submitBtn.setBorder(new Theme.RoundedBorder(Theme.LOSS, 4, 1));
        }
    }

    private void styleTypeBtn(JButton btn, boolean active) {
        btn.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 8f));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(42, 18));
        if (active) {
            btn.setBackground(Theme.ACCENT);
            btn.setForeground(Color.WHITE);
            btn.setBorder(new Theme.RoundedBorder(Theme.ACCENT, 3, 1));
        } else {
            btn.setBackground(Theme.SURFACE_SUB);
            btn.setForeground(Theme.TXT_MUTED);
            btn.setBorder(BorderFactory.createEmptyBorder());
        }
    }

    private void setOrderType(String type) {
        this.orderType = type;
        styleTypeBtn(typeMarket, "Market".equals(type));
        styleTypeBtn(typeLimit, "Limit".equals(type));
        styleTypeBtn(typeStop, "Stop".equals(type));
        recalculate();
    }

    private void stepQty(int delta) {
        int q = Math.max(1, parseQuantity() + delta);
        qtyField.setText(String.valueOf(q));
        recalculate();
    }

    private void setAllocPct(double pct) {
        User user = authService.getCurrentUser();
        if (user == null || currentAsset == null) return;
        double cash = user.getAccount().getBalance();
        int max = (int) Math.floor(cash / currentAsset.getPrice());
        int target = Math.max(1, (int) Math.floor(max * pct));
        qtyField.setText(String.valueOf(target));
        recalculate();
    }

    private int parseQuantity() {
        try {
            return Math.max(1, Integer.parseInt(qtyField.getText().trim()));
        } catch (Exception e) {
            return 1;
        }
    }

    public void setTargetAsset(Asset a) {
        if (a == null) return;
        this.currentAsset = a;
        recalculate();
    }

    public void recalculate() {
        if (currentAsset == null) return;
        User user = authService.getCurrentUser();
        double cash = user != null ? user.getAccount().getBalance() : 100000.0;
        int qty = parseQuantity();
        this.quantity = qty;

        double p = currentAsset.getPrice();
        double total = qty * p;
        double fee = tradingService != null ? tradingService.calculateFee(total) : 0.0;
        double rem = (currentSide == OrderSide.BUY) ? cash - (total + fee) : cash + (total - fee);

        assetTitleLabel.setText(currentAsset.getSymbol() + " - " + currentAsset.getName());
        assetPriceLabel.setText(Formatter.money(p));

        int maxUnits = (int) Math.floor(cash / p);
        maxSharesLabel.setText("Max: " + maxUnits);

        estValLabel.setText(Formatter.money(total));
        feeLabel.setText(fee == 0 ? "$0.00 (Zero Fee)" : Formatter.money(fee));
        availCashLabel.setText(Formatter.money(cash));
        remCashLabel.setText(Formatter.money(Math.max(0, rem)));

        submitBtn.setText("EXECUTE PAPER " + currentSide.name() + " (" + qty + " " + currentAsset.getSymbol() + ")");

        if (currentSide == OrderSide.BUY && total + fee > cash) {
            valMsgLabel.setText("⚠ Insufficient Virtual Liquidity! Max " + maxUnits + " units.");
            valMsgLabel.setForeground(Theme.LOSS);
            submitBtn.setEnabled(false);
        } else {
            valMsgLabel.setText("✓ Order parameters valid & within liquidity limits");
            valMsgLabel.setForeground(Theme.GAIN);
            submitBtn.setEnabled(true);
        }
    }

    private void executeTrade() {
        User user = authService.getCurrentUser();
        if (user == null || currentAsset == null) return;

        int qty = parseQuantity();
        try {
            com.papertrader.model.Order order;
            if ("Market".equals(orderType)) {
                order = tradingService.placeOrder(user, currentAsset, currentSide, qty);
            } else if ("Limit".equals(orderType)) {
                order = tradingService.placeOrder(user, new LimitOrder(currentAsset, currentSide, qty, currentAsset.getPrice()));
            } else {
                order = tradingService.placeOrder(user, new StopLossOrder(currentAsset, currentSide, qty, currentAsset.getPrice()));
            }

            String result = order.getStatus() == com.papertrader.model.OrderStatus.FILLED
                    ? "filled @ " + Formatter.money(order.getFilledPrice())
                    : "waiting (" + order.getConditionText() + ")";
            JOptionPane.showMessageDialog(this,
                    "Simulated Order:\n" + currentSide.name() + " " + qty + " " + currentAsset.getSymbol() + " " + result,
                    "Order Placed", JOptionPane.INFORMATION_MESSAGE);

            if (onOrderExecuted != null) {
                onOrderExecuted.run();
            }
            recalculate();
        } catch (SimulatorException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Order Rejected", JOptionPane.ERROR_MESSAGE);
        }
    }
}
