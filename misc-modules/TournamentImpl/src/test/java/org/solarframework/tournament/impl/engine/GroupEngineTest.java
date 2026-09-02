package org.solarframework.tournament.impl.engine;

import org.junit.jupiter.api.Test;
import org.solarframework.tournament.api.MatchState;
import org.solarframework.tournament.api.PhaseType;
import org.solarframework.tournament.obj.Participant;
import org.solarframework.tournament.obj.Tournament;
import org.solarframework.tournament.obj.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class GroupEngineTest {
    private List<Participant> field(Tournament t, int n) {
        List<Participant> out = new ArrayList<>();
        for (int i = 1; i <= n; i++) out.add(new Participant(t, "P" + i, i));
        t.getParticipants().addAll(out); // Match resolves participants through the tournament's list, same as Tournament.register()
        return out;
    }

    private void playAllBySeed(Phase phase) {
        for (Match m : phase.getMatches()) {
            if (m.getState().isDecided()) continue;
            Participant p1 = m.getParticipant1().orElseThrow(), p2 = m.getParticipant2().orElseThrow();
            boolean p1Wins = p1.getSeed() < p2.getSeed();
            m.setScore(p1Wins ? 1 : 0, p1Wins ? 0 : 1);
            m.setState(MatchState.COMPLETE);
        }
    }

    @Test
    void roundRobinSchedulesEveryPairExactlyOnce() {
        Tournament t = new Tournament("RR");
        Phase phase = t.addPhase("RR", PhaseType.ROUND_ROBIN);
        new GroupEngine(PhaseType.ROUND_ROBIN).generate(phase, field(t, 5));
        assertEquals(5, phase.getTotalRounds());
        assertEquals(10, phase.getMatches().size()); // C(5,2)
        Set<Set<Long>> pairs = phase.getMatches().stream().map(m -> Set.of(m.getParticipantID1(), m.getParticipantID2())).collect(Collectors.toSet());
        assertEquals(10, pairs.size());
    }
    @Test
    void doubleRoundRobinPlaysEveryPairTwiceWithSwappedSides() {
        Tournament t = new Tournament("RR2");
        Phase phase = t.addPhase("RR2", PhaseType.ROUND_ROBIN);
        phase.setDoubleRoundRobin(true);
        new GroupEngine(PhaseType.ROUND_ROBIN).generate(phase, field(t, 4));
        assertEquals(6, phase.getTotalRounds());
        assertEquals(12, phase.getMatches().size()); // C(4,2) * 2 legs
    }
    @Test
    void groupPhaseAdvancesTheTopEntrantsOfEachGroup() {
        Tournament t = new Tournament("GS");
        Phase phase = t.addPhase("Groups", PhaseType.GROUP);
        phase.setGroupCount(2);
        phase.setAdvancePerGroup(2);
        GroupEngine engine = new GroupEngine(PhaseType.GROUP);
        engine.generate(phase, field(t, 8));
        playAllBySeed(phase);

        List<Participant> qualifiers = engine.getQualifiers(phase);
        assertEquals(4, qualifiers.size());
        for (Participant q : qualifiers) {
            Standing s = phase.getStanding(q.getID()).orElseThrow();
            assertTrue(s.isQualified());
            assertTrue(s.getRank() <= 2);
        }
        assertEquals(4, phase.getStandings().stream().filter(Standing::isEliminated).count());
    }
}
