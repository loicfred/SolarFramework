package org.solarframework.tournament.impl.engine;

import org.junit.jupiter.api.Test;
import org.solarframework.tournament.api.BracketSide;
import org.solarframework.tournament.api.MatchState;
import org.solarframework.tournament.api.PhaseType;
import org.solarframework.tournament.impl.obj.Participant;
import org.solarframework.tournament.impl.obj.Tournament;
import org.solarframework.tournament.obj.*;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SingleEliminationEngineTest {
    private final SingleEliminationEngine engine = new SingleEliminationEngine();

    private List<IParticipant> field(ITournament t, int n) {
        List<IParticipant> out = new ArrayList<>();
        for (int i = 1; i <= n; i++) out.add(new Participant(t, "P" + i, i));
        t.getParticipants().addAll(out); // Match resolves participants through the tournament's list, same as Tournament.register()
        return out;
    }

    /** Always advances the better (lower) seed until every match is decided. */
    private void playThrough(IPhase phase) {
        List<IMatch> queue = new ArrayList<>(phase.getPlayableMatches());
        while (!queue.isEmpty()) {
            IMatch m = queue.removeFirst();
            if (m.getState().isDecided() || !m.isFilled()) continue; // its other slot may still be waiting on a sibling match
            IParticipant p1 = m.getParticipant1().orElseThrow(), p2 = m.getParticipant2().orElseThrow();
            boolean p1Wins = p1.getSeed() < p2.getSeed();
            m.setScore(p1Wins ? 1 : 0, p1Wins ? 0 : 1);
            m.setState(MatchState.COMPLETE);
            queue.addAll(engine.onMatchDecided(phase, m));
        }
    }

    @Test
    void generatePadsTheFieldToTheNextPowerOfTwoAndAwardsByes() {
        Tournament t = new Tournament("SE");
        IPhase phase = t.addPhase("Bracket", PhaseType.SINGLE_ELIMINATION);
        engine.generate(phase, field(t, 5));
        assertEquals(8, phase.getBracketSize());
        assertEquals(3, phase.getTotalRounds());
        assertEquals(3, phase.getMatches().stream().filter(m -> m.getState() == MatchState.BYE).count());
    }
    @Test
    void topSeedWinsEveryMatchAndTakesTheTitle() {
        Tournament t = new Tournament("SE");
        IPhase phase = t.addPhase("Bracket", PhaseType.SINGLE_ELIMINATION);
        List<IParticipant> field = field(t, 8);
        engine.generate(phase, field);
        playThrough(phase);
        assertTrue(engine.isComplete(phase));
        IStanding champion = engine.rank(phase).stream().filter(s -> s.getRank() == 1).findFirst().orElseThrow();
        assertEquals(field.getFirst().getID(), champion.getParticipantID());
    }
    @Test
    void thirdPlaceMatchIsWiredToBothSemifinalLosers() {
        Tournament t = new Tournament("SE");
        IPhase phase = t.addPhase("Bracket", PhaseType.SINGLE_ELIMINATION);
        phase.setThirdPlaceMatch(true);
        engine.generate(phase, field(t, 4));
        List<IMatch> thirdPlace = phase.getMatches(BracketSide.THIRD_PLACE);
        assertEquals(1, thirdPlace.size());
        playThrough(phase);
        assertTrue(thirdPlace.getFirst().getState().isDecided());
    }
    @Test
    void bothSemifinalLosersAreThirdWhenNoThirdPlaceMatchSeparatesThem() {
        Tournament t = new Tournament("SE");
        IPhase phase = t.addPhase("Bracket", PhaseType.SINGLE_ELIMINATION);
        phase.setThirdPlaceMatch(false);
        engine.generate(phase, field(t, 4));
        playThrough(phase);
        assertEquals(List.of(1, 2, 3, 3), engine.rank(phase).stream().map(IStanding::getRank).toList());
    }
    @Test
    void aPlayedThirdPlaceMatchSplitsTheSemifinalLosers() {
        Tournament t = new Tournament("SE");
        IPhase phase = t.addPhase("Bracket", PhaseType.SINGLE_ELIMINATION);
        phase.setThirdPlaceMatch(true);
        engine.generate(phase, field(t, 4));
        playThrough(phase);
        assertEquals(List.of(1, 2, 3, 4), engine.rank(phase).stream().map(IStanding::getRank).toList());
    }
    @Test
    void everyRoundOfExitsSharesAPlacingAndTheNextRankSkipsTheTie() {
        Tournament t = new Tournament("SE");
        IPhase phase = t.addPhase("Bracket", PhaseType.SINGLE_ELIMINATION);
        phase.setThirdPlaceMatch(false);
        engine.generate(phase, field(t, 8));
        playThrough(phase);
        assertEquals(List.of(1, 2, 3, 3, 5, 5, 5, 5), engine.rank(phase).stream().map(IStanding::getRank).toList());
    }
    @Test
    void oddFieldStillProducesAFullyDecidedBracket() {
        Tournament t = new Tournament("SE");
        IPhase phase = t.addPhase("Bracket", PhaseType.SINGLE_ELIMINATION);
        engine.generate(phase, field(t, 3));
        playThrough(phase);
        assertTrue(engine.isComplete(phase));
    }
}
