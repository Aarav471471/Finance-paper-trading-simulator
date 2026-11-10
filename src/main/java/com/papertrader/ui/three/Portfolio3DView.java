package com.papertrader.ui.three;

import com.papertrader.ui.Theme;

import javafx.application.Platform;
import javafx.geometry.VPos;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.transform.Rotate;

import java.util.ArrayList;
import java.util.List;

/**
 * Dashboard 3D bar chart: one bar per holding.
 * Bar height = market value, colour = green (profit) or red (loss).
 * Drag with the mouse to rotate, scroll to zoom.
 */
public class Portfolio3DView extends Base3DView {

    /** The data for one bar. (A Java "record" is a short way to write a simple data class.) */
    public record Bar(String label, double value, double profitLoss) {
    }

    private static final double MAX_BAR_HEIGHT = 260;
    private static final double SPACING = 95;

    private final Rotate rotateX = new Rotate(-22, Rotate.X_AXIS);
    private final Rotate rotateY = new Rotate(-28, Rotate.Y_AXIS);
    private final Group barsGroup = new Group();
    private final PerspectiveCamera camera = new PerspectiveCamera(true);
    private List<Bar> lastData = null;

    private double dragStartX;
    private double dragStartY;
    private double startAngleX;
    private double startAngleY;

    public Portfolio3DView() {
        super();
        start3D();
    }

    @Override
    protected Scene createScene() {
        Group world = new Group(barsGroup);
        world.getTransforms().addAll(rotateX, rotateY);
        world.setTranslateY(90);

        PointLight light = new PointLight(Color.WHITE);
        light.setTranslateX(-300);
        light.setTranslateY(-400);
        light.setTranslateZ(-500);
        Group root = new Group(world, light, new AmbientLight(Color.gray(0.4)));

        camera.setTranslateZ(-780);
        camera.setNearClip(1);
        camera.setFarClip(5000);
        camera.setFieldOfView(35);

        Scene scene = new Scene(root, 600, 380, true, SceneAntialiasing.BALANCED);
        scene.setFill(fxColor(Theme.CARD));
        scene.setCamera(camera);

        // mouse drag = rotate
        scene.setOnMousePressed(e -> {
            dragStartX = e.getSceneX();
            dragStartY = e.getSceneY();
            startAngleX = rotateX.getAngle();
            startAngleY = rotateY.getAngle();
        });
        scene.setOnMouseDragged(e -> {
            rotateY.setAngle(startAngleY + (e.getSceneX() - dragStartX) * 0.4);
            double newX = startAngleX - (e.getSceneY() - dragStartY) * 0.3;
            rotateX.setAngle(Math.max(-70, Math.min(0, newX)));
        });
        // mouse wheel = zoom
        scene.setOnScroll(e -> {
            double z = camera.getTranslateZ() + e.getDeltaY() * 1.5;
            camera.setTranslateZ(Math.max(-2500, Math.min(-400, z)));
        });

        rebuildBars(lastData == null ? new ArrayList<>() : lastData);
        return scene;
    }

    /** Called from Swing with fresh data; the drawing itself happens on the JavaFX thread. */
    public void update(List<Bar> data) {
        if (data.equals(lastData)) {
            return;   // nothing changed, no need to redraw
        }
        lastData = new ArrayList<>(data);
        if (is3DAvailable()) {
            List<Bar> copy = lastData;
            Platform.runLater(() -> rebuildBars(copy));
        }
    }

    private void rebuildBars(List<Bar> data) {
        barsGroup.getChildren().clear();

        double floorWidth = Math.max(420, data.size() * SPACING + 80);
        Box floor = new Box(floorWidth, 4, 180);
        floor.setTranslateY(2);
        floor.setMaterial(new PhongMaterial(fxColor(Theme.BORDER)));
        barsGroup.getChildren().add(floor);

        if (data.isEmpty()) {
            barsGroup.getChildren().add(label("Buy an asset to see your 3D portfolio", 0, -60, 18, Color.web("#8A93A6")));
            return;
        }

        double maxValue = 0;
        for (Bar bar : data) {
            maxValue = Math.max(maxValue, bar.value());
        }

        double x = -(data.size() - 1) * SPACING / 2;
        for (Bar bar : data) {
            double height = 20 + (maxValue == 0 ? 0 : bar.value() / maxValue * MAX_BAR_HEIGHT);
            Box box = new Box(52, height, 52);
            box.setTranslateX(x);
            box.setTranslateY(-height / 2);   // in JavaFX, negative Y is "up"
            PhongMaterial material = new PhongMaterial(fxColor(bar.profitLoss() >= 0 ? Theme.GREEN : Theme.RED));
            material.setSpecularColor(Color.web("#FFFFFF55"));
            box.setMaterial(material);

            barsGroup.getChildren().addAll(
                    box,
                    label(bar.label(), x, 28, 16, Color.WHITE),
                    label(shortMoney(bar.value()), x, -height - 18, 14, Color.web("#E6E9EF")));
            x += SPACING;
        }
    }

    private Text label(String text, double x, double y, int size, Color color) {
        Text t = new Text(text);
        t.setFont(Font.font("System", FontWeight.BOLD, size));
        t.setFill(color);
        t.setTextOrigin(VPos.CENTER);
        t.setTranslateX(x - t.getLayoutBounds().getWidth() / 2);
        t.setTranslateY(y);
        t.setTranslateZ(-30);
        return t;
    }

    private String shortMoney(double value) {
        if (value >= 1_000_000) {
            return String.format("$%.1fM", value / 1_000_000);
        }
        if (value >= 1_000) {
            return String.format("$%.1fK", value / 1_000);
        }
        return String.format("$%.0f", value);
    }
}
