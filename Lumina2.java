package lumina2;

import javafx.animation.*;
import javafx.application.Application;
import static javafx.application.Application.launch;
import javafx.application.Platform;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;

public class Lumina2 extends Application {

    private Pane root;
    private Circle sun;
    private Circle moon;
    private Rectangle ground;
    private Group cloud1;
    private Group cloud2;
    private SerialComm serialComm;

    private Group starGroup1;
    private Group starGroup2;
    private Group starGroup3;
    private Group starGroup4;
    private Group starGroup5;
    private Group starGroup6;

    private final double WIDTH = 1600;
    private final double HEIGHT = 900;
    private final double GROUND_HEIGHT = 100;

    private Timeline sunAnimation;
    private Timeline moonAnimation;

    @Override
    public void start(Stage primaryStage) {
        root = new Pane();

        Circle star1 = new Circle(2, Color.WHITE);
        Circle star2 = new Circle(2, Color.WHITE);
        Circle star3 = new Circle(2, Color.WHITE);
        Circle star4 = new Circle(2, Color.WHITE);
        Circle star5 = new Circle(2, Color.WHITE);
        Circle star6 = new Circle(2, Color.WHITE);
        setupStarTwinkle(star1);
        setupStarTwinkle(star2);
        setupStarTwinkle(star3);
        setupStarTwinkle(star4);
        setupStarTwinkle(star5);
        setupStarTwinkle(star6);

        starGroup1 = new Group(star1);
        starGroup1.setTranslateX(200);
        starGroup1.setTranslateY(300);
        starGroup1.setOpacity(0);

        starGroup2 = new Group(star2);
        starGroup2.setTranslateX(350);
        starGroup2.setTranslateY(500);
        starGroup2.setOpacity(0);

        starGroup3 = new Group(star3);
        starGroup3.setTranslateX(500);
        starGroup3.setTranslateY(400);
        starGroup3.setOpacity(0);
        
        starGroup4 = new Group(star4);
        starGroup4.setTranslateX(700);
        starGroup4.setTranslateY(500);
        starGroup4.setOpacity(0);
        
        starGroup5 = new Group(star5);
        starGroup5.setTranslateX(1000);
        starGroup5.setTranslateY(200);
        starGroup5.setOpacity(0);
        
        starGroup6 = new Group(star6);
        starGroup6.setTranslateX(1400);
        starGroup6.setTranslateY(500);
        starGroup6.setOpacity(0);

        cloud1 = createCloud(-100, 100);
        cloud2 = createCloud(WIDTH + 100, 200);

        sun = new Circle(10, Color.GOLD);
        sun.setTranslateX(WIDTH / 2);
        sun.setTranslateY(HEIGHT);

        moon = new Circle(10, Color.LIGHTGRAY);
        moon.setTranslateX(WIDTH / 2);
        moon.setTranslateY(HEIGHT);

        ground = new Rectangle(0, HEIGHT - GROUND_HEIGHT, WIDTH, GROUND_HEIGHT);
        ground.setFill(Color.GREEN);

        root.getChildren().addAll(starGroup1, starGroup2, starGroup3,starGroup4,starGroup5,starGroup6, sun, moon, ground, cloud1, cloud2);

        Scene scene = new Scene(root, WIDTH, HEIGHT);
        primaryStage.setTitle("Răsărit și Apus");
        primaryStage.setScene(scene);
        primaryStage.show();

        setupCloudMovement(cloud1, true);
        setupCloudMovement(cloud2, false);

        new Thread(this::setupSerialCommunication).start();
    }

    private Group createCloud(double x, double y) {
        Circle c1 = new Circle(50, Color.WHITE);
        Circle c2 = new Circle(50, Color.WHITE);
        c2.setTranslateX(50);
        Group cloud = new Group(c1, c2);
        cloud.setTranslateX(x);
        cloud.setTranslateY(y);
        return cloud;
    }

    private void setupStarTwinkle(Circle star) {
        Timeline twinkle = new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(star.opacityProperty(), 0.2)),
            new KeyFrame(Duration.seconds(0.1), new KeyValue(star.opacityProperty(), 1.0)),
            new KeyFrame(Duration.seconds(0.2), new KeyValue(star.opacityProperty(), 0.2))
        );
        twinkle.setCycleCount(Timeline.INDEFINITE);
        twinkle.setAutoReverse(false);
        twinkle.play();
    }

    private void setupSerialCommunication() {
        serialComm = new SerialComm("COM3", 9600);
        if (!serialComm.openPort()) {
            System.out.println("Port indisponibil.");
            return;
        }

        serialComm.addDataListener(data -> Platform.runLater(() -> {
            try {
                float lux = Float.parseFloat(data.trim());
                updateScene(lux);
            } catch (NumberFormatException ignored) {
            }
        }));
    }

    private void setupCloudMovement(Group cloud, boolean leftToRight) {
        moveCloud(cloud, leftToRight);
    }

    private void moveCloud(Group cloud, boolean leftToRight) {
        double startX = leftToRight ? -100 : WIDTH + 100;
        double endX = leftToRight ? WIDTH + 100 : -100;

        Timeline moveTimeline = new Timeline(
            new KeyFrame(Duration.seconds(20),
                new KeyValue(cloud.translateXProperty(), endX)
            )
        );

        moveTimeline.setOnFinished(e -> {
            PauseTransition pause = new PauseTransition(Duration.seconds(2));
            pause.setOnFinished(event -> moveCloud(cloud, !leftToRight));
            pause.play();
        });

        cloud.setTranslateX(startX);
        moveTimeline.play();
    }

    private void updateScene(float lux) {
        lux = Math.max(0, Math.min(lux, 6000));
        double frac = lux / 6000.0;

        double sunFrac = Math.max(0, (lux - 1500) / 4500.0);
        double sunY = HEIGHT - (sunFrac * HEIGHT - 200);
        double sunRadius = 10 + 50 * sunFrac;

        sunAnimation = new Timeline(
            new KeyFrame(Duration.seconds(0.1),
                new KeyValue(sun.translateYProperty(), sunY),
                new KeyValue(sun.radiusProperty(), sunRadius)
            )
        );
        sunAnimation.play();

        double moonFrac = Math.max(0, (1500 - lux) / 1500.0);
        double moonY = 200 + (1 - moonFrac) * (HEIGHT - GROUND_HEIGHT);
        double moonRadius = 10 + 30 * moonFrac;

        moonAnimation = new Timeline(
            new KeyFrame(Duration.seconds(0.1),
                new KeyValue(moon.translateYProperty(), moonY),
                new KeyValue(moon.radiusProperty(), moonRadius)
            )
        );
        moonAnimation.play();

        Color startColorSky = Color.DARKBLUE;
        Color endColorSky = Color.LIGHTSKYBLUE;
        Color backgroundColor = startColorSky.interpolate(endColorSky, frac);
        
        Color startColorGround = Color.DARKGREEN;
        Color endColorGround = Color.LIGHTGREEN;
        Color groundColor = startColorGround.interpolate(endColorGround, frac);
        
        ground.setFill(groundColor);

        root.setStyle("-fx-background-color: rgb(" +
                (int)(backgroundColor.getRed() * 255) + "," +
                (int)(backgroundColor.getGreen() * 255) + "," +
                (int)(backgroundColor.getBlue() * 255) + ");");

        if (lux < 4000) {
            sun.setFill(Color.GOLD);
        } else {
            sun.setFill(Color.ORANGE);
        }


        double starVisibility;
        if (lux < 1000) {
            starVisibility = 1.0;
        } else if (lux > 2000) {
            starVisibility = 0.0;
        } else {
            starVisibility = 1.0 - (lux - 1000) / 1000.0;
        }

        starGroup1.setOpacity(starVisibility);
        starGroup2.setOpacity(starVisibility);
        starGroup3.setOpacity(starVisibility);
        starGroup4.setOpacity(starVisibility);
        starGroup5.setOpacity(starVisibility);
        starGroup6.setOpacity(starVisibility);

        double cloudVisibility;
        if (lux < 1000) {
            cloudVisibility = 0.3;
        } else if (lux > 2000) {
            cloudVisibility = 1.0;
        } else {
            cloudVisibility = 0.3 + (lux - 1000) / 1000.0 * (1.0 - 0.3);
        }
        //end

        cloud1.setOpacity(cloudVisibility);
        cloud2.setOpacity(cloudVisibility);
    }

    public static void main(String[] args) {
        launch(args);
        
    }
}

