package com.photobooth.backend.service;

import com.photobooth.backend.model.FrameLayout;
import com.photobooth.backend.model.Theme;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.font.GlyphVector;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;

/**
 * Combines multiple captured shots into a single photobooth picture, either with a built-in
 * layout + theme, or into a user-uploaded custom frame.
 */
@Service
public class CompositionService {

    static final int CELL_W = 600;
    static final int CELL_H = 450;
    static final int FOOTER_H = 170;

    // ------------------------------------------------------------------ built-in layouts

    public BufferedImage composeLayout(List<BufferedImage> shots, FrameLayout layout, Theme theme, String caption) {
        if (shots.size() != layout.getFrameCount()) {
            throw new IllegalArgumentException(layout.getDisplayName() + " needs " + layout.getFrameCount()
                    + " photos but got " + shots.size());
        }
        int cols = layout.getColumns(), rows = layout.getRows();
        boolean polaroid = theme.getStyle() == Theme.Style.POLAROID;
        boolean film = theme.getStyle() == Theme.Style.FILM;
        boolean filmHorizontal = film && rows == 1 && cols > 1;
        boolean filmVertical = film && !filmHorizontal;

        int gapX = polaroid ? 80 : 32;
        int gapY = polaroid ? 120 : 32;
        int padX = filmVertical ? 100 : (polaroid ? 80 : 60);
        int padTop = filmHorizontal ? 100 : (polaroid ? 80 : 60);
        int extraBelow = polaroid ? 70 : 0;
        int bottom = filmHorizontal ? 90 : 30;

        int width = 2 * padX + cols * CELL_W + (cols - 1) * gapX;
        int photosBottom = padTop + rows * CELL_H + (rows - 1) * gapY + extraBelow;
        int height = photosBottom + FOOTER_H + bottom;

        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = ImageUtils.quality(out);

        g.setPaint(new GradientPaint(0, 0, theme.bgTop(), width * 0.3f, height, theme.bgBottom()));
        g.fillRect(0, 0, width, height);

        if (theme.isConfetti()) drawConfetti(g, width, height, theme);
        if (film) drawSprockets(g, width, height, filmHorizontal, padX, padTop, bottom);
        if (theme.isOutline()) drawOutline(g, width, height, theme.accent());

        for (int i = 0; i < shots.size(); i++) {
            int c = i % cols, r = i / cols;
            int x = padX + c * (CELL_W + gapX);
            int y = padTop + r * (CELL_H + gapY);
            if (polaroid) drawPolaroid(g, shots.get(i), x, y, i);
            else drawFramedPhoto(g, shots.get(i), x, y, theme);
        }

        drawFooter(g, theme, caption, width, photosBottom, padX);
        g.dispose();
        return out;
    }

    private void drawFramedPhoto(Graphics2D g, BufferedImage img, int x, int y, Theme theme) {
        int r = theme.getRadius();
        int bw = theme.getBorderWidth();
        if (theme.getStyle() != Theme.Style.FILM) {
            drawShadow(g, x - bw, y - bw, CELL_W + 2 * bw, CELL_H + 2 * bw, r + bw, 14, 10);
        }
        if (bw > 0) {
            g.setColor(theme.border());
            g.fill(new RoundRectangle2D.Double(x - bw, y - bw, CELL_W + 2.0 * bw, CELL_H + 2.0 * bw,
                    2.0 * (r + bw), 2.0 * (r + bw)));
        }
        Shape oldClip = g.getClip();
        g.clip(new RoundRectangle2D.Double(x, y, CELL_W, CELL_H, 2.0 * r, 2.0 * r));
        ImageUtils.drawCover(g, img, x, y, CELL_W, CELL_H);
        g.setClip(oldClip);
    }

    private void drawPolaroid(Graphics2D g, BufferedImage img, int x, int y, int index) {
        double[] angles = {-2.2, 1.6, -1.2, 2.4, -1.8, 1.1};
        AffineTransform oldTx = g.getTransform();
        g.rotate(Math.toRadians(angles[index % angles.length]), x + CELL_W / 2.0, y + CELL_H / 2.0);
        int m = 24, extra = 60;
        int cx = x - m, cy = y - m, cw = CELL_W + 2 * m, ch = CELL_H + 2 * m + extra;
        drawShadow(g, cx, cy, cw, ch, 4, 12, 8);
        g.setColor(new Color(253, 252, 248));
        g.fillRect(cx, cy, cw, ch);
        Shape oldClip = g.getClip();
        g.clip(new Rectangle(x, y, CELL_W, CELL_H));
        ImageUtils.drawCover(g, img, x, y, CELL_W, CELL_H);
        g.setClip(oldClip);
        g.setTransform(oldTx);
    }

    private void drawShadow(Graphics2D g, double x, double y, double w, double h, double radius, int spread, int offsetY) {
        g.setColor(new Color(0, 0, 0, 6));
        for (int i = spread; i >= 1; i--) {
            g.fill(new RoundRectangle2D.Double(x - i, y - i + offsetY, w + 2.0 * i, h + 2.0 * i,
                    2 * (radius + i), 2 * (radius + i)));
        }
    }

    private void drawConfetti(Graphics2D g, int width, int height, Theme theme) {
        Random rnd = new Random(7);
        Color[] palette = {Color.WHITE, theme.accent(), theme.textColor()};
        for (int i = 0; i < 170; i++) {
            Color c = palette[i % palette.length];
            g.setColor(ImageUtils.withAlpha(c, 50 + rnd.nextInt(110)));
            double size = 5 + rnd.nextInt(12);
            double x = rnd.nextDouble() * width, y = rnd.nextDouble() * height;
            if (i % 3 == 0) {
                AffineTransform old = g.getTransform();
                g.rotate(rnd.nextDouble() * Math.PI, x, y);
                g.fill(new RoundRectangle2D.Double(x, y, size * 1.8, size * 0.7, 3, 3));
                g.setTransform(old);
            } else {
                g.fill(new Ellipse2D.Double(x, y, size, size));
            }
        }
    }

    private void drawSprockets(Graphics2D g, int width, int height, boolean horizontal, int padX, int padTop, int bottom) {
        g.setColor(new Color(236, 236, 236));
        if (horizontal) {
            int topY = padTop / 2 - 15, bottomY = height - bottom / 2 - 15;
            for (int x = 30; x + 40 < width - 20; x += 70) {
                g.fill(new RoundRectangle2D.Double(x, topY, 40, 30, 10, 10));
                g.fill(new RoundRectangle2D.Double(x, bottomY, 40, 30, 10, 10));
            }
        } else {
            int leftX = padX / 2 - 15, rightX = width - padX / 2 - 15;
            for (int y = 30; y + 40 < height - 20; y += 70) {
                g.fill(new RoundRectangle2D.Double(leftX, y, 30, 40, 10, 10));
                g.fill(new RoundRectangle2D.Double(rightX, y, 30, 40, 10, 10));
            }
        }
    }

    private void drawOutline(Graphics2D g, int width, int height, Color accent) {
        g.setColor(accent);
        g.setStroke(new BasicStroke(4f));
        g.draw(new RoundRectangle2D.Double(18, 18, width - 36, height - 36, 30, 30));
        g.setColor(ImageUtils.withAlpha(accent, 140));
        g.setStroke(new BasicStroke(1.5f));
        g.draw(new RoundRectangle2D.Double(30, 30, width - 60, height - 60, 20, 20));
        g.setStroke(new BasicStroke(1f));
    }

    private Font captionFont(Theme theme) {
        return switch (theme) {
            case CLASSIC, POLAROID, BLUSH ->
                    ImageUtils.font(Font.PLAIN, 72, "Segoe Script", "Brush Script MT", "Lucida Handwriting", "Serif");
            case MIDNIGHT -> ImageUtils.font(Font.PLAIN, 64, "Georgia", "Palatino Linotype", "Serif");
            case FILM -> ImageUtils.font(Font.BOLD, 56, "Consolas", "Courier New", "Monospaced");
            case NEON -> ImageUtils.font(Font.BOLD, 66, "Segoe UI Black", "Arial Black", "SansSerif");
            default -> ImageUtils.font(Font.BOLD, 64, "Segoe UI Semibold", "Segoe UI", "Arial", "SansSerif");
        };
    }

    private void drawFooter(Graphics2D g, Theme theme, String caption, int width, int photosBottom, int padX) {
        String text = (caption == null || caption.isBlank()) ? "Photobooth" : caption.trim();
        Color textColor = theme.textColor();
        Color accent = theme.accent();

        Font f = captionFont(theme);
        int maxW = width - 2 * padX;
        g.setFont(f);
        while (g.getFontMetrics().stringWidth(text) > maxW && f.getSize() > 20) {
            f = f.deriveFont((float) (f.getSize() - 2));
            g.setFont(f);
        }
        int baseline = photosBottom + 88;
        GlyphVector gv = f.createGlyphVector(g.getFontRenderContext(), text);
        Rectangle2D bounds = gv.getLogicalBounds();
        float tx = (float) ((width - bounds.getWidth()) / 2);
        Shape glyphs = gv.getOutline(tx, baseline);

        if (theme == Theme.NEON) {
            for (int i = 14; i >= 2; i -= 3) {
                g.setColor(ImageUtils.withAlpha(textColor, 28));
                g.setStroke(new BasicStroke(i, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(glyphs);
            }
            g.setStroke(new BasicStroke(1f));
        } else if (ImageUtils.luminance(textColor) > 0.6) {
            AffineTransform old = g.getTransform();
            g.translate(2, 3);
            g.setColor(new Color(0, 0, 0, 60));
            g.fill(glyphs);
            g.setTransform(old);
        }
        g.setColor(textColor);
        g.fill(glyphs);

        // Divider: line - diamond - line
        int cx = width / 2, dy = baseline + 30;
        g.setColor(accent);
        g.fill(new RoundRectangle2D.Double(cx - 100, dy - 1.5, 80, 3, 3, 3));
        g.fill(new RoundRectangle2D.Double(cx + 20, dy - 1.5, 80, 3, 3, 3));
        Path2D diamond = new Path2D.Double();
        diamond.moveTo(cx, dy - 8);
        diamond.lineTo(cx + 8, dy);
        diamond.lineTo(cx, dy + 8);
        diamond.lineTo(cx - 8, dy);
        diamond.closePath();
        g.fill(diamond);

        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy"));
        g.setFont(ImageUtils.font(Font.PLAIN, 26, "Segoe UI", "Arial", "SansSerif"));
        g.setColor(ImageUtils.withAlpha(textColor, 200));
        int dw = g.getFontMetrics().stringWidth(date);
        g.drawString(date, (width - dw) / 2, dy + 50);
    }

    // ------------------------------------------------------------------ custom frames

    public BufferedImage composeCustom(List<BufferedImage> shots, BufferedImage overlay, List<Rectangle> slots) {
        if (shots.size() != slots.size()) {
            throw new IllegalArgumentException("This frame needs " + slots.size() + " photos but got " + shots.size());
        }
        int w = overlay.getWidth(), h = overlay.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = ImageUtils.quality(out);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        for (int i = 0; i < slots.size(); i++) {
            Rectangle r = slots.get(i);
            // Slightly oversize so anti-aliased window edges never show a seam
            int bleed = 3;
            Shape oldClip = g.getClip();
            g.clip(new Rectangle(r.x - bleed, r.y - bleed, r.width + 2 * bleed, r.height + 2 * bleed));
            ImageUtils.drawCover(g, shots.get(i), r.x - bleed, r.y - bleed, r.width + 2 * bleed, r.height + 2 * bleed);
            g.setClip(oldClip);
        }
        g.drawImage(overlay, 0, 0, null);
        g.dispose();
        return out;
    }
}
