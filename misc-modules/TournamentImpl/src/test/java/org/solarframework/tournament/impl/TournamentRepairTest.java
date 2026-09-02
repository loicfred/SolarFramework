package org.solarframework.tournament.impl;

import org.junit.jupiter.api.Test;
import org.solarframework.tournament.api.*;
import org.solarframework.tournament.obj.Tournament;
import org.solarframework.tournament.obj.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link Tournament#repair()}. Each test breaks a started run the way real drift breaks one - a table that was
 * never created, a winner that was never pushed forward, a phase that played out but never closed - then checks
 * repair puts it back <em>without</em> disturbing a reported result.
 */
class TournamentRepairTest {

    private Tournament started(PhaseType type, int entrants) {
        Tournament t = Tournament.create("Cup", type);
        t.openRegistration();
        for (int i = 1; i <= entrants; i++) t.register("P" + i);
        t.closeRegistration();
        t.setAutoAdvancePhases(false);
        t.start();
        return t;
    }

    private Match playable(Tournament t) { return t.getPlayableMatches().getFirst(); }

    @Test
    void aHealthyRunReportsNothingAndChangesNothing() {
        Tournament t = started(PhaseType.SINGLE_ELIMINATION, 8);
        playable(t).reportResult(1, 0);
        List<Long> before = t.getMatches().stream().map(Match::getID).toList();
        assertTrue(t.repair().isEmpty(), "a sound run should have nothing to report");
        assertEquals(before, t.getMatches().stream().map(Match::getID).toList());
    }

    @Test
    void aPhaseWithNoTableGetsOneRowPerEntrantItsMatchesName() {
        Tournament t = started(PhaseType.SINGLE_ELIMINATION, 8);
        Phase p = t.getPhases().getFirst();
        p.getStandings().clear(); // what an imported phase looks like: matches, no table
        assertTrue(p.getStandings().isEmpty());

        List<String> report = t.repair();
        assertEquals(8, p.getStandings().size(), "every entrant that appears in a match should get a row back");
        assertTrue(report.stream().anyMatch(l -> l.contains("missing standings row")), report.toString());
    }

    @Test
    void aWinnerTheBracketNeverCarriedForwardIsPushedIntoTheNextMatch() {
        Tournament t = started(PhaseType.SINGLE_ELIMINATION, 8);
        Match m = playable(t);
        m.reportResult(1, 0);
        Match next = t.getPhases().getFirst().getMatch(m.getNextMatchID()).orElseThrow();
        Long winner = m.getWinnerID();
        assertEquals(winner, next.getParticipantID(m.getNextMatchSlot()));

        next.setParticipant(m.getNextMatchSlot(), null); // the link the result should have followed, lost
        List<String> report = t.repair();
        assertEquals(winner, next.getParticipantID(m.getNextMatchSlot()), "repair should refill the slot from the decided match");
        assertTrue(report.stream().anyMatch(l -> l.contains("match slot")), report.toString());
    }

    /** The case the whole thing exists for: a later match reported before the one feeding it. */
    @Test
    void aSlotThatAlreadyHasSomebodyIsNeverOverwritten() {
        Tournament t = started(PhaseType.SINGLE_ELIMINATION, 8);
        Match m = playable(t);
        m.reportResult(1, 0);
        Phase p = t.getPhases().getFirst();
        Match next = p.getMatch(m.getNextMatchID()).orElseThrow();

        Long intruder = t.getParticipants().getLast().getID();
        next.setParticipant(m.getNextMatchSlot(), intruder);
        t.repair();
        assertEquals(intruder, next.getParticipantID(m.getNextMatchSlot()), "a filled slot must be left alone, even when it disagrees with the feeder");
    }

    @Test
    void aFinishedPhaseThatWasNeverClosedIsRankedAndClosedButTheRunIsNot() {
        Tournament t = started(PhaseType.ROUND_ROBIN, 4);
        Phase p = t.getPhases().getFirst();
        for (Match m : List.copyOf(p.getMatches())) {
            Participant p1 = m.getParticipant1().orElseThrow(), p2 = m.getParticipant2().orElseThrow();
            boolean first = p1.getSeed() < p2.getSeed();
            if (!m.getState().isDecided()) m.reportResult(first ? 1 : 0, first ? 0 : 1);
        }
        p.setStatus(PhaseStatus.RUNNING); // drifted back open after the last result
        t.setStatus(TournamentStatus.RUNNING);

        List<String> report = t.repair();
        assertTrue(p.getStatus().isComplete(), "a phase with every match decided should be closed");
        assertNotEquals(TournamentStatus.COMPLETE, t.getStatus(), "repair must never finish the run - the podium rides on finish()");
        assertTrue(report.stream().anyMatch(l -> l.contains("ready to be completed")), report.toString());
    }

    @Test
    void aPhaseWithNoMatchesAndNoResultsIsRebuilt() {
        Tournament t = started(PhaseType.SINGLE_ELIMINATION, 8);
        Phase p = t.getPhases().getFirst();
        p.getMatches().clear(); // drawn, then lost - nothing was ever reported

        List<String> report = t.repair();
        assertFalse(p.getMatches().isEmpty(), "a phase with entrants and no matches should be redrawn");
        assertTrue(report.stream().anyMatch(l -> l.contains("rebuilt the phase")), report.toString());
    }
}
