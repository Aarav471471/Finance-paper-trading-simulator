package com.papertrader.ui.components;

import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;

/**
 * A {@link FlowLayout} that moves items onto a new row when they do not fit, and
 * reports the height of all rows as its preferred size. A plain FlowLayout always
 * reports a single row, so on a narrow window its extra items vanish below the
 * panel; this layout makes the panel grow taller instead.
 */
public class WrapLayout extends FlowLayout {

    public WrapLayout() {
        super();
    }

    public WrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        return layoutSize(target, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension min = layoutSize(target, false);
        min.width -= getHgap() + 1;
        return min;
    }

    @Override
    public void layoutContainer(Container target) {
        super.layoutContainer(target);
        requestRelayoutIfHeightChanged(target, preferredLayoutSize(target).height);
    }

    /**
     * How many rows we need depends on the width we were just given, which our parent
     * only learns after laying us out. When the needed height changes, ask the parents
     * to lay out again (only once per change, so this cannot loop).
     */
    static void requestRelayoutIfHeightChanged(Container target, int neededHeight) {
        if (!(target instanceof javax.swing.JComponent jc)) {
            return;
        }
        Object last = jc.getClientProperty(WrapLayout.class);
        if (last instanceof Integer h && h == neededHeight) {
            return;
        }
        jc.putClientProperty(WrapLayout.class, neededHeight);
        // Our parents cache their preferred sizes, so tell them ours changed.
        SwingUtilities.invokeLater(jc::revalidate);
    }

    private Dimension layoutSize(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            int targetWidth = availableWidth(target);
            if (targetWidth <= 0) {
                targetWidth = Integer.MAX_VALUE;
            }

            int hgap = getHgap();
            int vgap = getVgap();
            Insets insets = target.getInsets();
            int horizontalInsetsAndGap = insets.left + insets.right + (hgap * 2);
            int maxWidth = targetWidth - horizontalInsetsAndGap;

            Dimension dim = new Dimension(0, 0);
            int rowWidth = 0;
            int rowHeight = 0;

            for (Component m : target.getComponents()) {
                if (!m.isVisible()) {
                    continue;
                }
                Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();
                if (rowWidth + d.width > maxWidth && rowWidth > 0) {
                    addRow(dim, rowWidth, rowHeight);
                    rowWidth = 0;
                    rowHeight = 0;
                }
                if (rowWidth != 0) {
                    rowWidth += hgap;
                }
                rowWidth += d.width;
                rowHeight = Math.max(rowHeight, d.height);
            }
            addRow(dim, rowWidth, rowHeight);

            dim.width += horizontalInsetsAndGap;
            dim.height += insets.top + insets.bottom + vgap * 2;

            // Inside a scroll pane the viewport would otherwise never let the panel shrink.
            Container scrollPane = SwingUtilities.getAncestorOfClass(JScrollPane.class, target);
            if (scrollPane != null && target.isValid()) {
                dim.width -= (hgap + 1);
            }
            return dim;
        }
    }

    /** The width we are allowed to use: our own width, or the nearest sized ancestor's. */
    private static int availableWidth(Container target) {
        Container c = target;
        while (c.getSize().width == 0 && c.getParent() != null) {
            c = c.getParent();
        }
        return c.getSize().width;
    }

    private void addRow(Dimension dim, int rowWidth, int rowHeight) {
        dim.width = Math.max(dim.width, rowWidth);
        if (dim.height > 0) {
            dim.height += getVgap();
        }
        dim.height += rowHeight;
    }
}
