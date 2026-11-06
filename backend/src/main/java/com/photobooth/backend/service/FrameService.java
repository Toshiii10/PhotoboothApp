package com.photobooth.backend.service;

import com.photobooth.backend.entity.CustomFrame;
import com.photobooth.backend.repository.CustomFrameRepository;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class FrameService {

    public static final int MAX_SLOTS = 12;

    private final CustomFrameRepository repo;
    private final StorageService storage;

    public FrameService(CustomFrameRepository repo, StorageService storage) {
        this.repo = repo;
        this.storage = storage;
    }

    public List<CustomFrame> list() {
        return repo.findAllByOrderByCreatedAtDesc();
    }

    public CustomFrame get(Long id) {
        return repo.findById(id).orElseThrow(() -> new NoSuchElementException("Frame not found"));
    }

    public CustomFrame create(String name, byte[] data) throws IOException {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
        if (img == null) throw new IllegalArgumentException("Unsupported image. Please upload a PNG file.");
        return create(name, img);
    }

    public CustomFrame create(String name, BufferedImage img) throws IOException {
        if (!img.getColorModel().hasAlpha()) {
            throw new IllegalArgumentException("This image has no transparency. Upload a PNG with transparent "
                    + "windows where the photos should appear.");
        }
        List<Rectangle> slots = SlotDetector.detect(img);
        if (slots.isEmpty()) {
            throw new IllegalArgumentException("No transparent photo windows were found. Make the areas where "
                    + "photos should go fully transparent.");
        }
        if (slots.size() > MAX_SLOTS) {
            throw new IllegalArgumentException("Found " + slots.size() + " transparent areas – the maximum is "
                    + MAX_SLOTS + ". Make sure small decorations are not transparent.");
        }
        String file = storage.saveFrame(ImageUtils.toArgb(img));
        String n = (name == null || name.isBlank()) ? "Custom Frame" : name.trim();
        if (n.length() > 100) n = n.substring(0, 100);
        return repo.save(new CustomFrame(n, file, img.getWidth(), img.getHeight(), slots));
    }

    public void delete(Long id) {
        CustomFrame f = get(id);
        repo.delete(f);
        storage.deleteQuietly(storage.framePath(f.getFileName()));
    }
}

