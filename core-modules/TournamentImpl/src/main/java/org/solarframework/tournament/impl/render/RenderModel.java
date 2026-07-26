package org.solarframework.tournament.impl.render;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolved drawing instructions, independent of the output format. The layout is computed once and
 * every painter (PNG, SVG, HTML) walks the same model, so the outputs cannot drift apart.
 */
public class RenderModel {
    public int width;
    public int height;
    public String background = "#000000";
    public final List<Shape> shapes = new ArrayList<>();

    public sealed interface Shape permits Box, Text, Poly {}

    /** Filled rectangle with an optional border and rounded corners. {@code dataId}, when set, tags the shape with a participant ID for hover highlighting in the HTML export. */
    public record Box(double x, double y, double w, double h, String fill, String stroke, double strokeWidth, int radius, String dataId) implements Shape {}

    /** A single line of text. {@code anchor} is start, middle or end. */
    public record Text(double x, double y, String text, int size, String color, String anchor, boolean bold, String dataId) implements Shape {}

    /** Open polyline used for the bracket connectors. */
    public record Poly(List<double[]> points, String stroke, double strokeWidth) implements Shape {}

    public void box(double x, double y, double w, double h, String fill, String stroke, int radius) { box(x, y, w, h, fill, stroke, radius, null); }

    public void box(double x, double y, double w, double h, String fill, String stroke, int radius, String dataId) {
        shapes.add(new Box(x, y, w, h, fill, stroke, 1, radius, dataId));
    }

    public void text(double x, double y, String text, int size, String color, String anchor, boolean bold) { text(x, y, text, size, color, anchor, bold, null); }

    public void text(double x, double y, String text, int size, String color, String anchor, boolean bold, String dataId) {
        if (text == null || text.isEmpty()) return;
        shapes.add(new Text(x, y, text, size, color, anchor, bold, dataId));
    }

    public void poly(String stroke, double strokeWidth, double... coords) {
        List<double[]> pts = new ArrayList<>();
        for (int i = 0; i + 1 < coords.length; i += 2) pts.add(new double[]{coords[i], coords[i + 1]});
        shapes.add(new Poly(pts, stroke, strokeWidth));
    }

    /**
     * Approximate width of a string, used for truncation. Deliberately format-agnostic: the same
     * estimate drives both painters so a name is clipped at the same point in the PNG and the SVG.
     */
    public static double textWidth(String s, int fontSize) { return s == null ? 0 : s.length() * fontSize * 0.55; }

    /** Truncates with an ellipsis so text never overflows its box. */
    public static String fit(String s, int fontSize, double maxWidth) {
        if (s == null || s.isEmpty()) return "";
        if (textWidth(s, fontSize) <= maxWidth) return s;
        int max = Math.max(1, (int) (maxWidth / (fontSize * 0.55)) - 1);
        return s.length() <= max ? s : s.substring(0, max).trim() + "…";
    }

    /** XML-escapes a string for the SVG/HTML output. */
    public static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
