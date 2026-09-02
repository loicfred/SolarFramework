package org.solarframework.tournament.impl.engine;

import org.junit.jupiter.api.Test;
import org.solarframework.tournament.api.BracketSide;
import org.solarframework.tournament.api.MatchState;
import org.solarframework.tournament.api.PhaseType;
import org.solarframework.tournament.obj.Participant;
import org.solarframework.tournament.obj.Tournament;
import org.solarframework.tournament.obj.*;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DoubleEliminationEngineTest {
    private final DoubleEliminationEngine engine = new DoubleEliminationEngine();

    private List<Participant> field(Tournament t, int n) {
        List<Participant> out = new ArrayList<>();
        for (int i = 1; i <= n; i++) out.add(new Participant(t, "P" + i, i));
        t.getParticipants().addAll(out); // Match resolves participants through the tournament's list, same as Tournament.register()
        return out;
    }

    private Participant by(List<Participant> field, String name) { return field.stream().filter(p -> p.getName().equals(name)).findFirst().orElseThrow(); }

    private void decide(Phase phase, Match m, Participant winner) {
        m.setScore(winner.getID().equals(m.getParticipantID1()) ? 1 : 0, winner.getID().equals(m.getParticipantID1()) ? 0 : 1);
        m.setState(MatchState.COMPLETE);
        engine.onMatchDecided(phase, m);
    }

    @Test
    void generateProducesWinnersLosersAndGrandFinalMatches() {
        Tournament t = new Tournament("DE");
        Phase phase = t.addPhase("Bracket", PhaseType.DOUBLE_ELIMINATION);
        engine.generate(phase, field(t, 4));
        assertEquals(3, phase.getMatches(BracketSide.WINNERS).size());
        assertEquals(2, phase.getMatches(BracketSide.LOSERS).size());
        assertEquals(1, phase.getMatches(BracketSide.GRAND_FINAL).size());
    }

    /**
     * With 4 entrants, seeding puts P1 v P4 and P2 v P3 in winners round one. This script sends P1
     * through the losers bracket and back into the grand final to force the reset, then finishes it.
     */
    @Test
    void losersBracketEntrantWinningTheGrandFinalForcesAReset() {
        Tournament t = new Tournament("DE");
        Phase phase = t.addPhase("Bracket", PhaseType.DOUBLE_ELIMINATION);
        List<Participant> field = field(t, 4);
        Participant p1 = by(field, "P1"), p2 = by(field, "P2"), p3 = by(field, "P3"), p4 = by(field, "P4");
        engine.generate(phase, field);

        Match wbR1a = phase.getMatches(BracketSide.WINNERS, 1).getFirst(); // P1 v P4
        Match wbR1b = phase.getMatches(BracketSide.WINNERS, 1).get(1); // P2 v P3
        decide(phase, wbR1a, p4); // upset - P1 drops to losers
        decide(phase, wbR1b, p2);

        Match wbFinal = phase.getMatches(BracketSide.WINNERS, 2).getFirst(); // P4 v P2
        decide(phase, wbFinal, p2); // P2 reaches the grand final undefeated, P4 also drops to losers

        Match lbR1 = phase.getMatches(BracketSide.LOSERS, 1).getFirst(); // P1 v P3
        decide(phase, lbR1, p1);
        Match lbR2 = phase.getMatches(BracketSide.LOSERS, 2).getFirst(); // P1 v P4
        decide(phase, lbR2, p1); // P1 reaches the grand final with one loss

        Match gf = phase.getMatches(BracketSide.GRAND_FINAL).getFirst();
        assertEquals(p2.getID(), gf.getParticipantID1());
        assertEquals(p1.getID(), gf.getParticipantID2());
        decide(phase, gf, p1); // losers-bracket entrant wins - must force a reset

        assertFalse(engine.isComplete(phase));
        List<Match> reset = phase.getMatches(BracketSide.GRAND_FINAL_RESET);
        assertEquals(1, reset.size());

        decide(phase, reset.getFirst(), p2); // P2 wins the decider and takes the title
        assertTrue(engine.isComplete(phase));
    }
    @Test
    void winnersBracketEntrantWinningTheGrandFinalNeedsNoReset() {
        Tournament t = new Tournament("DE");
        Phase phase = t.addPhase("Bracket", PhaseType.DOUBLE_ELIMINATION);
        List<Participant> field = field(t, 4);
        engine.generate(phase, field);
        List<Match> queue = new ArrayList<>(phase.getPlayableMatches());
        while (!queue.isEmpty()) {
            Match m = queue.removeFirst();
            if (m.getState().isDecided() || !m.isFilled()) continue; // its other slot may still be waiting on a sibling match
            Participant p1 = m.getParticipant1().orElseThrow(), p2 = m.getParticipant2().orElseThrow();
            Participant winner = p1.getSeed() < p2.getSeed() ? p1 : p2;
            m.setScore(winner.equals(p1) ? 1 : 0, winner.equals(p1) ? 0 : 1);
            m.setState(MatchState.COMPLETE);
            queue.addAll(engine.onMatchDecided(phase, m));
        }
        assertTrue(engine.isComplete(phase));
        assertTrue(phase.getMatches(BracketSide.GRAND_FINAL_RESET).isEmpty());
    }
}
