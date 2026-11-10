package com.papertrader.ui;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;

/**
 * ABSTRACT parent of every page (Dashboard, Market, Trade, Portfolio, History).
 * It gives each page the same title style and padding (code reuse through inheritance).
 *
 * RUNTIME POLYMORPHISM: MainFrame keeps a "BasePanel currentPage" and calls
 * currentPage.refresh() every second. Java runs the refresh() of whichever page is showing.
 */
public abstract class BasePanel extends JPanel implements Refreshable {

    protected BasePanel(String title, String subtitle) {
        setLayout(new BorderLayout(0, 18));
        setBackground(Theme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));

        JPanel header = new JPanel(new BorderLayout(0, 4));
        header.setOpaque(false);
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(Theme.TITLE);
        titleLabel.setForeground(Theme.TEXT);
        JLabel subtitleLabel = new JLabel(subtitle);
        subtitleLabel.setFont(Theme.BODY);
        subtitleLabel.setForeground(Theme.MUTED);
        header.add(titleLabel, BorderLayout.NORTH);
        header.add(subtitleLabel, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);
    }

    /** Each page decides how to redraw itself. */
    @Override
    public abstract void refresh();
}
