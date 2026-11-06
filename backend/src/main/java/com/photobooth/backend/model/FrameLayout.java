package com.photobooth.backend.model;

/**
 * Built-in multi-frame layouts: how many shots are combined into one final image and how they are arranged.
 */
public enum FrameLayout {
    SINGLE("Single", 1, 1),
    STRIP_2("Duo Strip", 1, 2),
    STRIP_3("Strip of 3", 1, 3),
    STRIP_4("Classic Strip", 1, 4),
    HORIZONTAL_3("Wide Trio", 3, 1),
    GRID_2X2("Grid 2×2", 2, 2),
    GRID_3X2("Grid 3×2", 3, 2);

    private final String displayName;
    private final int columns;
    private final int rows;

    FrameLayout(String displayName, int columns, int rows) {
        this.displayName = displayName;
        this.columns = columns;
        this.rows = rows;
    }

    public String getDisplayName() { return displayName; }
    public int getColumns() { return columns; }
    public int getRows() { return rows; }
    public int getFrameCount() { return columns * rows; }
}
