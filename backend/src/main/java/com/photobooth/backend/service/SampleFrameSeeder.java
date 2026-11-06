package com.photobooth.backend.service;

import com.photobooth.backend.repository.CustomFrameRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * Seeds a couple of built-in sample custom PNG frames into the database on first run so users
 * can test custom frames immediately (and see how transparent windows work).
 */
@Component
public class SampleFrameSeeder implements CommandLineRunner {

    private final CustomFrameRepository repo;
    private final FrameService frameService;

    public SampleFrameSeeder(CustomFrameRepository repo, FrameService frameService) {
        this.repo = repo;
        this.frameService = frameService;
    }

    @Override
    public void run(String... args) {
        try {
            if (repo.count() > 0) return;
            frameService.create("Gold Elegance (3 Shots)", buildGoldStrip());
            frameService.create("Retro Arcade (4 Grid)", buildArcadeGrid());
            frameService.create("Sweet Pastel Duo (2 Shots)", buildPastelDuo());
        } catch (Exception ignored) {
            // Non-fatal if seeding fails
        }
    }

    private BufferedImage buildGoldStrip() {
        int w = 720, h = 1650;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = ImageUtils.quality(img);

        g.setPaint(new GradientPaint(0, 0, new Color(18, 22, 34), w, h, new Color(34, 40, 58)));
        g.fillRect(0, 0, w, h);

        // Decorative gold border
        g.setColor(new Color(230, 190, 106));
        g.setStroke(new BasicStroke(6f));
        g.draw(new RoundRectangle2D.Double(22, 22, w - 44, h - 44, 32, 32));
        g.setStroke(new BasicStroke(2f));
        g.draw(new RoundRectangle2D.Double(34, 34, w - 68, h - 68, 24, 24));

        // Sparkle dots
        Random rnd = new Random(42);
        for (int i = 0; i < 60; i++) {
            g.setColor(new Color(247, 215, 138, 90 + rnd.nextInt(130)));
            int s = 3 + rnd.nextInt(7);
            g.fill(new Ellipse2D.Double(40 + rnd.nextInt(w - 80), 40 + rnd.nextInt(h - 80), s, s));
        }

        // Punch 3 transparent windows
        int slotW = 580, slotH = 420, startY = 75, gap = 36;
        g.setComposite(AlphaComposite.Clear);
        for (int i = 0; i < 3; i++) {
            int x = (w - slotW) / 2;
            int y = startY + i * (slotH + gap);
            g.fill(new RoundRectangle2D.Double(x, y, slotW, slotH, 24, 24));
        }
        g.setComposite(AlphaComposite.SrcOver);

        // Gold window frames
        g.setColor(new Color(230, 190, 106));
        g.setStroke(new BasicStroke(5f));
        for (int i = 0; i < 3; i++) {
            int x = (w - slotW) / 2;
            int y = startY + i * (slotH + gap);
            g.draw(new RoundRectangle2D.Double(x, y, slotW, slotH, 24, 24));
        }

        // Footer title
        g.setFont(ImageUtils.font(Font.BOLD, 52, "Georgia", "Serif"));
        String title = "✨ VIP PHOTOBOOTH ✨";
        int tw = g.getFontMetrics().stringWidth(title);
        g.drawString(title, (w - tw) / 2, h - 105);

        g.setFont(ImageUtils.font(Font.PLAIN, 24, "Segoe UI", "SansSerif"));
        g.setColor(new Color(220, 220, 230));
        String sub = "COLLECT THE MOMENTS";
        int sw = g.getFontMetrics().stringWidth(sub);
        g.drawString(sub, (w - sw) / 2, h - 60);

        g.dispose();
        return img;
    }

    private BufferedImage buildArcadeGrid() {
        int w = 1400, h = 1150;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = ImageUtils.quality(img);

        g.setPaint(new GradientPaint(0, 0, new Color(25, 12, 56), w, h, new Color(62, 18, 92)));
        g.fillRect(0, 0, w, h);

        g.setColor(new Color(0, 245, 212));
        g.setStroke(new BasicStroke(7f));
        g.draw(new RoundRectangle2D.Double(24, 24, w - 48, h - 48, 36, 36));

        int slotW = 590, slotH = 430, padX = 75, padY = 65, gapX = 70, gapY = 45;
        g.setComposite(AlphaComposite.Clear);
        for (int r = 0; r < 2; r++) {
            for (int c = 0; c < 2; c++) {
                int x = padX + c * (slotW + gapX);
                int y = padY + r * (slotH + gapY);
                g.fill(new RoundRectangle2D.Double(x, y, slotW, slotH, 20, 20));
            }
        }
        g.setComposite(AlphaComposite.SrcOver);

        g.setColor(new Color(255, 78, 205));
        g.setStroke(new BasicStroke(5f));
        for (int r = 0; r < 2; r++) {
            for (int c = 0; c < 2; c++) {
                int x = padX + c * (slotW + gapX);
                int y = padY + r * (slotH + gapY);
                g.draw(new RoundRectangle2D.Double(x, y, slotW, slotH, 20, 20));
            }
        }

        g.setColor(new Color(0, 245, 212));
        g.setFont(ImageUtils.font(Font.BOLD, 54, "Segoe UI Black", "Arial Black", "SansSerif"));
        String title = "ARCADE MEMORIES";
        int tw = g.getFontMetrics().stringWidth(title);
        g.drawString(title, (w - tw) / 2, h - 58);

        g.dispose();
        return img;
    }

    private BufferedImage buildPastelDuo() {
        int w = 720, h = 1180;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = ImageUtils.quality(img);

        g.setPaint(new GradientPaint(0, 0, new Color(255, 226, 234), 0, h, new Color(214, 236, 255)));
        g.fillRect(0, 0, w, h);

        int slotW = 580, slotH = 430, startY = 70, gap = 40;
        g.setComposite(AlphaComposite.Clear);
        for (int i = 0; i < 2; i++) {
            int x = (w - slotW) / 2;
            int y = startY + i * (slotH + gap);
            g.fill(new RoundRectangle2D.Double(x, y, slotW, slotH, 32, 32));
        }
        g.setComposite(AlphaComposite.SrcOver);

        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(10f));
        for (int i = 0; i < 2; i++) {
            int x = (w - slotW) / 2;
            int y = startY + i * (slotH + gap);
            g.draw(new RoundRectangle2D.Double(x, y, slotW, slotH, 32, 32));
        }

        g.setColor(new Color(112, 66, 98));
        g.setFont(ImageUtils.font(Font.BOLD, 50, "Segoe Script", "Serif"));
        String title = "Best Friends ♥";
        int tw = g.getFontMetrics().stringWidth(title);
        g.drawString(title, (w - tw) / 2, h - 80);

        g.dispose();
        return img;
    }
}

