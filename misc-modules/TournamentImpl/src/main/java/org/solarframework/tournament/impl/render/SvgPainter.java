package org.solarframework.tournament.impl.render;

import java.util.Locale;

/** Serialises a {@link RenderModel} to standalone SVG markup - no external fonts or assets. */
public final class SvgPainter {
    private SvgPainter() {}

    public static String render(RenderModel model) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(model.width).append("\" height=\"").append(model.height)
                .append("\" viewBox=\"0 0 ").append(model.width).append(' ').append(model.height).append("\">\n");
        sb.append("<rect x=\"0\" y=\"0\" width=\"100%\" height=\"100%\" fill=\"").append(model.background).append("\"/>\n");
        for (RenderModel.Shape s : model.shapes) append(sb, s);
        sb.append("</svg>\n");
        return sb.toString();
    }

    private static void append(StringBuilder sb, RenderModel.Shape shape) {
        switch (shape) {
            case RenderModel.Box b -> appendBox(sb, b);
            case RenderModel.Text t -> appendText(sb, t);
            case RenderModel.Poly p -> appendPoly(sb, p);
        }
    }

    private static void appendBox(StringBuilder sb, RenderModel.Box b) {
        sb.append("<rect x=\"").append(n(b.x())).append("\" y=\"").append(n(b.y())).append("\" width=\"").append(n(b.w())).append("\" height=\"").append(n(b.h()))
                .append("\" rx=\"").append(b.radius()).append('"');
        if (b.fill() != null) sb.append(" fill=\"").append(b.fill()).append('"'); else sb.append(" fill=\"none\"");
        if (b.stroke() != null) sb.append(" stroke=\"").append(b.stroke()).append("\" stroke-width=\"").append(n(b.strokeWidth())).append('"');
        appendDataId(sb, b.dataId());
        sb.append("/>\n");
    }

    private static void appendText(StringBuilder sb, RenderModel.Text t) {
        sb.append("<text x=\"").append(n(t.x())).append("\" y=\"").append(n(t.y())).append("\" font-size=\"").append(t.size())
                .append("\" fill=\"").append(t.color()).append("\" text-anchor=\"").append(t.anchor()).append('"')
                .append(t.bold() ? " font-weight=\"bold\"" : "").append(" font-family=\"sans-serif\"");
        appendDataId(sb, t.dataId());
        sb.append('>').append(RenderModel.escape(t.text())).append("</text>\n");
    }

    private static void appendDataId(StringBuilder sb, String dataId) {
        if (dataId != null) sb.append(" data-participant=\"").append(RenderModel.escape(dataId)).append('"');
    }

    private static void appendPoly(StringBuilder sb, RenderModel.Poly p) {
        sb.append("<polyline fill=\"none\" stroke=\"").append(p.stroke()).append("\" stroke-width=\"").append(n(p.strokeWidth()))
                .append("\" stroke-linecap=\"round\" stroke-linejoin=\"round\" points=\"");
        for (double[] pt : p.points()) sb.append(n(pt[0])).append(',').append(n(pt[1])).append(' ');
        sb.append("\"/>\n");
    }

    private static String n(double d) { return String.format(Locale.ROOT, "%.1f", d); }
}
