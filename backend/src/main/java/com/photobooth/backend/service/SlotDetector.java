package com.photobooth.backend.service;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Finds the transparent "windows" in a frame PNG. Each window becomes a photo slot.
 * Slots are returned in reading order (top-to-bottom, then left-to-right).
 */
public final class SlotDetector {

    private static final int ALPHA_THRESHOLD = 40;

    private SlotDetector() {
    }

    public static List<Rectangle> detect(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        if (!img.getColorModel().hasAlpha()) return List.of();

        int[] px = img.getRGB(0, 0, w, h, null, 0, w);
        boolean[] open = new boolean[w * h];
        for (int i = 0; i < px.length; i++) open[i] = (px[i] >>> 24) < ALPHA_THRESHOLD;
        px = null;

        int[] queue = new int[w * h];
        long minArea = (long) w * h / 250; // windows must be at least 0.4% of the frame
        List<Rectangle> found = new ArrayList<>();

        for (int start = 0; start < open.length; start++) {
            if (!open[start]) continue;
            int head = 0, tail = 0;
            queue[tail++] = start;
            open[start] = false;
            int minX = w, minY = h, maxX = -1, maxY = -1;
            long count = 0;
            while (head < tail) {
                int p = queue[head++];
                int x = p % w, y = p / w;
                count++;
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
                if (y < minY) minY = y;
                if (y > maxY) maxY = y;
                if (x > 0 && open[p - 1]) { open[p - 1] = false; queue[tail++] = p - 1; }
                if (x < w - 1 && open[p + 1]) { open[p + 1] = false; queue[tail++] = p + 1; }
                if (y > 0 && open[p - w]) { open[p - w] = false; queue[tail++] = p - w; }
                if (y < h - 1 && open[p + w]) { open[p + w] = false; queue[tail++] = p + w; }
            }
            int bw = maxX - minX + 1, bh = maxY - minY + 1;
            boolean coversWholeImage = bw > w * 0.95 && bh > h * 0.95;
            if (count >= minArea && bw >= 32 && bh >= 32 && !coversWholeImage) {
                found.add(new Rectangle(minX, minY, bw, bh));
            }
        }
        return readingOrder(found);
    }

    private static List<Rectangle> readingOrder(List<Rectangle> rects) {
        List<Rectangle> byY = new ArrayList<>(rects);
        byY.sort(Comparator.comparingInt(r -> r.y));
        List<List<Rectangle>> rows = new ArrayList<>();
        List<Rectangle> row = null;
        int rowTop = 0, rowHeight = 0;
        for (Rectangle r : byY) {
            if (row == null || r.y >= rowTop + rowHeight / 2) {
                row = new ArrayList<>();
                rows.add(row);
                rowTop = r.y;
                rowHeight = r.height;
            }
            row.add(r);
        }
        List<Rectangle> out = new ArrayList<>();
        for (List<Rectangle> rr : rows) {
            rr.sort(Comparator.comparingInt(r -> r.x));
            out.addAll(rr);
        }
        return out;
    }
}

