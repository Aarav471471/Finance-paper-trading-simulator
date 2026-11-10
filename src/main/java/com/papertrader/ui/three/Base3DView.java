package com.papertrader.ui.three;

import com.papertrader.ui.Theme;

import javafx.application.ConditionalFeature;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;

/**
 * Bridge between Swing and JavaFX 3D.
 * Swing cannot draw 3D, so we place a JFXPanel (a JavaFX area) inside this Swing panel
 * and let JavaFX draw the 3D scene there.
 *
 * TEMPLATE METHOD pattern: this class does the common set-up, and each subclass only
 * fills in createScene() (ABSTRACTION + INHERITANCE + RUNTIME POLYMORPHISM).
 *
 * If JavaFX cannot start on a computer, a text message is shown instead so the app still works.
 */
public abstract class Base3DView extends JPanel {

    private final JFXPanel fxPanel;

    protected Base3DView() {
        super(new BorderLayout());
        setBackground(Theme.BACKGROUND);
        JFXPanel panel = null;
        try {
            panel = new JFXPanel();            // this also starts the JavaFX engine
            Platform.setImplicitExit(false);   // keep JavaFX alive while the Swing window is open
            add(panel, BorderLayout.CENTER);
        } catch (Throwable error) {
            JLabel message = new JLabel("3D view is not available on this computer", SwingConstants.CENTER);
            message.setForeground(Theme.MUTED);
            add(message, BorderLayout.CENTER);
        }
        this.fxPanel = panel;
    }

    /** Subclasses call this at the END of their constructor, once their own fields are ready. */
    protected final void start3D() {
        if (fxPanel != null) {
            Platform.runLater(() -> {                       // JavaFX work runs on the JavaFX thread
                if (Platform.isSupported(ConditionalFeature.SCENE3D)) {
                    fxPanel.setScene(createScene());
                } else {
                    fxPanel.setScene(noSupportScene());
                }
            });
        }
    }

    /** Shown when the graphics card / driver cannot draw 3D. */
    private Scene noSupportScene() {
        Label label = new Label("3D is not supported by this graphics driver.\n"
                + "Try starting the app with:  -Dprism.forceGPU=true");
        label.setTextFill(fxColor(Theme.MUTED));
        StackPane pane = new StackPane(label);
        pane.setStyle("-fx-background-color: transparent;");
        Scene scene = new Scene(pane);
        scene.setFill(fxColor(Theme.CARD));
        return scene;
    }

    protected boolean is3DAvailable() {
        return fxPanel != null;
    }

    /** Each subclass builds its own 3D scene. */
    protected abstract Scene createScene();

    /** Converts a Swing (AWT) colour to a JavaFX colour. */
    protected static javafx.scene.paint.Color fxColor(java.awt.Color c) {
        return javafx.scene.paint.Color.rgb(c.getRed(), c.getGreen(), c.getBlue());
    }
}
