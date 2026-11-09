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
import javafx.scene.Node;
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
import javafx.scene.control.TitledPane;
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
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.input.KeyCode;
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
    private final Button navBoothBtn = new Button("Studio");
    private final Button navFramesBtn = new Button("Frames");
    private final Button navGalleryBtn = new Button("Gallery");
    private final Label pageTitle = new Label("Make a moment worth keeping.");
    private final Label pageDescription = new Label("Pick your look, strike a pose, and let the good times develop.");
    private final Label sessionSummary = new Label("Choose a layout to get started");
    private final Label previewSummary = new Label("Your next favorite keepsake");
    private final Label captureHint = new Label("A little countdown. A lot of personality.");
    private final Canvas printPreview = new Canvas(180, 300);

    // Status & header
    private final Label connectionBadge = new Label("Connecting...");
    private final Label statusLabel = new Label("Initializing Photobooth Studio...");

    // Booth controls
    private final ToggleButton modeBuiltinBtn = new ToggleButton("Studio looks");
    private final ToggleButton modeCustomBtn = new ToggleButton("My frames");
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
    private final Button startCaptureBtn = new Button("Start session");
    private final ImageView resultView = new ImageView();
    private final Button saveResultBtn = new Button("Save your photo");

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

        stage.setTitle("Photobooth Studio");
        stage.setMinWidth(1040);
        stage.setMinHeight(720);
        stage.setScene(createScene());
        var available = javafx.stage.Screen.getPrimary().getVisualBounds();
        stage.setWidth(Math.min(1440, available.getWidth() - 40));
        stage.setHeight(Math.min(940, available.getHeight() - 40));
        stage.show();
        startCameraLoop();
        connectToBackendWithRetry();
    }

    Scene createScene() {

        BorderPane root = new BorderPane();
        root.setTop(new VBox(buildHeader(), buildPageIntro()));
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

        Scene scene = new Scene(root, 1440, 900);
        try {
            String css = Objects.requireNonNull(getClass().getResource("/style.css")).toExternalForm();
            scene.getStylesheets().add(css);
        } catch (Exception ignored) {}

        return scene;
    }

    // ========================================================================= Header & Footer

    private HBox buildHeader() {
        StackPane mark = new StackPane(icon("camera", 24));
        mark.getStyleClass().add("brand-mark");
        Label brand = styledLabel("photobooth", "brand-name");
        Label edition = styledLabel("STUDIO", "brand-edition");
        HBox brandText = new HBox(9, brand, edition);
        brandText.setAlignment(Pos.CENTER_LEFT);
        HBox identity = new HBox(12, mark, brandText);
        identity.setAlignment(Pos.CENTER_LEFT);

        navBoothBtn.setGraphic(icon("camera", 17));
        navFramesBtn.setGraphic(icon("frames", 17));
        navGalleryBtn.setGraphic(icon("grid", 17));
        HBox navBox = new HBox(4, navBoothBtn, navFramesBtn, navGalleryBtn);
        navBox.getStyleClass().add("nav-group");
        navBox.setAlignment(Pos.CENTER);

        connectionBadge.getStyleClass().add("status-badge-offline");
        Button reconnect = iconButton("refresh", "Reconnect to your photo library");
        reconnect.setOnAction(e -> loadAllBackendData());
        HBox right = new HBox(12, connectionBadge, reconnect);
        right.setAlignment(Pos.CENTER_RIGHT);
        Region leftSpace = new Region();
        Region rightSpace = new Region();
        HBox.setHgrow(leftSpace, Priority.ALWAYS);
        HBox.setHgrow(rightSpace, Priority.ALWAYS);
        HBox header = new HBox(20, identity, leftSpace, navBox, rightSpace, right);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("header-bar");
        return header;
    }

    private HBox buildPageIntro() {
        pageTitle.getStyleClass().add("page-title");
        pageDescription.getStyleClass().add("page-description");
        VBox text = new VBox(6, pageTitle, pageDescription);
        Region space = new Region();
        HBox.setHgrow(space, Priority.ALWAYS);
        Label note = styledLabel("GOOD TIMES, ON REPEAT.", "intro-note");
        HBox intro = new HBox(20, text, space, note);
        intro.setAlignment(Pos.CENTER_LEFT);
        intro.getStyleClass().add("page-intro");
        return intro;
    }

    private HBox buildFooter() {
        statusLabel.getStyleClass().add("footer-status");
        statusLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(statusLabel, Priority.ALWAYS);
        Label local = styledLabel("Made here. Saved here.", "footer-note");
        HBox footer = new HBox(14, statusLabel, local);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.getStyleClass().add("footer-bar");
        return footer;
    }

    private void showView(Pane target, Button activeBtn) {
        for (var node : contentArea.getChildren()) {
            node.setVisible(node == target);
            node.setManaged(node == target);
        }
        for (Button button : List.of(navBoothBtn, navFramesBtn, navGalleryBtn)) {
            button.getStyleClass().removeAll("nav-tab-btn", "nav-tab-btn-active");
            button.getStyleClass().add(button == activeBtn ? "nav-tab-btn-active" : "nav-tab-btn");
        }
        if (activeBtn == navBoothBtn) {
            pageTitle.setText("Make a moment worth keeping.");
            pageDescription.setText("Pick your look, strike a pose, and let the good times develop.");
        } else if (activeBtn == navFramesBtn) {
            pageTitle.setText("A frame for every occasion.");
            pageDescription.setText("Bring your own designs. Make every session feel a little more you.");
        } else {
            pageTitle.setText("The good times, collected.");
            pageDescription.setText("All your favorite moments, ready for another look.");
        }
    }

    private Pane buildBoothView() {
        ToggleGroup modes = new ToggleGroup();
        modeBuiltinBtn.setToggleGroup(modes);
        modeCustomBtn.setToggleGroup(modes);
        modeBuiltinBtn.setSelected(true);
        for (ToggleButton mode : List.of(modeBuiltinBtn, modeCustomBtn)) {
            mode.getStyleClass().add("mode-button");
            mode.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(mode, Priority.ALWAYS);
        }
        HBox modeSwitch = new HBox(3, modeBuiltinBtn, modeCustomBtn);
        modeSwitch.getStyleClass().add("mode-switch");
        modes.selectedToggleProperty().addListener((obs, old, selected) -> {
            if (selected == null) { modeBuiltinBtn.setSelected(true); return; }
            boolean builtin = selected == modeBuiltinBtn;
            builtinOptionsPane.setVisible(builtin);
            builtinOptionsPane.setManaged(builtin);
            customOptionsPane.setVisible(!builtin);
            customOptionsPane.setManaged(!builtin);
            updateSessionPreview();
        });

        layoutTiles.setHgap(7);
        layoutTiles.setVgap(7);
        themeTiles.setHgap(7);
        themeTiles.setVgap(7);
        captionField.setPromptText("A date, a name, a little memory...");
        captionField.textProperty().addListener((obs, old, value) -> updateSessionPreview());
        builtinOptionsPane.getChildren().addAll(sectionHeading("01", "Pick a layout"), layoutTiles,
                sectionHeading("02", "Set the mood"), themeTiles,
                sectionHeading("03", "Make it personal"), captionField);
        builtinOptionsPane.setSpacing(14);

        customFrameCombo.setMaxWidth(Double.MAX_VALUE);
        customFrameCombo.setPromptText("Choose a frame");
        customFrameCombo.setOnAction(e -> updateSessionPreview());
        Label frameHint = styledLabel("Your photos fit into the transparent spaces in your PNG design.", "muted-label");
        frameHint.setWrapText(true);
        Button upload = new Button("Add a frame", icon("plus", 16));
        upload.getStyleClass().add("secondary-btn");
        upload.setMaxWidth(Double.MAX_VALUE);
        upload.setOnAction(e -> navFramesBtn.fire());
        customOptionsPane.getChildren().addAll(sectionHeading("01", "Choose your frame"), customFrameCombo, frameHint, upload);
        customOptionsPane.setVisible(false);
        customOptionsPane.setManaged(false);

        cameraDeviceCombo.setMaxWidth(Double.MAX_VALUE);
        cameraDeviceCombo.setMinWidth(0);
        HBox.setHgrow(cameraDeviceCombo, Priority.ALWAYS);
        cameraDeviceCombo.setOnAction(e -> {
            if (!updatingCameraList && cameraDeviceCombo.getValue() != null) switchCameraDevice(cameraDeviceCombo.getValue());
        });
        Button scan = iconButton("refresh", "Find connected cameras");
        scan.setOnAction(e -> refreshCameraDevices());
        HBox source = new HBox(6, cameraDeviceCombo, scan);
        cameraStatusLabel.setWrapText(true);
        cameraStatusLabel.setMaxWidth(250);
        cameraStatusLabel.getStyleClass().add("muted-label");
        filterCombo.getItems().setAll(CameraService.Filter.values());
        filterCombo.getSelectionModel().select(CameraService.Filter.NONE);
        filterCombo.setOnAction(e -> camera.setFilter(filterCombo.getValue()));
        filterCombo.setMaxWidth(Double.MAX_VALUE);
        countdownCombo.getItems().setAll(1, 2, 3, 5);
        countdownCombo.getSelectionModel().select(Integer.valueOf(3));
        countdownCombo.setPrefWidth(70);
        countdownCombo.setOnAction(e -> updateSessionPreview());
        mirrorCheck.setSelected(true);
        mirrorCheck.setText("Mirror preview");
        mirrorCheck.setOnAction(e -> camera.setMirror(mirrorCheck.isSelected()));
        HBox timerRow = new HBox(10, styledLabel("Countdown", "field-label"), countdownCombo, styledLabel("seconds", "muted-label"));
        timerRow.setAlignment(Pos.CENTER_LEFT);
        VBox settings = new VBox(12, styledLabel("Camera source", "field-label"), source, cameraStatusLabel,
                styledLabel("Photo filter", "field-label"), filterCombo, timerRow, mirrorCheck);
        TitledPane cameraSettings = new TitledPane("Camera & countdown", settings);
        cameraSettings.setExpanded(false);
        cameraSettings.setAnimated(false);
        cameraSettings.getStyleClass().add("camera-settings");

        VBox customization = new VBox(20, styledLabel("YOUR SESSION", "eyebrow"), modeSwitch,
                builtinOptionsPane, customOptionsPane, new Separator(), cameraSettings);
        customization.getStyleClass().add("customization-panel");
        ScrollPane left = new ScrollPane(customization);
        left.setFitToWidth(true);
        left.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        left.setMinWidth(294);
        left.setPrefWidth(294);
        left.setMaxWidth(294);
        left.getStyleClass().add("sidebar-scroll");
        Rectangle sidebarClip = new Rectangle();
        sidebarClip.widthProperty().bind(left.widthProperty());
        sidebarClip.heightProperty().bind(left.heightProperty());
        sidebarClip.setArcWidth(32);
        sidebarClip.setArcHeight(32);
        left.setClip(sidebarClip);

        Label live = styledLabel("LIVE VIEW", "live-badge");
        Region cameraSpace = new Region();
        HBox.setHgrow(cameraSpace, Priority.ALWAYS);
        Button cameraOptions = new Button("Camera settings", icon("camera", 14));
        cameraOptions.getStyleClass().add("camera-options-button");
        cameraOptions.setOnAction(e -> {
            cameraSettings.setExpanded(true);
            left.applyCss();
            left.layout();
            left.setVvalue(1);
            cameraDeviceCombo.requestFocus();
        });
        HBox cameraHeader = new HBox(10, live, cameraSpace, cameraOptions);
        cameraHeader.setAlignment(Pos.CENTER_LEFT);
        liveView.setPreserveRatio(true);
        VBox connecting = emptyState("camera", "The spotlight is yours.", "Getting your camera ready...");
        connecting.getStyleClass().add("camera-empty");
        connecting.visibleProperty().bind(liveView.imageProperty().isNull());
        connecting.managedProperty().bind(connecting.visibleProperty());
        countdownOverlay.setFont(Font.font("Segoe UI", FontWeight.BOLD, 112));
        countdownOverlay.setTextFill(Color.WHITE);
        countdownOverlay.setEffect(new DropShadow(24, Color.BLACK));
        shotProgressOverlay.getStyleClass().add("shot-badge");
        shotProgressOverlay.setVisible(false);
        StackPane.setAlignment(shotProgressOverlay, Pos.TOP_LEFT);
        StackPane.setMargin(shotProgressOverlay, new Insets(16));
        StackPane cameraStage = new StackPane(connecting, liveView, shotProgressOverlay, countdownOverlay);
        cameraStage.getStyleClass().add("camera-stage");
        cameraStage.setMinSize(0, 180);
        cameraStage.setPrefHeight(430);
        liveView.fitWidthProperty().bind(cameraStage.widthProperty().subtract(2));
        liveView.fitHeightProperty().bind(cameraStage.heightProperty().subtract(2));
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(cameraStage.widthProperty());
        clip.heightProperty().bind(cameraStage.heightProperty());
        clip.setArcWidth(32);
        clip.setArcHeight(32);
        cameraStage.setClip(clip);
        VBox.setVgrow(cameraStage, Priority.ALWAYS);

        sessionSummary.getStyleClass().add("session-summary");
        shotThumbnails.setAlignment(Pos.CENTER);
        shotThumbnails.setMinHeight(60);
        shotThumbnails.setPrefHeight(70);
        shotThumbnails.getStyleClass().add("shot-tray");
        ScrollPane shots = new ScrollPane(shotThumbnails);
        shots.setFitToHeight(true);
        shots.setFitToWidth(true);
        shots.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        shots.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        shots.setMinHeight(76);
        shots.setPrefHeight(76);
        shots.getStyleClass().add("shots-scroll");
        startCaptureBtn.setGraphic(icon("camera", 21));
        startCaptureBtn.getStyleClass().add("primary-btn");
        startCaptureBtn.setMaxWidth(Double.MAX_VALUE);
        startCaptureBtn.setOnAction(e -> startCaptureSession());
        startCaptureBtn.setDisable(true);
        captureHint.getStyleClass().add("capture-hint");
        captureHint.setWrapText(true);
        VBox action = new VBox(9, startCaptureBtn, captureHint);
        action.setAlignment(Pos.CENTER);
        VBox center = new VBox(14, cameraHeader, cameraStage, sessionSummary, shots, action);
        center.setMinWidth(0);
        center.getStyleClass().add("camera-workspace");
        HBox.setHgrow(center, Priority.ALWAYS);

        Label previewTitle = styledLabel("Your keepsake", "panel-title");
        Label format = styledLabel("PNG", "format-badge");
        Region previewSpace = new Region();
        HBox.setHgrow(previewSpace, Priority.ALWAYS);
        HBox previewHeader = new HBox(8, previewTitle, previewSpace, format);
        previewHeader.setAlignment(Pos.CENTER_LEFT);
        previewSummary.getStyleClass().add("muted-label");
        previewSummary.setWrapText(true);
        Label previewHint = styledLabel("A little preview of what’s to come.", "preview-hint");
        previewHint.setWrapText(true);
        previewHint.setAlignment(Pos.CENTER);
        VBox draft = new VBox(20, printPreview, previewHint);
        draft.setAlignment(Pos.CENTER);
        draft.setMinHeight(0);
        draft.visibleProperty().bind(resultView.imageProperty().isNull());
        draft.managedProperty().bind(draft.visibleProperty());
        resultView.setPreserveRatio(true);
        resultView.visibleProperty().bind(resultView.imageProperty().isNotNull());
        resultView.setEffect(new DropShadow(18, Color.rgb(35, 29, 24, 0.15)));
        StackPane resultHolder = new StackPane(draft, resultView);
        resultHolder.getStyleClass().add("result-holder");
        resultHolder.setMinHeight(120);
        var previewScale = javafx.beans.binding.Bindings.max(0.25,
                javafx.beans.binding.Bindings.min(1, javafx.beans.binding.Bindings.min(
                        resultHolder.widthProperty().subtract(26).divide(180),
                        resultHolder.heightProperty().subtract(74).divide(300))));
        printPreview.widthProperty().bind(previewScale.multiply(180));
        printPreview.heightProperty().bind(previewScale.multiply(300));
        javafx.beans.InvalidationListener redraw = observable -> {
            CustomFrameItem selected = customFrameCombo.getValue();
            int count = modeCustomBtn.isSelected() && selected != null ? selected.slotCount()
                    : selectedLayout == null ? 4 : selectedLayout.frameCount();
            drawPrintPreview(modeCustomBtn.isSelected(), count);
        };
        printPreview.widthProperty().addListener(redraw);
        printPreview.heightProperty().addListener(redraw);
        resultView.fitWidthProperty().bind(resultHolder.widthProperty().subtract(20));
        resultView.fitHeightProperty().bind(resultHolder.heightProperty().subtract(24));
        VBox.setVgrow(resultHolder, Priority.ALWAYS);
        saveResultBtn.setGraphic(icon("download", 17));
        saveResultBtn.getStyleClass().add("secondary-btn");
        saveResultBtn.setMaxWidth(Double.MAX_VALUE);
        saveResultBtn.setDisable(true);
        saveResultBtn.setOnAction(e -> exportBytes(lastResultBytes, "photobooth-strip.png"));
        Label saveHint = styledLabel("Full quality. All yours.", "capture-hint");
        VBox right = new VBox(12, previewHeader, previewSummary, resultHolder, saveResultBtn, saveHint);
        right.getStyleClass().add("preview-panel");
        right.setAlignment(Pos.TOP_CENTER);
        right.setMinWidth(236);
        right.setPrefWidth(258);
        right.setMaxWidth(258);

        HBox main = new HBox(20, left, center, right);
        main.setPadding(new Insets(0, 28, 24, 28));
        main.getStyleClass().add("studio-layout");
        updateSessionPreview();
        return main;
    }

    private Pane buildCustomFramesView() {
        Button upload = new Button("Upload a frame", icon("plus", 18));
        upload.getStyleClass().add("primary-btn");
        upload.setOnAction(e -> promptUploadCustomFrame());
        Label title = styledLabel("Designed by you. Captured here.", "panel-title");
        Label hint = styledLabel("Upload a PNG with transparent photo windows. We’ll take care of the fit.", "muted-label");
        hint.setWrapText(true);
        VBox text = new VBox(7, title, hint);
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox toolbar = new HBox(20, text, upload);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("card-pane");
        customFramesGrid.setPadding(new Insets(12, 0, 16, 0));
        customFramesGrid.getChildren().setAll(emptyState("frames", "Make it your own.", "Your uploaded frames will appear here. Start with a transparent PNG design."));
        ScrollPane scroll = new ScrollPane(customFramesGrid);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        VBox root = new VBox(12, toolbar, scroll);
        root.setPadding(new Insets(0, 28, 24, 28));
        return root;
    }

    private Pane buildGalleryView() {
        Button refresh = new Button("Refresh", icon("refresh", 16));
        refresh.getStyleClass().add("secondary-btn");
        refresh.setOnAction(e -> refreshGallery());
        VBox heading = new VBox(5, styledLabel("Your collection", "panel-title"),
                styledLabel("Little moments. Lasting memories.", "muted-label"));
        Region space = new Region();
        HBox.setHgrow(space, Priority.ALWAYS);
        HBox toolbar = new HBox(14, heading, space, refresh);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        galleryGrid.setPadding(new Insets(12, 0, 16, 0));
        galleryGrid.getChildren().setAll(emptyState("grid", "The first of many.", "Start a session in the studio. Your saved photos will be waiting right here."));
        ScrollPane scroll = new ScrollPane(galleryGrid);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        VBox collection = new VBox(12, toolbar, scroll);
        collection.setMinWidth(0);
        HBox.setHgrow(collection, Priority.ALWAYS);

        galleryInspectView.setPreserveRatio(true);
        StackPane preview = new StackPane(emptyState("image", "Take a closer look.", "Select a photo from your collection."), galleryInspectView);
        Node placeholder = preview.getChildren().get(0);
        placeholder.visibleProperty().bind(galleryInspectView.imageProperty().isNull());
        galleryInspectView.fitWidthProperty().bind(preview.widthProperty().subtract(28));
        galleryInspectView.fitHeightProperty().bind(preview.heightProperty().subtract(28));
        preview.setMinHeight(160);
        preview.getStyleClass().add("result-holder");
        VBox.setVgrow(preview, Priority.ALWAYS);
        galleryInspectMeta.setWrapText(true);
        galleryInspectMeta.getStyleClass().add("muted-label");
        Button export = new Button("Save a copy", icon("download", 17));
        export.getStyleClass().add("secondary-btn");
        export.setMaxWidth(Double.MAX_VALUE);
        export.disableProperty().bind(galleryInspectView.imageProperty().isNull());
        export.setOnAction(e -> {
            if (selectedGalleryBytes != null && selectedGalleryPhoto != null) exportBytes(selectedGalleryBytes, "photobooth-" + selectedGalleryPhoto.id() + ".png");
        });
        Button delete = new Button("Delete photo", icon("trash", 16));
        delete.getStyleClass().add("danger-btn");
        delete.setMaxWidth(Double.MAX_VALUE);
        delete.disableProperty().bind(galleryInspectView.imageProperty().isNull());
        delete.setOnAction(e -> { if (selectedGalleryPhoto != null) deleteGalleryPhoto(selectedGalleryPhoto.id()); });
        VBox inspector = new VBox(14, styledLabel("IN THE FRAME", "eyebrow"), preview, galleryInspectMeta, export, delete);
        inspector.getStyleClass().add("preview-panel");
        inspector.setMinWidth(320);
        inspector.setPrefWidth(350);
        inspector.setMaxWidth(350);
        HBox root = new HBox(24, collection, inspector);
        root.setPadding(new Insets(0, 28, 24, 28));
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
                Platform.runLater(() -> setStatus("Opening your photo library... (" + a + "/25)"));
                try { Thread.sleep(1000); } catch (InterruptedException ignored) { return; }
            }
            Platform.runLater(() -> {
                connectionBadge.setText("● Offline");
                connectionBadge.getStyleClass().setAll("status-badge-offline");
                setStatus("Your photo library is offline. Try reconnecting, or restart Photobooth Studio.");
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
                    connectionBadge.setText("● Studio ready");
                    connectionBadge.getStyleClass().setAll("status-badge-online");
                    rebuildLayoutTiles();
                    rebuildThemeTiles();
                    updateCustomFrameCombo();
                    setStatus("Your studio is ready. Choose a look and make a memory.");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    connectionBadge.setText("● Offline");
                    connectionBadge.getStyleClass().setAll("status-badge-offline");
                    setStatus("Could not open your photo library. Try reconnecting.");
                    e.printStackTrace();
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
            tile.setPrefSize(76, 83);
            tile.setMinSize(76, 83);
            tile.setMaxSize(76, 83);
            tile.getStyleClass().add(item.equals(selectedLayout) ? "choice-tile-selected" : "choice-tile");

            Canvas icon = drawLayoutMiniIcon(item.columns(), item.rows());
            Label name = new Label(item.displayName());
            name.getStyleClass().add("tile-label");
            name.setWrapText(true);
            name.setMaxWidth(68);
            name.setAlignment(Pos.CENTER);
            name.setPrefHeight(27);
            Label count = new Label(item.frameCount() + (item.frameCount() == 1 ? " shot" : " shots"));
            count.getStyleClass().add("muted-label");

            tile.getChildren().addAll(icon, name, count);
            Runnable choose = () -> {
                selectedLayout = item;
                rebuildLayoutTiles();
            };
            configureTile(tile, item.displayName() + ", " + item.frameCount() + " shots", choose);
            layoutTiles.getChildren().add(tile);
        }
        updateSessionPreview();
    }

    private Canvas drawLayoutMiniIcon(int cols, int rows) {
        Canvas c = new Canvas(36, 26);
        GraphicsContext g = c.getGraphicsContext2D();
        g.setFill(Color.web("#9e745f"));
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
            tile.setPrefSize(76, 58);
            tile.setMinSize(76, 58);
            tile.setMaxSize(76, 58);
            tile.getStyleClass().add(item.equals(selectedTheme) ? "choice-tile-selected" : "choice-tile");

            Region swatch = new Region();
            swatch.setMinSize(52, 16);
            swatch.setPrefSize(52, 16);
            swatch.setMaxSize(52, 16);
            swatch.setBackground(new Background(new BackgroundFill(
                    new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                            new Stop(0, Color.web(item.bgTop())),
                            new Stop(1, Color.web(item.bgBottom()))),
                    new CornerRadii(5), Insets.EMPTY)));
            swatch.setStyle("-fx-border-color: " + item.accent() + "; -fx-border-radius: 5; -fx-border-width: 1.5;");

            Label name = new Label(item.displayName());
            name.getStyleClass().add("tile-label");
            name.setMaxWidth(68);
            Tooltip.install(tile, new Tooltip(item.displayName()));

            tile.getChildren().addAll(swatch, name);
            Runnable choose = () -> {
                selectedTheme = item;
                rebuildThemeTiles();
            };
            configureTile(tile, item.displayName(), choose);
            themeTiles.getChildren().add(tile);
        }
        updateSessionPreview();
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
            setStatus("Your studio is still loading. Try again in a moment.");
            return;
        }

        int totalShots = useCustom ? customFrame.slotCount() : layout.frameCount();
        int timerSec = countdownCombo.getValue() == null ? 3 : countdownCombo.getValue();
        String caption = captionField.getText();

        capturing = true;
        startCaptureBtn.setDisable(true);
        startCaptureBtn.setText("Capturing your moment...");
        builtinOptionsPane.setDisable(true);
        customOptionsPane.setDisable(true);
        modeBuiltinBtn.setDisable(true);
        modeCustomBtn.setDisable(true);
        saveResultBtn.setDisable(true);
        resultView.setImage(null);
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
                        countdownOverlay.setText("");
                        triggerFlashEffect();
                        ImageView thumb = new ImageView(SwingFXUtils.toFXImage(captured, null));
                        thumb.setFitHeight(52);
                        thumb.setPreserveRatio(true);
                        shotThumbnails.getChildren().add(thumb);
                    });
                    Thread.sleep(550);
                }

                Platform.runLater(() -> {
                    countdownOverlay.setText("");
                    shotProgressOverlay.setVisible(false);
                    startCaptureBtn.setText("Developing your keepsake...");
                    setStatus("Putting the finishing touches on your photo...");
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
                    setStatus("Your keepsake is ready. Save a copy, or find it anytime in your gallery.");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> setStatus("Session failed: " + ex.getMessage()));
            } finally {
                capturing = false;
                Platform.runLater(() -> {
                    countdownOverlay.setText("");
                    shotProgressOverlay.setVisible(false);
                    startCaptureBtn.setDisable(false);
                    startCaptureBtn.setText("Start another session");
                    builtinOptionsPane.setDisable(false);
                    customOptionsPane.setDisable(false);
                    modeBuiltinBtn.setDisable(false);
                    modeCustomBtn.setDisable(false);
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
                    if (list.isEmpty()) {
                        customFramesGrid.getChildren().add(emptyState("frames", "Make it your own.",
                                "Add a transparent PNG to give your next session a personal touch."));
                    }
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

        Button useBtn = new Button("Use this frame");
        useBtn.getStyleClass().add("secondary-btn");
        useBtn.setOnAction(e -> {
            modeCustomBtn.setSelected(true);
            customFrameCombo.setValue(item);
            navBoothBtn.fire();
        });

        Button delBtn = new Button("", icon("trash", 16));
        delBtn.setTooltip(new Tooltip("Delete " + item.name()));
        delBtn.setAccessibleText("Delete " + item.name());
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

        StackPane mat = new StackPane(icon("frames", 30), preview);
        mat.getChildren().get(0).visibleProperty().bind(preview.imageProperty().isNull());
        mat.getStyleClass().add("media-mat");
        VBox card = new VBox(12, mat, title, meta, actions);
        card.setAlignment(Pos.TOP_CENTER);
        card.setPrefWidth(240);
        card.getStyleClass().add("media-card");

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
                        VBox empty = emptyState("grid", "The first of many.", "Start a session in the studio. Your favorite moments will be waiting right here.");
                        galleryGrid.getChildren().add(empty);
                        selectedGalleryPhoto = null;
                        selectedGalleryBytes = null;
                        galleryInspectView.setImage(null);
                        galleryInspectMeta.setText("Select a photo to preview");
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

        Label name = new Label(p.frameName());
        name.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        Label sub = new Label(p.shotCount() + " shots" + (p.caption().isBlank() ? "" : " • \"" + p.caption() + "\""));
        sub.getStyleClass().add("muted-label");

        StackPane mat = new StackPane(thumb);
        mat.getStyleClass().add("media-mat");
        sub.setMaxWidth(175);
        sub.setWrapText(true);
        VBox card = new VBox(10, mat, name, sub);
        card.setAlignment(Pos.TOP_CENTER);
        card.setPrefWidth(215);
        card.getStyleClass().add("media-card");
        card.setUserData(p);
        configureTile(card, "Photo " + p.id() + ", " + p.frameName(), () -> selectGalleryPhoto(p));

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
        selectedGalleryBytes = null;
        galleryInspectView.setImage(null);
        for (Node card : galleryGrid.getChildren()) {
            card.getStyleClass().remove("media-card-selected");
            if (card.getUserData() instanceof PhotoItem item && item.id() == p.id()) card.getStyleClass().add("media-card-selected");
        }
        galleryInspectMeta.setText("Loading Photo #" + p.id() + "...");
        Thread t = new Thread(() -> {
            try {
                byte[] full = backend.getPhotoImage(p.id());
                Image img = new Image(new ByteArrayInputStream(full));
                Platform.runLater(() -> {
                    if (selectedGalleryPhoto == null || selectedGalleryPhoto.id() != p.id()) return;
                    selectedGalleryBytes = full;
                    galleryInspectView.setImage(img);
                    galleryInspectMeta.setText(
                            p.frameName()
                                    + (p.themeName().isBlank() ? "" : " (" + p.themeName() + ")")
                                    + "\n" + p.width() + "×" + p.height() + " px  •  " + p.shotCount() + " photos"
                                    + (p.caption().isBlank() ? "" : "\n" + p.caption()));
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

    private Label styledLabel(String text, String style) {
        Label label = new Label(text);
        label.getStyleClass().add(style);
        return label;
    }

    private HBox sectionHeading(String number, String text) {
        HBox heading = new HBox(9, styledLabel(number, "step-number"), sectionLabel(text));
        heading.setAlignment(Pos.CENTER_LEFT);
        return heading;
    }

    private VBox emptyState(String symbol, String title, String description) {
        StackPane illustration = new StackPane(icon(symbol, 30));
        illustration.getStyleClass().add("empty-illustration");
        Label heading = styledLabel(title, "empty-title");
        Label body = styledLabel(description, "empty-description");
        body.setWrapText(true);
        body.setMaxWidth(235);
        body.setAlignment(Pos.CENTER);
        VBox state = new VBox(14, illustration, heading, body);
        state.setAlignment(Pos.CENTER);
        state.setPrefSize(280, 290);
        state.getStyleClass().add("empty-state");
        return state;
    }

    private Node icon(String name, double size) {
        String path = switch (name) {
            case "camera" -> "M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3z M16 13a4 4 0 1 1-8 0 4 4 0 0 1 8 0";
            case "frames" -> "M3 3h18v18H3z M7 7h10v10H7z";
            case "grid" -> "M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z";
            case "refresh" -> "M21 3v6h-6 M3 21v-6h6 M20 9a8 8 0 0 0-13-5L3 9 M4 15a8 8 0 0 0 13 5l4-5";
            case "download" -> "M12 3v12 M7 10l5 5 5-5 M3 15v5h18v-5";
            case "plus" -> "M12 5v14 M5 12h14";
            case "trash" -> "M3 6h18 M9 6V3h6v3 M5 6l1 15h12l1-15 M10 10v7 M14 10v7";
            default -> "M3 3h18v18H3z M3 17l6-6 4 4 3-3 5 5 M16 7h.01";
        };
        SVGPath shape = new SVGPath();
        shape.setContent(path);
        shape.setFill(Color.TRANSPARENT);
        shape.setStroke(Color.web("#68645f"));
        shape.setStrokeWidth(1.7);
        shape.setStrokeLineCap(StrokeLineCap.ROUND);
        shape.setStrokeLineJoin(StrokeLineJoin.ROUND);
        shape.setScaleX(size / 24);
        shape.setScaleY(size / 24);
        shape.getStyleClass().add("line-icon");
        StackPane wrapper = new StackPane(shape);
        wrapper.setMinSize(size, size);
        wrapper.setPrefSize(size, size);
        wrapper.setMaxSize(size, size);
        wrapper.setMouseTransparent(true);
        return wrapper;
    }

    private Button iconButton(String symbol, String description) {
        Button button = new Button("", icon(symbol, 17));
        button.getStyleClass().add("icon-button");
        button.setTooltip(new Tooltip(description));
        button.setAccessibleText(description);
        return button;
    }

    private void configureTile(VBox tile, String description, Runnable choose) {
        tile.setFocusTraversable(true);
        tile.setAccessibleRole(javafx.scene.AccessibleRole.BUTTON);
        tile.setAccessibleText(description);
        tile.setOnMouseClicked(e -> choose.run());
        tile.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) {
                Pane parent = tile.getParent() instanceof Pane pane ? pane : null;
                int index = parent == null ? -1 : parent.getChildren().indexOf(tile);
                choose.run();
                if (parent != null && index >= 0 && index < parent.getChildren().size()) parent.getChildren().get(index).requestFocus();
                e.consume();
            }
        });
    }

    private void updateSessionPreview() {
        boolean custom = modeCustomBtn.isSelected();
        CustomFrameItem frame = customFrameCombo.getValue();
        int count = custom && frame != null ? frame.slotCount() : selectedLayout == null ? 4 : selectedLayout.frameCount();
        int seconds = countdownCombo.getValue() == null ? 3 : countdownCombo.getValue();
        String layout = custom ? (frame == null ? "Choose a frame" : frame.name())
                : selectedLayout == null ? "Classic strip" : selectedLayout.displayName();
        sessionSummary.setText(layout + "  /  " + count + (count == 1 ? " photo" : " photos"));
        previewSummary.setText(custom ? layout : selectedTheme == null ? "Classic White" : selectedTheme.displayName());
        captureHint.setText(seconds + " second countdown before each photo. Find your good side.");
        startCaptureBtn.setDisable(capturing || (custom ? frame == null : selectedLayout == null));
        if (!capturing) {
            shotThumbnails.getChildren().clear();
            for (int index = 1; index <= count; index++) {
                Label number = styledLabel(String.format("%02d", index), "shot-number");
                StackPane slot = new StackPane(number);
                slot.setPrefSize(48, 52);
                slot.setMinSize(40, 48);
                slot.getStyleClass().add("shot-slot");
                shotThumbnails.getChildren().add(slot);
            }
        }
        drawPrintPreview(custom, count);
    }

    private void drawPrintPreview(boolean custom, int count) {
        GraphicsContext g = printPreview.getGraphicsContext2D();
        g.setTransform(1, 0, 0, 1, 0, 0);
        g.clearRect(0, 0, printPreview.getWidth(), printPreview.getHeight());
        double scale = Math.min(printPreview.getWidth() / 180, printPreview.getHeight() / 300);
        g.scale(scale, scale);
        int columns = custom || selectedLayout == null ? 1 : selectedLayout.columns();
        int rows = custom || selectedLayout == null ? count : selectedLayout.rows();
        double paperWidth = columns == 1 && rows > 1 ? 118 : 170;
        double paperHeight = rows > columns ? 266 : rows == columns ? 204 : 125;
        double left = (180 - paperWidth) / 2;
        double top = (300 - paperHeight) / 2;
        Color background = selectedTheme == null || custom ? Color.WHITE : Color.web(selectedTheme.bgTop());
        Color ink = selectedTheme == null || custom ? Color.web("#69645d") : Color.web(selectedTheme.text());
        g.setFill(Color.rgb(45, 36, 27, 0.07));
        g.fillRoundRect(left + 3, top + 6, paperWidth, paperHeight, 6, 6);
        g.setFill(background);
        g.fillRoundRect(left, top, paperWidth, paperHeight, 4, 4);
        g.setStroke(Color.rgb(80, 70, 60, 0.13));
        g.setLineWidth(1);
        g.strokeRoundRect(left, top, paperWidth, paperHeight, 4, 4);
        double gap = 7;
        double cellWidth = (paperWidth - gap * (columns + 1)) / columns;
        double cellHeight = (paperHeight - 38 - gap * (rows + 1)) / rows;
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                double x = left + gap + column * (cellWidth + gap);
                double y = top + gap + row * (cellHeight + gap);
                g.setFill(background.interpolate(ink, 0.09));
                g.fillRoundRect(x, y, cellWidth, cellHeight, 2, 2);
                g.setFill(ink.deriveColor(0, 1, 1, 0.32));
                g.setFont(Font.font("Segoe UI", 10));
                g.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
                g.fillText(String.format("%02d", row * columns + column + 1), x + cellWidth / 2, y + cellHeight / 2 + 3);
            }
        }
        String caption = custom ? "MADE FOR YOU" : captionField.getText().isBlank() ? "PHOTOBOOTH STUDIO" : captionField.getText();
        if (caption.length() > 22) caption = caption.substring(0, 20) + "…";
        g.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
        g.setFill(ink);
        g.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        g.fillText(caption, 90, top + paperHeight - 19, paperWidth - 14);
        g.setFont(Font.font("Segoe UI", 6));
        g.fillText("GOOD TIMES, ON REPEAT", 90, top + paperHeight - 8);
    }

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
