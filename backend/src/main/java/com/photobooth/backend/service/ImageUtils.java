package com.photobooth.backend.service;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Java2D helpers shared by the image services. */
public final class ImageUtils {

    private static volatile Set<String> fontFamilies;

    private ImageUtils() {
    }

    public static Graphics2D quality(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        return g;
    }

    /** Scales and center-crops {@code img} so it fills the target box (caller sets the clip). */
    public static void drawCover(Graphics2D g, BufferedImage img, double x, double y, double w, double h) {
        double scale = Math.max(w / img.getWidth(), h / img.getHeight());
        double sw = img.getWidth() * scale;
        double sh = img.getHeight() * scale;
        AffineTransform at = new AffineTransform();
        at.translate(x + (w - sw) / 2, y + (h - sh) / 2);
        at.scale(scale, scale);
        g.drawImage(img, at, null);
    }

    /** High quality downscale (progressive halving) so the longest side is at most {@code maxDim}. */
    public static BufferedImage scaleToFit(BufferedImage src, int maxDim) {
        int w = src.getWidth(), h = src.getHeight();
        double scale = Math.min(1.0, (double) maxDim / Math.max(w, h));
        int tw = Math.max(1, (int) Math.round(w * scale));
        int th = Math.max(1, (int) Math.round(h * scale));
        BufferedImage cur = src;
        while (cur.getWidth() / 2 >= tw && cur.getHeight() / 2 >= th) {
            cur = resize(cur, cur.getWidth() / 2, cur.getHeight() / 2);
        }
        if (cur.getWidth() != tw || cur.getHeight() != th) cur = resize(cur, tw, th);
        return cur;
    }

    private static BufferedImage resize(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = quality(out);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    public static BufferedImage toArgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_ARGB) return src;
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    public static BufferedImage toRgb(BufferedImage src, Color background) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(background);
        g.fillRect(0, 0, src.getWidth(), src.getHeight());
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    /** First installed font family from the list (falls back to the last entry, which should be logical). */
    public static Font font(int style, int size, String... families) {
        Set<String> available = fontFamilies;
        if (available == null) {
            available = new HashSet<>(Arrays.asList(
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
            fontFamilies = available;
        }
        for (String f : families) {
            if (available.contains(f)) return new Font(f, style, size);
        }
        return new Font(families[families.length - 1], style, size);
    }

    public static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    public static double luminance(Color c) {
        return (0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue()) / 255.0;
    }
}

