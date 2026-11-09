package com.photobooth.frontend;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamResolution;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages multiple webcam devices, resolution negotiation, live mirror & color filters,
 * plus an animated Studio Demo Camera fallback.
 */
public class CameraService {

    private static final Logger LOG = Logger.getLogger(CameraService.class.getName());
    private static final long FRAME_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(5);

    public static final String DEMO_DEVICE_ID = "__STUDIO_DEMO__";

    public record CameraDevice(String id, String displayName, boolean demo) {
        @Override
        public String toString() {
            return displayName;
        }
    }

    public enum Filter {
        NONE("Natural"),
        WARM("Golden Warm"),
        BW("B&W Classic"),
        SEPIA("Vintage Sepia"),
        COOL("Cool Cinema");

        private final String label;
        Filter(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    private volatile Webcam activeWebcam;
    private volatile String activeDeviceId = "";
    private volatile boolean forceDemo = false;
    private volatile boolean mirror = true;
    private volatile Filter filter = Filter.NONE;
    private volatile BufferedImage lastValidFrame;
    private volatile String lastError = "";
    private long lastFrameAt;
    private long tick;

    /**
     * Lists all connected hardware/virtual webcams plus the built-in Studio Demo Camera.
     */
    public synchronized List<CameraDevice> listDevices() {
        List<CameraDevice> result = new ArrayList<>();
        try {
            for (Webcam w : Webcam.getWebcams(5, TimeUnit.SECONDS)) {
                result.add(new CameraDevice(w.getName(), w.getName(), false));
            }
        } catch (Exception | LinkageError error) {
            reportError("Could not discover cameras", error);
        }
        result.add(new CameraDevice(DEMO_DEVICE_ID, "Studio Demo Camera (Built-in)", true));
        return result;
    }

    public String getActiveDeviceId() {
        return activeDeviceId;
    }

    public String getLastError() { return lastError; }

    /**
     * Opens the first available webcam, or falls back to the Studio Demo Camera.
     */
    public synchronized boolean openDefault() {
        List<CameraDevice> devices = listDevices();
        List<String> failures = new ArrayList<>();
        if (!lastError.isBlank()) failures.add(lastError);
        for (CameraDevice d : devices) {
            if (!d.demo() && selectDevice(d)) {
                return true;
            }
            if (!d.demo()) failures.add(lastError);
        }
        selectDevice(new CameraDevice(DEMO_DEVICE_ID, "Studio Demo Camera (Built-in)", true));
        lastError = failures.isEmpty() ? "No camera detected. Connect a webcam and rescan." : String.join("; ", failures);
        return false;
    }

    /**
     * Switches to the specified camera device safely.
     */
    public synchronized boolean selectDevice(CameraDevice device) {
        if (device == null || device.demo() || DEMO_DEVICE_ID.equals(device.id())) {
            closeActive();
            forceDemo = true;
            activeDeviceId = DEMO_DEVICE_ID;
            lastValidFrame = null;
            lastError = "";
            return true;
        }

        forceDemo = false;
        if (activeWebcam != null && activeWebcam.isOpen() && activeWebcam.getName().equals(device.id())) {
            activeDeviceId = device.id();
            return true;
        }

        closeActive();
        lastValidFrame = null;
        lastError = "";

        try {
            for (Webcam candidate : Webcam.getWebcams(5, TimeUnit.SECONDS)) {
                if (candidate.getName().equals(device.id())) {
                    if (candidate.getLock().isLocked()) {
                        throw new IllegalStateException("Camera is in use by another Photobooth instance. Close the other window and retry.");
                    }
                    configureBestResolution(candidate);
                    // Read directly on our camera worker. The library's asynchronous updater
                    // can leave getImage() waiting on an empty cached frame for ten seconds.
                    if (candidate.open(false)) {
                        activeWebcam = candidate;
                        activeDeviceId = candidate.getName();
                        lastFrameAt = System.nanoTime();
                        return true;
                    }
                    candidate.close();
                    throw new IllegalStateException("Camera refused to open. Close other camera apps and check Windows camera permissions.");
                }
            }
            throw new IllegalStateException("Camera was disconnected. Reconnect it and rescan.");
        } catch (Exception | LinkageError error) {
            reportError("Could not open '" + device.displayName() + "'", error);
        }

        // Fallback if camera is busy/locked by another app
        forceDemo = true;
        activeDeviceId = DEMO_DEVICE_ID;
        return false;
    }

    private void configureBestResolution(Webcam cam) {
        try {
            Dimension[] sizes = cam.getViewSizes();
            if (sizes == null || sizes.length == 0) {
                cam.setViewSize(WebcamResolution.VGA.getSize());
                return;
            }
            Dimension chosen = sizes[sizes.length - 1];
            for (Dimension d : sizes) {
                if (d.width == 640 && d.height == 480) {
                    chosen = d;
                    break;
                }
            }
            cam.setViewSize(chosen);
        } catch (Exception error) {
            LOG.log(Level.WARNING, "Could not configure camera resolution", error);
        }
    }

    public void setMirror(boolean mirror) { this.mirror = mirror; }
    public boolean isMirror() { return mirror; }

    public void setFilter(Filter filter) { this.filter = filter == null ? Filter.NONE : filter; }
    public Filter getFilter() { return filter; }

    public synchronized BufferedImage grab() {
        BufferedImage raw = null;
        Webcam cam = this.activeWebcam;
        if (!forceDemo && cam != null && cam.isOpen()) {
            try {
                raw = cam.getImage();
                if (raw != null) {
                    lastValidFrame = raw;
                    lastFrameAt = System.nanoTime();
                    lastError = "";
                } else if (System.nanoTime() - lastFrameAt > FRAME_TIMEOUT_NANOS) {
                    throw new IllegalStateException("No frames received for 5 seconds. Close other camera apps, check Windows camera access, then reselect the camera.");
                } else if (lastValidFrame != null) {
                    // Reuse previous hardware frame during transient USB frame drops
                    raw = lastValidFrame;
                } else {
                    raw = warmingUpPattern(cam.getName());
                    return raw;
                }
            } catch (Exception | LinkageError error) {
                reportError("Camera '" + cam.getName() + "' stopped delivering frames", error);
                closeActive();
                forceDemo = true;
                activeDeviceId = DEMO_DEVICE_ID;
                lastValidFrame = null;
            }
        }

        if (raw == null) {
            raw = testPattern();
        } else if (mirror) {
            raw = mirrorImage(raw);
        }
        return applyFilter(raw, filter);
    }

    public synchronized void close() {
        closeActive();
    }

    private void closeActive() {
        if (activeWebcam != null) {
            try { activeWebcam.close(); } catch (Exception error) {
                LOG.log(Level.WARNING, "Could not close camera", error);
            }
            activeWebcam = null;
        }
    }

    private void reportError(String context, Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        lastError = context + ": " + (cause.getMessage() == null ? cause.toString() : cause.getMessage());
        LOG.log(Level.WARNING, lastError, error);
    }

    private BufferedImage mirrorImage(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, src.getWidth(), 0, -src.getWidth(), src.getHeight(), null);
        g.dispose();
        return out;
    }

    private BufferedImage applyFilter(BufferedImage src, Filter f) {
        if (f == Filter.NONE) return src;
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int[] px = src.getRGB(0, 0, w, h, null, 0, w);
        for (int i = 0; i < px.length; i++) {
            int rgb = px[i];
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            switch (f) {
                case BW -> {
                    int lum = Math.min(255, (int) (0.299 * r + 0.587 * g + 0.114 * b));
                    r = g = b = lum;
                }
                case SEPIA -> {
                    int tr = Math.min(255, (int) (0.393 * r + 0.769 * g + 0.189 * b));
                    int tg = Math.min(255, (int) (0.349 * r + 0.686 * g + 0.168 * b));
                    int tb = Math.min(255, (int) (0.272 * r + 0.534 * g + 0.131 * b));
                    r = tr; g = tg; b = tb;
                }
                case WARM -> {
                    r = Math.min(255, r + 18);
                    g = Math.min(255, g + 6);
                    b = Math.max(0, b - 14);
                }
                case COOL -> {
                    r = Math.max(0, r - 12);
                    g = Math.min(255, g + 4);
                    b = Math.min(255, b + 20);
                }
                default -> {}
            }
            px[i] = (r << 16) | (g << 8) | b;
        }
        out.setRGB(0, 0, w, h, px, 0, w);
        return out;
    }

    private BufferedImage warmingUpPattern(String cameraName) {
        int w = 640, h = 480;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(18, 21, 33));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(165, 180, 252));
        g.setFont(new Font("Segoe UI", Font.BOLD, 22));
        String s1 = "Connecting to " + cameraName + "...";
        g.drawString(s1, (w - g.getFontMetrics().stringWidth(s1)) / 2, h / 2 - 10);
        g.setColor(new Color(148, 163, 184));
        g.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        String s2 = "(If this stays blank, ensure no other app is using the camera)";
        g.drawString(s2, (w - g.getFontMetrics().stringWidth(s2)) / 2, h / 2 + 22);
        g.dispose();
        return img;
    }

    private BufferedImage testPattern() {
        tick++;
        int w = 640, h = 480;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g.setPaint(new GradientPaint(0, 0, new Color(34, 39, 37),
                w, h, new Color(57, 67, 57)));
        g.fillRect(0, 0, w, h);

        g.setColor(new Color(230, 181, 130, 13));
        for (int i = 0; i < 6; i++) {
            double angle = (tick * 0.03) + i * 1.1;
            int cx = (int) (w / 2.0 + Math.cos(angle) * 180);
            int cy = (int) (h / 2.0 + Math.sin(angle * 0.8) * 120);
            int rad = 50 + (i * 15);
            g.fill(new Ellipse2D.Double(cx - rad, cy - rad, rad * 2, rad * 2));
        }

        g.setColor(Color.WHITE);
        g.setFont(new Font("Segoe UI", Font.BOLD, 26));
        String msg = "The spotlight is yours.";
        int mw = g.getFontMetrics().stringWidth(msg);
        g.drawString(msg, (w - mw) / 2, h / 2 - 10);

        g.setColor(new Color(181, 189, 177));
        g.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        String hint = "Choose a camera in Camera & countdown to join the frame.";
        g.drawString(hint, (w - g.getFontMetrics().stringWidth(hint)) / 2, h / 2 + 20);
        g.setFont(new Font("Consolas", Font.PLAIN, 11));
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        g.drawString("STUDIO DEMO  /  " + time, 24, h - 24);

        g.dispose();
        return img;
    }
}
