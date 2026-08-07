package org.solarframework.tournament.impl.render;

import org.solarframework.tournament.api.IBracketRenderer;
import org.solarframework.tournament.api.TournamentRegistry;
import org.solarframework.tournament.api.dto.BracketTheme;
import org.solarframework.tournament.obj.IPhase;
import org.solarframework.tournament.obj.ITournament;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/** Default {@link IBracketRenderer}: lays a phase out with {@link BracketLayout}, then paints it as PNG or SVG. */
public class BracketRenderer implements IBracketRenderer {

    public BracketRenderer() { TournamentRegistry.SolarBrackets = this; }

    @Override
    public byte[] renderPng(IPhase phase, BracketTheme theme) {
        BracketTheme t = orDefault(theme);
        try {
            return PngPainter.toBytes(new BracketLayout(phase, t).build(), t.getScale());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode bracket PNG for phase " + phase.getID(), e);
        }
    }

    @Override
    public byte[] renderPng(IPhase phase) { return renderPng(phase, BracketTheme.dark()); }

    @Override
    public byte[] renderPng(ITournament tournament, BracketTheme theme) {
        BracketTheme t = orDefault(theme);
        try {
            return PngPainter.toBytes(stack(tournament, t), t.getScale());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode bracket PNG for tournament " + tournament.getID(), e);
        }
    }

    @Override
    public void writePng(IPhase phase, BracketTheme theme, OutputStream out) throws IOException {
        BracketTheme t = orDefault(theme);
        PngPainter.write(new BracketLayout(phase, t).build(), t.getScale(), out);
    }

    @Override
    public File writePng(IPhase phase, BracketTheme theme, File target) throws IOException {
        try (OutputStream out = new FileOutputStream(target)) { writePng(phase, theme, out); }
        return target;
    }

    @Override
    public String renderSvg(IPhase phase, BracketTheme theme) { return SvgPainter.render(new BracketLayout(phase, orDefault(theme)).build()); }

    @Override
    public String renderSvg(IPhase phase) { return renderSvg(phase, BracketTheme.dark()); }

    @Override
    public String renderSvg(ITournament tournament, BracketTheme theme) { return SvgPainter.render(stack(tournament, orDefault(theme))); }

    @Override
    public File writeSvg(IPhase phase, BracketTheme theme, File target) throws IOException {
        Files.writeString(target.toPath(), renderSvg(phase, theme), StandardCharsets.UTF_8);
        return target;
    }

    @Override
    public String renderHtml(IPhase phase, BracketTheme theme) {
        BracketTheme t = orDefault(theme);
        ITournament parent = phase.getTournament();
        return HtmlPainter.render(new BracketLayout(phase, t).build(), t, parent == null ? phase.getName() : parent.getName() + " - " + phase.getName());
    }

    @Override
    public String renderHtml(IPhase phase) { return renderHtml(phase, BracketTheme.dark()); }

    @Override
    public String renderHtml(ITournament tournament, BracketTheme theme) {
        BracketTheme t = orDefault(theme);
        return HtmlPainter.render(stack(tournament, t), t, tournament.getName());
    }

    @Override
    public File writeHtml(IPhase phase, BracketTheme theme, File target) throws IOException {
        Files.writeString(target.toPath(), renderHtml(phase, theme), StandardCharsets.UTF_8);
        return target;
    }

    @Override
    public File writeHtml(ITournament tournament, BracketTheme theme, File target) throws IOException {
        Files.writeString(target.toPath(), renderHtml(tournament, theme), StandardCharsets.UTF_8);
        return target;
    }

    private static BracketTheme orDefault(BracketTheme theme) { return theme == null ? BracketTheme.dark() : theme; }

    private RenderModel stack(ITournament tournament, BracketTheme theme) {
        List<RenderModel> parts = tournament.getPhases().stream().map(p -> new BracketLayout(p, theme).build()).toList();
        return BracketLayout.stack(parts, theme, theme.getBackground());
    }
}
