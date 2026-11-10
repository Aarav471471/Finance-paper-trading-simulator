package com.papertrader.ui.three;

import com.papertrader.ui.Theme;
import com.papertrader.util.Formatter;

import javafx.animation.AnimationTimer;
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

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 3D Portfolio Volumetric Matrix.
 * Renders holdings as interactive 3D pillars with specular illumination,
 * smooth orbit rotation, perspective zoom, and real-time Swing HUD telemetry.
 */
public class Portfolio3DView extends Base3DView {

    public record Bar(String label, String name, double value, double profitLoss, double avgCost, int quantity, double changePercent) {
        public Bar(String label, double value, double profitLoss) {
            this(label, label, value, profitLoss, 0.0, 1, 0.0);
        }
        public Bar(String label, double value, double profitLoss, String type, double changePercent) {
            this(label, label, value, profitLoss, 0.0, 1, changePercent);
        }
    }

    private static final double MAX_BAR_HEIGHT = 140.0;
    private static final double DEFAULT_CAM_Z = -580.0;

    private final Rotate rotateX = new Rotate(-22, Rotate.X_AXIS);
    private final Rotate rotateY = new Rotate(-26, Rotate.Y_AXIS);
    private final Group barsGroup = new Group();
    private final Group groundGrid = new Group();
    private final PerspectiveCamera camera = new PerspectiveCamera(true);

    private List<Bar> lastData = null;
    private Consumer<String> onSelectSymbol;

    // Controls state
    private boolean isAutoRotating = false;
    private AnimationTimer rotateTimer;

    // Swing HUD Components (Crisp 2D telemetry above 3D viewport)
    private final JLabel hudSym = new JLabel("---");
    private final JLabel hudName = new JLabel("Select an asset");
    private final JLabel hudRet = new JLabel("+0.00%");
    private final JLabel hudQty = new JLabel("0 Units");
    private final JLabel hudAvg = new JLabel("$0.00");
    private final JLabel hudVal = new JLabel("$0.00");
    private final JLabel hudPnl = new JLabel("+$0.00");

    private double dragStartX;
    private double dragStartY;
    private double startAngleX;
    private double startAngleY;

    public Portfolio3DView() {
        super();

        // 1. Swing HUD Strip (NORTH)
        JPanel hudStrip = buildHudStrip();
        add(hudStrip, BorderLayout.NORTH);

        // 2. Swing Status Legend (SOUTH)
        JPanel legendStrip = buildLegendStrip();
        add(legendStrip, BorderLayout.SOUTH);

        start3D();
    }

    private JPanel buildHudStrip() {
        JPanel p = new JPanel(new BorderLayout(8, 0));
        p.setOpaque(true);
        p.setBackground(new java.awt.Color(0x13, 0x1A, 0x27));
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_CUSTOM),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)));
        p.setPreferredSize(new Dimension(100, 28));

        // Left: Asset Symbol & Name + Return Pill
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.setOpaque(false);

        hudSym.setFont(Theme.MONO_BODY.deriveFont(java.awt.Font.BOLD, 11f));
        hudSym.setForeground(java.awt.Color.WHITE);

        hudName.setFont(Theme.TINY);
        hudName.setForeground(Theme.TXT_SECONDARY);

        hudRet.setFont(Theme.MONO_TINY.deriveFont(java.awt.Font.BOLD, 9f));
        hudRet.setForeground(Theme.GAIN);
        hudRet.setOpaque(true);
        hudRet.setBackground(Theme.GREEN_BG);
        hudRet.setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 4));

        left.add(hudSym);
        left.add(hudName);
        left.add(hudRet);
        p.add(left, BorderLayout.WEST);

        // Right: Key Metrics (Units, Avg, Value, PnL)
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        right.setOpaque(false);

        hudQty.setFont(Theme.MONO_TINY);
        hudQty.setForeground(Theme.TXT_SECONDARY);

        hudAvg.setFont(Theme.MONO_TINY);
        hudAvg.setForeground(Theme.TXT_MUTED);

        hudVal.setFont(Theme.MONO_TINY.deriveFont(java.awt.Font.BOLD, 10f));
        hudVal.setForeground(java.awt.Color.WHITE);

        hudPnl.setFont(Theme.MONO_TINY.deriveFont(java.awt.Font.BOLD, 10f));
        hudPnl.setForeground(Theme.GAIN);

        right.add(new JLabel("Qty:") {{ setFont(Theme.MICRO); setForeground(Theme.TXT_MUTED); }});
        right.add(hudQty);
        right.add(new JLabel("Avg:") {{ setFont(Theme.MICRO); setForeground(Theme.TXT_MUTED); }});
        right.add(hudAvg);
        right.add(new JLabel("Value:") {{ setFont(Theme.MICRO); setForeground(Theme.TXT_MUTED); }});
        right.add(hudVal);
        right.add(new JLabel("Unrealized:") {{ setFont(Theme.MICRO); setForeground(Theme.TXT_MUTED); }});
        right.add(hudPnl);

        p.add(right, BorderLayout.EAST);
        return p;
    }

    private JPanel buildLegendStrip() {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(true);
        p.setBackground(new java.awt.Color(0x0E, 0x14, 0x22));
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER_CUSTOM),
                BorderFactory.createEmptyBorder(2, 10, 2, 10)));
        p.setPreferredSize(new Dimension(100, 20));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        left.setOpaque(false);

        JLabel lGain = new JLabel("● Profit / Positive Return");
        lGain.setFont(Theme.MICRO);
        lGain.setForeground(Theme.GAIN);

        JLabel lLoss = new JLabel("● Loss / Negative Return");
        lLoss.setFont(Theme.MICRO);
        lLoss.setForeground(Theme.LOSS);

        JLabel lHint = new JLabel("Hover/click 3D bars to inspect holding details");
        lHint.setFont(Theme.MICRO);
        lHint.setForeground(Theme.TXT_MUTED);

        left.add(lGain);
        left.add(lLoss);
        left.add(lHint);
        p.add(left, BorderLayout.WEST);

        JLabel lProj = new JLabel("Camera: ISO-45° Orbit");
        lProj.setFont(Theme.MICRO);
        lProj.setForeground(Theme.TXT_MUTED);
        p.add(lProj, BorderLayout.EAST);

        return p;
    }

    public void setOnSelectSymbol(Consumer<String> onSelectSymbol) {
        this.onSelectSymbol = onSelectSymbol;
    }

    @Override
    protected Scene createScene() {
        Group world = new Group(groundGrid, barsGroup);
        world.getTransforms().addAll(rotateX, rotateY);
        world.setTranslateY(25);

        PointLight light1 = new PointLight(Color.WHITE);
        light1.setTranslateX(-180);
        light1.setTranslateY(-250);
        light1.setTranslateZ(-350);

        PointLight light2 = new PointLight(Color.web("#38BDF8"));
        light2.setTranslateX(200);
        light2.setTranslateY(-120);
        light2.setTranslateZ(-150);

        PointLight light3 = new PointLight(Color.WHITE);
        light3.setTranslateX(0);
        light3.setTranslateY(-200);
        light3.setTranslateZ(-450);

        AmbientLight ambient = new AmbientLight(Color.rgb(215, 230, 245, 0.95));
        Group root = new Group(world, light1, light2, light3, ambient);

        camera.setVerticalFieldOfView(true);
        camera.setTranslateZ(DEFAULT_CAM_Z);
        camera.setNearClip(1.0);
        camera.setFarClip(6000.0);
        camera.setFieldOfView(32.0);

        Scene scene = new Scene(root, 600, 320, true, SceneAntialiasing.BALANCED);
        scene.setFill(Color.web("#070B14"));
        scene.setCamera(camera);

        // Mouse drag = Orbit rotation
        scene.setOnMousePressed(e -> {
            dragStartX = e.getSceneX();
            dragStartY = e.getSceneY();
            startAngleX = rotateX.getAngle();
            startAngleY = rotateY.getAngle();
        });

        scene.setOnMouseDragged(e -> {
            rotateY.setAngle(startAngleY + (e.getSceneX() - dragStartX) * 0.4);
            double newX = startAngleX - (e.getSceneY() - dragStartY) * 0.3;
            rotateX.setAngle(Math.max(-75, Math.min(10, newX)));
        });

        // Mouse scroll = Zoom
        scene.setOnScroll(e -> {
            double z = camera.getTranslateZ() + e.getDeltaY() * 1.5;
            camera.setTranslateZ(Math.max(-2500, Math.min(-350, z)));
        });

        rotateTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                rotateY.setAngle((rotateY.getAngle() + 0.6) % 360);
            }
        };

        if (lastData != null) {
            rebuildBars(lastData);
        } else {
            rebuildBars(new ArrayList<>());
        }
        return scene;
    }

    // Public Java/Swing control hooks
    public void setIsoView() {
        Platform.runLater(() -> {
            rotateX.setAngle(-22);
            rotateY.setAngle(-26);
            camera.setTranslateZ(DEFAULT_CAM_Z);
        });
    }

    public void setTopView() {
        Platform.runLater(() -> {
            rotateX.setAngle(-60);
            rotateY.setAngle(0);
            camera.setTranslateZ(DEFAULT_CAM_Z - 50);
        });
    }

    public void toggleAutoRotate() {
        Platform.runLater(() -> {
            isAutoRotating = !isAutoRotating;
            if (isAutoRotating) {
                rotateTimer.start();
            } else {
                rotateTimer.stop();
            }
        });
    }

    public void zoomIn() {
        Platform.runLater(() -> camera.setTranslateZ(Math.min(-350, camera.getTranslateZ() + 70)));
    }

    public void zoomOut() {
        Platform.runLater(() -> camera.setTranslateZ(Math.max(-2500, camera.getTranslateZ() - 70)));
    }

    public void resetView() {
        Platform.runLater(() -> {
            if (isAutoRotating) {
                isAutoRotating = false;
                rotateTimer.stop();
            }
            rotateX.setAngle(-22);
            rotateY.setAngle(-26);
            camera.setTranslateZ(DEFAULT_CAM_Z);
        });
    }

    private void updateHud(Bar b) {
        if (b == null) return;
        SwingUtilities.invokeLater(() -> {
            hudSym.setText(b.label());
            hudName.setText(b.name());

            boolean up = b.profitLoss() >= 0;
            String sign = up ? "+" : "";
            hudRet.setText(sign + String.format("%.2f%%", b.changePercent()));
            hudRet.setForeground(up ? Theme.GAIN : Theme.LOSS);
            hudRet.setBackground(up ? Theme.GREEN_BG : Theme.RED_BG);

            hudQty.setText(b.quantity() + " Units");
            hudAvg.setText(Formatter.money(b.avgCost()));
            hudVal.setText(Formatter.money(b.value()));
            hudPnl.setText(sign + Formatter.money(b.profitLoss()));
            hudPnl.setForeground(up ? Theme.GAIN : Theme.LOSS);
        });
    }

    public void update(List<Bar> data) {
        if (data == null) return;
        this.lastData = new ArrayList<>(data);
        if (is3DAvailable()) {
            List<Bar> copy = new ArrayList<>(lastData);
            Platform.runLater(() -> rebuildBars(copy));
        }
    }

    private void rebuildBars(List<Bar> data) {
        barsGroup.getChildren().clear();
        groundGrid.getChildren().clear();

        int n = data.size();
        double spacing = Math.max(75.0, Math.min(105.0, 480.0 / Math.max(1, n)));
        double barWidth = Math.min(52.0, spacing * 0.68);
        double floorWidth = Math.max(480.0, n * spacing + 120.0);
        double floorDepth = 220.0;

        // Dark Floor Pedestal
        Box pedestal = new Box(floorWidth, 4, floorDepth);
        pedestal.setTranslateY(2);
        PhongMaterial pedMat = new PhongMaterial(Color.web("#1E293B"));
        pedMat.setSpecularColor(Color.web("#38BDF8"));
        pedestal.setMaterial(pedMat);
        groundGrid.getChildren().add(pedestal);

        // Floor Grid Lines
        int divisions = (int) (floorWidth / 40);
        for (int i = -divisions / 2; i <= divisions / 2; i++) {
            Box line = new Box(1.4, 0.4, floorDepth - 10);
            line.setTranslateX(i * 40);
            line.setTranslateY(-0.5);
            PhongMaterial lm = new PhongMaterial(Color.web("#334155"));
            line.setMaterial(lm);
            groundGrid.getChildren().add(line);
        }

        if (data.isEmpty()) {
            Text empty = label("Buy an asset to see your 3D portfolio bars", 0, -20, 13, Color.web("#94A3B8"));
            barsGroup.getChildren().add(empty);
            return;
        }

        // Default telemetry to first holding
        updateHud(data.get(0));

        double maxVal = 0.0;
        for (Bar b : data) maxVal = Math.max(maxVal, b.value());
        if (maxVal <= 0.0) maxVal = 1.0;

        double startX = -(n - 1) * spacing / 2.0;

        for (int i = 0; i < n; i++) {
            Bar b = data.get(i);
            double h = Math.max(30.0, (b.value() / maxVal) * MAX_BAR_HEIGHT);
            double x = startX + i * spacing;

            // Pillar Box
            Box box = new Box(barWidth, h, barWidth);
            box.setTranslateX(x);
            box.setTranslateY(-h / 2.0);

            boolean up = b.profitLoss() >= 0;
            Color baseCol = up ? Color.web("#22C55E") : Color.web("#EF4444");
            Color specCol = up ? Color.web("#4ADE80") : Color.web("#F87171");

            PhongMaterial mat = new PhongMaterial(baseCol);
            mat.setSpecularColor(specCol);
            mat.setSpecularPower(32.0);
            box.setMaterial(mat);

            // Glowing Top Cap
            Box cap = new Box(barWidth + 2, 3, barWidth + 2);
            cap.setTranslateX(x);
            cap.setTranslateY(-h - 1.5);
            PhongMaterial capMat = new PhongMaterial(specCol);
            capMat.setSpecularColor(Color.WHITE);
            cap.setMaterial(capMat);

            box.setOnMouseEntered(e -> {
                box.setScaleX(1.10);
                box.setScaleZ(1.10);
                cap.setScaleX(1.10);
                cap.setScaleZ(1.10);
                mat.setSpecularColor(Color.WHITE);
                updateHud(b);
            });

            box.setOnMouseExited(e -> {
                box.setScaleX(1.0);
                box.setScaleZ(1.0);
                cap.setScaleX(1.0);
                cap.setScaleZ(1.0);
                mat.setSpecularColor(specCol);
            });

            box.setOnMouseClicked(e -> {
                updateHud(b);
                if (onSelectSymbol != null) {
                    onSelectSymbol.accept(b.label());
                }
            });

            // 3D Text labels
            Text symText = label(b.label(), x, 24, 13, up ? Color.web("#4ADE80") : Color.web("#F87171"));
            Text valText = label(shortMoney(b.value()), x, -h - 16, 11, Color.web("#E2E8F0"));

            barsGroup.getChildren().addAll(box, cap, symText, valText);
        }
    }

    private Text label(String text, double x, double y, int size, Color color) {
        Text t = new Text(text);
        t.setFont(Font.font("JetBrains Mono", FontWeight.BOLD, size));
        t.setFill(color);
        t.setTextOrigin(VPos.CENTER);
        t.setTranslateX(x - t.getLayoutBounds().getWidth() / 2.0);
        t.setTranslateY(y);
        t.setTranslateZ(-30);
        return t;
    }

    private String shortMoney(double val) {
        if (val >= 1_000_000) return String.format("$%.1fM", val / 1_000_000.0);
        if (val >= 1_000) return String.format("$%.1fk", val / 1_000.0);
        return String.format("$%.0f", val);
    }
}
