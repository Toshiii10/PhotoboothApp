package com.photobooth.frontend;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** Owns the local backend process for one desktop session. */
final class DesktopBackend implements AutoCloseable {
    private Process process;
    private String url;

    String start(Path appDirectory, Path home) throws Exception {
        Path backend = appDirectory.resolve("backend/backend.jar");
        Path java = Path.of(System.getProperty("java.home"), "bin", "java.exe");
        if (!Files.isRegularFile(backend) || !Files.isRegularFile(java)) {
            throw new IOException("Application files are missing. Reinstall Photobooth Studio.");
        }
        int port;
        try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))) {
            port = socket.getLocalPort();
        }
        url = "http://127.0.0.1:" + port;
        Files.createDirectories(home.resolve("logs"));
        process = new ProcessBuilder(java.toString(), "-Djava.awt.headless=true",
                "-Dnet.bytebuddy.experimental=true", "-jar", backend.toString(),
                "--server.address=127.0.0.1", "--server.port=" + port,
                "--photobooth.home=" + home.toAbsolutePath())
                .directory(home.toFile())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(home.resolve("logs/backend.log").toFile()))
                .start();

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
        while (System.nanoTime() < deadline) {
            if (!process.isAlive()) {
                throw new IOException("The photo service stopped during startup. See " + home.resolve("logs/backend.log"));
            }
            try {
                HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create(url + "/api/health"))
                        .timeout(Duration.ofSeconds(2)).GET().build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200 && response.body().contains("\"UP\"")) return url;
            } catch (IOException ignored) {
                // The server has not bound its port yet.
            }
            Thread.sleep(250);
        }
        throw new IOException("The photo service did not start within 90 seconds. See " + home.resolve("logs/backend.log"));
    }

    String url() { return url; }
    boolean isAlive() { return process != null && process.isAlive(); }

    @Override
    public synchronized void close() {
        if (process == null) return;
        process.destroy();
        try {
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException error) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }
}
