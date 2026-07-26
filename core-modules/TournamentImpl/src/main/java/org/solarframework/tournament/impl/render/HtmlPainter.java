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
                html, body { margin: 0; padding: 0; background: %s; }
                .bracket-wrap { width: 100%%; overflow: auto; padding: 8px; }
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
            })();
            """;
}
