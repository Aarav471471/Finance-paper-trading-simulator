package com.papertrader.ui.components;

import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.MouseWheelEvent;

/**
 * A scroll pane whose content stretches to fill the window when there is room,
 * and scrolls (vertically and/or horizontally) once the window is smaller than
 * the content needs. Nothing gets cut off.
 *
 * <ul>
 *   <li><b>Width:</b> the content always follows the viewport width, so the page
 *   fills the window and there is never a horizontal scrollbar.</li>
 *   <li><b>Height:</b> the content follows the viewport height while the viewport
 *   is at least {@code minHeight} tall (or, with {@link #FIT_CONTENT}, at least as
 *   tall as the content's preferred height). Below that, a vertical scrollbar
 *   appears.</li>
 * </ul>
 *
 * Mouse-wheel events that reach the edge of a nested scroll pane (a table inside a
 * scrolling page, for example) are passed on to this pane, so the page keeps
 * scrolling instead of getting "stuck" on the inner list.
 */
public class ResponsiveScrollPane extends JScrollPane {

    /** Use the content's own preferred height as the point where vertical scrolling starts. */
    public static final int FIT_CONTENT = -1;

    private static final int UNIT_INCREMENT = 18;

    public ResponsiveScrollPane(Component content, int minHeight) {
        this(content, minHeight, null);
    }

    /**
     * @param content   the page or section to show
     * @param minHeight shortest height the content can be laid out at, or {@link #FIT_CONTENT}
     * @param background viewport background, or {@code null} for a transparent pane
     */
    public ResponsiveScrollPane(Component content, int minHeight, Color background) {
        super(new FitPanel(content, minHeight));
        setBorder(null);
        setViewportBorder(null);
        setHorizontalScrollBarPolicy(HORIZONTAL_SCROLLBAR_NEVER);
        setVerticalScrollBarPolicy(VERTICAL_SCROLLBAR_AS_NEEDED);
        getVerticalScrollBar().setUnitIncrement(UNIT_INCREMENT);
        getHorizontalScrollBar().setUnitIncrement(UNIT_INCREMENT);
        if (background == null) {
            setOpaque(false);
            getViewport().setOpaque(false);
        } else {
            setBackground(background);
            getViewport().setBackground(background);
        }
    }

    /**
     * Lets a nested scroll pane hand mouse-wheel scrolling to the scroll pane around
     * it once it reaches its top or bottom.
     */
    public static void forwardWheelAtEdges(JScrollPane inner) {
        inner.addMouseWheelListener(e -> {
            JScrollBar bar = inner.getVerticalScrollBar();
            boolean atEdge = bar == null || !bar.isVisible()
                    || (e.getWheelRotation() < 0 && bar.getValue() <= bar.getMinimum())
                    || (e.getWheelRotation() > 0 && bar.getValue() >= bar.getMaximum() - bar.getVisibleAmount());
            if (atEdge) {
                Container parent = inner.getParent();
                JScrollPane outer = parent == null ? null
                        : (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, parent);
                if (outer != null) {
                    MouseWheelEvent converted = (MouseWheelEvent) SwingUtilities.convertMouseEvent(inner, e, outer);
                    outer.dispatchEvent(converted);
                }
            }
        });
    }

    /** Holds the content and tells the viewport when to stretch it and when to scroll. */
    private static final class FitPanel extends JPanel implements Scrollable {
        private final Component content;
        private final int minHeight;

        FitPanel(Component content, int minHeight) {
            super(new BorderLayout());
            this.content = content;
            this.minHeight = minHeight;
            setOpaque(false);
            add(content, BorderLayout.CENTER);
        }

        private int neededHeight() {
            return minHeight == FIT_CONTENT ? content.getPreferredSize().height : minHeight;
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension pref = content.getPreferredSize();
            // The height the viewport gives the content when it is not stretched to fit.
            return new Dimension(pref.width, Math.max(neededHeight(), 0));
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true; // always fit the window width: no horizontal scrolling
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return getParent() instanceof JViewport vp && vp.getHeight() >= neededHeight();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return UNIT_INCREMENT;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            int span = orientation == SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
            return Math.max(UNIT_INCREMENT, span - UNIT_INCREMENT * 2);
        }
    }
}
