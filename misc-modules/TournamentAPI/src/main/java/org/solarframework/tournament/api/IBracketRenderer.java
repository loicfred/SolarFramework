package org.solarframework.tournament.api;

import org.solarframework.tournament.api.dto.BracketTheme;
import org.solarframework.tournament.obj.Phase;
import org.solarframework.tournament.obj.Tournament;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Draws a phase (bracket, group tables or swiss rounds) as a picture.
 * The PNG path uses Graphics2D; the SVG path emits standalone markup with no external assets;
 * the HTML path wraps that same SVG in a self-contained page that highlights an entrant's whole
 * run through the bracket on hover.
 */
public interface IBracketRenderer {

    byte[] renderPng(Phase phase, BracketTheme theme);
    byte[] renderPng(Phase phase);
    /** Every phase of the tournament stacked into one image. */
    byte[] renderPng(Tournament tournament, BracketTheme theme);

    void writePng(Phase phase, BracketTheme theme, OutputStream out) throws IOException;
    File writePng(Phase phase, BracketTheme theme, File target) throws IOException;

    String renderSvg(Phase phase, BracketTheme theme);
    String renderSvg(Phase phase);
    String renderSvg(Tournament tournament, BracketTheme theme);

    File writeSvg(Phase phase, BracketTheme theme, File target) throws IOException;

    String renderHtml(Phase phase, BracketTheme theme);
    String renderHtml(Phase phase);
    String renderHtml(Tournament tournament, BracketTheme theme);

    File writeHtml(Phase phase, BracketTheme theme, File target) throws IOException;
    File writeHtml(Tournament tournament, BracketTheme theme, File target) throws IOException;
}
