package lumina2;

import javafx.animation.*;
import javafx.application.Application;
import static javafx.application.Application.launch;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.itextpdf.text.Document;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Image;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

public class Lumina2 extends Application {

    private static final Logger LOGGER = Logger.getLogger(Lumina2.class.getName());

    private BorderPane root;
    private Pane animationPane;

    private Circle sunCircle;
    private Circle moonCircle;
    private Rectangle ground;
    private Group cloud1;
    private Group cloud2;
    private SerialComm serialComm;

    // Stele
    private final List<Group> starGroups = new ArrayList<>();

    // Noile controale
    private CheckBox testModeCheckBox;
    private LineChart<Number, Number> luxChart;
    private XYChart.Series<Number, Number> luxSeries;
    private Label alertLabel;

    // Istoric + detectare schimbări bruște
    private final List<Double> luxHistory = new ArrayList<>();
    private final List<Integer> suddenChangeIndices = new ArrayList<>();
    private int sampleIndex = 0;
    private double lastLux = -1;
    private static final double SUDDEN_CHANGE_THRESHOLD = 800.0; // lux

    private static final double width = 1600.0;
    private static final double height = 900.0;
    private static final double groundHeight = 100.0;

    private Timeline sunAnimation;
    private Timeline moonAnimation;

    // Constante pentru lux și vizibilități
    private static final double LUX_MIN = 0.0;
    private static final double LUX_MAX = 6000.0;

    private static final double STAR_FULL_VISIBLE_LUX = 1000.0;
    private static final double STAR_HIDDEN_LUX = 2000.0;

    private static final double CLOUD_MIN_LUX = 1000.0;
    private static final double CLOUD_MAX_LUX = 2000.0;
    private static final double CLOUD_MIN_VISIBILITY = 0.3;
    private static final double CLOUD_MAX_VISIBILITY = 1.0;

    private static final double SUN_COLOR_THRESHOLD_LUX = 4000.0;

    @Override
    public void start(Stage primaryStage) {
        root = new BorderPane();
        animationPane = new Pane();
        animationPane.setPrefSize(width, height);

        // --- Stele ---
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

        Group sg1 = new Group(star1);
        sg1.setTranslateX(200);
        sg1.setTranslateY(300);
        sg1.setOpacity(0);

        Group sg2 = new Group(star2);
        sg2.setTranslateX(350);
        sg2.setTranslateY(500);
        sg2.setOpacity(0);

        Group sg3 = new Group(star3);
        sg3.setTranslateX(500);
        sg3.setTranslateY(400);
        sg3.setOpacity(0);

        Group sg4 = new Group(star4);
        sg4.setTranslateX(700);
        sg4.setTranslateY(500);
        sg4.setOpacity(0);

        Group sg5 = new Group(star5);
        sg5.setTranslateX(1000);
        sg5.setTranslateY(200);
        sg5.setOpacity(0);

        Group sg6 = new Group(star6);
        sg6.setTranslateX(1400);
        sg6.setTranslateY(500);
        sg6.setOpacity(0);

        starGroups.add(sg1);
        starGroups.add(sg2);
        starGroups.add(sg3);
        starGroups.add(sg4);
        starGroups.add(sg5);
        starGroups.add(sg6);

        // --- Nori ---
        cloud1 = createCloud(-100, 100);
        cloud2 = createCloud(width + 100, 200);

        // --- Soare + Lună ---
        sunCircle = new Circle(10, Color.GOLD);
        sunCircle.setTranslateX(width / 2);
        sunCircle.setTranslateY(height);

        moonCircle = new Circle(10, Color.LIGHTGRAY);
        moonCircle.setTranslateX(width / 2);
        moonCircle.setTranslateY(height);

        // --- Sol ---
        ground = new Rectangle(0, height - groundHeight, width, groundHeight);
        ground.setFill(Color.GREEN);

        // --- Label alertă schimbări bruște ---
        alertLabel = new Label();
        alertLabel.setTextFill(Color.RED);
        alertLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");
        alertLabel.setVisible(false);
        alertLabel.setLayoutX(20);
        alertLabel.setLayoutY(20);

        animationPane.getChildren().addAll(
                sg1, sg2, sg3, sg4, sg5, sg6,
                sunCircle, moonCircle, ground, cloud1, cloud2,
                alertLabel
        );

        root.setCenter(animationPane);
        root.setBottom(createControlPanel());
        root.setRight(createRightPanel());

        Scene scene = new Scene(root, width, height);
        primaryStage.setTitle("Răsărit și Apus");
        primaryStage.setScene(scene);
        primaryStage.show();

        setupCloudMovement(cloud1, true);
        setupCloudMovement(cloud2, false);

        // Thread pentru comunicarea serială
        new Thread(this::setupSerialCommunication).start();
    }

    // Panou jos: slider + mod test
    private HBox createControlPanel() {
        Slider testSlider = new Slider(0, LUX_MAX, 0);
        testSlider.setShowTickMarks(true);
        testSlider.setShowTickLabels(true);
        testSlider.setMajorTickUnit(1000);
        testSlider.setMinorTickCount(4);
        testSlider.setPrefWidth(400);

        Label sliderLabel = new Label("Luminozitate (Mod Test):");

        testModeCheckBox = new CheckBox("Mod Test (simulare luminozitate)");
        testModeCheckBox.setSelected(false);

        testSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (isTestModeEnabled()) {
                handleNewLuxValue(newVal.floatValue());
            }
        });

        HBox hbox = new HBox(10, sliderLabel, testSlider, testModeCheckBox);
        hbox.setPadding(new Insets(10));
        return hbox;
    }

    // Panou dreapta: grafic + buton PDF
    private VBox createRightPanel() {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Timp (mostre)");

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Lux");

        luxChart = new LineChart<>(xAxis, yAxis);
        luxChart.setTitle("Istoric luminozitate");
        luxChart.setAnimated(false);
        luxChart.setCreateSymbols(true);
        luxChart.setPrefSize(450, 400);

        luxSeries = new XYChart.Series<>();
        luxSeries.setName("Lux");
        luxChart.getData().add(luxSeries);

        Button exportPdfButton = new Button("Export PDF");
        exportPdfButton.setOnAction(e -> exportHistoryToPdf());

        VBox vbox = new VBox(10, luxChart, exportPdfButton);
        vbox.setPadding(new Insets(10));
        return vbox;
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

    // === Comunicare serial ===

    private void setupSerialCommunication() {
        serialComm = new SerialComm("COM3", 9600);
        if (!serialComm.openPort()) {
            LOGGER.warning("Port indisponibil.");
            return;
        }

        serialComm.addDataListener(data ->
                Platform.runLater(() -> processSerialDataOnFxThread(data))
        );
    }

    private void processSerialDataOnFxThread(String data) {
        if (isTestModeEnabled()) {
            return;
        }

        Float lux = parseLux(data);
        if (lux != null) {
            handleNewLuxValue(lux);
        }
    }

    private Float parseLux(String data) {
        try {
            return Float.parseFloat(data.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isTestModeEnabled() {
        return testModeCheckBox != null && testModeCheckBox.isSelected();
    }

    // Unificăm tratarea valorilor de lux
    private void handleNewLuxValue(float lux) {
        double clampedLux = clampLux(lux);
        updateScene((float) clampedLux);
        addLuxToHistory(clampedLux);
    }

    private double clampLux(double lux) {
        return Math.max(LUX_MIN, Math.min(lux, LUX_MAX));
    }

    private void setupCloudMovement(Group cloud, boolean leftToRight) {
        moveCloud(cloud, leftToRight);
    }

    private void moveCloud(Group cloud, boolean leftToRight) {
        double startX = leftToRight ? -100 : width + 100;
        double endX = leftToRight ? width + 100 : -100;

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

    // === Actualizare scenă ===

    private void updateScene(float lux) {
        double clampedLux = clampLux(lux);
        double frac = clampedLux / LUX_MAX;

        updateSun(clampedLux);
        updateMoon(clampedLux);
        updateSkyAndGround(frac);
        updateSunColor(clampedLux);
        updateStars(clampedLux);
        updateClouds(clampedLux);
    }

    private void updateSun(double lux) {
        double sunFrac = Math.max(0, (lux - 1500) / 4500.0);
        double sunY = height - (sunFrac * height - 200);
        double sunRadius = 10 + 50 * sunFrac;

        if (sunAnimation != null) {
            sunAnimation.stop();
        }

        sunAnimation = new Timeline(
                new KeyFrame(Duration.seconds(0.1),
                        new KeyValue(sunCircle.translateYProperty(), sunY),
                        new KeyValue(sunCircle.radiusProperty(), sunRadius)
                )
        );
        sunAnimation.play();
    }

    private void updateMoon(double lux) {
        double moonFrac = Math.max(0, (1500 - lux) / 1500.0);
        double moonY = 200 + (1 - moonFrac) * (height - groundHeight);
        double moonRadius = 10 + 30 * moonFrac;

        if (moonAnimation != null) {
            moonAnimation.stop();
        }

        moonAnimation = new Timeline(
                new KeyFrame(Duration.seconds(0.1),
                        new KeyValue(moonCircle.translateYProperty(), moonY),
                        new KeyValue(moonCircle.radiusProperty(), moonRadius)
                )
        );
        moonAnimation.play();
    }

    private void updateSkyAndGround(double frac) {
        Color startColorSky = Color.DARKBLUE;
        Color endColorSky = Color.LIGHTSKYBLUE;
        Color backgroundColor = startColorSky.interpolate(endColorSky, frac);

        Color startColorGround = Color.DARKGREEN;
        Color endColorGround = Color.LIGHTGREEN;
        Color groundColor = startColorGround.interpolate(endColorGround, frac);

        ground.setFill(groundColor);

        animationPane.setStyle("-fx-background-color: rgb(" +
                (int) (backgroundColor.getRed() * 255) + "," +
                (int) (backgroundColor.getGreen() * 255) + "," +
                (int) (backgroundColor.getBlue() * 255) + ");");
    }

    private void updateSunColor(double lux) {
        double t = computeClampedLinear(
                lux,
                0.0,
                SUN_COLOR_THRESHOLD_LUX,
                0.0,
                1.0
        );
        Color start = Color.GOLD;
        Color end = Color.ORANGE;
        sunCircle.setFill(start.interpolate(end, t));
    }

    private void updateStars(double lux) {
        double starVisibility = computeClampedLinear(
                lux,
                STAR_FULL_VISIBLE_LUX,
                STAR_HIDDEN_LUX,
                1.0,
                0.0
        );

        for (Group starGroup : starGroups) {
            starGroup.setOpacity(starVisibility);
        }
    }

    private void updateClouds(double lux) {
        double cloudVisibility = computeClampedLinear(
                lux,
                CLOUD_MIN_LUX,
                CLOUD_MAX_LUX,
                CLOUD_MIN_VISIBILITY,
                CLOUD_MAX_VISIBILITY
        );

        cloud1.setOpacity(cloudVisibility);
        cloud2.setOpacity(cloudVisibility);
    }

    /**
     * Mapare liniară între [minValue, maxValue] -> [minResult, maxResult] cu
     * saturare la capete, implementată fără if-uri explicite.
     */
    private double computeClampedLinear(double value,
                                        double minValue,
                                        double maxValue,
                                        double minResult,
                                        double maxResult) {
        double ratio = (value - minValue) / (maxValue - minValue);
        double clampedRatio = Math.max(0.0, Math.min(1.0, ratio));
        return minResult + clampedRatio * (maxResult - minResult);
    }

    // === ISTORIC + DETECTARE SCHIMBĂRI BRUȘTE ===

    private void addLuxToHistory(double lux) {
        sampleIndex++;
        luxHistory.add(lux);

        boolean sudden = isSuddenChange(lux);

        XYChart.Data<Number, Number> dataPoint = new XYChart.Data<>(sampleIndex, lux);
        luxSeries.getData().add(dataPoint);

        if (sudden) {
            suddenChangeIndices.add(sampleIndex);
            dataPoint.nodeProperty().addListener((obs, oldNode, newNode) -> {
                if (newNode != null) {
                    newNode.setStyle("-fx-background-color: red; -fx-background-radius: 4px;");
                }
            });
        }
    }

    private boolean isSuddenChange(double currentLux) {
        if (lastLux < 0) {
            lastLux = currentLux;
            return false;
        }
        double diff = Math.abs(currentLux - lastLux);
        boolean sudden = diff >= SUDDEN_CHANGE_THRESHOLD;
        if (sudden) {
            triggerAlert(lastLux, currentLux, diff);
        }
        lastLux = currentLux;
        return sudden;
    }

    private void triggerAlert(double oldLux, double newLux, double diff) {
        alertLabel.setText(String.format(Locale.US,
                "Schimbare bruscă de lumină: %.0f → %.0f lux (Δ%.0f)",
                oldLux, newLux, diff));
        alertLabel.setOpacity(1.0);
        alertLabel.setVisible(true);

        FadeTransition fade = new FadeTransition(Duration.seconds(2), alertLabel);
        fade.setFromValue(1.0);
        fade.setToValue(0.0);
        fade.setOnFinished(e -> alertLabel.setVisible(false));
        fade.play();
    }

    // === EXPORT PDF ===

    private void exportHistoryToPdf() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Salvează raport PDF");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Fișier PDF", "*.pdf")
        );
        File pdfFile = fileChooser.showSaveDialog(root.getScene().getWindow());
        if (pdfFile == null) {
            return;
        }

        try {
            // Snapshot pentru grafic
            WritableImage chartImage = luxChart.snapshot(new SnapshotParameters(), null);
            File tempPng = File.createTempFile("lux_chart_", ".png");
            ImageIO.write(SwingFXUtils.fromFXImage(chartImage, null), "png", tempPng);

            Document document = new Document();
            PdfWriter.getInstance(document, new FileOutputStream(pdfFile));
            document.open();

            // Statistici
            double min = getMin();
            double max = getMax();
            double avg = getAverage();

            document.add(new Paragraph("Raport variatii luminozitate"));
            document.add(new Paragraph(" "));
            document.add(new Paragraph("Numar valori: " + luxHistory.size()));
            document.add(new Paragraph(String.format(Locale.US, "Valoare minima: %.2f lux", min)));
            document.add(new Paragraph(String.format(Locale.US, "Valoare maxima: %.2f lux", max)));
            document.add(new Paragraph(String.format(Locale.US, "Valoare medie: %.2f lux", avg)));
            document.add(new Paragraph(" "));

            // Imaginea graficului
            Image chart = Image.getInstance(tempPng.getAbsolutePath());
            chart.scaleToFit(500, 300);
            document.add(chart);
            document.add(new Paragraph(" "));

            // Tabel cu valori (mostre + marcaj schimbari bruste)
            PdfPTable table = new PdfPTable(2);
            table.addCell("Mostra");
            table.addCell("Lux");

            IntStream.range(0, luxHistory.size()).forEach(i -> {
                int index = i + 1;
                double value = luxHistory.get(i);

                table.addCell(String.valueOf(index));

                String text = String.format(Locale.US, "%.2f", value);
                if (suddenChangeIndices.contains(index)) {
                    text += " *"; // * = schimbare bruscă
                }
                table.addCell(text);
            });

            document.add(table);

            // Adăugăm mereu legenda (chiar dacă nu există * efectiv)
            document.add(new Paragraph(" "));
            document.add(new Paragraph(
                    "* Valorile marcate indica mostre unde a fost detectata o schimbare brusca a luminii."
            ));

            document.close();
            tempPng.delete();

            LOGGER.info("PDF generat: " + pdfFile.getAbsolutePath());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Eroare la generarea PDF-ului", e);
        }
    }

    // Versiuni cu Stream – fără bucle + if în codul tău
    private double getMin() {
        return luxHistory.isEmpty()
                ? 0.0
                : luxHistory.stream()
                            .mapToDouble(Double::doubleValue)
                            .min()
                            .orElse(0.0);
    }

    private double getMax() {
        return luxHistory.isEmpty()
                ? 0.0
                : luxHistory.stream()
                            .mapToDouble(Double::doubleValue)
                            .max()
                            .orElse(0.0);
    }

    private double getAverage() {
        if (luxHistory.isEmpty()) {
            return 0.0;
        }
        DoubleStream stream = luxHistory.stream().mapToDouble(Double::doubleValue);
        return stream.average().orElse(0.0);
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        if (serialComm != null) {
            serialComm.closePort();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
