package com.photobooth.backend.entity;

import jakarta.persistence.*;

import java.awt.Rectangle;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A user-uploaded frame: a PNG overlay whose transparent windows are the photo slots.
 */
@Entity
@Table(name = "custom_frames")
public class CustomFrame {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private String fileName;

    private int width;
    private int height;
    private int slotCount;

    /** Slots encoded as "x,y,w,h;x,y,w,h;..." in fill order. */
    @Column(length = 4000)
    private String slots;

    private Instant createdAt;

    protected CustomFrame() {
    }

    public CustomFrame(String name, String fileName, int width, int height, List<Rectangle> slotRects) {
        this.name = name;
        this.fileName = fileName;
        this.width = width;
        this.height = height;
        this.slotCount = slotRects.size();
        StringBuilder sb = new StringBuilder();
        for (Rectangle r : slotRects) {
            if (!sb.isEmpty()) sb.append(';');
            sb.append(r.x).append(',').append(r.y).append(',').append(r.width).append(',').append(r.height);
        }
        this.slots = sb.toString();
        this.createdAt = Instant.now();
    }

    public List<Rectangle> getSlotRects() {
        List<Rectangle> out = new ArrayList<>();
        if (slots == null || slots.isBlank()) return out;
        for (String part : slots.split(";")) {
            String[] v = part.split(",");
            out.add(new Rectangle(Integer.parseInt(v[0]), Integer.parseInt(v[1]),
                    Integer.parseInt(v[2]), Integer.parseInt(v[3])));
        }
        return out;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getFileName() { return fileName; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getSlotCount() { return slotCount; }
    public Instant getCreatedAt() { return createdAt; }
}

