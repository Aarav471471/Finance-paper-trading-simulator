package com.papertrader.ui.three;

import com.papertrader.ui.Theme;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.RotateTransition;
import javafx.animation.TranslateTransition;
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
import javafx.scene.shape.Cylinder;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;

/** Login screen decoration: a shiny gold coin spinning in 3D, with small price bars orbiting it. */
public class Coin3DView extends Base3DView {

    public Coin3DView() {
        super();
        start3D();
    }

    @Override
    protected Scene createScene() {
        Group coin = buildCoin();
        Group orbit = buildOrbitingBars();

        Group root = new Group(coin, orbit);
        PointLight light = new PointLight(Color.WHITE);
        light.setTranslateX(-250);
        light.setTranslateY(-250);
        light.setTranslateZ(-500);
        root.getChildren().addAll(light, new AmbientLight(Color.gray(0.35)));

        // spin the coin forever around the vertical (Y) axis
        RotateTransition spin = new RotateTransition(Duration.seconds(5), coin);
        spin.setAxis(Rotate.Y_AXIS);
        spin.setByAngle(360);
        spin.setInterpolator(Interpolator.LINEAR);
        spin.setCycleCount(Animation.INDEFINITE);
        spin.play();

        // make the coin float gently up and down
        TranslateTransition bob = new TranslateTransition(Duration.seconds(2), coin);
        bob.setFromY(-8);
        bob.setToY(8);
        bob.setAutoReverse(true);
        bob.setCycleCount(Animation.INDEFINITE);
        bob.play();

        // the bars orbit the opposite way
        RotateTransition orbitSpin = new RotateTransition(Duration.seconds(9), orbit);
        orbitSpin.setAxis(Rotate.Y_AXIS);
        orbitSpin.setByAngle(-360);
        orbitSpin.setInterpolator(Interpolator.LINEAR);
        orbitSpin.setCycleCount(Animation.INDEFINITE);
        orbitSpin.play();

        PerspectiveCamera camera = new PerspectiveCamera(true);
        camera.setTranslateZ(-700);
        camera.setNearClip(1);
        camera.setFarClip(3000);
        camera.setFieldOfView(35);

        Scene scene = new Scene(root, 420, 420, true, SceneAntialiasing.BALANCED);
        scene.setFill(fxColor(Theme.BACKGROUND));
        scene.setCamera(camera);
        return scene;
    }

    private Group buildCoin() {
        PhongMaterial gold = new PhongMaterial(fxColor(Theme.GOLD));
        gold.setSpecularColor(Color.web("#FFF6D5"));
        gold.setSpecularPower(20);
        PhongMaterial darkGold = new PhongMaterial(Color.web("#C98F1E"));
        darkGold.setSpecularColor(Color.web("#FFE7A3"));

        Cylinder outer = new Cylinder(100, 18);   // the coin body
        outer.setMaterial(darkGold);
        Cylinder inner = new Cylinder(84, 22);    // raised centre face
        inner.setMaterial(gold);

        // a Cylinder stands upright by default, so tip it 90 degrees to face the camera
        Group disc = new Group(outer, inner);
        disc.getTransforms().add(new Rotate(90, Rotate.X_AXIS));

        Text front = dollarSign();
        front.setTranslateZ(-11.5);
        Text back = dollarSign();
        back.setRotationAxis(Rotate.Y_AXIS);
        back.setRotate(180);
        back.setTranslateZ(11.5);

        return new Group(disc, front, back);
    }

    private Text dollarSign() {
        Text text = new Text("$");
        text.setFont(Font.font("System", FontWeight.BOLD, 120));
        text.setFill(Color.web("#7A5410"));
        text.setTextOrigin(VPos.CENTER);
        text.setTranslateX(-text.getLayoutBounds().getWidth() / 2);
        return text;
    }

    private Group buildOrbitingBars() {
        Group orbit = new Group();
        double[] heights = {50, 80, 35, 95, 60, 70};
        for (int i = 0; i < heights.length; i++) {
            Box bar = new Box(16, heights[i], 16);
            boolean green = i % 2 == 0;
            bar.setMaterial(new PhongMaterial(fxColor(green ? Theme.GREEN : Theme.RED)));
            double angle = Math.toRadians(i * 60);
            bar.setTranslateX(Math.cos(angle) * 150);
            bar.setTranslateZ(Math.sin(angle) * 150);
            bar.setTranslateY(70 - heights[i] / 2);
            orbit.getChildren().add(bar);
        }
        return orbit;
    }
}
