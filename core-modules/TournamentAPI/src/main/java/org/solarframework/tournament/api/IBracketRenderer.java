package org.solarframework.tournament.api;

import org.solarframework.tournament.api.dto.BracketTheme;
import org.solarframework.tournament.obj.IPhase;
import org.solarframework.tournament.obj.ITournament;

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

    byte[] renderPng(IPhase phase, BracketTheme theme);
    byte[] renderPng(IPhase phase);
    /** Every phase of the tournament stacked into one image. */
    byte[] renderPng(ITournament tournament, BracketTheme theme);

    void writePng(IPhase phase, BracketTheme theme, OutputStream out) throws IOException;
    File writePng(IPhase phase, BracketTheme theme, File target) throws IOException;

    String renderSvg(IPhase phase, BracketTheme theme);
    String renderSvg(IPhase phase);
    String renderSvg(ITournament tournament, BracketTheme theme);

    File writeSvg(IPhase phase, BracketTheme theme, File target) throws IOException;

    String renderHtml(IPhase phase, BracketTheme theme);
    String renderHtml(IPhase phase);
    String renderHtml(ITournament tournament, BracketTheme theme);

    File writeHtml(IPhase phase, BracketTheme theme, File target) throws IOException;
    File writeHtml(ITournament tournament, BracketTheme theme, File target) throws IOException;
}
