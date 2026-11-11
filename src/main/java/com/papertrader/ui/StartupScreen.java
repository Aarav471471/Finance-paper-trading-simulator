package com.papertrader.ui;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * What the user sees while the app starts: a small loading window (downloading live prices can
 * take several seconds), a log file next to the saved data, and an error dialog instead of a
 * silent exit if something goes wrong. Matters most for the installed app, which has no console.
 */
public final class StartupScreen {

    private static JFrame window;
    private static volatile boolean started;

    private StartupScreen() {
    }

    /** Copies everything printed to the console into dataDir/papertrader.log as well. */
    public static Path logToFile(Path dataDir) {
        try {
            Files.createDirectories(dataDir);
            Path log = dataDir.resolve("papertrader.log");
            OutputStream file = new FileOutputStream(log.toFile(), false);
            System.setOut(new PrintStream(new Tee(System.out, file), true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(new Tee(System.err, file), true, StandardCharsets.UTF_8));
            return log;
        } catch (IOException | RuntimeException e) {
            return null;   // logging is a nice-to-have, never stop the app for it
        }
    }

    /** Shows an error dialog for any crash that would otherwise close the app without a word. */
    public static void installCrashDialog(Path log) {
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            error.printStackTrace();
            if (!started && (thread.getName().startsWith("AWT-EventQueue") || thread.getName().equals("main"))) {
                showError(error, log);
            }
        });
    }

    public static void showError(Throwable error, Path log) {
        String where = log == null ? "" : "\n\nDetails are in:\n" + log.toAbsolutePath();
        Runnable dialog = () -> {
            hide();
            JOptionPane.showMessageDialog(null,
                    "PaperTrader could not start:\n" + error + where,
                    "PaperTrader", JOptionPane.ERROR_MESSAGE);
        };
        if (SwingUtilities.isEventDispatchThread()) {
            dialog.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(dialog);
            } catch (Exception ignored) {
                // nothing more we can do
            }
        }
    }

    /** Opens the loading window. Safe to call from the main thread. */
    public static void show(String message) {
        try {
            SwingUtilities.invokeAndWait(() -> {
                JFrame frame = new JFrame("PaperTrader 3D");
                frame.setUndecorated(true);
                JPanel panel = new JPanel(new BorderLayout(0, 12));
                panel.setBackground(new Color(0x0b0f17));
                panel.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(0x1f2937)),
                        BorderFactory.createEmptyBorder(24, 28, 24, 28)));
                JLabel title = new JLabel("PaperTrader 3D");
                title.setForeground(Color.WHITE);
                title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
                JLabel text = new JLabel(message);
                text.setForeground(new Color(0x94a3b8));
                JProgressBar bar = new JProgressBar();
                bar.setIndeterminate(true);
                panel.add(title, BorderLayout.NORTH);
                panel.add(text, BorderLayout.CENTER);
                panel.add(bar, BorderLayout.SOUTH);
                frame.setContentPane(panel);
                frame.setSize(380, 140);
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
                window = frame;
            });
        } catch (Exception e) {
            // no screen (headless) or similar: start without the loading window
        }
    }

    /** Call once the main window is up: later errors are only logged, not shown as start-up failures. */
    public static void markStarted() {
        started = true;
        hide();
    }

    /** Closes the loading window. */
    public static void hide() {
        if (window != null) {
            window.dispose();
            window = null;
        }
    }

    /** Writes to two streams at once. */
    private static final class Tee extends OutputStream {
        private final OutputStream first;
        private final OutputStream second;

        Tee(OutputStream first, OutputStream second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public void write(int b) throws IOException {
            first.write(b);
            second.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            first.write(b, off, len);
            second.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            first.flush();
            second.flush();
        }
    }
}
