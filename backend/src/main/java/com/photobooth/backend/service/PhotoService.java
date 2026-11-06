package com.photobooth.backend.service;

import com.photobooth.backend.entity.CustomFrame;
import com.photobooth.backend.entity.Photo;
import com.photobooth.backend.model.FrameLayout;
import com.photobooth.backend.model.Theme;
import com.photobooth.backend.repository.PhotoRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

@Service
public class PhotoService {

    private final PhotoRepository repo;
    private final FrameService frames;
    private final CompositionService composition;
    private final StorageService storage;

    public PhotoService(PhotoRepository repo, FrameService frames, CompositionService composition,
                        StorageService storage) {
        this.repo = repo;
        this.frames = frames;
        this.composition = composition;
        this.storage = storage;
    }

    public Photo create(String frameType, String layoutId, String themeId, Long frameId, String caption,
                        List<MultipartFile> files) throws IOException {
        if (files == null || files.isEmpty()) throw new IllegalArgumentException("No photos were uploaded");
        List<BufferedImage> shots = new ArrayList<>();
        for (MultipartFile f : files) {
            BufferedImage img = ImageIO.read(f.getInputStream());
            if (img == null) throw new IllegalArgumentException("Invalid image: " + f.getOriginalFilename());
            shots.add(img);
        }

        BufferedImage result;
        String frameName;
        String themeName = null;
        String cleanCaption = caption == null ? null : caption.strip();

        if ("CUSTOM".equalsIgnoreCase(frameType)) {
            if (frameId == null) throw new IllegalArgumentException("frameId is required for custom frames");
            CustomFrame frame = frames.get(frameId);
            List<Rectangle> slots = frame.getSlotRects();
            result = composition.composeCustom(shots, storage.readFrame(frame.getFileName()), slots);
            frameName = frame.getName();
            cleanCaption = null;
        } else {
            FrameLayout layout = parse(FrameLayout.class, layoutId, "layout");
            Theme theme = (themeId == null || themeId.isBlank()) ? Theme.CLASSIC : parse(Theme.class, themeId, "theme");
            result = composition.composeLayout(shots, layout, theme, cleanCaption);
            frameName = layout.getDisplayName();
            themeName = theme.getDisplayName();
        }

        String file = storage.savePhoto(result);
        String thumb = storage.saveThumbnail(result);
        return repo.save(new Photo(file, thumb, frameName, themeName, cleanCaption, shots.size(),
                result.getWidth(), result.getHeight()));
    }

    public List<Photo> list() {
        return repo.findAllByOrderByCreatedAtDesc();
    }

    public Photo get(Long id) {
        return repo.findById(id).orElseThrow(() -> new NoSuchElementException("Photo not found"));
    }

    public void delete(Long id) {
        Photo p = get(id);
        repo.delete(p);
        storage.deleteQuietly(storage.photoPath(p.getFileName()));
        if (p.getThumbnailName() != null) storage.deleteQuietly(storage.thumbnailPath(p.getThumbnailName()));
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, String what) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + what);
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown " + what + ": " + value);
        }
    }
}

