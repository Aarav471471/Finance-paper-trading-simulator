package com.papertrader.ui.components;

import com.papertrader.ui.Theme;
import com.papertrader.util.Formatter;

import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Component;

/**
 * Table cell that shows a number in green (positive) or red (negative).
 * INHERITANCE + METHOD OVERRIDING: we extend Swing's DefaultTableCellRenderer and override
 * getTableCellRendererComponent() to change only the colour and text.
 */
public class ColoredNumberRenderer extends DefaultTableCellRenderer {

    public enum Style { MONEY, PERCENT }

    private final Style style;

    public ColoredNumberRenderer(Style style) {
        this.style = style;
        setHorizontalAlignment(SwingConstants.RIGHT);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                   boolean hasFocus, int row, int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        if (value instanceof Double number) {
            setText(style == Style.MONEY ? Formatter.signedMoney(number) : Formatter.percent(number));
            setForeground(Theme.colorFor(number));
        }
        return this;
    }
}
