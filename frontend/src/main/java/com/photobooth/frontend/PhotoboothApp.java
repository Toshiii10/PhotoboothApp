package com.photobooth.frontend;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.photobooth.frontend.BackendClient.CustomFrameItem;
import com.photobooth.frontend.BackendClient.LayoutItem;
import com.photobooth.frontend.BackendClient.PhotoItem;
import com.photobooth.frontend.BackendClient.ThemeItem;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * Studio-grade JavaFX Photobooth frontend with Built-in Themes, Custom PNG Frame Uploads,
 * and a Database-backed Photo Gallery.
 */
public class PhotoboothApp extends Application {

    private final BackendClient backend = new BackendClient(
            System.getProperty("backend.url", "http://localhost:8080"));
    private final CameraService camera = new CameraService();

    // Navigation views
    private final StackPane contentArea = new StackPane();
    private final Button navBoothBtn = new Button("📸  Booth Studio");
    private final Button navFramesBtn = new Button("🖼  Custom Frames");
    private final Button navGalleryBtn = new Button("🗂  Photo Gallery");

    // Status & header
    private final Label connectionBadge = new Label("Connecting...");
    private final Label statusLabel = new Label("Initializing Photobooth Studio...");

    // Booth controls
    private final ToggleButton modeBuiltinBtn = new ToggleButton("Built-in Themes");
    private final ToggleButton modeCustomBtn = new ToggleButton("Custom PNG Frame");
    private final VBox builtinOptionsPane = new VBox(12);
    private final VBox customOptionsPane = new VBox(12);

    private final FlowPane layoutTiles = new FlowPane(8, 8);
    private final FlowPane themeTiles = new FlowPane(8, 8);
    private final TextField captionField = new TextField("Photobooth");
    private final ComboBox<CustomFrameItem> customFrameCombo = new ComboBox<>();
    private final ComboBox<CameraService.CameraDevice> cameraDeviceCombo = new ComboBox<>();
    private final ComboBox<CameraService.Filter> filterCombo = new ComboBox<>();
    private final ComboBox<Integer> countdownCombo = new ComboBox<>();
    private final CheckBox mirrorCheck = new CheckBox("Mirror Camera");
    private final Label cameraStatusLabel = new Label("Connecting to camera...");
    private boolean updatingCameraList = false;

    private final ImageView liveView = new ImageView();
    private final Label countdownOverlay = new Label();
    private final Label shotProgressOverlay = new Label();
    private final HBox shotThumbnails = new HBox(10);
    private final Button startCaptureBtn = new Button("📸  Start Photobooth");
    private final ImageView resultView = new ImageView();
    private final Button saveResultBtn = new Button("💾  Export PNG");

    // Custom Frames view
    private final FlowPane customFramesGrid = new FlowPane(16, 16);

    // Gallery view
    private final FlowPane galleryGrid = new FlowPane(16, 16);
    private final ImageView galleryInspectView = new ImageView();
    private final Label galleryInspectMeta = new Label("Select a photo to preview");
    private PhotoItem selectedGalleryPhoto;
    private byte[] selectedGalleryBytes;

    // State
    private List<LayoutItem> layouts = new ArrayList<>();
    private List<ThemeItem> themes = new ArrayList<>();
    private List<CustomFrameItem> customFrames = new ArrayList<>();
    private LayoutItem selectedLayout;
    private ThemeItem selectedTheme;
    private volatile boolean capturing = false;
    private byte[] lastResultBytes;
    private final ScheduledExecutorService cameraWorker = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "camera-worker");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean previewPending = new AtomicBoolean();
    private volatile boolean stopping;
    private Stage primaryStage;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;

        BorderPane root = new BorderPane();
        root.setTop(buildHeader());
        root.setCenter(contentArea);
        root.setBottom(buildFooter());

        Pane boothPane = buildBoothView();
        Pane framesPane = buildCustomFramesView();
        Pane galleryPane = buildGalleryView();

        contentArea.getChildren().addAll(boothPane, framesPane, galleryPane);
        showView(boothPane, navBoothBtn);

        navBoothBtn.setOnAction(e -> showView(boothPane, navBoothBtn));
        navFramesBtn.setOnAction(e -> {
            showView(framesPane, navFramesBtn);
            refreshCustomFrames();
        });
        navGalleryBtn.setOnAction(e -> {
            showView(galleryPane, navGalleryBtn);
            refreshGallery();
        });

        Scene scene = new Scene(root, 1340, 820);
        try {
            String css = Objects.requireNonNull(getClass().getResource("/style.css")).toExternalForm();
            scene.getStylesheets().add(css);
        } catch (Exception ignored) {}

        stage.setTitle("Photobooth Studio — Multi-Frame & Custom Frame Edition");
        stage.setScene(scene);
        stage.show();

        startCameraLoop();
        connectToBackendWithRetry();
    }

    // ========================================================================= Header & Footer

    private HBox buildHeader() {
        Label title = new Label("✨ PHOTOBOOTH STUDIO");
        title.getStyleClass().add("app-title");
        Label subtitle = new Label("Multi-Frame Collages | Custom PNG Overlays ");
        subtitle.getStyleClass().add("app-subtitle");
        VBox titleBox = new VBox(2, title, subtitle);

        HBox navBox = new HBox(8, navBoothBtn, navFramesBtn, navGalleryBtn);
        navBox.setAlignment(Pos.CENTER);

        Region spacer1 = new Region();
        Region spacer2 = new Region();
        HBox.setHgrow(spacer1, Priority.ALWAYS);
        HBox.setHgrow(spacer2, Priority.ALWAYS);

        connectionBadge.getStyleClass().add("status-badge-offline");
        Button reconnectBtn = new Button("↻");
        reconnectBtn.getStyleClass().add("secondary-btn");
        reconnectBtn.setTooltip(new Tooltip("Reload layouts, frames & gallery from backend"));
        reconnectBtn.setOnAction(e -> loadAllBackendData());

        HBox rightBox = new HBox(10, connectionBadge, reconnectBtn);
        rightBox.setAlignment(Pos.CENTER_RIGHT);

        HBox header = new HBox(20, titleBox, spacer1, navBox, spacer2, rightBox);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("header-bar");
        return header;
    }

    private HBox buildFooter() {
        statusLabel.getStyleClass().add("muted-label");
        HBox footer = new HBox(statusLabel);
        footer.getStyleClass().add("footer-bar");
        return footer;
    }

    private void showView(Pane target, Button activeBtn) {
        for (var node : contentArea.getChildren()) {
            node.setVisible(node == target);
            node.setManaged(node == target);
        }
        for (Button b : List.of(navBoothBtn, navFramesBtn, navGalleryBtn)) {
            b.getStyleClass().removeAll("nav-tab-btn", "nav-tab-btn-active");
            b.getStyleClass().add(b == activeBtn ? "nav-tab-btn-active" : "nav-tab-btn");
        }
    }

    // ========================================================================= Tab 1: Booth Studio

    private Pane buildBoothView() {
        // Left sidebar: Frame Mode + Layout/Theme or Custom Frame + Camera Settings
        ToggleGroup modeGroup = new ToggleGroup();
        modeBuiltinBtn.setToggleGroup(modeGroup);
        modeCustomBtn.setToggleGroup(modeGroup);
        modeBuiltinBtn.setSelected(true);
        modeBuiltinBtn.getStyleClass().add("secondary-btn");
        modeCustomBtn.getStyleClass().add("secondary-btn");
        modeBuiltinBtn.setMaxWidth(Double.MAX_VALUE);
        modeCustomBtn.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(modeBuiltinBtn, Priority.ALWAYS);
        HBox.setHgrow(modeCustomBtn, Priority.ALWAYS);
        HBox modeSwitch = new HBox(8, modeBuiltinBtn, modeCustomBtn);

        modeGroup.selectedToggleProperty().addListener((obs, o, n) -> {
            if (n == null) {
                modeBuiltinBtn.setSelected(true);
                return;
            }
            boolean builtin = n == modeBuiltinBtn;
            builtinOptionsPane.setVisible(builtin);
            builtinOptionsPane.setManaged(builtin);
            customOptionsPane.setVisible(!builtin);
            customOptionsPane.setManaged(!builtin);
        });

        captionField.setPromptText("Footer caption (e.g. Sarah's 21st)");
        builtinOptionsPane.getChildren().addAll(
                sectionLabel("1. Choose Multi-Frame Layout"),
                layoutTiles,
                sectionLabel("2. Choose Frame Theme"),
                themeTiles,
                sectionLabel("3. Footer Caption"),
                captionField
        );

        customFrameCombo.setMaxWidth(Double.MAX_VALUE);
        Button jumpUploadBtn = new Button("＋ Upload New PNG Frame...");
        jumpUploadBtn.getStyleClass().add("secondary-btn");
        jumpUploadBtn.setMaxWidth(Double.MAX_VALUE);
        jumpUploadBtn.setOnAction(e -> navFramesBtn.fire());

        Label customHint = new Label("Custom PNG frames automatically place your webcam shots inside the transparent windows of the frame.");
        customHint.setWrapText(true);
        customHint.getStyleClass().add("muted-label");

        customOptionsPane.getChildren().addAll(
                sectionLabel("Select Uploaded Custom Frame"),
                customFrameCombo,
                customHint,
                jumpUploadBtn
        );
        customOptionsPane.setVisible(false);
        customOptionsPane.setManaged(false);

        // Camera device & settings
        cameraDeviceCombo.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(cameraDeviceCombo, Priority.ALWAYS);
        cameraDeviceCombo.setOnAction(e -> {
            if (!updatingCameraList && cameraDeviceCombo.getValue() != null) {
                switchCameraDevice(cameraDeviceCombo.getValue());
            }
        });

        Button refreshCamsBtn = new Button("↻");
        refreshCamsBtn.getStyleClass().add("secondary-btn");
        refreshCamsBtn.setTooltip(new Tooltip("Rescan connected camera devices"));
        refreshCamsBtn.setOnAction(e -> refreshCameraDevices());
        HBox cameraSelectRow = new HBox(8, cameraDeviceCombo, refreshCamsBtn);
        cameraSelectRow.setAlignment(Pos.CENTER_LEFT);
        cameraStatusLabel.setWrapText(true);
        cameraStatusLabel.setMaxWidth(300);
        cameraStatusLabel.getStyleClass().add("muted-label");

        filterCombo.getItems().setAll(CameraService.Filter.values());
        filterCombo.getSelectionModel().select(CameraService.Filter.NONE);
        filterCombo.setOnAction(e -> camera.setFilter(filterCombo.getValue()));
        filterCombo.setMaxWidth(Double.MAX_VALUE);

        countdownCombo.getItems().setAll(1, 2, 3, 5);
        countdownCombo.getSelectionModel().select(Integer.valueOf(3));

        mirrorCheck.setSelected(true);
        mirrorCheck.setTextFill(Color.WHITE);
        mirrorCheck.setOnAction(e -> camera.setMirror(mirrorCheck.isSelected()));

        HBox camRow = new HBox(12, new Label("Timer (s):"), countdownCombo, mirrorCheck);
        camRow.setAlignment(Pos.CENTER_LEFT);

        VBox leftSidebar = new VBox(12,
                sectionLabel("Frame Mode"),
                modeSwitch,
                builtinOptionsPane,
                customOptionsPane,
                new Separator(),
                sectionLabel("Camera Device"),
                cameraSelectRow,
                cameraStatusLabel,
                sectionLabel("Camera Filter & Timer"),
                filterCombo,
                camRow
        );
        leftSidebar.getStyleClass().add("card-pane");
        leftSidebar.setPrefWidth(340);

        ScrollPane leftScroll = new ScrollPane(leftSidebar);
        leftScroll.setFitToWidth(true);
        leftScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        leftScroll.setPrefWidth(355);

        // Center: Live camera + overlays + thumbnails + Start button
        liveView.setFitWidth(580);
        liveView.setFitHeight(435);
        liveView.setPreserveRatio(true);

        countdownOverlay.setFont(Font.font("Segoe UI", FontWeight.EXTRA_BOLD, 130));
        countdownOverlay.setTextFill(Color.WHITE);
        countdownOverlay.setEffect(new DropShadow(25, Color.BLACK));

        shotProgressOverlay.setStyle("-fx-background-color: rgba(15,17,26,0.75); -fx-text-fill: #f8fafc; "
                + "-fx-padding: 6 14; -fx-background-radius: 16; -fx-font-weight: bold;");
        shotProgressOverlay.setVisible(false);
        StackPane.setAlignment(shotProgressOverlay, Pos.TOP_LEFT);
        StackPane.setMargin(shotProgressOverlay, new Insets(14));

        StackPane cameraStage = new StackPane(liveView, shotProgressOverlay, countdownOverlay);
        cameraStage.setPrefSize(590, 445);
        cameraStage.setStyle("-fx-background-color: #090a10; -fx-background-radius: 14; -fx-border-color: #2b314d; -fx-border-radius: 14;");

        shotThumbnails.setAlignment(Pos.CENTER);
        shotThumbnails.setPrefHeight(88);
        shotThumbnails.setStyle("-fx-background-color: #121521; -fx-background-radius: 10; -fx-padding: 8;");

        startCaptureBtn.getStyleClass().add("primary-btn");
        startCaptureBtn.setPrefWidth(280);
        startCaptureBtn.setOnAction(e -> startCaptureSession());

        VBox centerStage = new VBox(12, cameraStage, shotThumbnails, startCaptureBtn);
        centerStage.setAlignment(Pos.TOP_CENTER);
        centerStage.getStyleClass().add("card-pane");
        HBox.setHgrow(centerStage, Priority.ALWAYS);

        // Right: Composed Multi-Frame Strip Preview
        resultView.setPreserveRatio(true);
        resultView.setFitWidth(310);
        resultView.setFitHeight(560);
        resultView.setEffect(new DropShadow(18, Color.rgb(0, 0, 0, 0.65)));

        StackPane resultHolder = new StackPane(resultView);
        VBox.setVgrow(resultHolder, Priority.ALWAYS);

        saveResultBtn.getStyleClass().add("secondary-btn");
        saveResultBtn.setMaxWidth(Double.MAX_VALUE);
        saveResultBtn.setDisable(true);
        saveResultBtn.setOnAction(e -> exportBytes(lastResultBytes, "photobooth-strip.png"));

        VBox rightPane = new VBox(12, sectionLabel("Final Composed Photo"), resultHolder, saveResultBtn);
        rightPane.setAlignment(Pos.TOP_CENTER);
        rightPane.getStyleClass().add("card-pane");
        rightPane.setPrefWidth(350);

        HBox mainRow = new HBox(16, leftScroll, centerStage, rightPane);
        mainRow.setPadding(new Insets(16));
        return mainRow;
    }

    // ========================================================================= Tab 2: Custom Frames Studio

    private Pane buildCustomFramesView() {
        Label title = new Label("Custom PNG Frame Overlays");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));

        Label explain = new Label("Upload any PNG image that has transparent cutouts (alpha < 40). "
                + "The backend automatically detects each transparent window, counts how many shots are required, "
                + "and composites your webcam photos underneath the frame artwork!");
        explain.setWrapText(true);
        explain.getStyleClass().add("muted-label");

        Button uploadBtn = new Button("＋ Upload Custom PNG Frame...");
        uploadBtn.getStyleClass().add("primary-btn");
        uploadBtn.setOnAction(e -> promptUploadCustomFrame());

        HBox topBar = new HBox(20, new VBox(4, title, explain), uploadBtn);
        topBar.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(topBar.getChildren().get(0), Priority.ALWAYS);
        topBar.getStyleClass().add("card-pane");

        customFramesGrid.setPadding(new Insets(10));
        ScrollPane gridScroll = new ScrollPane(customFramesGrid);
        gridScroll.setFitToWidth(true);
        VBox.setVgrow(gridScroll, Priority.ALWAYS);

        VBox root = new VBox(16, topBar, gridScroll);
        root.setPadding(new Insets(16));
        return root;
    }

    // ========================================================================= Tab 3: Database Gallery

    private Pane buildGalleryView() {
        Label title = new Label("Saved Photobooth Gallery (Database)");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));

        Button refreshBtn = new Button("↻ Refresh Gallery");
        refreshBtn.getStyleClass().add("secondary-btn");
        refreshBtn.setOnAction(e -> refreshGallery());

        HBox header = new HBox(16, title, refreshBtn);
        header.setAlignment(Pos.CENTER_LEFT);

        galleryGrid.setPadding(new Insets(8));
        ScrollPane scroll = new ScrollPane(galleryGrid);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox leftCol = new VBox(12, header, scroll);
        HBox.setHgrow(leftCol, Priority.ALWAYS);

        galleryInspectView.setPreserveRatio(true);
        galleryInspectView.setFitWidth(360);
        galleryInspectView.setFitHeight(540);
        galleryInspectView.setEffect(new DropShadow(16, Color.BLACK));

        galleryInspectMeta.setWrapText(true);
        galleryInspectMeta.getStyleClass().add("muted-label");

        Button exportBtn = new Button("💾 Save Copy...");
        exportBtn.getStyleClass().add("secondary-btn");
        exportBtn.setMaxWidth(Double.MAX_VALUE);
        exportBtn.setOnAction(e -> {
            if (selectedGalleryBytes != null && selectedGalleryPhoto != null) {
                exportBytes(selectedGalleryBytes, "photobooth-" + selectedGalleryPhoto.id() + ".png");
            }
        });

        Button deleteBtn = new Button("🗑 Delete from Database");
        deleteBtn.getStyleClass().add("danger-btn");
        deleteBtn.setMaxWidth(Double.MAX_VALUE);
        deleteBtn.setOnAction(e -> {
            if (selectedGalleryPhoto != null) {
                deleteGalleryPhoto(selectedGalleryPhoto.id());
            }
        });

        VBox inspector = new VBox(12,
                sectionLabel("Selected Photo"),
                new StackPane(galleryInspectView),
                galleryInspectMeta,
                exportBtn,
                deleteBtn
        );
        inspector.setAlignment(Pos.TOP_CENTER);
        inspector.getStyleClass().add("card-pane");
        inspector.setPrefWidth(400);

        HBox root = new HBox(16, leftCol, inspector);
        root.setPadding(new Insets(16));
        return root;
    }

    // ========================================================================= Backend Data Loading

    private void connectToBackendWithRetry() {
        Thread t = new Thread(() -> {
            for (int attempt = 1; attempt <= 25; attempt++) {
                if (backend.isHealthy()) {
                    loadAllBackendData();
                    return;
                }
                final int a = attempt;
                Platform.runLater(() -> setStatus("Waiting for Spring Boot backend to start... (attempt " + a + "/25)"));
                try { Thread.sleep(1000); } catch (InterruptedException ignored) { return; }
            }
            Platform.runLater(() -> {
                connectionBadge.setText("● Offline");
                connectionBadge.getStyleClass().setAll("status-badge-offline");
                setStatus("Could not reach backend at " + backend.getBaseUrl() + ". Start the backend or use run-photobooth.cmd.");
            });
        }, "backend-connector");
        t.setDaemon(true);
        t.start();
    }

    private void loadAllBackendData() {
        Thread t = new Thread(() -> {
            try {
                List<LayoutItem> l = backend.getLayouts();
                List<ThemeItem> th = backend.getThemes();
                List<CustomFrameItem> cf = backend.getCustomFrames();
                Platform.runLater(() -> {
                    this.layouts = l;
                    this.themes = th;
                    this.customFrames = cf;
                    connectionBadge.setText("● Backend Online");
                    connectionBadge.getStyleClass().setAll("status-badge-online");
                    rebuildLayoutTiles();
                    rebuildThemeTiles();
                    updateCustomFrameCombo();
                    setStatus("Connected to Spring Boot backend & H2 Database (" + cf.size() + " custom frames available).");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    connectionBadge.setText("● Offline");
                    connectionBadge.getStyleClass().setAll("status-badge-offline");
                    setStatus("Backend error: " + e.getMessage());
                });
            }
        }, "data-loader");
        t.setDaemon(true);
        t.start();
    }

    private void rebuildLayoutTiles() {
        layoutTiles.getChildren().clear();
        if (selectedLayout == null && !layouts.isEmpty()) {
            selectedLayout = layouts.get(Math.min(3, layouts.size() - 1)); // default Classic Strip (4)
        }
        for (LayoutItem item : layouts) {
            VBox tile = new VBox(5);
            tile.setAlignment(Pos.CENTER);
            tile.setPrefSize(92, 74);
            tile.getStyleClass().add(item.equals(selectedLayout) ? "choice-tile-selected" : "choice-tile");

            Canvas icon = drawLayoutMiniIcon(item.columns(), item.rows());
            Label name = new Label(item.displayName());
            name.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");
            Label count = new Label(item.frameCount() + (item.frameCount() == 1 ? " shot" : " shots"));
            count.getStyleClass().add("muted-label");

            tile.getChildren().addAll(icon, name, count);
            tile.setOnMouseClicked(e -> {
                selectedLayout = item;
                rebuildLayoutTiles();
            });
            layoutTiles.getChildren().add(tile);
        }
    }

    private Canvas drawLayoutMiniIcon(int cols, int rows) {
        Canvas c = new Canvas(36, 26);
        GraphicsContext g = c.getGraphicsContext2D();
        g.setFill(Color.web("#a5b4fc"));
        double pad = 2;
        double cellW = (36 - pad * (cols + 1)) / cols;
        double cellH = (26 - pad * (rows + 1)) / rows;
        for (int r = 0; r < rows; r++) {
            for (int col = 0; col < cols; col++) {
                g.fillRoundRect(pad + col * (cellW + pad), pad + r * (cellH + pad), cellW, cellH, 3, 3);
            }
        }
        return c;
    }

    private void rebuildThemeTiles() {
        themeTiles.getChildren().clear();
        if (selectedTheme == null && !themes.isEmpty()) {
            selectedTheme = themes.get(0);
        }
        for (ThemeItem item : themes) {
            VBox tile = new VBox(5);
            tile.setAlignment(Pos.CENTER);
            tile.setPrefSize(92, 60);
            tile.getStyleClass().add(item.equals(selectedTheme) ? "choice-tile-selected" : "choice-tile");

            Region swatch = new Region();
            swatch.setPrefSize(56, 20);
            swatch.setBackground(new Background(new BackgroundFill(
                    new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                            new Stop(0, Color.web(item.bgTop())),
                            new Stop(1, Color.web(item.bgBottom()))),
                    new CornerRadii(5), Insets.EMPTY)));
            swatch.setStyle("-fx-border-color: " + item.accent() + "; -fx-border-radius: 5; -fx-border-width: 1.5;");

            Label name = new Label(item.displayName());
            name.setStyle("-fx-font-size: 11px;");

            tile.getChildren().addAll(swatch, name);
            tile.setOnMouseClicked(e -> {
                selectedTheme = item;
                rebuildThemeTiles();
            });
            themeTiles.getChildren().add(tile);
        }
    }

    private void updateCustomFrameCombo() {
        CustomFrameItem prev = customFrameCombo.getValue();
        customFrameCombo.getItems().setAll(customFrames);
        if (prev != null && customFrames.contains(prev)) {
            customFrameCombo.setValue(prev);
        } else if (!customFrames.isEmpty()) {
            customFrameCombo.getSelectionModel().selectFirst();
        }
    }

    // ========================================================================= Camera & Capture Session

    private void startCameraLoop() {
        cameraWorker.execute(() -> {
            List<CameraService.CameraDevice> devices = camera.listDevices();
            boolean ok = camera.openDefault();
            String activeId = camera.getActiveDeviceId();
            Platform.runLater(() -> {
                if (stopping) return;
                populateCameraCombo(devices, activeId);
                if (!ok) {
                    setStatus(camera.getLastError());
                } else {
                    setStatus("Active camera: " + activeId);
                }
            });
        });
        cameraWorker.scheduleWithFixedDelay(() -> {
            if (stopping || !previewPending.compareAndSet(false, true)) return;
            try {
                BufferedImage frame = camera.grab();
                String error = camera.getLastError();
                String activeId = camera.getActiveDeviceId();
                Platform.runLater(() -> {
                    try {
                        if (stopping) return;
                        liveView.setImage(SwingFXUtils.toFXImage(frame, null));
                        CameraService.CameraDevice selected = cameraDeviceCombo.getValue();
                        if (selected != null && !selected.id().equals(activeId)) {
                            populateCameraCombo(cameraDeviceCombo.getItems(), activeId);
                        }
                        cameraStatusLabel.setText(error.isBlank()
                                ? "Active camera: " + activeId
                                : error + " Using Studio Demo Camera.");
                    } finally {
                        previewPending.set(false);
                    }
                });
            } catch (Exception | LinkageError error) {
                previewPending.set(false);
                error.printStackTrace();
                Platform.runLater(() -> cameraStatusLabel.setText("Camera preview failed: " + error.getMessage()));
            }
        }, 0, 33, TimeUnit.MILLISECONDS);
    }

    private void refreshCameraDevices() {
        setStatus("Scanning connected camera devices...");
        cameraWorker.execute(() -> {
            List<CameraService.CameraDevice> devices = camera.listDevices();
            String activeId = camera.getActiveDeviceId();
            Platform.runLater(() -> {
                populateCameraCombo(devices, activeId);
                setStatus("Found " + (devices.size() - 1) + " camera device(s).");
            });
        });
    }

    private void switchCameraDevice(CameraService.CameraDevice target) {
        setStatus("Switching camera to " + target.displayName() + "...");
        cameraDeviceCombo.setDisable(true);
        cameraWorker.execute(() -> {
            boolean ok = camera.selectDevice(target);
            String activeId = camera.getActiveDeviceId();
            Platform.runLater(() -> {
                cameraDeviceCombo.setDisable(false);
                if (ok) {
                    setStatus("Switched camera to: " + target.displayName());
                } else {
                    setStatus(camera.getLastError());
                    populateCameraCombo(cameraDeviceCombo.getItems(), activeId);
                }
            });
        });
    }

    private void populateCameraCombo(List<CameraService.CameraDevice> devices, String activeId) {
        updatingCameraList = true;
        try {
            cameraDeviceCombo.getItems().setAll(new ArrayList<>(devices));
            for (CameraService.CameraDevice d : devices) {
                if (d.id().equals(activeId)) {
                    cameraDeviceCombo.setValue(d);
                    return;
                }
            }
            if (!devices.isEmpty()) {
                cameraDeviceCombo.getSelectionModel().selectFirst();
            }
        } finally {
            updatingCameraList = false;
        }
    }

    private void startCaptureSession() {
        if (capturing) return;

        boolean useCustom = modeCustomBtn.isSelected();
        CustomFrameItem customFrame = customFrameCombo.getValue();
        LayoutItem layout = selectedLayout;
        ThemeItem theme = selectedTheme;

        if (useCustom && customFrame == null) {
            setStatus("Please select or upload a Custom PNG Frame first.");
            return;
        }
        if (!useCustom && layout == null) {
            setStatus("Backend layouts not loaded yet.");
            return;
        }

        int totalShots = useCustom ? customFrame.slotCount() : layout.frameCount();
        int timerSec = countdownCombo.getValue() == null ? 3 : countdownCombo.getValue();
        String caption = captionField.getText();

        capturing = true;
        startCaptureBtn.setDisable(true);
        saveResultBtn.setDisable(true);
        shotThumbnails.getChildren().clear();
        shotProgressOverlay.setVisible(true);

        Thread sessionThread = new Thread(() -> {
            List<BufferedImage> shots = new ArrayList<>();
            try {
                for (int i = 1; i <= totalShots; i++) {
                    final int shotNum = i;
                    for (int sec = timerSec; sec > 0; sec--) {
                        final int s = sec;
                        Platform.runLater(() -> {
                            shotProgressOverlay.setText("Shot " + shotNum + " of " + totalShots);
                            countdownOverlay.setText(String.valueOf(s));
                            setStatus("Get ready! Taking shot " + shotNum + " of " + totalShots + " in " + s + "...");
                        });
                        Thread.sleep(1000);
                    }

                    BufferedImage captured = camera.grab();
                    shots.add(captured);

                    Platform.runLater(() -> {
                        countdownOverlay.setText("📸");
                        triggerFlashEffect();
                        ImageView thumb = new ImageView(SwingFXUtils.toFXImage(captured, null));
                        thumb.setFitHeight(72);
                        thumb.setPreserveRatio(true);
                        shotThumbnails.getChildren().add(thumb);
                    });
                    Thread.sleep(550);
                }

                Platform.runLater(() -> {
                    countdownOverlay.setText("");
                    shotProgressOverlay.setVisible(false);
                    setStatus("Compositing high-resolution multi-frame strip on backend...");
                });

                PhotoItem saved = useCustom
                        ? backend.composeCustom(customFrame.id(), shots)
                        : backend.composeBuiltin(layout.id(), theme != null ? theme.id() : "CLASSIC", caption, shots);

                byte[] pngBytes = backend.getPhotoImage(saved.id());
                lastResultBytes = pngBytes;
                Image finalImg = new Image(new ByteArrayInputStream(pngBytes));

                Platform.runLater(() -> {
                    resultView.setImage(finalImg);
                    saveResultBtn.setDisable(false);
                    setStatus("Saved Photo #" + saved.id() + " (" + saved.frameName() + ") to database!");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> setStatus("Session failed: " + ex.getMessage()));
            } finally {
                capturing = false;
                Platform.runLater(() -> {
                    countdownOverlay.setText("");
                    shotProgressOverlay.setVisible(false);
                    startCaptureBtn.setDisable(false);
                });
            }
        }, "capture-session");
        sessionThread.setDaemon(true);
        sessionThread.start();
    }

    private void triggerFlashEffect() {
        liveView.setOpacity(0.15);
        Thread t = new Thread(() -> {
            try { Thread.sleep(140); } catch (InterruptedException ignored) {}
            Platform.runLater(() -> liveView.setOpacity(1.0));
        });
        t.setDaemon(true);
        t.start();
    }

    // ========================================================================= Custom Frame Management

    private void promptUploadCustomFrame() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select Transparent PNG Frame Overlay");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG Image with Transparency", "*.png"));
        File file = fc.showOpenDialog(primaryStage);
        if (file == null) return;

        TextInputDialog nameDialog = new TextInputDialog(file.getName().replaceFirst("\\.png$", ""));
        nameDialog.setTitle("Name Your Custom Frame");
        nameDialog.setHeaderText("Upload Custom PNG Frame Overlay");
        nameDialog.setContentText("Frame Name:");
        nameDialog.showAndWait().ifPresent(name -> {
            setStatus("Uploading and analyzing transparent photo slots...");
            Thread t = new Thread(() -> {
                try {
                    CustomFrameItem created = backend.uploadCustomFrame(name, file);
                    Platform.runLater(() -> {
                        setStatus("Uploaded '" + created.name() + "' — detected " + created.slotCount() + " photo slots!");
                        refreshCustomFrames();
                        loadAllBackendData();
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        setStatus("Upload error: " + ex.getMessage());
                        Alert alert = new Alert(Alert.AlertType.ERROR, ex.getMessage(), ButtonType.OK);
                        alert.setHeaderText("Could Not Create Custom Frame");
                        alert.showAndWait();
                    });
                }
            }, "frame-uploader");
            t.setDaemon(true);
            t.start();
        });
    }

    private void refreshCustomFrames() {
        Thread t = new Thread(() -> {
            try {
                List<CustomFrameItem> list = backend.getCustomFrames();
                Platform.runLater(() -> {
                    this.customFrames = list;
                    updateCustomFrameCombo();
                    customFramesGrid.getChildren().clear();
                    for (CustomFrameItem item : list) {
                        customFramesGrid.getChildren().add(createCustomFrameCard(item));
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> setStatus("Failed to load custom frames: " + e.getMessage()));
            }
        }, "frames-loader");
        t.setDaemon(true);
        t.start();
    }

    private VBox createCustomFrameCard(CustomFrameItem item) {
        ImageView preview = new ImageView();
        preview.setFitWidth(180);
        preview.setFitHeight(220);
        preview.setPreserveRatio(true);

        Label title = new Label(item.name());
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        Label meta = new Label(item.slotCount() + " photo windows • " + item.width() + "×" + item.height());
        meta.getStyleClass().add("muted-label");

        Button useBtn = new Button("Use in Booth");
        useBtn.getStyleClass().add("secondary-btn");
        useBtn.setOnAction(e -> {
            modeCustomBtn.setSelected(true);
            customFrameCombo.setValue(item);
            navBoothBtn.fire();
        });

        Button delBtn = new Button("🗑");
        delBtn.getStyleClass().add("danger-btn");
        delBtn.setOnAction(e -> {
            Thread dt = new Thread(() -> {
                try {
                    backend.deleteCustomFrame(item.id());
                    Platform.runLater(this::refreshCustomFrames);
                } catch (Exception ignored) {}
            });
            dt.setDaemon(true);
            dt.start();
        });

        HBox actions = new HBox(8, useBtn, delBtn);
        actions.setAlignment(Pos.CENTER);

        VBox card = new VBox(8, new StackPane(preview), title, meta, actions);
        card.setAlignment(Pos.TOP_CENTER);
        card.setPrefWidth(220);
        card.getStyleClass().add("card-pane");

        // Load thumbnail async
        Thread imgThread = new Thread(() -> {
            try {
                byte[] bytes = backend.getCustomFrameImage(item.id());
                Image img = new Image(new ByteArrayInputStream(bytes), 200, 240, true, true);
                Platform.runLater(() -> preview.setImage(img));
            } catch (Exception ignored) {}
        });
        imgThread.setDaemon(true);
        imgThread.start();

        return card;
    }

    // ========================================================================= Gallery Management

    private void refreshGallery() {
        Thread t = new Thread(() -> {
            try {
                List<PhotoItem> list = backend.getPhotos();
                Platform.runLater(() -> {
                    galleryGrid.getChildren().clear();
                    if (list.isEmpty()) {
                        Label empty = new Label("No photos saved yet. Take your first strip in the Booth Studio!");
                        empty.getStyleClass().add("muted-label");
                        galleryGrid.getChildren().add(empty);
                        return;
                    }
                    for (PhotoItem p : list) {
                        galleryGrid.getChildren().add(createGalleryCard(p));
                    }
                    selectGalleryPhoto(list.get(0));
                });
            } catch (Exception e) {
                Platform.runLater(() -> setStatus("Could not load gallery: " + e.getMessage()));
            }
        }, "gallery-loader");
        t.setDaemon(true);
        t.start();
    }

    private VBox createGalleryCard(PhotoItem p) {
        ImageView thumb = new ImageView();
        thumb.setFitWidth(165);
        thumb.setFitHeight(210);
        thumb.setPreserveRatio(true);

        Label name = new Label("#" + p.id() + " • " + p.frameName());
        name.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        Label sub = new Label(p.shotCount() + " shots" + (p.caption().isBlank() ? "" : " • \"" + p.caption() + "\""));
        sub.getStyleClass().add("muted-label");

        VBox card = new VBox(6, new StackPane(thumb), name, sub);
        card.setAlignment(Pos.TOP_CENTER);
        card.setPrefWidth(190);
        card.getStyleClass().add("choice-tile");
        card.setOnMouseClicked(e -> selectGalleryPhoto(p));

        Thread loader = new Thread(() -> {
            try {
                byte[] bytes = backend.getPhotoThumbnail(p.id());
                Image img = new Image(new ByteArrayInputStream(bytes));
                Platform.runLater(() -> thumb.setImage(img));
            } catch (Exception ignored) {}
        });
        loader.setDaemon(true);
        loader.start();

        return card;
    }

    private void selectGalleryPhoto(PhotoItem p) {
        this.selectedGalleryPhoto = p;
        galleryInspectMeta.setText("Loading Photo #" + p.id() + "...");
        Thread t = new Thread(() -> {
            try {
                byte[] full = backend.getPhotoImage(p.id());
                Image img = new Image(new ByteArrayInputStream(full));
                Platform.runLater(() -> {
                    selectedGalleryBytes = full;
                    galleryInspectView.setImage(img);
                    galleryInspectMeta.setText(
                            "Photo #" + p.id() + "  |  " + p.frameName()
                                    + (p.themeName().isBlank() ? "" : " (" + p.themeName() + ")")
                                    + "\nResolution: " + p.width() + "×" + p.height() + " px  •  " + p.shotCount() + " shots"
                                    + (p.caption().isBlank() ? "" : "\nCaption: \"" + p.caption() + "\""));
                });
            } catch (Exception ignored) {}
        });
        t.setDaemon(true);
        t.start();
    }

    private void deleteGalleryPhoto(long id) {
        Thread t = new Thread(() -> {
            try {
                backend.deletePhoto(id);
                Platform.runLater(() -> {
                    selectedGalleryPhoto = null;
                    selectedGalleryBytes = null;
                    galleryInspectView.setImage(null);
                    galleryInspectMeta.setText("Photo deleted.");
                    refreshGallery();
                });
            } catch (Exception e) {
                Platform.runLater(() -> setStatus("Delete failed: " + e.getMessage()));
            }
        });
        t.setDaemon(true);
        t.start();
    }

    // ========================================================================= Helpers

    private void exportBytes(byte[] bytes, String defaultName) {
        if (bytes == null) return;
        FileChooser fc = new FileChooser();
        fc.setInitialFileName(defaultName);
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG Image", "*.png"));
        File out = fc.showSaveDialog(primaryStage);
        if (out == null) return;
        try {
            Files.write(out.toPath(), bytes);
            setStatus("Saved PNG to " + out.getAbsolutePath());
        } catch (Exception e) {
            setStatus("Failed to save file: " + e.getMessage());
        }
    }

    private Label sectionLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("section-title");
        return l;
    }

    private void setStatus(String msg) {
        statusLabel.setText(msg);
    }

    @Override
    public void stop() {
        stopping = true;
        cameraWorker.execute(camera::close);
        cameraWorker.shutdown();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
