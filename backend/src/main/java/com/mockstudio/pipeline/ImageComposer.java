package com.mockstudio.pipeline;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * 「模拟生成」的图像合成：把素材按数量布局到画布上并加上边框与水印图形。
 * 刻意不绘制文字，避免容器缺少字体导致的渲染问题。
 */
final class ImageComposer {

    static final int WIDTH = 900;
    static final int HEIGHT = 1200;
    private static final int PAD = 36;
    private static final int GAP = 18;

    private ImageComposer() {
    }

    static BufferedImage compose(List<BufferedImage> images, String seed) {
        BufferedImage canvas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            Color accent = accentFor(seed);
            g.setColor(new Color(24, 24, 28));
            g.fillRect(0, 0, WIDTH, HEIGHT);

            List<Rectangle> slots = layout(images.size(), new Rectangle(PAD, PAD, WIDTH - PAD * 2, HEIGHT - PAD * 2 - 90));
            for (int i = 0; i < images.size(); i++) {
                drawCover(g, images.get(i), slots.get(i));
            }

            // 底部色带 + 圆点徽标，作为「模拟生成」的可见标记
            g.setColor(accent);
            g.fillRect(0, HEIGHT - 90, WIDTH, 90);
            g.setColor(Color.WHITE);
            for (int i = 0; i < 3; i++) {
                g.fill(new Ellipse2D.Double(PAD + i * 34, HEIGHT - 60, 22, 22));
            }

            // 半透明斜向水印条
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.18f));
            g.setColor(Color.WHITE);
            g.rotate(Math.toRadians(-30), WIDTH / 2.0, HEIGHT / 2.0);
            for (int y = -HEIGHT; y < HEIGHT * 2; y += 160) {
                g.fillRect(-WIDTH, y, WIDTH * 3, 26);
            }
            g.rotate(Math.toRadians(30), WIDTH / 2.0, HEIGHT / 2.0);
            g.setComposite(AlphaComposite.SrcOver);

            g.setColor(accent);
            g.setStroke(new BasicStroke(10));
            g.drawRect(5, 5, WIDTH - 10, HEIGHT - 10);
        } finally {
            g.dispose();
        }
        return canvas;
    }

    static List<Rectangle> layout(int n, Rectangle area) {
        List<Rectangle> r = new ArrayList<>();
        if (n <= 1) {
            r.add(area);
        } else if (n == 2) {
            int h = (area.height - GAP) / 2;
            r.add(new Rectangle(area.x, area.y, area.width, h));
            r.add(new Rectangle(area.x, area.y + h + GAP, area.width, h));
        } else {
            int topH = (area.height - GAP) * 3 / 5;
            r.add(new Rectangle(area.x, area.y, area.width, topH));
            int rest = n - 1;
            int w = (area.width - GAP * (rest - 1)) / rest;
            int y = area.y + topH + GAP;
            int h = area.height - topH - GAP;
            for (int i = 0; i < rest; i++) {
                r.add(new Rectangle(area.x + i * (w + GAP), y, w, h));
            }
        }
        return r;
    }

    /** object-fit: cover，裁切居中并带圆角。 */
    private static void drawCover(Graphics2D g, BufferedImage img, Rectangle slot) {
        double scale = Math.max((double) slot.width / img.getWidth(), (double) slot.height / img.getHeight());
        int w = (int) Math.ceil(img.getWidth() * scale);
        int h = (int) Math.ceil(img.getHeight() * scale);
        int x = slot.x + (slot.width - w) / 2;
        int y = slot.y + (slot.height - h) / 2;
        java.awt.Shape oldClip = g.getClip();
        g.setClip(new RoundRectangle2D.Double(slot.x, slot.y, slot.width, slot.height, 28, 28));
        g.drawImage(img, x, y, w, h, null);
        g.setClip(oldClip);
    }

    private static Color accentFor(String seed) {
        int h = seed == null ? 0 : Math.abs(seed.hashCode());
        float hue = (h % 360) / 360f;
        return Color.getHSBColor(hue, 0.55f, 0.85f);
    }
}
