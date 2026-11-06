package com.photobooth.backend.controller;

import com.photobooth.backend.controller.Dtos.*;
import com.photobooth.backend.entity.CustomFrame;
import com.photobooth.backend.entity.Photo;
import com.photobooth.backend.model.FrameLayout;
import com.photobooth.backend.model.Theme;
import com.photobooth.backend.service.FrameService;
import com.photobooth.backend.service.PhotoService;
import com.photobooth.backend.service.StorageService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api")
public class PhotoController {

    private final PhotoService photos;
    private final FrameService frames;
    private final StorageService storage;

    public PhotoController(PhotoService photos, FrameService frames, StorageService storage) {
        this.photos = photos;
        this.frames = frames;
        this.storage = storage;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "storage", storage.root().toString());
    }

    @GetMapping("/layouts")
    public List<LayoutDto> layouts() {
        return Arrays.stream(FrameLayout.values()).map(LayoutDto::from).toList();
    }

    @GetMapping("/themes")
    public List<ThemeDto> themes() {
        return Arrays.stream(Theme.values()).map(ThemeDto::from).toList();
    }

    // --------------------------------------------------------- Custom Frames

    @GetMapping("/frames")
    public List<FrameDto> listFrames() {
        return frames.list().stream().map(FrameDto::from).toList();
    }

    @PostMapping(value = "/frames", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadFrame(@RequestParam("name") String name,
                                         @RequestParam("file") MultipartFile file) {
        try {
            CustomFrame created = frames.create(name, file.getBytes());
            return ResponseEntity.ok(FrameDto.from(created));
        } catch (IllegalArgumentException | IOException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping(value = "/frames/{id}/image", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<Resource> frameImage(@PathVariable Long id) {
        try {
            CustomFrame f = frames.get(id);
            Path p = storage.framePath(f.getFileName());
            if (!Files.exists(p)) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(new FileSystemResource(p));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/frames/{id}")
    public ResponseEntity<?> deleteFrame(@PathVariable Long id) {
        try {
            frames.delete(id);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // --------------------------------------------------------- Photos

    @GetMapping("/photos")
    public List<PhotoDto> listPhotos() {
        return photos.list().stream().map(PhotoDto::from).toList();
    }

    @PostMapping(value = "/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> compose(@RequestParam(value = "frameType", defaultValue = "BUILTIN") String frameType,
                                     @RequestParam(value = "layout", required = false) String layout,
                                     @RequestParam(value = "theme", required = false) String theme,
                                     @RequestParam(value = "frameId", required = false) Long frameId,
                                     @RequestParam(value = "caption", required = false) String caption,
                                     @RequestParam("frames") List<MultipartFile> shotFiles) {
        try {
            Photo p = photos.create(frameType, layout, theme, frameId, caption, shotFiles);
            return ResponseEntity.ok(PhotoDto.from(p));
        } catch (IllegalArgumentException | NoSuchElementException | IOException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping(value = "/photos/{id}/image", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<Resource> photoImage(@PathVariable Long id) {
        try {
            Photo p = photos.get(id);
            Path path = storage.photoPath(p.getFileName());
            if (!Files.exists(path)) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(new FileSystemResource(path));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping(value = "/photos/{id}/thumbnail", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<Resource> photoThumbnail(@PathVariable Long id) {
        try {
            Photo p = photos.get(id);
            Path path = p.getThumbnailName() != null
                    ? storage.thumbnailPath(p.getThumbnailName())
                    : storage.photoPath(p.getFileName());
            if (!Files.exists(path)) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(new FileSystemResource(path));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/photos/{id}")
    public ResponseEntity<?> deletePhoto(@PathVariable Long id) {
        try {
            photos.delete(id);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
