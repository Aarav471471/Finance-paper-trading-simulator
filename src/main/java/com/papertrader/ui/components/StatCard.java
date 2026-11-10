package com.papertrader.ui.components;

import com.papertrader.ui.Theme;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Color;

/** A small rounded card with a title, a big number and a small note underneath. */
public class StatCard extends JPanel {

    private final JLabel valueLabel = new JLabel("-");
    private final JLabel noteLabel = new JLabel(" ");

    public StatCard(String title) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(Theme.CARD);
        setBorder(Theme.cardBorder());

        JLabel titleLabel = new JLabel(title.toUpperCase());
        titleLabel.setFont(Theme.SMALL);
        titleLabel.setForeground(Theme.MUTED);
        valueLabel.setFont(Theme.BIG_NUMBER);
        valueLabel.setForeground(Theme.TEXT);
        noteLabel.setFont(Theme.SMALL);
        noteLabel.setForeground(Theme.MUTED);

        add(titleLabel);
        add(javax.swing.Box.createVerticalStrut(6));
        add(valueLabel);
        add(javax.swing.Box.createVerticalStrut(4));
        add(noteLabel);
    }

    public void setValue(String value, Color color) {
        valueLabel.setText(value);
        valueLabel.setForeground(color);
    }

    public void setNote(String note, Color color) {
        noteLabel.setText(note);
        noteLabel.setForeground(color);
    }
}
