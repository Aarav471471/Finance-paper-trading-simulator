package com.papertrader.ui.components;

import com.papertrader.exception.SimulatorException;
import com.papertrader.model.Asset;
import com.papertrader.model.LimitOrder;
import com.papertrader.model.OrderSide;
import com.papertrader.model.Transaction;
import com.papertrader.model.User;
import com.papertrader.service.AuthService;
import com.papertrader.service.MarketService;
import com.papertrader.service.TradingService;
import com.papertrader.ui.Theme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pinned Quick Paper Trade Terminal (Right 3.5 Columns).
 * Directly wired to TradingService, AuthService, and live account telemetry.
 * Supports BUY / SELL toggles, Market/Limit/SL-M orders, quantity steppers,
 * allocation chips, live ledger breakdowns, and simulated execution toasts.
 */
public class StockTradeTicketPanel extends JPanel {

    private final AuthService authService;
    private final MarketService marketService;
    private final TradingService tradingService;
    private final Runnable onOrderExecuted;

    private Asset currentAsset;
    private OrderSide currentSide = OrderSide.BUY;
    private String orderType = "MARKET"; // MARKET, LIMIT, SL-M
    private int quantity = 50;

    // Header & Preview Labels
    private final JLabel previewSymbol = new JLabel("RELIANCE");
    private final JLabel previewType = new JLabel("NSE Cash Equity");
    private final JLabel previewCmp = new JLabel("₹2,945.50");
    private final JLabel previewDelta = new JLabel("+2.37%");

    // Action Segmented Buttons
    private final JButton btnBuy = new JButton("BUY / LONG");
    private final JButton btnSell = new JButton("SELL / SHORT");

    // Order type buttons
    private final JButton btnMarket = new JButton("MARKET");
    private final JButton btnLimit = new JButton("LIMIT");
    private final JButton btnSlm = new JButton("SL-M");

    // Stepper & Limit Input
    private final JTextField qtyInput = new JTextField("50");
    private final JPanel limitRow = new JPanel(new BorderLayout(6, 0));
    private final JTextField limitPriceInput = new JTextField("");

    // Financial Breakdown
    private final JLabel lblEstVal = new JLabel("₹0.00");
    private final JLabel lblBrokerage = new JLabel("0.00 (Zero Fee)");
    private final JLabel lblMarginAvail = new JLabel("₹0.00");
    private final JLabel lblRemBalance = new JLabel("₹0.00");

    // Liquidity Pill & CTA
    private final JLabel liquidityPill = new JLabel("✓ Liquidity verified • Sim instant fill available");
    private final JButton executeBtn = new JButton("EXECUTE PAPER BUY (50 SH)");

    // Mini Execution History Container
    private final JPanel historyList = new JPanel();

    // Floating Notification Toast
    private final JPanel toastBanner = new JPanel(new BorderLayout(8, 0));
    private final JLabel toastText = new JLabel("");
    private Timer toastTimer;

    public StockTradeTicketPanel(AuthService authService, MarketService marketService,
                                 TradingService tradingService, Runnable onOrderExecuted) {
        this.authService = authService;
        this.marketService = marketService;
        this.tradingService = tradingService;
        this.onOrderExecuted = onOrderExecuted;

        setLayout(new BorderLayout());
        setOpaque(false);
        setBackground(Theme.CARD_BG);
        setBorder(Theme.terminalCardBorder());
        setPreferredSize(new Dimension(320, 680));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        // 1. Ticket Header
        content.add(buildHeader());
        content.add(Box.createVerticalStrut(10));

        // 2. Instrument Identity Preview
        content.add(buildInstrumentPreview());
        content.add(Box.createVerticalStrut(10));

        // 3. Buy / Sell Action Segmented Toggle
        content.add(buildSideToggle());
        content.add(Box.createVerticalStrut(10));

        // 4. Order Type Selector
        content.add(buildOrderTypeSelector());
        content.add(Box.createVerticalStrut(8));

        // 5. Limit Price Input (Hidden unless LIMIT selected)
        limitRow.setOpaque(false);
        limitRow.setVisible(false);
        JLabel lblLim = new JLabel("Limit Price:");
        lblLim.setFont(Theme.MICRO);
        lblLim.setForeground(Theme.TXT_MUTED);
        limitPriceInput.setFont(Theme.MONO_BODY);
        limitPriceInput.setBackground(new Color(0x14, 0x1B, 0x2B));
        limitPriceInput.setForeground(Color.WHITE);
        limitPriceInput.setCaretColor(Color.WHITE);
        limitPriceInput.setBorder(new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 4, 1));
        limitRow.add(lblLim, BorderLayout.WEST);
        limitRow.add(limitPriceInput, BorderLayout.CENTER);
        content.add(limitRow);
        content.add(Box.createVerticalStrut(6));

        // 6. Quantity Stepper & Quick Allocation Chips
        content.add(buildQuantitySection());
        content.add(Box.createVerticalStrut(10));

        // 7. Financial Breakdown
        content.add(buildFinancialBreakdown());
        content.add(Box.createVerticalStrut(8));

        // 8. Liquidity Status Pill
        content.add(buildLiquidityPill());
        content.add(Box.createVerticalStrut(10));

        // 9. Execute Button
        content.add(buildExecuteButton());
        content.add(Box.createVerticalStrut(8));

        // 10. Disclaimer
        JLabel disc = new JLabel("Paper simulated orders mirror live depth without real risk.", JLabel.CENTER);
        disc.setFont(Theme.MICRO);
        disc.setForeground(Theme.TXT_MUTED);
        disc.setAlignmentX(CENTER_ALIGNMENT);
        content.add(disc);
        content.add(Box.createVerticalStrut(12));

        // 11. Mini Execution History for Current Asset
        content.add(buildMiniHistorySection());

        add(content, BorderLayout.CENTER);

        // Toast setup
        buildToastBanner();
        add(toastBanner, BorderLayout.SOUTH);
    }

    private JPanel buildHeader() {
        JPanel h = new JPanel(new BorderLayout());
        h.setOpaque(false);
        h.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_CUSTOM));
        h.setPreferredSize(new Dimension(100, 26));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.setOpaque(false);

        JLabel dot = new JLabel("●");
        dot.setFont(new Font("SansSerif", Font.BOLD, 10));
        dot.setForeground(new Color(0x4A, 0xE1, 0x76));

        JLabel title = new JLabel("PAPER TRADE TICKET");
        title.setFont(Theme.HEADING.deriveFont(Font.BOLD, 11f));
        title.setForeground(Color.WHITE);

        left.add(dot);
        left.add(title);
        h.add(left, BorderLayout.WEST);

        JLabel sim = new JLabel("SIMULATED");
        sim.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 8f));
        sim.setForeground(Theme.TXT_MUTED);
        sim.setOpaque(true);
        sim.setBackground(new Color(0x23, 0x2A, 0x3A));
        sim.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        h.add(sim, BorderLayout.EAST);

        return h;
    }

    private JPanel buildInstrumentPreview() {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(true);
        p.setBackground(new Color(0x14, 0x1B, 0x2B));
        p.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 6, 1),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));

        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setOpaque(false);

        previewSymbol.setFont(Theme.HEADING.deriveFont(Font.BOLD, 13f));
        previewSymbol.setForeground(Color.WHITE);
        previewType.setFont(Theme.MICRO);
        previewType.setForeground(Theme.TXT_MUTED);

        left.add(previewSymbol);
        left.add(previewType);
        p.add(left, BorderLayout.WEST);

        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.setOpaque(false);

        previewCmp.setFont(Theme.MONO_BODY.deriveFont(Font.BOLD, 13f));
        previewCmp.setForeground(Color.WHITE);
        previewCmp.setHorizontalAlignment(JLabel.RIGHT);

        previewDelta.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 10f));
        previewDelta.setForeground(new Color(0x4A, 0xE1, 0x76));
        previewDelta.setHorizontalAlignment(JLabel.RIGHT);

        right.add(previewCmp);
        right.add(previewDelta);
        p.add(right, BorderLayout.EAST);

        return p;
    }

    private JPanel buildSideToggle() {
        JPanel toggle = new JPanel(new GridLayout(1, 2, 6, 0));
        toggle.setOpaque(true);
        toggle.setBackground(new Color(0x14, 0x1B, 0x2B));
        toggle.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        btnBuy.setFont(Theme.BODY.deriveFont(Font.BOLD, 11f));
        btnBuy.setFocusPainted(false);
        btnBuy.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnBuy.addActionListener(e -> setSide(OrderSide.BUY));

        btnSell.setFont(Theme.BODY.deriveFont(Font.BOLD, 11f));
        btnSell.setFocusPainted(false);
        btnSell.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnSell.addActionListener(e -> setSide(OrderSide.SELL));

        styleSideButtons();

        toggle.add(btnBuy);
        toggle.add(btnSell);
        return toggle;
    }

    public void setSide(OrderSide side) {
        this.currentSide = side;
        styleSideButtons();
        updateCalculations();
    }

    private void styleSideButtons() {
        if (currentSide == OrderSide.BUY) {
            btnBuy.setBackground(new Color(0x00, 0xB9, 0x54));
            btnBuy.setForeground(Color.WHITE);
            btnBuy.setBorder(new Theme.RoundedBorder(new Color(0x00, 0xB9, 0x54), 4, 1));

            btnSell.setBackground(new Color(0x14, 0x1B, 0x2B));
            btnSell.setForeground(Theme.TXT_MUTED);
            btnSell.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        } else {
            btnSell.setBackground(new Color(0xFF, 0x54, 0x51));
            btnSell.setForeground(Color.WHITE);
            btnSell.setBorder(new Theme.RoundedBorder(new Color(0xFF, 0x54, 0x51), 4, 1));

            btnBuy.setBackground(new Color(0x14, 0x1B, 0x2B));
            btnBuy.setForeground(Theme.TXT_MUTED);
            btnBuy.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        }
    }

    private JPanel buildOrderTypeSelector() {
        JPanel wrap = new JPanel(new BorderLayout(0, 4));
        wrap.setOpaque(false);

        JLabel lbl = new JLabel("ORDER TYPE");
        lbl.setFont(Theme.MICRO.deriveFont(Font.BOLD, 9f));
        lbl.setForeground(Theme.TXT_MUTED);
        wrap.add(lbl, BorderLayout.NORTH);

        JPanel p = new JPanel(new GridLayout(1, 3, 4, 0));
        p.setOpaque(true);
        p.setBackground(new Color(0x14, 0x1B, 0x2B));
        p.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

        for (JButton b : List.of(btnMarket, btnLimit, btnSlm)) {
            b.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 9f));
            b.setFocusPainted(false);
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            p.add(b);
        }

        btnMarket.addActionListener(e -> selectOrderType("MARKET"));
        btnLimit.addActionListener(e -> selectOrderType("LIMIT"));
        btnSlm.addActionListener(e -> selectOrderType("SL-M"));

        styleOrderTypeButtons();
        wrap.add(p, BorderLayout.CENTER);
        return wrap;
    }

    private void selectOrderType(String type) {
        this.orderType = type;
        styleOrderTypeButtons();
        limitRow.setVisible("LIMIT".equals(type));
        if ("LIMIT".equals(type) && currentAsset != null) {
            limitPriceInput.setText(String.format(Locale.US, "%.2f", currentAsset.getPrice()));
        }
        revalidate();
        repaint();
    }

    private void styleOrderTypeButtons() {
        for (JButton b : List.of(btnMarket, btnLimit, btnSlm)) {
            boolean active = b.getText().equalsIgnoreCase(orderType);
            if (active) {
                b.setBackground(new Color(0x23, 0x2A, 0x3A));
                b.setForeground(Theme.ACCENT);
                b.setBorder(new Theme.RoundedBorder(Theme.ACCENT, 3, 1));
            } else {
                b.setBackground(new Color(0x14, 0x1B, 0x2B));
                b.setForeground(Theme.TXT_MUTED);
                b.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
            }
        }
    }

    private JPanel buildQuantitySection() {
        JPanel sec = new JPanel(new BorderLayout(0, 4));
        sec.setOpaque(false);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);

        JLabel lbl = new JLabel("QUANTITY (SHARES)");
        lbl.setFont(Theme.MICRO.deriveFont(Font.BOLD, 9f));
        lbl.setForeground(Theme.TXT_MUTED);

        JLabel lot = new JLabel("Lot Size: 1");
        lot.setFont(Theme.MONO_TINY);
        lot.setForeground(Theme.TXT_MUTED);

        top.add(lbl, BorderLayout.WEST);
        top.add(lot, BorderLayout.EAST);
        sec.add(top, BorderLayout.NORTH);

        // Stepper
        JPanel stepper = new JPanel(new BorderLayout());
        stepper.setOpaque(true);
        stepper.setBackground(new Color(0x14, 0x1B, 0x2B));
        stepper.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 4, 1),
                BorderFactory.createEmptyBorder(2, 4, 2, 4)));

        JButton minus = new JButton("-");
        minus.setFont(Theme.HEADING.deriveFont(Font.BOLD, 14f));
        minus.setForeground(Theme.TXT_SECONDARY);
        minus.setContentAreaFilled(false);
        minus.setBorderPainted(false);
        minus.setFocusPainted(false);
        minus.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        minus.addActionListener(e -> {
            quantity = Math.max(1, quantity - 10);
            qtyInput.setText(String.valueOf(quantity));
            updateCalculations();
        });

        JButton plus = new JButton("+");
        plus.setFont(Theme.HEADING.deriveFont(Font.BOLD, 14f));
        plus.setForeground(Theme.TXT_SECONDARY);
        plus.setContentAreaFilled(false);
        plus.setBorderPainted(false);
        plus.setFocusPainted(false);
        plus.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        plus.addActionListener(e -> {
            quantity += 10;
            qtyInput.setText(String.valueOf(quantity));
            updateCalculations();
        });

        qtyInput.setHorizontalAlignment(JTextField.CENTER);
        qtyInput.setFont(Theme.MONO_BODY.deriveFont(Font.BOLD, 13f));
        qtyInput.setBackground(new Color(0x14, 0x1B, 0x2B));
        qtyInput.setForeground(Color.WHITE);
        qtyInput.setCaretColor(Color.WHITE);
        qtyInput.setBorder(BorderFactory.createEmptyBorder());
        qtyInput.addActionListener(e -> {
            parseQuantity();
            qtyInput.setText(String.valueOf(quantity));
            updateCalculations();
        });

        stepper.add(minus, BorderLayout.WEST);
        stepper.add(qtyInput, BorderLayout.CENTER);
        stepper.add(plus, BorderLayout.EAST);
        sec.add(stepper, BorderLayout.CENTER);

        // Quick Allocation Pills
        JPanel pills = new JPanel(new GridLayout(1, 4, 4, 0));
        pills.setOpaque(false);
        pills.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        for (int q : List.of(10, 50, 100, 250)) {
            JButton pb = new JButton(q + " Qty");
            pb.setFont(Theme.MONO_TINY.deriveFont(Font.PLAIN, 9f));
            pb.setBackground(new Color(0x14, 0x1B, 0x2B));
            pb.setForeground(Theme.TXT_SECONDARY);
            pb.setBorder(new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 3, 1));
            pb.setFocusPainted(false);
            pb.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            pb.addActionListener(e -> {
                quantity = q;
                qtyInput.setText(String.valueOf(quantity));
                updateCalculations();
            });
            pills.add(pb);
        }
        sec.add(pills, BorderLayout.SOUTH);

        return sec;
    }

    private int parseQuantity() {
        try {
            int val = Integer.parseInt(qtyInput.getText().trim());
            quantity = Math.max(1, Math.min(100_000, val));
        } catch (NumberFormatException ignored) {
            quantity = 50;
        }
        return quantity;
    }

    private JPanel buildFinancialBreakdown() {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setOpaque(true);
        card.setBackground(new Color(0x14, 0x1B, 0x2B));
        card.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 6, 1),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));

        card.add(buildBreakdownRow("Estimated Order Value:", lblEstVal, true));
        card.add(Box.createVerticalStrut(4));
        card.add(buildBreakdownRow("Simulated Brokerage & STT:", lblBrokerage, false));
        card.add(Box.createVerticalStrut(4));
        card.add(buildBreakdownRow("Virtual Margin Available:", lblMarginAvail, false));
        card.add(Box.createVerticalStrut(4));
        card.add(buildBreakdownRow("Remaining Sim Balance:", lblRemBalance, true));

        lblBrokerage.setForeground(new Color(0x4A, 0xE1, 0x76));
        lblRemBalance.setForeground(Theme.ACCENT);
        return card;
    }

    private JPanel buildBreakdownRow(String label, JLabel valueLabel, boolean bold) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);

        JLabel l = new JLabel(label);
        l.setFont(Theme.MONO_TINY.deriveFont(8.5f));
        l.setForeground(Theme.TXT_MUTED);

        valueLabel.setFont(Theme.MONO_TINY.deriveFont(bold ? Font.BOLD : Font.PLAIN, 9.5f));
        valueLabel.setForeground(Color.WHITE);

        row.add(l, BorderLayout.WEST);
        row.add(valueLabel, BorderLayout.EAST);
        return row;
    }

    private JPanel buildLiquidityPill() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        p.setOpaque(true);
        p.setBackground(new Color(0, 185, 84, 25));
        p.setBorder(BorderFactory.createCompoundBorder(
                new Theme.RoundedBorder(new Color(74, 225, 118, 60), 4, 1),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));

        liquidityPill.setFont(Theme.MONO_TINY.deriveFont(8.5f));
        liquidityPill.setForeground(new Color(0x4A, 0xE1, 0x76));
        p.add(liquidityPill);
        return p;
    }

    private JButton buildExecuteButton() {
        executeBtn.setFont(Theme.HEADING.deriveFont(Font.BOLD, 12f));
        executeBtn.setFocusPainted(false);
        executeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        executeBtn.setPreferredSize(new Dimension(100, 38));
        executeBtn.addActionListener(e -> executePaperOrder());
        return executeBtn;
    }

    private JPanel buildMiniHistorySection() {
        JPanel sec = new JPanel(new BorderLayout(0, 6));
        sec.setOpaque(false);
        sec.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER_CUSTOM));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

        JLabel t = new JLabel("RECENT FILLS");
        t.setFont(Theme.MICRO.deriveFont(Font.BOLD, 9f));
        t.setForeground(Theme.TXT_MUTED);

        JLabel vAll = new JLabel("View All");
        vAll.setFont(Theme.MICRO);
        vAll.setForeground(Theme.ACCENT);
        vAll.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        top.add(t, BorderLayout.WEST);
        top.add(vAll, BorderLayout.EAST);
        sec.add(top, BorderLayout.NORTH);

        historyList.setLayout(new BoxLayout(historyList, BoxLayout.Y_AXIS));
        historyList.setOpaque(false);
        sec.add(historyList, BorderLayout.CENTER);

        return sec;
    }

    private void buildToastBanner() {
        toastBanner.setOpaque(true);
        toastBanner.setBackground(new Color(0x0C, 0x2E, 0x1A));
        toastBanner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0x4A, 0xE1, 0x76)),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        toastBanner.setVisible(false);

        toastText.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 9.5f));
        toastText.setForeground(new Color(0x4A, 0xE1, 0x76));
        toastBanner.add(toastText, BorderLayout.CENTER);
    }

    private void showToast(String msg, boolean success) {
        toastText.setText(msg);
        toastText.setForeground(success ? new Color(0x4A, 0xE1, 0x76) : new Color(0xFF, 0x54, 0x51));
        toastBanner.setBackground(success ? new Color(0x0C, 0x2E, 0x1A) : new Color(0x3B, 0x10, 0x12));
        toastBanner.setVisible(true);

        if (toastTimer != null && toastTimer.isRunning()) {
            toastTimer.stop();
        }
        toastTimer = new Timer(3500, e -> toastBanner.setVisible(false));
        toastTimer.setRepeats(false);
        toastTimer.start();
    }

    public void setAsset(Asset asset) {
        this.currentAsset = asset;
        if (asset == null) return;

        boolean isIndian = isIndianAsset(asset.getSymbol());
        String cur = isIndian ? "₹" : "$";

        previewSymbol.setText(asset.getSymbol());
        previewType.setText(isIndian ? "NSE Cash Equity" : (asset.getType() + " Instrument"));
        previewCmp.setText(String.format(Locale.US, "%s%,.2f", cur, asset.getPrice()));

        double chg = asset.getChangePercent();
        String sign = chg >= 0 ? "+" : "";
        previewDelta.setText(String.format(Locale.US, "%s%.2f%%", sign, chg));
        previewDelta.setForeground(chg >= 0 ? new Color(0x4A, 0xE1, 0x76) : new Color(0xFF, 0x54, 0x51));

        updateCalculations();
        refreshMiniHistory();
    }

    public void updateCalculations() {
        if (currentAsset == null) return;

        parseQuantity();

        double cmp = currentAsset.getPrice();
        double orderVal = cmp * quantity;
        boolean isIndian = isIndianAsset(currentAsset.getSymbol());
        String cur = isIndian ? "₹" : "$";

        lblEstVal.setText(String.format(Locale.US, "%s%,.2f", cur, orderVal));

        User user = authService.getCurrentUser();
        double cash = (user != null && user.getAccount() != null) ? user.getAccount().getBalance() : 1_000_000.0;
        lblMarginAvail.setText(String.format(Locale.US, "%s%,.2f", cur, cash));

        double remCash = (currentSide == OrderSide.BUY) ? (cash - orderVal) : (cash + orderVal);
        lblRemBalance.setText(String.format(Locale.US, "%s%,.2f", cur, Math.max(0.0, remCash)));

        boolean hasMargin = (currentSide == OrderSide.SELL) || (cash >= orderVal);

        if (hasMargin) {
            liquidityPill.setText("✓ Liquidity verified • Sim instant fill available");
            liquidityPill.setForeground(new Color(0x4A, 0xE1, 0x76));
            ((JPanel) liquidityPill.getParent()).setBackground(new Color(0, 185, 84, 25));
        } else {
            liquidityPill.setText("⚠ Insufficient margin for order value");
            liquidityPill.setForeground(new Color(0xFF, 0x54, 0x51));
            ((JPanel) liquidityPill.getParent()).setBackground(new Color(147, 0, 10, 25));
        }

        String sideStr = (currentSide == OrderSide.BUY) ? "BUY" : "SELL";
        executeBtn.setText("EXECUTE PAPER " + sideStr + " (" + quantity + " SH)");

        if (currentSide == OrderSide.BUY) {
            executeBtn.setBackground(new Color(0x00, 0xB9, 0x54));
            executeBtn.setForeground(Color.WHITE);
        } else {
            executeBtn.setBackground(new Color(0xFF, 0x54, 0x51));
            executeBtn.setForeground(Color.WHITE);
        }
    }

    private void executePaperOrder() {
        if (currentAsset == null) return;

        User user = authService.getCurrentUser();
        if (user == null) {
            showToast("Please log in to trade", false);
            return;
        }

        double cmp = currentAsset.getPrice();
        double orderVal = cmp * quantity;
        boolean isIndian = isIndianAsset(currentAsset.getSymbol());
        String cur = isIndian ? "₹" : "$";

        try {
            if ("MARKET".equals(orderType)) {
                tradingService.placeOrder(user, currentAsset, currentSide, quantity);
                showToast(String.format(Locale.US, "Order Filled: %s %d %s @ %s%,.2f",
                        currentSide, quantity, currentAsset.getSymbol(), cur, cmp), true);
            } else if ("LIMIT".equals(orderType)) {
                double limitP = Double.parseDouble(limitPriceInput.getText().trim());
                tradingService.placeOrder(user, new LimitOrder(currentAsset, currentSide, quantity, limitP));
                showToast(String.format(Locale.US, "Limit Placed: %s %d %s @ %s%,.2f",
                        currentSide, quantity, currentAsset.getSymbol(), cur, limitP), true);
            }

            updateCalculations();
            refreshMiniHistory();

            if (onOrderExecuted != null) {
                onOrderExecuted.run();
            }
        } catch (SimulatorException ex) {
            showToast("Execution Rejected: " + ex.getMessage(), false);
        } catch (Exception ex) {
            showToast("Order Error: " + ex.getMessage(), false);
        }
    }

    private void refreshMiniHistory() {
        historyList.removeAll();
        if (currentAsset == null) return;

        User user = authService.getCurrentUser();
        if (user == null) return;

        List<Transaction> txs = tradingService.getTransactionHistory(user);
        List<Transaction> matching = new ArrayList<>();
        for (Transaction t : txs) {
            if (t.getSymbol().equalsIgnoreCase(currentAsset.getSymbol())) {
                matching.add(t);
            }
        }

        boolean isIndian = isIndianAsset(currentAsset.getSymbol());
        String cur = isIndian ? "₹" : "$";

        if (matching.isEmpty()) {
            JLabel empty = new JLabel("No simulated fills for " + currentAsset.getSymbol());
            empty.setFont(Theme.MICRO);
            empty.setForeground(Theme.TXT_MUTED);
            empty.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
            historyList.add(empty);
        } else {
            int show = Math.min(3, matching.size());
            for (int i = 0; i < show; i++) {
                Transaction t = matching.get(i);
                JPanel row = new JPanel(new BorderLayout());
                row.setOpaque(true);
                row.setBackground(new Color(0x14, 0x1B, 0x2B));
                row.setBorder(BorderFactory.createCompoundBorder(
                        new Theme.RoundedBorder(Theme.BORDER_CUSTOM, 3, 1),
                        BorderFactory.createEmptyBorder(3, 6, 3, 6)));

                boolean isBuy = t.getSide() == OrderSide.BUY;
                JLabel lLeft = new JLabel(String.format(Locale.US, "%s %d @ %s%,.2f",
                        t.getSide().name(), t.getQuantity(), cur, t.getPrice()));
                lLeft.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 8.5f));
                lLeft.setForeground(isBuy ? new Color(0x4A, 0xE1, 0x76) : new Color(0xFF, 0x54, 0x51));

                DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm");
                JLabel lRight = new JLabel(t.getTimestamp().format(dtf) + " IST • FILLED");
                lRight.setFont(Theme.MONO_TINY.deriveFont(8f));
                lRight.setForeground(Theme.TXT_MUTED);

                row.add(lLeft, BorderLayout.WEST);
                row.add(lRight, BorderLayout.EAST);
                historyList.add(row);
                historyList.add(Box.createVerticalStrut(3));
            }
        }

        historyList.revalidate();
        historyList.repaint();
    }

    private boolean isIndianAsset(String sym) {
        if (sym == null) return false;
        return List.of("RELIANCE", "TCS", "HDFCBANK", "INFY", "ICICIBANK", "BHARTIARTL",
                "SBIN", "ITC", "LT", "TATAMOTORS", "TRENT", "ETERNAL").contains(sym.toUpperCase());
    }
}
