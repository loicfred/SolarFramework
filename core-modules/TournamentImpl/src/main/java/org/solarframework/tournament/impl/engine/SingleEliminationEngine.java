package org.solarframework.tournament.impl.engine;

import org.solarframework.tournament.api.BracketSide;
import org.solarframework.tournament.api.PhaseType;
import org.solarframework.tournament.api.TournamentException;
import org.solarframework.tournament.impl.StandingsCalculator;
import org.solarframework.tournament.impl.seed.Brackets;
import org.solarframework.tournament.impl.seed.Seeder;
import org.solarframework.tournament.obj.IMatch;
import org.solarframework.tournament.obj.IParticipant;
import org.solarframework.tournament.obj.IPhase;
import org.solarframework.tournament.obj.IStanding;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Knockout bracket. The field is padded up to the next power of two and the empty slots become
 * byes, so any entrant count works. Seeds are placed so 1 and 2 can only meet in the final.
 */
public class SingleEliminationEngine extends AbstractPhaseEngine {

    @Override
    public PhaseType type() { return PhaseType.SINGLE_ELIMINATION; }

    @Override
    public void generate(IPhase phase, List<IParticipant> entrants) {
        if (entrants.size() < 2) throw TournamentException.of("A bracket needs at least 2 entrants, got %d", entrants.size());
        int size = Brackets.nextPowerOfTwo(entrants.size());
        int rounds = Brackets.log2(size);
        phase.setBracketSize(size);
        phase.setTotalRounds(rounds);
        phase.setParticipantCount(entrants.size());
        phase.setCurrentRound(1);
        seedStandings(phase, entrants);

        List<List<IMatch>> byRound = buildSkeleton(phase, size, rounds, BracketSide.WINNERS);
        if (phase.isThirdPlaceMatch() && rounds >= 2) addThirdPlaceMatch(phase, byRound.get(rounds - 2), rounds);
        placeEntrants(phase, byRound.getFirst(), entrants, size);

        numberMatches(phase);
        labelSlots(phase);
        resolveByes(phase);
        log.info("Generated single elimination phase '{}': {} entrants, bracket of {}, {} rounds, {} matches",
                phase.getName(), entrants.size(), size, rounds, phase.getMatches().size());
    }

    /** Creates every round of an empty bracket and wires each match to its successor. */
    protected List<List<IMatch>> buildSkeleton(IPhase phase, int size, int rounds, BracketSide side) {
        List<List<IMatch>> byRound = new ArrayList<>();
        for (int r = 1; r <= rounds; r++) {
            int count = size >> r;
            List<IMatch> round = new ArrayList<>(count);
            for (int p = 0; p < count; p++) round.add(newMatch(phase, side, r, p));
            byRound.add(round);
        }
        for (int r = 0; r < rounds - 1; r++) {
            List<IMatch> cur = byRound.get(r), next = byRound.get(r + 1);
            for (int p = 0; p < cur.size(); p++) linkWinner(cur.get(p), next.get(p / 2), p % 2 + 1);
        }
        return byRound;
    }

    private void addThirdPlaceMatch(IPhase phase, List<IMatch> semifinals, int rounds) {
        IMatch third = newMatch(phase, BracketSide.THIRD_PLACE, rounds, 1);
        third.setLabel("Third place");
        for (int i = 0; i < Math.min(2, semifinals.size()); i++) linkLoser(semifinals.get(i), third, i + 1);
    }

    /** Drops the seeded field into round one; slots past the field size are left empty. */
    protected void placeEntrants(IPhase phase, List<IMatch> firstRound, List<IParticipant> entrants, int size) {
        List<Optional<IParticipant>> slots = Seeder.intoBracketSlots(entrants, size);
        for (int i = 0; i < size; i++) {
            IMatch m = firstRound.get(i / 2);
            Long id = slots.get(i).map(IParticipant::getID).orElse(null);
            if (i % 2 == 0) m.setParticipantID1(id); else m.setParticipantID2(id);
        }
    }

    @Override
    public List<IMatch> onMatchDecided(IPhase phase, IMatch match) {
        List<IMatch> changed = new ArrayList<>(propagate(phase, match));
        changed.addAll(resolveByes(phase));
        phase.setCurrentRound(phase.getMatches().stream().filter(m -> !m.getState().isDecided() && m.getBracketSide() != BracketSide.THIRD_PLACE)
                .mapToInt(IMatch::getRound).min().orElse(phase.getTotalRounds()));
        return changed;
    }

    /**
     * Knockout placement is "how far did you get", not win count - so the table is rebuilt for its
     * game totals and then re-ranked by the round each entrant was knocked out in.
     */
    @Override
    public List<IStanding> rank(IPhase phase) {
        List<IStanding> rows = StandingsCalculator.recompute(phase);
        Map<Long, Integer> exit = exitRounds(phase);
        Map<Long, Integer> thirdPlaceBonus = thirdPlaceOrder(phase);
        rows.sort(Comparator.comparingInt((IStanding s) -> exit.getOrDefault(s.getParticipantID(), Integer.MAX_VALUE)).reversed()
                .thenComparingInt(s -> thirdPlaceBonus.getOrDefault(s.getParticipantID(), 0))
                .thenComparing(Comparator.comparingInt(IStanding::getWins).reversed())
                .thenComparing(Comparator.comparingInt(IStanding::getGameDiff).reversed())
                .thenComparingInt(IStanding::getSeed));
        for (int i = 0; i < rows.size(); i++) rows.get(i).setRank(i + 1);
        return rows;
    }

    /** Round in which each entrant lost; absent means they are still alive or won it all. */
    protected Map<Long, Integer> exitRounds(IPhase phase) {
        Map<Long, Integer> exit = new HashMap<>();
        for (IMatch m : phase.getMatches()) {
            if (!m.getState().isDecided() || m.getLoserID() == null) continue;
            if (m.getBracketSide() == BracketSide.THIRD_PLACE) continue;
            exit.merge(m.getLoserID(), m.getRound(), Math::max);
        }
        return exit;
    }

    /** Orders the two semifinal losers by the third-place match: 0 for its winner, 1 for its loser. */
    private Map<Long, Integer> thirdPlaceOrder(IPhase phase) {
        Map<Long, Integer> out = new HashMap<>();
        for (IMatch m : phase.getMatches(BracketSide.THIRD_PLACE)) {
            if (!m.getState().isDecided()) continue;
            if (m.getWinnerID() != null) out.put(m.getWinnerID(), 0);
            if (m.getLoserID() != null) out.put(m.getLoserID(), 1);
        }
        return out;
    }
}
