package org.solarframework.tournament.impl.render;

import org.solarframework.tournament.api.dto.BracketTheme;

/**
 * Wraps an SVG bracket in a standalone, self-contained HTML page - no external assets. Every slot
 * carries a {@code data-participant} attribute (see {@link RenderModel}), and a small script lights
 * up every box belonging to that entrant on hover while dimming the rest, so you can trace one
 * team's whole run through the bracket at a glance.
 */
public final class HtmlPainter {
    private HtmlPainter() {}

    public static String render(RenderModel model, BracketTheme theme, String title) {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                <meta charset="utf-8">
                <title>%s</title>
                <style>
                %s
                </style>
                </head>
                <body>
                <div class="bracket-wrap">
                %s
                </div>
                <script>
                %s
                </script>
                </body>
                </html>
                """.formatted(RenderModel.escape(title), css(theme), SvgPainter.render(model), JS);
    }

    private static String css(BracketTheme th) {
        return """
                * { box-sizing: border-box; }
                html, body { margin: 0; padding: 0; height: 100%%; background: %s; }
                /* The wrap, not the page, is the scroller: a big bracket embedded in an iframe is then pannable in both axes. */
                .bracket-wrap { width: 100%%; height: 100%%; overflow: auto; padding: 8px; cursor: grab; }
                .bracket-wrap.panning { cursor: grabbing; user-select: none; }
                svg { display: block; }
                svg rect[data-participant], svg text[data-participant] { cursor: pointer; transition: opacity .15s ease, stroke-width .15s ease; }
                svg [data-participant].dimmed { opacity: .3; }
                svg rect.highlighted { stroke: %s; stroke-width: 3px; }
                svg text.highlighted { font-weight: bold; }
                """.formatted(th.getBackground(), th.getAccentColor());
    }

    private static final String JS = """
            (function () {
                var els = Array.prototype.slice.call(document.querySelectorAll('[data-participant]'));
                els.forEach(function (el) {
                    el.addEventListener('mouseenter', function () {
                        var id = el.getAttribute('data-participant');
                        els.forEach(function (e) {
                            var mine = e.getAttribute('data-participant') === id;
                            e.classList.toggle('highlighted', mine);
                            e.classList.toggle('dimmed', !mine);
                        });
                    });
                    el.addEventListener('mouseleave', function () {
                        els.forEach(function (e) { e.classList.remove('highlighted', 'dimmed'); });
                    });
                });
                // Grab-and-drag panning. Mouse only: touch already pans natively, and capturing it would kill pinch zoom.
                var wrap = document.querySelector('.bracket-wrap');
                if (!wrap) return;
                var sx = 0, sy = 0, ox = 0, oy = 0, panning = false;
                wrap.addEventListener('pointerdown', function (e) {
                    if (e.pointerType !== 'mouse' || e.button !== 0) return;
                    panning = true; sx = e.clientX; sy = e.clientY; ox = wrap.scrollLeft; oy = wrap.scrollTop;
                    wrap.setPointerCapture(e.pointerId);
                    wrap.classList.add('panning');
                });
                wrap.addEventListener('pointermove', function (e) {
                    if (!panning) return;
                    wrap.scrollLeft = ox - (e.clientX - sx);
                    wrap.scrollTop = oy - (e.clientY - sy);
                });
                ['pointerup', 'pointercancel'].forEach(function (t) {
                    wrap.addEventListener(t, function () { panning = false; wrap.classList.remove('panning'); });
                });
            })();
            """;
}
