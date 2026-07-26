package org.solarframework.tournament.impl.render;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.solarframework.tournament.api.BracketSide;
import org.solarframework.tournament.api.MatchState;
import org.solarframework.tournament.api.PhaseType;
import org.solarframework.tournament.api.dto.BracketTheme;
import org.solarframework.tournament.impl.engine.DoubleEliminationEngine;
import org.solarframework.tournament.impl.engine.GroupEngine;
import org.solarframework.tournament.impl.engine.SingleEliminationEngine;
import org.solarframework.tournament.impl.obj.Participant;
import org.solarframework.tournament.impl.obj.Tournament;
import org.solarframework.tournament.obj.IMatch;
import org.solarframework.tournament.obj.IParticipant;
import org.solarframework.tournament.obj.IPhase;
import org.solarframework.tournament.obj.ITournament;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

class BracketRendererTest {
    private final BracketRenderer renderer = new BracketRenderer();

    private List<IParticipant> field(ITournament t, int n) {
        List<IParticipant> out = new ArrayList<>();
        for (int i = 1; i <= n; i++) out.add(new Participant(t, "P" + i, i));
        t.getParticipants().addAll(out); // Match resolves participants through the tournament's list, same as Tournament.register()
        return out;
    }

    @Test
    void rendersAPngWithAValidSignatureForABracketPhase() {
        Tournament t = new Tournament("Cup");
        IPhase phase = t.addPhase("Bracket", PhaseType.SINGLE_ELIMINATION);
        new SingleEliminationEngine().generate(phase, field(t, 5));
        byte[] png = renderer.renderPng(phase);
        assertTrue(png.length > 8);
        assertEquals((byte) 0x89, png[0]);
        assertEquals('P', png[1]);
        assertEquals('N', png[2]);
        assertEquals('G', png[3]);
    }
    @Test
    void rendersSvgMarkupForABracketPhase() {
        Tournament t = new Tournament("Cup");
        IPhase phase = t.addPhase("Bracket", PhaseType.SINGLE_ELIMINATION);
        new SingleEliminationEngine().generate(phase, field(t, 4));
        String svg = renderer.renderSvg(phase);
        assertTrue(svg.startsWith("<svg"));
        assertTrue(svg.trim().endsWith("</svg>"));
        assertTrue(svg.contains("P1"));
    }
    @Test
    void rendersSvgForAGroupPhaseTable() {
        Tournament t = new Tournament("League");
        IPhase phase = t.addPhase("Groups", PhaseType.GROUP);
        phase.setGroupCount(2);
        new GroupEngine(PhaseType.GROUP).generate(phase, field(t, 8));
        String svg = renderer.renderSvg(phase);
        assertTrue(svg.startsWith("<svg"));
        assertTrue(svg.contains("Group A"));
    }
    @Test
    void rendersTheWholeTournamentAcrossPhases() {
        Tournament t = new Tournament("Cup");
        IPhase group = t.addPhase("Groups", PhaseType.GROUP);
        new GroupEngine(PhaseType.GROUP).generate(group, field(t, 4));
        IPhase bracket = t.addPhase("Playoffs", PhaseType.SINGLE_ELIMINATION);
        new SingleEliminationEngine().generate(bracket, field(t, 4));

        String svg = renderer.renderSvg(t, BracketTheme.dark());
        String phase1Only = renderer.renderSvg(group, BracketTheme.dark());
        assertTrue(svg.startsWith("<svg"));
        assertTrue(svg.length() > phase1Only.length());
    }
    @Test
    void lightAndDarkThemesProduceDifferentBackgrounds() {
        Tournament t = new Tournament("Cup");
        IPhase phase = t.addPhase("Bracket", PhaseType.SINGLE_ELIMINATION);
        new SingleEliminationEngine().generate(phase, field(t, 4));
        String dark = renderer.renderSvg(phase, BracketTheme.dark());
        String light = renderer.renderSvg(phase, BracketTheme.light());
        assertNotEquals(dark, light);
        assertTrue(light.contains(BracketTheme.light().getBackground()));
    }

    @Test
    void rendersHtmlWithDataParticipantAttributesAndHoverScript() {
        Tournament t = new Tournament("Cup");
        IPhase phase = t.addPhase("Bracket", PhaseType.SINGLE_ELIMINATION);
        new SingleEliminationEngine().generate(phase, field(t, 4));
        String html = renderer.renderHtml(phase);
        assertTrue(html.startsWith("<!doctype html>"));
        assertTrue(html.contains("<svg"));
        assertTrue(html.contains("data-participant=\""));
        assertTrue(html.contains("addEventListener"));
    }
    @Test
    void writesHtmlFileToDisk(@TempDir File dir) throws IOException {
        Tournament t = new Tournament("Cup");
        IPhase phase = t.addPhase("Bracket", PhaseType.SINGLE_ELIMINATION);
        new SingleEliminationEngine().generate(phase, field(t, 4));
        File out = renderer.writeHtml(phase, BracketTheme.dark(), new File(dir, "bracket.html"));
        assertTrue(out.exists());
        assertTrue(Files.readString(out.toPath()).startsWith("<!doctype html>"));
    }

    /**
     * Regression test for a real bug: the grand final and its reset used to be placed by a generic
     * feeder-averaging rule that had no feeders to average for the reset match, dropping it right on
     * top of other boxes. This forces the exact reset scenario and checks nothing overlaps.
     */
    @Test
    void noTwoMatchBoxesOverlapWhenAGrandFinalResetHappens() {
        Tournament t = new Tournament("DE");
        IPhase phase = t.addPhase("Bracket", PhaseType.DOUBLE_ELIMINATION);
        List<IParticipant> field = field(t, 4);
        DoubleEliminationEngine engine = new DoubleEliminationEngine();
        engine.generate(phase, field);
        forceGrandFinalReset(phase, engine, field);
        assertFalse(phase.getMatches(BracketSide.GRAND_FINAL_RESET).isEmpty()); // sanity: the scenario actually happened

        assertNoOverlappingBoxes(new BracketLayout(phase, BracketTheme.dark()).build());
    }
    @Test
    void noTwoMatchBoxesOverlapInALargerDoubleEliminationBracket() {
        Tournament t = new Tournament("DE8");
        IPhase phase = t.addPhase("Bracket", PhaseType.DOUBLE_ELIMINATION);
        DoubleEliminationEngine engine = new DoubleEliminationEngine();
        engine.generate(phase, field(t, 8));
        List<IMatch> queue = new ArrayList<>(phase.getPlayableMatches());
        while (!queue.isEmpty()) {
            IMatch m = queue.removeFirst();
            if (m.getState().isDecided() || !m.isFilled()) continue;
            IParticipant p1 = m.getParticipant1().orElseThrow(), p2 = m.getParticipant2().orElseThrow();
            boolean p1Wins = p1.getSeed() < p2.getSeed();
            m.setScore(p1Wins ? 1 : 0, p1Wins ? 0 : 1);
            m.setState(MatchState.COMPLETE);
            queue.addAll(engine.onMatchDecided(phase, m));
        }
        assertNoOverlappingBoxes(new BracketLayout(phase, BracketTheme.dark()).build());
    }

    /**
     * Regression test for a real bug: a losers-bracket "major" round centred itself between its own
     * feeder AND the winners-bracket match dropping a loser into it, which dragged the row up toward
     * the (much higher) winners bracket instead of staying in the losers section. The boxes involved
     * didn't land on literally the same pixel, so {@link #assertNoOverlappingBoxes} missed it entirely -
     * this checks the actual invariant: later losers rounds never sit above earlier ones.
     */
    @Test
    void losersBracketRoundsNeverDriftAboveTheOnesBeforeThem() {
        Tournament t = new Tournament("DE8");
        IPhase phase = t.addPhase("Bracket", PhaseType.DOUBLE_ELIMINATION);
        new DoubleEliminationEngine().generate(phase, field(t, 8));

        BracketLayout layout = new BracketLayout(phase, BracketTheme.dark());
        layout.build();

        Map<Integer, Double> topOfRound = new TreeMap<>();
        for (IMatch m : phase.getMatches(BracketSide.LOSERS)) {
            Double y = layout.placedY(m.getID());
            assertNotNull(y, "Losers round " + m.getRound() + " match " + m.getMatchNumber() + " was never placed");
            topOfRound.merge(m.getRound(), y, Math::min);
        }
        assertTrue(topOfRound.size() >= 3, "This bracket size should produce several losers rounds to check ordering");

        double previous = Double.NEGATIVE_INFINITY;
        for (Map.Entry<Integer, Double> e : topOfRound.entrySet()) {
            assertTrue(e.getValue() + 0.01 >= previous,
                    "Losers round " + e.getKey() + " (y=" + e.getValue() + ") drifted above round " + (e.getKey() - 1) + " (y=" + previous + ")");
            previous = e.getValue();
        }
    }

    /**
     * The losers bracket is drawn as its own bracket below the winners one, so the only line allowed
     * to leave the winners section is the one into the grand final: a loser drop is spelled out by the
     * target slot's "Loser of M7" label instead of by a connector sweeping down across the picture.
     */
    @Test
    void noConnectorRunsFromTheWinnersBracketDownIntoTheLosersBracket() {
        Tournament t = new Tournament("DE8");
        IPhase phase = t.addPhase("Bracket", PhaseType.DOUBLE_ELIMINATION);
        new DoubleEliminationEngine().generate(phase, field(t, 8));

        BracketTheme th = BracketTheme.dark();
        BracketLayout layout = new BracketLayout(phase, th);
        RenderModel model = layout.build();
        double wbBottom = phase.getMatches(BracketSide.WINNERS).stream().mapToDouble(m -> layout.placedY(m.getID())).max().orElseThrow();
        double lbTop = phase.getMatches(BracketSide.LOSERS).stream().mapToDouble(m -> layout.placedY(m.getID())).min().orElseThrow();
        double gfY = layout.placedY(phase.getMatches(BracketSide.GRAND_FINAL).getFirst().getID());

        for (RenderModel.Shape s : model.shapes) {
            if (!(s instanceof RenderModel.Poly p) || p.points().size() < 3) continue; // the section divider is a 2-point rule, not a connector
            double top = p.points().stream().mapToDouble(pt -> pt[1]).min().orElseThrow();
            double bottom = p.points().stream().mapToDouble(pt -> pt[1]).max().orElseThrow();
            double endY = p.points().getLast()[1];
            boolean intoGrandFinal = endY >= gfY && endY <= gfY + th.matchHeight();
            assertTrue(intoGrandFinal || top > wbBottom || bottom < lbTop, "A connector spans the winners and losers sections: y " + top + " to " + bottom);
        }
    }

    /** Regression test for a real bug: a third place match asked for after the phase existed never reached the phase, so it was silently missing from the bracket. */
    @Test
    void aThirdPlaceMatchRequestedAfterPhaseCreationStillShowsOnTheBracket() {
        Tournament t = Tournament.create("Cup", PhaseType.SINGLE_ELIMINATION);
        t.setThirdPlaceMatch(true);
        IPhase phase = t.getPhases().getFirst();
        new SingleEliminationEngine().generate(phase, field(t, 4));
        assertEquals(1, phase.getMatches(BracketSide.THIRD_PLACE).size());
        assertTrue(renderer.renderSvg(phase).contains("Third place"));
    }

    /** Same script verified in DoubleEliminationEngineTest: sends seed 1 through the losers bracket and back into the grand final. */
    private void forceGrandFinalReset(IPhase phase, DoubleEliminationEngine engine, List<IParticipant> field) {
        IParticipant p1 = field.get(0), p2 = field.get(1);
        decide(phase, engine, phase.getMatches(BracketSide.WINNERS, 1).get(0), field.get(3));
        decide(phase, engine, phase.getMatches(BracketSide.WINNERS, 1).get(1), p2);
        decide(phase, engine, phase.getMatches(BracketSide.WINNERS, 2).getFirst(), p2);
        decide(phase, engine, phase.getMatches(BracketSide.LOSERS, 1).getFirst(), p1);
        decide(phase, engine, phase.getMatches(BracketSide.LOSERS, 2).getFirst(), p1);
        decide(phase, engine, phase.getMatches(BracketSide.GRAND_FINAL).getFirst(), p1);
        decide(phase, engine, phase.getMatches(BracketSide.GRAND_FINAL_RESET).getFirst(), p2);
    }

    private void decide(IPhase phase, DoubleEliminationEngine engine, IMatch m, IParticipant winner) {
        m.setScore(winner.getID().equals(m.getParticipantID1()) ? 1 : 0, winner.getID().equals(m.getParticipantID1()) ? 0 : 1);
        m.setState(MatchState.COMPLETE);
        engine.onMatchDecided(phase, m);
    }

    private void assertNoOverlappingBoxes(RenderModel model) {
        Set<String> seen = new HashSet<>();
        for (RenderModel.Shape s : model.shapes) {
            if (!(s instanceof RenderModel.Box b)) continue;
            String key = b.x() + "," + b.y() + "," + b.w() + "," + b.h();
            assertTrue(seen.add(key), "Duplicate box position: " + key);
        }
    }
}
