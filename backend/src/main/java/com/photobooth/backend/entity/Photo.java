package com.photobooth.backend.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * A finished, composed photobooth picture.
 */
@Entity
@Table(name = "photos")
public class Photo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fileName;

    private String thumbnailName;

    @Column(length = 100)
    private String frameName;

    @Column(length = 100)
    private String themeName;

    @Column(length = 200)
    private String caption;

    private int shotCount;
    private int width;
    private int height;
    private Instant createdAt;

    protected Photo() {
    }

    public Photo(String fileName, String thumbnailName, String frameName, String themeName, String caption,
                 int shotCount, int width, int height) {
        this.fileName = fileName;
        this.thumbnailName = thumbnailName;
        this.frameName = frameName;
        this.themeName = themeName;
        this.caption = caption;
        this.shotCount = shotCount;
        this.width = width;
        this.height = height;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getFileName() { return fileName; }
    public String getThumbnailName() { return thumbnailName; }
    public String getFrameName() { return frameName; }
    public String getThemeName() { return themeName; }
    public String getCaption() { return caption; }
    public int getShotCount() { return shotCount; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public Instant getCreatedAt() { return createdAt; }
}

