package org.solarframework.tournament.impl.render;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/** Paints a {@link RenderModel} onto a raster image with Graphics2D. */
public final class PngPainter {
    private PngPainter() {}

    public static BufferedImage paint(RenderModel model, double scale) {
        int w = Math.max(1, (int) Math.ceil(model.width * scale));
        int h = Math.max(1, (int) Math.ceil(model.height * scale));
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.scale(scale, scale);
        g.setColor(color(model.background));
        g.fillRect(0, 0, model.width, model.height);
        for (RenderModel.Shape s : model.shapes) paint(g, s);
        g.dispose();
        return img;
    }

    public static byte[] toBytes(RenderModel model, double scale) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(paint(model, scale), "png", out);
        return out.toByteArray();
    }

    public static void write(RenderModel model, double scale, OutputStream out) throws IOException { ImageIO.write(paint(model, scale), "png", out); }

    private static void paint(Graphics2D g, RenderModel.Shape shape) {
        switch (shape) {
            case RenderModel.Box b -> paintBox(g, b);
            case RenderModel.Text t -> paintText(g, t);
            case RenderModel.Poly p -> paintPoly(g, p);
        }
    }

    private static void paintBox(Graphics2D g, RenderModel.Box b) {
        RoundRectangle2D shape = new RoundRectangle2D.Double(b.x(), b.y(), b.w(), b.h(), b.radius() * 2.0, b.radius() * 2.0);
        if (b.fill() != null) { g.setColor(color(b.fill())); g.fill(shape); }
        if (b.stroke() != null) {
            g.setColor(color(b.stroke()));
            g.setStroke(new BasicStroke((float) b.strokeWidth()));
            g.draw(shape);
        }
    }

    private static void paintText(Graphics2D g, RenderModel.Text t) {
        g.setColor(color(t.color()));
        g.setFont(new Font(Font.SANS_SERIF, t.bold() ? Font.BOLD : Font.PLAIN, t.size()));
        FontMetrics fm = g.getFontMetrics();
        double x = switch (t.anchor()) {
            case "middle" -> t.x() - fm.stringWidth(t.text()) / 2.0;
            case "end" -> t.x() - fm.stringWidth(t.text());
            default -> t.x();
        };
        g.drawString(t.text(), (float) x, (float) t.y());
    }

    private static void paintPoly(Graphics2D g, RenderModel.Poly p) {
        List<double[]> pts = p.points();
        if (pts.size() < 2) return;
        Path2D.Double path = new Path2D.Double();
        path.moveTo(pts.getFirst()[0], pts.getFirst()[1]);
        for (int i = 1; i < pts.size(); i++) path.lineTo(pts.get(i)[0], pts.get(i)[1]);
        g.setColor(color(p.stroke()));
        g.setStroke(new BasicStroke((float) p.strokeWidth(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(path);
    }

    /** Accepts #RGB, #RRGGBB or #RRGGBBAA. */
    private static Color color(String hex) {
        if (hex == null || hex.isBlank()) return Color.BLACK;
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        if (h.length() == 3) h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
        long v = Long.parseLong(h, 16);
        if (h.length() == 8) return new Color((int) (v >> 24) & 0xFF, (int) (v >> 16) & 0xFF, (int) (v >> 8) & 0xFF, (int) v & 0xFF);
        return new Color((int) v);
    }
}
