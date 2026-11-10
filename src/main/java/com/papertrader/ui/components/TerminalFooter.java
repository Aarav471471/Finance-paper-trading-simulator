package com.papertrader.ui.components;

import com.papertrader.model.Holding;
import com.papertrader.model.User;
import com.papertrader.service.MarketService;
import com.papertrader.service.PortfolioService;
import com.papertrader.ui.Theme;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.List;

/**
 * Compact Terminal Footer Information Strip.
 * Matches HTML: Total Allocation breakdown on the left,
 * Assets Held, Best Performer, and Worst Performer telemetry on the right.
 */
public class TerminalFooter extends JPanel {

    private final JLabel eqLabel = new JLabel("US Equities (62.4%)");
    private final JLabel cryptoLabel = new JLabel("Crypto (24.8%)");
    private final JLabel cashLabel = new JLabel("Cash Reserve (12.8%)");

    private final JLabel assetsHeldLabel = new JLabel("0 Assets");
    private final JLabel bestPerfLabel = new JLabel("-");
    private final JLabel worstPerfLabel = new JLabel("-");

    public TerminalFooter() {
        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(Theme.SIDEBAR_BG);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER_CUSTOM),
                BorderFactory.createEmptyBorder(0, 10, 0, 10)));
        setPreferredSize(new Dimension(100, 24));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

        // Left Section: Allocation Badges
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 3));
        left.setOpaque(false);

        JLabel title = new JLabel("TOTAL ALLOCATION:");
        title.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 8f));
        title.setForeground(Theme.TXT_MUTED);
        left.add(title);

        left.add(createBadge("●", Theme.ACCENT, eqLabel));
        left.add(createBadge("●", Theme.GAIN, cryptoLabel));
        left.add(createBadge("●", Theme.TXT_MUTED, cashLabel));
        add(left, BorderLayout.WEST);

        // Right Section: Telemetry Metrics
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 3));
        right.setOpaque(false);

        right.add(createMetric("Assets Held:", assetsHeldLabel, Color.WHITE));
        right.add(new JLabel("│") {{ setForeground(Theme.BORDER_CUSTOM); }});
        right.add(createMetric("Best Performer:", bestPerfLabel, Theme.GAIN));
        right.add(new JLabel("│") {{ setForeground(Theme.BORDER_CUSTOM); }});
        right.add(createMetric("Worst Performer:", worstPerfLabel, Theme.LOSS));
        add(right, BorderLayout.EAST);
    }

    private JPanel createBadge(String dot, Color dotColor, JLabel textLabel) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        p.setOpaque(false);

        JLabel dotLabel = new JLabel(dot);
        dotLabel.setFont(new Font("SansSerif", Font.BOLD, 9));
        dotLabel.setForeground(dotColor);

        textLabel.setFont(Theme.MONO_TINY);
        textLabel.setForeground(new Color(0xE2, 0xE8, 0xF0));

        p.add(dotLabel);
        p.add(textLabel);
        return p;
    }

    private JPanel createMetric(String tag, JLabel valLabel, Color valColor) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        p.setOpaque(false);

        JLabel t = new JLabel(tag);
        t.setFont(Theme.MONO_TINY);
        t.setForeground(Theme.TXT_MUTED);

        valLabel.setFont(Theme.MONO_TINY.deriveFont(Font.BOLD, 9f));
        valLabel.setForeground(valColor);

        p.add(t);
        p.add(valLabel);
        return p;
    }

    public void updateData(User user, PortfolioService portfolioService, MarketService marketService) {
        if (user == null) return;

        double cash = user.getAccount().getBalance();
        List<Holding> holdings = user.getPortfolio().getHoldings();

        double total = cash;
        double eqVal = 0.0;
        double cryptoVal = 0.0;

        String bestSym = "-";
        double bestPct = -Double.MAX_VALUE;
        String worstSym = "-";
        double worstPct = Double.MAX_VALUE;

        for (Holding h : holdings) {
            double v = portfolioService.getMarketValue(h);
            total += v;
            double pnlPct = portfolioService.getProfitLossPercent(h);

            String type = marketService.findBySymbol(h.getSymbol()).map(a -> a.getType()).orElse("Stock");
            if ("Crypto".equalsIgnoreCase(type)) {
                cryptoVal += v;
            } else {
                eqVal += v;
            }

            if (pnlPct > bestPct) {
                bestPct = pnlPct;
                bestSym = h.getSymbol();
            }
            if (pnlPct < worstPct) {
                worstPct = pnlPct;
                worstSym = h.getSymbol();
            }
        }

        if (total > 0) {
            double eqPct = eqVal / total * 100.0;
            double cryPct = cryptoVal / total * 100.0;
            double cashPct = cash / total * 100.0;

            eqLabel.setText(String.format("US Equities (%.1f%%)", eqPct));
            cryptoLabel.setText(String.format("Crypto (%.1f%%)", cryPct));
            cashLabel.setText(String.format("Cash Reserve (%.1f%%)", cashPct));
        }

        assetsHeldLabel.setText(holdings.size() + " Assets");
        if (!bestSym.equals("-")) {
            bestPerfLabel.setText(bestSym + " (" + (bestPct >= 0 ? "+" : "") + String.format("%.1f%%", bestPct) + ")");
            worstPerfLabel.setText(worstSym + " (" + (worstPct >= 0 ? "+" : "") + String.format("%.1f%%", worstPct) + ")");
        }
    }
}
