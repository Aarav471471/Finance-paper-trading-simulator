package com.papertrader.ui.components;

import javax.swing.JPanel;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;

/**
 * Lays out a header row with one item on the left (the first child) and one on the
 * right (the second child), like a BorderLayout WEST/EAST pair. When the window is too
 * narrow for both on one line, the right-hand item drops onto its own line below
 * instead of being drawn on top of the left one.
 */
public class LeadingTrailingLayout implements LayoutManager {

    private final int hgap;
    private final int vgap;
    /** When above 0, the right-hand item fills the rest of the line and needs at least this width. */
    private final int trailingFillMinWidth;

    public LeadingTrailingLayout(int hgap, int vgap) {
        this(hgap, vgap, 0);
    }

    /**
     * Side-by-side panes: the left item keeps its preferred width and the right item fills
     * the rest of the line. If that would leave the right item narrower than
     * {@code trailingFillMinWidth}, the right item moves below the left one instead.
     */
    public LeadingTrailingLayout(int hgap, int vgap, int trailingFillMinWidth) {
        this.hgap = hgap;
        this.vgap = vgap;
        this.trailingFillMinWidth = trailingFillMinWidth;
    }

    /**
     * A panel using this layout whose maximum height follows its preferred height, so a
     * BoxLayout parent gives it the extra line it needs when the right side wraps.
     */
    public static JPanel newRow(int hgap, int vgap) {
        return new JPanel(new LeadingTrailingLayout(hgap, vgap)) {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Short.MAX_VALUE, getPreferredSize().height);
            }
        };
    }

    @Override
    public void addLayoutComponent(String name, Component comp) {
    }

    @Override
    public void removeLayoutComponent(Component comp) {
    }

    private static Component child(Container parent, int index) {
        if (parent.getComponentCount() <= index) {
            return null;
        }
        Component c = parent.getComponent(index);
        return c.isVisible() ? c : null;
    }

    private boolean fitsOnOneLine(Container parent, Component lead, Component trail, int width) {
        if (lead == null || trail == null || width <= 0) {
            return true;
        }
        int trailWidth = trailingFillMinWidth > 0 ? trailingFillMinWidth : trail.getPreferredSize().width;
        return lead.getPreferredSize().width + hgap + trailWidth <= width;
    }

    private static int innerWidth(Container parent) {
        Insets in = parent.getInsets();
        return parent.getWidth() - in.left - in.right;
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        synchronized (parent.getTreeLock()) {
            Insets in = parent.getInsets();
            Component lead = child(parent, 0);
            Component trail = child(parent, 1);
            Dimension l = lead == null ? new Dimension() : lead.getPreferredSize();
            Dimension t = trail == null ? new Dimension() : trail.getPreferredSize();
            int w;
            int h;
            if (fitsOnOneLine(parent, lead, trail, innerWidth(parent))) {
                w = l.width + (lead != null && trail != null ? hgap : 0) + t.width;
                h = Math.max(l.height, t.height);
            } else {
                w = Math.max(l.width, t.width);
                h = l.height + vgap + t.height;
            }
            return new Dimension(w + in.left + in.right, h + in.top + in.bottom);
        }
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        synchronized (parent.getTreeLock()) {
            Insets in = parent.getInsets();
            Component lead = child(parent, 0);
            Component trail = child(parent, 1);
            Dimension l = lead == null ? new Dimension() : lead.getMinimumSize();
            Dimension t = trail == null ? new Dimension() : trail.getMinimumSize();
            return new Dimension(Math.max(l.width, t.width) + in.left + in.right,
                    Math.max(l.height, t.height) + in.top + in.bottom);
        }
    }

    @Override
    public void layoutContainer(Container parent) {
        doLayout(parent);
        WrapLayout.requestRelayoutIfHeightChanged(parent, preferredLayoutSize(parent).height);
    }

    private void doLayout(Container parent) {
        synchronized (parent.getTreeLock()) {
            Insets in = parent.getInsets();
            int x = in.left;
            int y = in.top;
            int width = innerWidth(parent);
            int height = parent.getHeight() - in.top - in.bottom;
            Component lead = child(parent, 0);
            Component trail = child(parent, 1);

            if (fitsOnOneLine(parent, lead, trail, width)) {
                if (lead != null) {
                    Dimension l = lead.getPreferredSize();
                    int lw = trail == null ? width : Math.min(l.width, width);
                    lead.setBounds(x, y + (height - Math.min(l.height, height)) / 2, lw, Math.min(l.height, height));
                }
                if (trail != null && trailingFillMinWidth > 0) {
                    int lw = lead == null ? 0 : lead.getWidth() + hgap;
                    trail.setBounds(x + lw, y, width - lw, height);
                } else if (trail != null) {
                    Dimension t = trail.getPreferredSize();
                    int tw = Math.min(t.width, width);
                    trail.setBounds(x + width - tw, y + (height - Math.min(t.height, height)) / 2, tw, Math.min(t.height, height));
                }
            } else {
                Dimension l = lead.getPreferredSize();
                lead.setBounds(x, y, width, l.height);
                Dimension t = trail.getPreferredSize();
                trail.setBounds(x, y + l.height + vgap, width, Math.max(0, Math.min(t.height, height - l.height - vgap)));
            }
        }
    }
}
