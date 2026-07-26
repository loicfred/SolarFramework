package org.solarframework.tournament.impl;

import org.solarframework.tournament.api.Tiebreaker;
import org.solarframework.tournament.obj.IMatch;
import org.solarframework.tournament.obj.IPhase;
import org.solarframework.tournament.obj.IStanding;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ranks a phase's table. Every row's record, points and tiebreaker aggregates are computed live on
 * {@link IStanding} itself from the phase's matches - nothing here accumulates or resets state
 * anymore, so a "recompute" is just re-sorting by the current tiebreaker chain and writing ranks.
 */
public final class StandingsCalculator {
    private StandingsCalculator() {}

    public static List<IStanding> recompute(IPhase phase) {
        List<IStanding> rows = phase.getStandings();
        if (!rows.isEmpty()) rank(phase, rows);
        return rows;
    }

    /** Sorts each group with the phase's tiebreaker chain and writes 1-based ranks. */
    public static void rank(IPhase phase, List<IStanding> rows) {
        Map<Integer, List<IStanding>> groups = new HashMap<>();
        for (IStanding s : rows) groups.computeIfAbsent(s.getGroupIndex(), _ -> new ArrayList<>()).add(s);
        Comparator<IStanding> cmp = comparator(phase);
        for (List<IStanding> g : groups.values()) {
            g.sort(cmp);
            for (int i = 0; i < g.size(); i++) g.get(i).setRank(i + 1);
        }
    }

    /** Builds the ordering from the phase's tiebreaker chain; best entrant sorts first. */
    public static Comparator<IStanding> comparator(IPhase phase) {
        List<Tiebreaker> chain = phase.getTiebreakers();
        long randomSeed = phase.getTournament() == null ? phase.getID() : phase.getTournament().getRandomSeed();
        Comparator<IStanding> cmp = null;
        for (Tiebreaker t : chain) {
            Comparator<IStanding> c = single(t, phase, randomSeed);
            cmp = cmp == null ? c : cmp.thenComparing(c);
        }
        // Always finish on a stable, total order so equal rows never swap between recounts.
        Comparator<IStanding> stable = Comparator.comparingLong(IStanding::getParticipantID);
        return cmp == null ? stable : cmp.thenComparing(stable);
    }

    private static Comparator<IStanding> single(Tiebreaker t, IPhase phase, long randomSeed) {
        return switch (t) {
            case POINTS -> Comparator.comparingDouble(IStanding::getPoints).reversed();
            case WINS -> Comparator.comparingInt(IStanding::getWins).reversed();
            case HEAD_TO_HEAD -> StandingsCalculator::headToHead;
            case GAME_DIFF -> Comparator.comparingInt(IStanding::getGameDiff).reversed();
            case GAMES_WON -> Comparator.comparingInt(IStanding::getGamesWon).reversed();
            case SCORE_DIFF -> Comparator.comparingInt(IStanding::getScoreDiff).reversed();
            case SCORE_FOR -> Comparator.comparingInt(IStanding::getScoreFor).reversed();
            case BUCHHOLZ -> Comparator.comparingDouble(IStanding::getBuchholz).reversed();
            case MEDIAN_BUCHHOLZ -> Comparator.comparingDouble(IStanding::getMedianBuchholz).reversed();
            case SONNEBORN_BERGER -> Comparator.comparingDouble(IStanding::getSonnebornBerger).reversed();
            case FORFEITS -> Comparator.comparingInt(IStanding::getForfeits);
            case SEED -> Comparator.comparingInt(s -> s.getSeed() > 0 ? s.getSeed() : Integer.MAX_VALUE);
            case RANDOM -> Comparator.comparingInt(s -> Long.hashCode(randomSeed * 31 + s.getParticipantID()));
        };
    }

    /**
     * Compares two rows on the matches they played against each other only.
     * Not transitive across a three-way tie - that is inherent to the rule, not a bug here.
     */
    private static int headToHead(IStanding a, IStanding b) {
        IPhase phase = a.getPhase();
        if (phase == null) return 0;
        int aw = 0, bw = 0, ag = 0, bg = 0;
        for (IMatch m : phase.getMatches()) {
            if (!m.getState().isDecided() || !m.hasParticipant(a.getParticipantID()) || !m.hasParticipant(b.getParticipantID())) continue;
            ag += m.getScoreFor(a.getParticipantID());
            bg += m.getScoreFor(b.getParticipantID());
            if (m.isWinner(a.getParticipantID())) aw++;
            else if (m.isWinner(b.getParticipantID())) bw++;
        }
        return aw != bw ? Integer.compare(bw, aw) : Integer.compare(bg, ag);
    }
}
