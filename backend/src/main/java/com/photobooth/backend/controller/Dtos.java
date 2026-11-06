package com.photobooth.backend.controller;

import com.photobooth.backend.entity.CustomFrame;
import com.photobooth.backend.entity.Photo;
import com.photobooth.backend.model.FrameLayout;
import com.photobooth.backend.model.Theme;

import java.util.List;

/** JSON payloads returned by the API. */
public final class Dtos {

    private Dtos() {
    }

    public record LayoutDto(String id, String displayName, int columns, int rows, int frameCount) {
        public static LayoutDto from(FrameLayout l) {
            return new LayoutDto(l.name(), l.getDisplayName(), l.getColumns(), l.getRows(), l.getFrameCount());
        }
    }

    public record ThemeDto(String id, String displayName, String bgTop, String bgBottom, String text,
                           String accent, String style) {
        public static ThemeDto from(Theme t) {
            return new ThemeDto(t.name(), t.getDisplayName(), t.getBgTopHex(), t.getBgBottomHex(), t.getTextHex(),
                    t.getAccentHex(), t.getStyle().name());
        }
    }

    public record SlotDto(int x, int y, int width, int height) {
    }

    public record FrameDto(Long id, String name, int width, int height, int slotCount, List<SlotDto> slots,
                           String createdAt) {
        public static FrameDto from(CustomFrame f) {
            List<SlotDto> slots = f.getSlotRects().stream()
                    .map(r -> new SlotDto(r.x, r.y, r.width, r.height)).toList();
            return new FrameDto(f.getId(), f.getName(), f.getWidth(), f.getHeight(), f.getSlotCount(), slots,
                    String.valueOf(f.getCreatedAt()));
        }
    }

    public record PhotoDto(Long id, String frameName, String themeName, String caption, int shotCount, int width,
                           int height, String createdAt) {
        public static PhotoDto from(Photo p) {
            return new PhotoDto(p.getId(), p.getFrameName(), p.getThemeName(), p.getCaption(), p.getShotCount(),
                    p.getWidth(), p.getHeight(), String.valueOf(p.getCreatedAt()));
        }
    }
}

