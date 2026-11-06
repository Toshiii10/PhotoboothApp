package com.photobooth.backend.model;

import java.awt.Color;

/**
 * Visual styles applied to built-in layouts.
 */
public enum Theme {
    //            name             bgTop      bgBottom   text       accent     border     bw  radius style            outline confetti
    CLASSIC("Classic White", "#FFFFFF", "#F1EDE6", "#2B2B2B", "#C9A227", null, 0, 10, Style.STANDARD, false, false),
    MIDNIGHT("Midnight Glam", "#141E30", "#243B55", "#FFFFFF", "#F7C873", "#F7C873", 4, 14, Style.STANDARD, true, false),
    BLUSH("Blush Pastel", "#FFE3E8", "#F5A3B5", "#6B2D3E", "#FFFFFF", "#FFFFFF", 10, 22, Style.STANDARD, false, true),
    FILM("Film Strip", "#111111", "#1C1C1C", "#F5F5F5", "#E0B04B", null, 0, 4, Style.FILM, false, false),
    POLAROID("Polaroid", "#E9E2D6", "#D6CAB6", "#3A3A3A", "#B5523B", null, 0, 0, Style.POLAROID, false, false),
    NEON("Neon Party", "#0F0C29", "#302B63", "#FF4ECD", "#00F5D4", "#00F5D4", 4, 16, Style.STANDARD, true, true),
    SUNSET("Golden Hour", "#FF7E5F", "#FEB47B", "#FFFFFF", "#FFF4E0", "#FFFFFF", 8, 18, Style.STANDARD, false, true),
    MINT("Fresh Mint", "#E0F7EF", "#9EE6CF", "#1F4D40", "#1F4D40", "#FFFFFF", 8, 20, Style.STANDARD, false, false);

    public enum Style { STANDARD, FILM, POLAROID }

    private final String displayName;
    private final String bgTop, bgBottom, text, accent, border;
    private final int borderWidth, radius;
    private final Style style;
    private final boolean outline, confetti;

    Theme(String displayName, String bgTop, String bgBottom, String text, String accent, String border,
          int borderWidth, int radius, Style style, boolean outline, boolean confetti) {
        this.displayName = displayName;
        this.bgTop = bgTop;
        this.bgBottom = bgBottom;
        this.text = text;
        this.accent = accent;
        this.border = border;
        this.borderWidth = borderWidth;
        this.radius = radius;
        this.style = style;
        this.outline = outline;
        this.confetti = confetti;
    }

    public String getDisplayName() { return displayName; }
    public String getBgTopHex() { return bgTop; }
    public String getBgBottomHex() { return bgBottom; }
    public String getTextHex() { return text; }
    public String getAccentHex() { return accent; }
    public Color bgTop() { return Color.decode(bgTop); }
    public Color bgBottom() { return Color.decode(bgBottom); }
    public Color textColor() { return Color.decode(text); }
    public Color accent() { return Color.decode(accent); }
    public Color border() { return border == null ? null : Color.decode(border); }
    public int getBorderWidth() { return border == null ? 0 : borderWidth; }
    public int getRadius() { return radius; }
    public Style getStyle() { return style; }
    public boolean isOutline() { return outline; }
    public boolean isConfetti() { return confetti; }
}

