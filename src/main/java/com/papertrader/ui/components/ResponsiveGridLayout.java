package com.papertrader.ui.components;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;

/**
 * A grid of equal-width cells (like a one-row {@link java.awt.GridLayout}) that moves
 * cells onto extra rows when the window is too narrow for every cell to be at least
 * {@code minCellWidth} wide. Used for rows of KPI / stat cards, so a narrow window
 * shows two rows of readable cards instead of one row of squashed ones.
 */
public class ResponsiveGridLayout implements LayoutManager {

    private final int maxColumns;
    private final int minCellWidth;
    private final int hgap;
    private final int vgap;

    /**
     * @param maxColumns   columns to use when there is room (usually the number of cards)
     * @param minCellWidth narrowest a cell may get before the grid adds a row
     */
    public ResponsiveGridLayout(int maxColumns, int minCellWidth, int hgap, int vgap) {
        this.maxColumns = Math.max(1, maxColumns);
        this.minCellWidth = minCellWidth;
        this.hgap = hgap;
        this.vgap = vgap;
    }

    @Override
    public void addLayoutComponent(String name, Component comp) {
    }

    @Override
    public void removeLayoutComponent(Component comp) {
    }

    private int visibleCount(Container parent) {
        int n = 0;
        for (Component c : parent.getComponents()) {
            if (c.isVisible()) {
                n++;
            }
        }
        return n;
    }

    private int columnsFor(Container parent, int count) {
        int width = availableWidth(parent);
        int cols = Math.min(maxColumns, Math.max(1, count));
        if (width <= 0) {
            return cols;
        }
        while (cols > 1 && (width - (cols - 1) * hgap) / cols < minCellWidth) {
            cols--;
        }
        return cols;
    }

    private static int availableWidth(Container parent) {
        Container c = parent;
        while (c.getWidth() == 0 && c.getParent() != null) {
            c = c.getParent();
        }
        Insets in = parent.getInsets();
        return c.getWidth() - in.left - in.right;
    }

    private int cellHeight(Container parent) {
        int h = 0;
        for (Component c : parent.getComponents()) {
            if (c.isVisible()) {
                h = Math.max(h, c.getPreferredSize().height);
            }
        }
        return h;
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        synchronized (parent.getTreeLock()) {
            Insets in = parent.getInsets();
            int count = visibleCount(parent);
            int cols = columnsFor(parent, count);
            int rows = count == 0 ? 0 : (count + cols - 1) / cols;
            int cellW = 0;
            for (Component c : parent.getComponents()) {
                if (c.isVisible()) {
                    cellW = Math.max(cellW, Math.min(c.getPreferredSize().width, minCellWidth));
                }
            }
            int w = cols * cellW + (cols - 1) * hgap;
            int h = rows * cellHeight(parent) + Math.max(0, rows - 1) * vgap;
            return new Dimension(w + in.left + in.right, h + in.top + in.bottom);
        }
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        return preferredLayoutSize(parent);
    }

    @Override
    public void layoutContainer(Container parent) {
        synchronized (parent.getTreeLock()) {
            Insets in = parent.getInsets();
            int count = visibleCount(parent);
            if (count == 0) {
                return;
            }
            int cols = columnsFor(parent, count);
            int rows = (count + cols - 1) / cols;
            int width = parent.getWidth() - in.left - in.right;
            int height = parent.getHeight() - in.top - in.bottom;
            int cellW = (width - (cols - 1) * hgap) / cols;
            int cellH = Math.max(0, (height - (rows - 1) * vgap) / rows);

            int i = 0;
            for (Component c : parent.getComponents()) {
                if (!c.isVisible()) {
                    continue;
                }
                int row = i / cols;
                int col = i % cols;
                c.setBounds(in.left + col * (cellW + hgap), in.top + row * (cellH + vgap), cellW, cellH);
                i++;
            }
            WrapLayout.requestRelayoutIfHeightChanged(parent, preferredLayoutSize(parent).height);
        }
    }
}
