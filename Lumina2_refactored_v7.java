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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.itextpdf.text.Document;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Image;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

public class Lumina2 extends Application {

    private BorderPane root;
    private Pane animationPane;

    private Circle sun;
    private Circle moon;
    private Rectangle ground;
    private Group cloud1;
    private Group cloud2;
    private SerialComm serialComm;

    // Stele
    private final List<Group> starGroups = new ArrayList<>();

    // NOILE CONTROALE
    private Slider testSlider;
    private CheckBox testModeCheckBox;
    private LineChart<Number, Number> luxChart;
    private XYChart.Series<Number, Number> luxSeries;
    private Button exportPdfButton;
    private Label alertLabel;

    // Istoric + detectare schimbări bruște
    private final List<Double> luxHistory = new ArrayList<>();
    private final List<Integer> suddenChangeIndices = new ArrayList<>();
    private int sampleIndex = 0;
    private double lastLux = -1;
    private static final double SUDDEN_CHANGE_THRESHOLD = 800.0; // lux

    // Lux curent (ultimul primit) – se loghează pe grafic o dată/secundă
    private double currentLux = 0.0;
    private Timeline sampleTimeline; // timeline pentru eșantionare 1 Hz

    private final double WIDTH = 1600;
    private final double HEIGHT = 900;
    private final double GROUND_HEIGHT = 100;

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
        animationPane.setPrefSize(WIDTH, HEIGHT);

        createStars();
        createClouds();
        createSunAndMoon();
        createGround();
        createAlertLabel();

        animationPane.getChildren().addAll(starGroups);
        animationPane.getChildren().addAll(sun, moon, ground, cloud1, cloud2, alertLabel);

        root.setCenter(animationPane);
        root.setBottom(createControlPanel());
        root.setRight(createRightPanel());

        Scene scene = new Scene(root, WIDTH, HEIGHT);
        primaryStage.setTitle("Răsărit și Apus");
        primaryStage.setScene(scene);
        primaryStage.show();

        setupCloudMovement(cloud1, true);
        setupCloudMovement(cloud2, false);

        setupSamplingTimeline();
        new Thread(this::setupSerialCommunication).start();
    }

    // === Inițializare elemente scenă ===

    private void createStars() {
        Circle star1 = createStarCircle();
        Circle star2 = createStarCircle();
        Circle star3 = createStarCircle();
        Circle star4 = createStarCircle();
        Circle star5 = createStarCircle();
        Circle star6 = createStarCircle();

        setupStarTwinkle(star1);
        setupStarTwinkle(star2);
        setupStarTwinkle(star3);
        setupStarTwinkle(star4);
        setupStarTwinkle(star5);
        setupStarTwinkle(star6);

        starGroups.add(createStarGroup(star1, 200, 300));
        starGroups.add(createStarGroup(star2, 350, 500));
        starGroups.add(createStarGroup(star3, 500, 400));
        starGroups.add(createStarGroup(star4, 700, 500));
        starGroups.add(createStarGroup(star5, 1000, 200));
        starGroups.add(createStarGroup(star6, 1400, 500));
    }

    private Circle createStarCircle() {
        return new Circle(2, Color.WHITE);
    }

    private Group createStarGroup(Circle star, double x, double y) {
        Group group = new Group(star);
        group.setTranslateX(x);
        group.setTranslateY(y);
        group.setOpacity(0);
        return group;
    }

    private void createClouds() {
        cloud1 = createCloud(-100, 100);
        cloud2 = createCloud(WIDTH + 100, 200);
    }

    private void createSunAndMoon() {
        sun = new Circle(10, Color.GOLD);
        sun.setTranslateX(WIDTH / 2);
        sun.setTranslateY(HEIGHT);

        moon = new Circle(10, Color.LIGHTGRAY);
        moon.setTranslateX(WIDTH / 2);
        moon.setTranslateY(HEIGHT);
    }

    private void createGround() {
        ground = new Rectangle(0, HEIGHT - GROUND_HEIGHT, WIDTH, GROUND_HEIGHT);
        ground.setFill(Color.GREEN);
    }

    private void createAlertLabel() {
        alertLabel = new Label();
        alertLabel.setTextFill(Color.RED);
        alertLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");
        alertLabel.setVisible(false);
        alertLabel.setLayoutX(20);
        alertLabel.setLayoutY(20);
    }

    // Panou jos: slider + mod test
    private HBox createControlPanel() {
        testSlider = new Slider(0, LUX_MAX, 0);
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

        exportPdfButton = new Button("Export PDF");
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

    private void setupSamplingTimeline() {
        sampleTimeline = new Timeline(
                new KeyFrame(Duration.seconds(1), e -> addLuxToHistory(currentLux))
        );
        sampleTimeline.setCycleCount(Timeline.INDEFINITE);
        sampleTimeline.play();
    }

    // === Comunicare serial ===

    private void setupSerialCommunication() {
        serialComm = new SerialComm("COM3", 9600);
        if (!serialComm.openPort()) {
            System.out.println("Port indisponibil.");
            return;
        }

        serialComm.addDataListener(this::handleSerialData);
    }

    private void handleSerialData(String data) {
        Platform.runLater(() -> processSerialDataOnFxThread(data));
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
        currentLux = clampedLux;
        updateScene((float) clampedLux);
    }

    private double clampLux(double lux) {
        return Math.max(LUX_MIN, Math.min(lux, LUX_MAX));
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
        double sunY = HEIGHT - (sunFrac * HEIGHT - 200);
        double sunRadius = 10 + 50 * sunFrac;

        if (sunAnimation != null) {
            sunAnimation.stop();
        }

        sunAnimation = new Timeline(
                new KeyFrame(Duration.seconds(0.1),
                        new KeyValue(sun.translateYProperty(), sunY),
                        new KeyValue(sun.radiusProperty(), sunRadius)
                )
        );
        sunAnimation.play();
    }

    private void updateMoon(double lux) {
        double moonFrac = Math.max(0, (1500 - lux) / 1500.0);
        double moonY = 200 + (1 - moonFrac) * (HEIGHT - GROUND_HEIGHT);
        double moonRadius = 10 + 30 * moonFrac;

        if (moonAnimation != null) {
            moonAnimation.stop();
        }

        moonAnimation = new Timeline(
                new KeyFrame(Duration.seconds(0.1),
                        new KeyValue(moon.translateYProperty(), moonY),
                        new KeyValue(moon.radiusProperty(), moonRadius)
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
        if (lux < SUN_COLOR_THRESHOLD_LUX) {
            sun.setFill(Color.GOLD);
        } else {
            sun.setFill(Color.ORANGE);
        }
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
     * Mapare liniară între [minValue, maxValue] -> [minResult, maxResult] cu saturare la capete.
     */
    private double computeClampedLinear(double value,
                                        double minValue,
                                        double maxValue,
                                        double minResult,
                                        double maxResult) {
        if (value <= minValue) {
            return minResult;
        }
        if (value >= maxValue) {
            return maxResult;
        }
        double ratio = (value - minValue) / (maxValue - minValue);
        return minResult + ratio * (maxResult - minResult);
    }

    // === ISTORIC + DETECTARE SCHIMBĂRI BRUȘTE ===

    private void addLuxToHistory(double lux) {
        sampleIndex++;
        luxHistory.add(lux);

        boolean sudden = isSuddenChange(lux);

        XYChart.Data<Number, Number> dataPoint = new XYChart.Data<>(sampleIndex, lux);
        luxSeries.getData().add(dataPoint);

        if (sudden) {
            markSuddenChange(sampleIndex, dataPoint);
        }
    }

    private void markSuddenChange(int index, XYChart.Data<Number, Number> dataPoint) {
        suddenChangeIndices.add(index);
        dataPoint.nodeProperty().addListener((obs, oldNode, newNode) -> {
            if (newNode != null) {
                newNode.setStyle("-fx-background-color: red; -fx-background-radius: 4px;");
            }
        });
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
        if (luxHistory.isEmpty()) {
            System.out.println("Nu există date de exportat.");
            return;
        }

        File pdfFile = choosePdfDestination();
        if (pdfFile == null) {
            return;
        }

        try {
            File chartImageFile = createChartSnapshotFile();
            writePdfReport(pdfFile, chartImageFile);
            chartImageFile.delete();
            System.out.println("PDF generat: " + pdfFile.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private File choosePdfDestination() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Salvează raport PDF");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Fișier PDF", "*.pdf")
        );
        return fileChooser.showSaveDialog(root.getScene().getWindow());
    }

    private File createChartSnapshotFile() throws IOException {
        WritableImage chartImage = luxChart.snapshot(new SnapshotParameters(), null);
        File tempPng = File.createTempFile("lux_chart_", ".png");
        ImageIO.write(SwingFXUtils.fromFXImage(chartImage, null), "png", tempPng);
        return tempPng;
    }

    private void writePdfReport(File pdfFile, File chartImageFile) throws Exception {
        Document document = new Document();
        try {
            PdfWriter.getInstance(document, new FileOutputStream(pdfFile));
            document.open();

            addStatisticsToDocument(document);
            addChartImageToDocument(document, chartImageFile);
            addHistoryTableToDocument(document);
            addSuddenChangeLegend(document);
        } finally {
            document.close();
        }
    }

    private void addStatisticsToDocument(Document document) throws Exception {
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
    }

    private void addChartImageToDocument(Document document, File chartImageFile) throws Exception {
        Image chart = Image.getInstance(chartImageFile.getAbsolutePath());
        chart.scaleToFit(500, 300);
        document.add(chart);
        document.add(new Paragraph(" "));
    }

    private void addHistoryTableToDocument(Document document) throws Exception {
        PdfPTable table = new PdfPTable(2);
        table.addCell("Mostra");
        table.addCell("Lux");

        for (int i = 0; i < luxHistory.size(); i++) {
            int index = i + 1;
            double value = luxHistory.get(i);

            table.addCell(String.valueOf(index));

            String text = String.format(Locale.US, "%.2f", value);
            if (suddenChangeIndices.contains(index)) {
                text += " *"; // * = schimbare bruscă
            }
            table.addCell(text);
        }

        document.add(table);
    }

    private void addSuddenChangeLegend(Document document) throws Exception {
        if (suddenChangeIndices.isEmpty()) {
            return;
        }
        document.add(new Paragraph(" "));
        document.add(new Paragraph(
                "* Valorile marcate indica mostre unde a fost detectata o schimbare brusca a luminii."
        ));
    }

    private double getMin() {
        double min = Double.MAX_VALUE;
        for (double v : luxHistory) {
            if (v < min) min = v;
        }
        return min;
    }

    private double getMax() {
        double max = -Double.MAX_VALUE;
        for (double v : luxHistory) {
            if (v > max) max = v;
        }
        return max;
    }

    private double getAverage() {
        double sum = 0;
        for (double v : luxHistory) {
            sum += v;
        }
        return sum / luxHistory.size();
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        if (serialComm != null) {
            serialComm.closePort();
        }
        if (sampleTimeline != null) {
            sampleTimeline.stop();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
