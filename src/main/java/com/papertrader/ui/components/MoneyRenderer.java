package com.papertrader.ui.components;

import com.papertrader.util.Formatter;

import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;

/** Shows a Double as money ($1,234.56), right-aligned. Overrides setValue(). */
public class MoneyRenderer extends DefaultTableCellRenderer {

    public MoneyRenderer() {
        setHorizontalAlignment(SwingConstants.RIGHT);
    }

    @Override
    protected void setValue(Object value) {
        setText(value instanceof Double number ? Formatter.money(number) : String.valueOf(value));
    }
}
