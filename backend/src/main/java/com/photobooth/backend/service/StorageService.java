package com.photobooth.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/** Stores image files on disk. Metadata lives in the database. */
@Service
public class StorageService {

    private final Path root;
    private final Path photosDir;
    private final Path thumbsDir;
    private final Path framesDir;

    public StorageService(@Value("${photobooth.home}") String home) throws IOException {
        this.root = Paths.get(home).toAbsolutePath().normalize();
        this.photosDir = Files.createDirectories(root.resolve("photos"));
        this.thumbsDir = Files.createDirectories(root.resolve("thumbnails"));
        this.framesDir = Files.createDirectories(root.resolve("frames"));
    }

    public Path root() { return root; }

    public String savePhoto(BufferedImage img) throws IOException {
        String name = newName("png");
        ImageIO.write(img, "png", photosDir.resolve(name).toFile());
        return name;
    }

    public String saveThumbnail(BufferedImage img) throws IOException {
        String name = newName("jpg");
        BufferedImage thumb = ImageUtils.toRgb(ImageUtils.scaleToFit(img, 520), Color.WHITE);
        ImageIO.write(thumb, "jpg", thumbsDir.resolve(name).toFile());
        return name;
    }

    public String saveFrame(BufferedImage img) throws IOException {
        String name = newName("png");
        ImageIO.write(img, "png", framesDir.resolve(name).toFile());
        return name;
    }

    public BufferedImage readFrame(String name) throws IOException {
        BufferedImage img = ImageIO.read(framePath(name).toFile());
        if (img == null) throw new IOException("Frame image is missing or unreadable: " + name);
        return img;
    }

    public Path photoPath(String name) { return safe(photosDir, name); }
    public Path thumbnailPath(String name) { return safe(thumbsDir, name); }
    public Path framePath(String name) { return safe(framesDir, name); }

    public void deleteQuietly(Path p) {
        try {
            if (p != null) Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // best effort
        }
    }

    private static String newName(String ext) {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + "-"
                + UUID.randomUUID().toString().substring(0, 8) + "." + ext;
    }

    private static Path safe(Path dir, String name) {
        Path p = dir.resolve(name).normalize();
        if (!p.startsWith(dir)) throw new IllegalArgumentException("Invalid file name");
        return p;
    }
}
