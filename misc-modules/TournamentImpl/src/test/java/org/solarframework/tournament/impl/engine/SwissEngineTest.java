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

class SwissEngineTest {
    private final SwissEngine engine = new SwissEngine();

    private List<Participant> field(Tournament t, int n) {
        List<Participant> out = new ArrayList<>();
        for (int i = 1; i <= n; i++) out.add(new Participant(t, "P" + i, i));
        t.getParticipants().addAll(out); // Match resolves participants through the tournament's list, same as Tournament.register()
        return out;
    }

    /** Decides every match of one round, letting the round redraw happen, then moves to the next. */
    private void playAllRounds(Phase phase) {
        for (int round = 1; round <= phase.getTotalRounds(); round++) {
            for (Match m : new ArrayList<>(phase.getRound(round))) {
                if (m.getState().isDecided()) continue;
                Participant p1 = m.getParticipant1().orElseThrow(), p2 = m.getParticipant2().orElseThrow();
                boolean p1Wins = p1.getSeed() < p2.getSeed();
                m.setScore(p1Wins ? 1 : 0, p1Wins ? 0 : 1);
                m.setState(MatchState.COMPLETE);
                engine.onMatchDecided(phase, m);
            }
        }
    }

    @Test
    void roundCountIsCeilLog2OfTheFieldSize() {
        Tournament t = new Tournament("Swiss");
        Phase phase = t.addPhase("Swiss", PhaseType.SWISS);
        engine.generate(phase, field(t, 8));
        assertEquals(3, phase.getTotalRounds());
        assertEquals(4, phase.getRound(1).size());
    }
    @Test
    void oddFieldGetsExactlyOneRotatingByePerRound() {
        Tournament t = new Tournament("Swiss");
        Phase phase = t.addPhase("Swiss", PhaseType.SWISS);
        engine.generate(phase, field(t, 5));
        assertEquals(1, phase.getRound(1).stream().filter(m -> m.getState() == MatchState.BYE).count());
    }
    @Test
    void noPairingIsRepeatedAcrossAllRoundsPlayed() {
        Tournament t = new Tournament("Swiss");
        Phase phase = t.addPhase("Swiss", PhaseType.SWISS);
        engine.generate(phase, field(t, 8));
        playAllRounds(phase);
        assertTrue(engine.isComplete(phase));

        List<Match> completed = phase.getMatches().stream().filter(m -> m.getState() == MatchState.COMPLETE).toList();
        Set<Set<Long>> pairs = completed.stream().map(m -> Set.of(m.getParticipantID1(), m.getParticipantID2())).collect(Collectors.toSet());
        assertEquals(completed.size(), pairs.size());
    }
    @Test
    void topSeedWinningEveryMatchFinishesFirst() {
        Tournament t = new Tournament("Swiss");
        Phase phase = t.addPhase("Swiss", PhaseType.SWISS);
        List<Participant> field = field(t, 8);
        engine.generate(phase, field);
        playAllRounds(phase);
        Standing top = engine.rank(phase).stream().filter(s -> s.getRank() == 1).findFirst().orElseThrow();
        assertEquals(field.getFirst().getID(), top.getParticipantID());
    }
}
