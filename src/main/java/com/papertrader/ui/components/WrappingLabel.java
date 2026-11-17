package com.papertrader.ui.components;

import javax.swing.JTextArea;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/**
 * Read-only text that wraps onto more lines when its column is narrow, instead of being
 * cut off with "..." the way a long {@link javax.swing.JLabel} is.
 */
public class WrappingLabel extends JTextArea {

    public WrappingLabel(String text, Font font, Color color) {
        super(text);
        setLineWrap(true);
        setWrapStyleWord(true);
        setEditable(false);
        setFocusable(false);
        setOpaque(false);
        setBorder(null);
        setFont(font);
        setForeground(color);
        setAlignmentX(Component.LEFT_ALIGNMENT);
        // The number of lines depends on the width we get, so re-check our height after a resize.
        addComponentListener(new ComponentAdapter() {
            private int lastWidth = -1;

            @Override
            public void componentResized(ComponentEvent e) {
                if (getWidth() != lastWidth) {
                    lastWidth = getWidth();
                    if (getPreferredSize().height != getHeight()) {
                        revalidate();
                    }
                }
            }
        });
    }
}
