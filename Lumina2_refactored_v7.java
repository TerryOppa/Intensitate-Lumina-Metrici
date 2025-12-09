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

    private Group starGroup1;
    private Group starGroup2;
    private Group starGroup3;
    private Group starGroup4;
    private Group starGroup5;
    private Group starGroup6;

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

    @Override
    public void start(Stage primaryStage) {
        root = new BorderPane();
        animationPane = new Pane();
        animationPane.setPrefSize(WIDTH, HEIGHT);

        // STELE
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

        // NORI
        cloud1 = createCloud(-100, 100);
        cloud2 = createCloud(WIDTH + 100, 200);

        // SOARE + LUNĂ
        sun = new Circle(10, Color.GOLD);
        sun.setTranslateX(WIDTH / 2);
        sun.setTranslateY(HEIGHT);

        moon = new Circle(10, Color.LIGHTGRAY);
        moon.setTranslateX(WIDTH / 2);
        moon.setTranslateY(HEIGHT);

        // SOL
        ground = new Rectangle(0, HEIGHT - GROUND_HEIGHT, WIDTH, GROUND_HEIGHT);
        ground.setFill(Color.GREEN);

        // LABEL ALERTĂ SCHIMBĂRI BRUȘTE
        alertLabel = new Label();
        alertLabel.setTextFill(Color.RED);
        alertLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");
        alertLabel.setVisible(false);
        alertLabel.setLayoutX(20);
        alertLabel.setLayoutY(20);

        animationPane.getChildren().addAll(
                starGroup1, starGroup2, starGroup3,
                starGroup4, starGroup5, starGroup6,
                sun, moon, ground, cloud1, cloud2,
                alertLabel
        );

        // Panouri UI
        root.setCenter(animationPane);
        root.setBottom(createControlPanel());
        root.setRight(createRightPanel());

        Scene scene = new Scene(root, WIDTH, HEIGHT);
        primaryStage.setTitle("Răsărit și Apus");
        primaryStage.setScene(scene);
        primaryStage.show();

        setupCloudMovement(cloud1, true);
        setupCloudMovement(cloud2, false);

        // Timeline care adaugă un punct pe grafic o dată pe secundă
        sampleTimeline = new Timeline(
                new KeyFrame(Duration.seconds(1), e -> addLuxToHistory(currentLux))
        );
        sampleTimeline.setCycleCount(Timeline.INDEFINITE);
        sampleTimeline.play();

        new Thread(this::setupSerialCommunication).start();
    }

    // Panou jos: slider + mod test
    private HBox createControlPanel() {
        testSlider = new Slider(0, 6000, 0);
        testSlider.setShowTickMarks(true);
        testSlider.setShowTickLabels(true);
        testSlider.setMajorTickUnit(1000);
        testSlider.setMinorTickCount(4);
        testSlider.setPrefWidth(400);

        Label sliderLabel = new Label("Luminozitate (Mod Test):");

        testModeCheckBox = new CheckBox("Mod Test (simulare luminozitate)");
        testModeCheckBox.setSelected(false);

        // Când sliderul se mișcă și Mod Test e activ -> actualizăm scena,
        // iar valoarea va fi preluată de timeline-ul de 1s
        testSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (testModeCheckBox != null && testModeCheckBox.isSelected()) {
                double lux = newVal.doubleValue();
                handleNewLuxValue((float) lux, true);
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

    private void setupSerialCommunication() {
        serialComm = new SerialComm("COM3", 9600);
        if (!serialComm.openPort()) {
            System.out.println("Port indisponibil.");
            return;
        }

        serialComm.addDataListener(data -> Platform.runLater(() -> {
            try {
                float lux = Float.parseFloat(data.trim());

                // Dacă NU suntem în Mod Test, folosim senzorul real
                if (testModeCheckBox == null || !testModeCheckBox.isSelected()) {
                    handleNewLuxValue(lux, false);
                }
            } catch (NumberFormatException ignored) {
            }
        }));
    }

    // Unificăm tratarea valorilor de lux (din senzor sau slider)
    // -> actualizăm scena + luxul curent; graficul este alimentat separat, la 1s
    private void handleNewLuxValue(float lux, boolean simulated) {
        lux = (float) Math.max(0, Math.min(lux, 6000));
        currentLux = lux;       // memorăm ultima valoare
        updateScene(lux);       // actualizăm animația imediat
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
        lux = (float) Math.max(0, Math.min(lux, 6000));
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

        animationPane.setStyle("-fx-background-color: rgb(" +
                (int) (backgroundColor.getRed() * 255) + "," +
                (int) (backgroundColor.getGreen() * 255) + "," +
                (int) (backgroundColor.getBlue() * 255) + ");");

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

        cloud1.setOpacity(cloudVisibility);
        cloud2.setOpacity(cloudVisibility);
    }

    // === ISTORIC + DETECTARE SCHIMBĂRI BRUȘTE ===

    // Acum este apelată o dată pe secundă din sampleTimeline
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
        if (luxHistory.isEmpty()) {
            System.out.println("Nu există date de exportat.");
            return;
        }

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

            double min = getMin();
            double max = getMax();
            double avg = getAverage();

            Document document = new Document();
            PdfWriter.getInstance(document, new FileOutputStream(pdfFile));
            document.open();

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

            for (int i = 0; i < luxHistory.size(); i++) {
                int index = i + 1;
                double value = luxHistory.get(i);

                table.addCell(String.valueOf(index));

                String text = String.format(Locale.US, "%.2f", value);
                if (suddenChangeIndices.contains(index)) {
                    text += " *"; // * = schimbare brusca
                }
                table.addCell(text);
            }

            document.add(table);

            if (!suddenChangeIndices.isEmpty()) {
                document.add(new Paragraph(" "));
                document.add(new Paragraph(
                        "* Valorile marcate indica mostre unde a fost detectata o schimbare brusca a luminii."
                ));
            }

            document.close();
            tempPng.delete();

            System.out.println("PDF generat: " + pdfFile.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
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
