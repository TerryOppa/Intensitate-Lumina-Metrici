package lumina;

import javafx.animation.FillTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Lumina2 extends Application {

    private static final double MIN_VAL = 0.0;
    private static final double MAX_VAL = 1000.0;
    private static final double BAR_HEIGHT = 300.0;
    private static final double BAR_WIDTH = 60.0;
    private static final int ANIM_MS = 200;

    private static final Pattern NUMBER_EXTRACT = Pattern.compile("[-+]?\\d+(?:\\.\\d+)?");
    private static final double[] THRESHOLDS = {100.0, 400.0, 700.0};
    private static final Color[] COLORS = { Color.DODGERBLUE, Color.LIGHTGREEN, Color.GOLD, Color.TOMATO };

    private Label valueLabel;
    private Rectangle barFill;
    private Rectangle barOutline;
    private ComboBox<String> portPicker;

    private final SerialComm serial = new SerialComm();

    @Override
    public void start(Stage stage) {
        stage.setTitle("Lumina");

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(16));
        root.setTop(buildTopBar());
        root.setCenter(buildMeter());

        stage.setScene(new Scene(root, 520, 420));
        stage.show();

        serial.setOnLine(this::onSerialLine);
        refreshPorts();
    }

    @Override
    public void stop() {
        serial.close();
    }

    private VBox buildTopBar() {
        Label title = new Label("Port serial:");
        title.setFont(Font.font(16));
        portPicker = new ComboBox<>();
        portPicker.setOnAction(e -> openSelectedPort());

        VBox box = new VBox(8, title, portPicker);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private StackPane buildMeter() {
        valueLabel = new Label("Lumina: --");
        valueLabel.setFont(Font.font(28));

        barFill = new Rectangle(BAR_WIDTH, 0, Color.DODGERBLUE);
        barFill.setArcWidth(18);
        barFill.setArcHeight(18);

        barOutline = new Rectangle(BAR_WIDTH, BAR_HEIGHT);
        barOutline.setFill(Color.TRANSPARENT);
        barOutline.setStroke(Color.BLACK);
        barOutline.setArcWidth(18);
        barOutline.setArcHeight(18);

        StackPane bar = new StackPane(barOutline, barFill);
        VBox v = new VBox(10, valueLabel, bar);
        v.setAlignment(Pos.CENTER);
        v.setPadding(new Insets(16));
        return new StackPane(v);
    }

    private void refreshPorts() {
        String[] ports = serial.listPorts();
        portPicker.getItems().setAll(ports);
        int index = ports.length > 0 ? 0 : -1;
        portPicker.getSelectionModel().select(index);
        openSelectedPort(); // will safely no-op if null
    }

    private void openSelectedPort() {
        String sel = portPicker.getValue();
        if (sel == null) return;
        if (!serial.open(sel)) valueLabel.setText("Eroare port");
    }

    private void onSerialLine(String line) {
        Matcher m = NUMBER_EXTRACT.matcher(line);
        if (!m.find()) return;
        double v = Double.parseDouble(m.group());
        Platform.runLater(() -> updateUI(v));
    }

    private void updateUI(double value) {
        valueLabel.setText(String.format(Locale.ROOT, "Lumina: %.1f", value));
        animateBar(toBarHeight(value));
        animateBarColor(value);
    }

    private double toBarHeight(double v) {
        double clamped = Math.max(MIN_VAL, Math.min(MAX_VAL, v));
        double ratio = (clamped - MIN_VAL) / (MAX_VAL - MIN_VAL);
        return ratio * BAR_HEIGHT;
    }

    private void animateBar(double newHeight) {
        Timeline t = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(barFill.heightProperty(), barFill.getHeight())),
                new KeyFrame(Duration.millis(ANIM_MS), new KeyValue(barFill.heightProperty(), Math.max(0, newHeight)))
        );
        t.play();
    }

    private void animateBarColor(double v) {
        Color target = colorFor(v);
        FillTransition ft = new FillTransition(Duration.millis(ANIM_MS), barFill, (Color) barFill.getFill(), target);
        ft.play();
    }

    private static Color colorFor(double v) {
        int pos = Arrays.binarySearch(THRESHOLDS, v);
        int bucket = (pos >= 0) ? pos + 1 : -pos - 1;
        return COLORS[bucket];
    }

    public static void main(String[] args) {
        launch(args);
    }
}
