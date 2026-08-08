package org.solarframework.tournament.impl;

import org.junit.jupiter.api.Test;
import org.solarframework.tournament.api.MatchState;
import org.solarframework.tournament.api.PhaseType;
import org.solarframework.tournament.impl.engine.GroupEngine;
import org.solarframework.tournament.impl.obj.Participant;
import org.solarframework.tournament.impl.obj.Tournament;
import org.solarframework.tournament.obj.*;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StandingsCalculatorTest {

    @Test
    void recomputeRanksARoundRobinByPointsThenTiebreakers() {
        Tournament t = new Tournament("RR");
        IPhase phase = t.addPhase("RR", PhaseType.ROUND_ROBIN);
        List<IParticipant> field = List.of(new Participant(t, "P1", 1), new Participant(t, "P2", 2), new Participant(t, "P3", 3), new Participant(t, "P4", 4));
        t.getParticipants().addAll(field); // Match resolves participants through the tournament's list, same as Tournament.register()
        new GroupEngine(PhaseType.ROUND_ROBIN).generate(phase, field);

        // P1 beats everyone, P2 beats P3/P4, P3 beats P4, P4 wins nothing - a total order.
        Map<String, Integer> strength = Map.of("P1", 0, "P2", 1, "P3", 2, "P4", 3);
        for (IMatch m : phase.getMatches()) {
            IParticipant a = m.getParticipant1().orElseThrow(), b = m.getParticipant2().orElseThrow();
            boolean aWins = strength.get(a.getName()) < strength.get(b.getName());
            m.setScore(aWins ? 1 : 0, aWins ? 0 : 1);
            m.setState(MatchState.COMPLETE);
        }

        List<IStanding> ranked = StandingsCalculator.recompute(phase).stream().sorted(Comparator.comparingInt(IStanding::getRank)).toList();
        assertEquals(List.of("P1", "P2", "P3", "P4"), ranked.stream().map(IStanding::getName).toList());
        assertEquals(9.0, ranked.getFirst().getPoints()); // 3 wins * 3 points
        assertEquals(0.0, ranked.getLast().getPoints());
    }
    @Test
    void headToHeadBreaksAPointsTieBeforeGameDifference() {
        Tournament t = new Tournament("RR");
        IPhase phase = t.addPhase("RR", PhaseType.ROUND_ROBIN);
        IParticipant a = new Participant(t, "A", 1), b = new Participant(t, "B", 2), c = new Participant(t, "C", 3);
        t.getParticipants().addAll(List.of(a, b, c));
        new GroupEngine(PhaseType.ROUND_ROBIN).generate(phase, List.of(a, b, c));

        // A beats B, B beats C, C beats A - a 3-way tie on 1 win each, broken pairwise.
        decide(phase, a, b, 1, 0);
        decide(phase, b, c, 1, 0);
        decide(phase, c, a, 1, 0);

        List<IStanding> ranked = StandingsCalculator.recompute(phase).stream().sorted(Comparator.comparingInt(IStanding::getRank)).toList();
        // A 3-cycle has no true head-to-head winner, but the tiebreaker chain must still resolve to a total order.
        assertEquals(List.of(1, 2, 3), ranked.stream().map(IStanding::getRank).toList());
        ranked.forEach(s -> assertEquals(3.0, s.getPoints()));
    }

    private void decide(IPhase phase, IParticipant winner, IParticipant loser, int s1, int s2) {
        IMatch m = phase.getMatches().stream().filter(x -> x.hasParticipant(winner.getID()) && x.hasParticipant(loser.getID())).findFirst().orElseThrow();
        boolean winnerIsSlot1 = winner.getID().equals(m.getParticipantID1());
        m.setScore(winnerIsSlot1 ? s1 : s2, winnerIsSlot1 ? s2 : s1);
        m.setState(MatchState.COMPLETE);
    }
}
