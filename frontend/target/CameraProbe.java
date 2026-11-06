import com.photobooth.frontend.CameraService;

public class CameraProbe {
    public static void main(String[] args) throws Exception {
        CameraService camera = new CameraService();
        try {
            System.out.println("Devices: " + camera.listDevices());
            System.out.println("Opened hardware: " + camera.openDefault());
            var frameField = CameraService.class.getDeclaredField("lastValidFrame");
            frameField.setAccessible(true);
            int hardwareFrames = 0;
            for (int i = 0; i < 100; i++) {
                camera.grab();
                var hardwareFrame = (java.awt.image.BufferedImage) frameField.get(camera);
                if (hardwareFrame != null) {
                    hardwareFrames++;
                    if (hardwareFrames == 1) System.out.println("Hardware frame: " + hardwareFrame.getWidth() + "x" + hardwareFrame.getHeight());
                    if (hardwareFrames == 30) break;
                }
                if (!camera.getLastError().isEmpty()) break;
                Thread.sleep(100);
            }
            System.out.println("Hardware reads: " + hardwareFrames);
            System.out.println("Active: " + camera.getActiveDeviceId());
            System.out.println("Error: " + camera.getLastError());
        } finally {
            camera.close();
        }
        System.exit(0);
    }
}
