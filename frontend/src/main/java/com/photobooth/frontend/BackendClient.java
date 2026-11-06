package com.photobooth.frontend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Rich HTTP client for communicating with the Spring Boot Photobooth backend.
 */
public class BackendClient {

    public record LayoutItem(String id, String displayName, int columns, int rows, int frameCount) {
        @Override public String toString() { return displayName + " (" + frameCount + ")"; }
    }

    public record ThemeItem(String id, String displayName, String bgTop, String bgBottom, String text,
                            String accent, String style) {
        @Override public String toString() { return displayName; }
    }

    public record SlotRect(int x, int y, int width, int height) {}

    public record CustomFrameItem(long id, String name, int width, int height, int slotCount, List<SlotRect> slots) {
        @Override public String toString() { return name + " (" + slotCount + " shots)"; }
    }

    public record PhotoItem(long id, String frameName, String themeName, String caption, int shotCount,
                            int width, int height, String createdAt) {}

    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    public BackendClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public boolean isHealthy() {
        try {
            HttpResponse<String> r = http.send(
                    HttpRequest.newBuilder(URI.create(baseUrl + "/api/health"))
                            .timeout(Duration.ofSeconds(2)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            return r.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    public List<LayoutItem> getLayouts() throws IOException, InterruptedException {
        HttpResponse<String> r = http.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/layouts")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        List<LayoutItem> out = new ArrayList<>();
        for (JsonNode n : mapper.readTree(r.body())) {
            out.add(new LayoutItem(
                    n.path("id").asText(),
                    n.path("displayName").asText(),
                    n.path("columns").asInt(),
                    n.path("rows").asInt(),
                    n.path("frameCount").asInt()));
        }
        return out;
    }

    public List<ThemeItem> getThemes() throws IOException, InterruptedException {
        HttpResponse<String> r = http.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/themes")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        List<ThemeItem> out = new ArrayList<>();
        for (JsonNode n : mapper.readTree(r.body())) {
            out.add(new ThemeItem(
                    n.path("id").asText(),
                    n.path("displayName").asText(),
                    n.path("bgTop").asText("#FFFFFF"),
                    n.path("bgBottom").asText("#F0F0F0"),
                    n.path("text").asText("#222222"),
                    n.path("accent").asText("#C9A227"),
                    n.path("style").asText("STANDARD")));
        }
        return out;
    }

    public List<CustomFrameItem> getCustomFrames() throws IOException, InterruptedException {
        HttpResponse<String> r = http.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/frames")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        List<CustomFrameItem> out = new ArrayList<>();
        for (JsonNode n : mapper.readTree(r.body())) {
            List<SlotRect> slots = new ArrayList<>();
            for (JsonNode s : n.path("slots")) {
                slots.add(new SlotRect(s.path("x").asInt(), s.path("y").asInt(),
                        s.path("width").asInt(), s.path("height").asInt()));
            }
            out.add(new CustomFrameItem(
                    n.path("id").asLong(),
                    n.path("name").asText(),
                    n.path("width").asInt(),
                    n.path("height").asInt(),
                    n.path("slotCount").asInt(),
                    slots));
        }
        return out;
    }

    public CustomFrameItem uploadCustomFrame(String name, File pngFile) throws IOException, InterruptedException {
        String boundary = "----PhotoboothFrame" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeField(body, boundary, "name", name);
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
                + pngFile.getName() + "\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(Files.readAllBytes(pngFile.toPath()));
        body.write("\r\n".getBytes(StandardCharsets.UTF_8));
        body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/frames"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
        JsonNode n = mapper.readTree(r.body());
        if (r.statusCode() != 200) {
            throw new IOException(n.path("error").asText("Upload failed (" + r.statusCode() + ")"));
        }
        List<SlotRect> slots = new ArrayList<>();
        for (JsonNode s : n.path("slots")) {
            slots.add(new SlotRect(s.path("x").asInt(), s.path("y").asInt(),
                    s.path("width").asInt(), s.path("height").asInt()));
        }
        return new CustomFrameItem(
                n.path("id").asLong(),
                n.path("name").asText(),
                n.path("width").asInt(),
                n.path("height").asInt(),
                n.path("slotCount").asInt(),
                slots);
    }

    public void deleteCustomFrame(long id) throws IOException, InterruptedException {
        http.send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/frames/" + id)).DELETE().build(),
                HttpResponse.BodyHandlers.discarding());
    }

    public byte[] getCustomFrameImage(long id) throws IOException, InterruptedException {
        HttpResponse<byte[]> r = http.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/frames/" + id + "/image")).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        return r.body();
    }

    public PhotoItem composeBuiltin(String layoutId, String themeId, String caption, List<BufferedImage> shots)
            throws IOException, InterruptedException {
        return sendCompose("BUILTIN", layoutId, themeId, null, caption, shots);
    }

    public PhotoItem composeCustom(long frameId, List<BufferedImage> shots)
            throws IOException, InterruptedException {
        return sendCompose("CUSTOM", null, null, frameId, null, shots);
    }

    private PhotoItem sendCompose(String frameType, String layoutId, String themeId, Long frameId, String caption,
                                  List<BufferedImage> shots) throws IOException, InterruptedException {
        String boundary = "----PhotoboothCompose" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeField(body, boundary, "frameType", frameType);
        if (layoutId != null) writeField(body, boundary, "layout", layoutId);
        if (themeId != null) writeField(body, boundary, "theme", themeId);
        if (frameId != null) writeField(body, boundary, "frameId", String.valueOf(frameId));
        if (caption != null) writeField(body, boundary, "caption", caption);

        for (int i = 0; i < shots.size(); i++) {
            ByteArrayOutputStream img = new ByteArrayOutputStream();
            ImageIO.write(shots.get(i), "png", img);
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"frames\"; filename=\"shot" + i
                    + ".png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(img.toByteArray());
            body.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/photos"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
        JsonNode n = mapper.readTree(r.body());
        if (r.statusCode() != 200) {
            throw new IOException(n.path("error").asText("Compose failed (" + r.statusCode() + ")"));
        }
        return parsePhoto(n);
    }

    public List<PhotoItem> getPhotos() throws IOException, InterruptedException {
        HttpResponse<String> r = http.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/photos")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        List<PhotoItem> out = new ArrayList<>();
        for (JsonNode n : mapper.readTree(r.body())) {
            out.add(parsePhoto(n));
        }
        return out;
    }

    public byte[] getPhotoImage(long id) throws IOException, InterruptedException {
        HttpResponse<byte[]> r = http.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/photos/" + id + "/image")).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        return r.body();
    }

    public byte[] getPhotoThumbnail(long id) throws IOException, InterruptedException {
        HttpResponse<byte[]> r = http.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/photos/" + id + "/thumbnail")).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        return r.body();
    }

    public void deletePhoto(long id) throws IOException, InterruptedException {
        http.send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/photos/" + id)).DELETE().build(),
                HttpResponse.BodyHandlers.discarding());
    }

    private PhotoItem parsePhoto(JsonNode n) {
        return new PhotoItem(
                n.path("id").asLong(),
                n.path("frameName").asText("Layout"),
                n.path("themeName").asText(""),
                n.path("caption").asText(""),
                n.path("shotCount").asInt(),
                n.path("width").asInt(),
                n.path("height").asInt(),
                n.path("createdAt").asText(""));
    }

    private void writeField(ByteArrayOutputStream out, String boundary, String name, String value) throws IOException {
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                + value + "\r\n").getBytes(StandardCharsets.UTF_8));
    }
}

