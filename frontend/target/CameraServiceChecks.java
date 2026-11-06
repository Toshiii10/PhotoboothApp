import com.github.sarxos.webcam.*;
import com.photobooth.frontend.CameraService;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class CameraServiceChecks {
    static class Device implements WebcamDevice {
        boolean open, frames = true, broken;
        Dimension size = new Dimension(640, 480);
        public String getName() { return "Photobooth regression camera"; }
        public Dimension[] getResolutions() { return new Dimension[]{size}; }
        public Dimension getResolution() { return size; }
        public void setResolution(Dimension value) { size = value; }
        public BufferedImage getImage() {
            if (broken) throw new UnsatisfiedLinkError("test native failure");
            if (!frames) return null;
            BufferedImage image = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
            image.setRGB(0, 0, 0xff123456);
            return image;
        }
        public void open() { open = true; }
        public void close() { open = false; }
        public void dispose() { close(); }
        public boolean isOpen() { return open; }
    }
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        Device device = new Device();
        Webcam.setDriver(new WebcamDriver() {
            public List<WebcamDevice> getDevices() { return List.of(device); }
            public boolean isThreadSafe() { return true; }
        });
        CameraService camera = new CameraService();
        try {
            check(camera.openDefault(), "Hardware selection failed");
            check(camera.grab().getRGB(639, 0) == 0xff123456, "Mirror lost hardware frame");
            camera.setMirror(false);
            check(camera.grab().getRGB(0, 0) == 0xff123456, "Unmirrored frame incorrect");
            camera.close();
            check(!device.open, "Close leaked camera");
            device.frames = false;
            check(camera.openDefault(), "Warm-up open failed");
            long started = System.nanoTime();
            check(camera.grab() != null, "Warm-up image missing");
            check(System.nanoTime() - started < TimeUnit.SECONDS.toNanos(1), "Null frame blocked preview");
            var clock = CameraService.class.getDeclaredField("lastFrameAt");
            clock.setAccessible(true);
            clock.setLong(camera, System.nanoTime() - TimeUnit.SECONDS.toNanos(6));
            check(camera.grab() != null, "Timeout fallback missing");
            check(camera.getLastError().contains("No frames received"), "Timeout cause hidden");
            check(!device.open, "Timed-out camera lock leaked");
            device.frames = true;
            check(camera.openDefault(), "Retry failed");
            check(camera.grab() != null && camera.getLastError().isEmpty(), "Retry did not clear failure");
            device.broken = true;
            check(camera.grab() != null, "Native failure killed preview");
            check(camera.getLastError().contains("test native failure"), "Native cause hidden");
            check(!device.open, "Native failure leaked camera");
            System.out.println("PASS: hardware frames, mirror, warm-up, timeout, retry, native failure, cleanup");
        } finally { camera.close(); }
        System.exit(0);
    }
}
