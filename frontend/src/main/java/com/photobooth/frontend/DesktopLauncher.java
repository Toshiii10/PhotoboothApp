package com.photobooth.frontend;

import javax.swing.JOptionPane;
import java.io.PrintStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Arrays;

/** Entry point for the bundled Windows application. No external Java or Maven needed. */
public final class DesktopLauncher {
    private DesktopLauncher() {}

    public static void main(String[] args) {
        boolean smokeTest = Arrays.asList(args).contains("--smoke-test");
        Path home = Path.of(System.getProperty("photobooth.home",
                Path.of(System.getProperty("user.home"), "Photobooth").toString())).toAbsolutePath();
        int status = 0;
        try {
            if (smokeTest && System.getProperty("photobooth.home") == null) {
                home = Files.createTempDirectory("photobooth-smoke-");
            }
            Files.createDirectories(home.resolve("logs"));
            PrintStream log = new PrintStream(Files.newOutputStream(home.resolve("logs/desktop.log"),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND), true, java.nio.charset.StandardCharsets.UTF_8);
            System.setOut(log);
            System.setErr(log);
            System.out.println("Photobooth Studio started " + Instant.now());
            try (FileChannel channel = FileChannel.open(home.resolve("desktop.lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock lock = channel.tryLock()) {
                if (lock == null) {
                    if (smokeTest) throw new IllegalStateException("Another instance is already running.");
                    JOptionPane.showMessageDialog(null, "Photobooth Studio is already running. Open its existing window.",
                            "Photobooth Studio", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                Path appDirectory = Path.of(DesktopLauncher.class.getProtectionDomain().getCodeSource()
                        .getLocation().toURI()).getParent();
                DesktopBackend backend = new DesktopBackend();
                Thread shutdown = new Thread(backend::close, "desktop-backend-shutdown");
                Runtime.getRuntime().addShutdownHook(shutdown);
                try (backend) {
                    String url = backend.start(appDirectory, home);
                    System.setProperty("backend.url", url);
                    if (smokeTest) {
                        BackendClient client = new BackendClient(url);
                        if (client.getLayouts().isEmpty() || client.getThemes().isEmpty()) {
                            throw new IllegalStateException("The packaged backend returned no layouts or themes.");
                        }
                        var fxCheck = new java.util.concurrent.FutureTask<Void>(() -> {
                            var scene = new javafx.scene.Scene(new javafx.scene.layout.StackPane(
                                    new javafx.scene.control.Label("Photobooth packaging check")), 160, 80);
                            scene.getRoot().applyCss();
                            return null;
                        });
                        javafx.application.Platform.startup(fxCheck);
                        try {
                            fxCheck.get(15, java.util.concurrent.TimeUnit.SECONDS);
                        } finally {
                            javafx.application.Platform.exit();
                        }
                        var shot = new java.awt.image.BufferedImage(64, 48, java.awt.image.BufferedImage.TYPE_INT_RGB);
                        var photo = client.composeBuiltin("SINGLE", client.getThemes().get(0).id(),
                                "Packaging smoke test", java.util.List.of(shot));
                        var image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(client.getPhotoImage(photo.id())));
                        if (image == null || image.getWidth() != photo.width() || image.getHeight() != photo.height()
                                || client.getPhotos().stream().noneMatch(item -> item.id() == photo.id())) {
                            throw new IllegalStateException("Photo composition or gallery storage failed.");
                        }
                        client.deletePhoto(photo.id());
                        if (client.getPhotos().stream().anyMatch(item -> item.id() == photo.id())) {
                            throw new IllegalStateException("Photo deletion failed.");
                        }
                        System.out.println("SMOKE TEST PASSED: bundled runtime, JavaFX toolkit, backend, database, photo composition, PNG decoding and gallery CRUD");
                    } else {
                        PhotoboothApp.main(args);
                    }
                } finally {
                    Runtime.getRuntime().removeShutdownHook(shutdown);
                }
                if (backend.isAlive()) throw new IllegalStateException("Backend did not stop after the desktop session.");
            }
        } catch (Exception | LinkageError error) {
            status = 1;
            error.printStackTrace();
            if (!smokeTest) {
                JOptionPane.showMessageDialog(null,
                        "Photobooth Studio could not start.\n" + error.getMessage()
                                + "\n\nLogs: " + home.resolve("logs"),
                        "Photobooth Studio", JOptionPane.ERROR_MESSAGE);
            }
        }
        // JavaFX/AWT and the webcam library can leave non-daemon threads behind.
        System.exit(status);
    }
}
