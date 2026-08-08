package org.solarframework.tournament.impl.engine;

import org.solarframework.tournament.api.BracketSide;
import org.solarframework.tournament.api.PhaseType;
import org.solarframework.tournament.api.TournamentException;
import org.solarframework.tournament.impl.seed.Brackets;
import org.solarframework.tournament.obj.IMatch;
import org.solarframework.tournament.obj.IParticipant;
import org.solarframework.tournament.obj.IPhase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Winners bracket, losers bracket and a grand final. Losers alternate between "minor" rounds
 * (survivors playing each other) and "major" rounds (survivors meeting the freshly dropped winners
 * bracket losers), which is what keeps the two brackets in step.
 *
 * <p>The winners-bracket losers are fed into alternate major rounds in reverse order, the usual
 * heuristic for delaying a rematch of a pairing that already happened upstairs.
 */
public class DoubleEliminationEngine extends SingleEliminationEngine {

    @Override
    public PhaseType type() { return PhaseType.DOUBLE_ELIMINATION; }

    @Override
    public void generate(IPhase phase, List<IParticipant> entrants) {
        if (entrants.size() < 2) throw TournamentException.of("A bracket needs at least 2 entrants, got %d", entrants.size());
        int size = Brackets.nextPowerOfTwo(entrants.size());
        int wbRounds = Brackets.log2(size);
        int lbRounds = Math.max(0, 2 * wbRounds - 2);
        phase.setBracketSize(size);
        phase.setTotalRounds(wbRounds);
        phase.setParticipantCount(entrants.size());
        phase.setCurrentRound(1);
        seedStandings(phase, entrants);

        List<List<IMatch>> wb = buildSkeleton(phase, size, wbRounds, BracketSide.WINNERS);
        List<List<IMatch>> lb = buildLosersBracket(phase, size, wbRounds);
        wireLosersFeeds(wb, lb, size, wbRounds);

        IMatch grandFinal = newMatch(phase, BracketSide.GRAND_FINAL, 1, 0);
        grandFinal.setLabel("Grand Final");
        linkWinner(wb.getLast().getFirst(), grandFinal, 1);
        if (lbRounds == 0) linkLoser(wb.getLast().getFirst(), grandFinal, 2);
        else linkWinner(lb.getLast().getFirst(), grandFinal, 2);

        placeEntrants(phase, wb.getFirst(), entrants, size);
        numberMatches(phase);
        labelSlots(phase);
        resolveByes(phase);
        log.info("Generated double elimination phase '{}': {} entrants, bracket of {}, {} WB rounds, {} LB rounds, {} matches",
                phase.getName(), entrants.size(), size, wbRounds, lbRounds, phase.getMatches().size());
    }

    /** Losers rounds 1..2W-2, alternating minor (halves the field) and major (absorbs WB losers). */
    private List<List<IMatch>> buildLosersBracket(IPhase phase, int size, int wbRounds) {
        List<List<IMatch>> lb = new ArrayList<>();
        for (int k = 1; k <= wbRounds - 1; k++) {
            int count = size >> (k + 1);
            lb.add(round(phase, 2 * k - 1, count));
            lb.add(round(phase, 2 * k, count));
        }
        return lb;
    }

    private List<IMatch> round(IPhase phase, int roundNumber, int count) {
        List<IMatch> out = new ArrayList<>(count);
        for (int p = 0; p < count; p++) out.add(newMatch(phase, BracketSide.LOSERS, roundNumber, p));
        return out;
    }

    /** Connects WB losers into the losers bracket and chains the losers rounds together. */
    private void wireLosersFeeds(List<List<IMatch>> wb, List<List<IMatch>> lb, int size, int wbRounds) {
        if (lb.isEmpty()) return;
        List<IMatch> lbFirst = lb.getFirst();
        List<IMatch> wbFirst = wb.getFirst();
        for (int i = 0; i < wbFirst.size(); i++) linkLoser(wbFirst.get(i), lbFirst.get(i / 2), i % 2 + 1);

        for (int k = 1; k <= wbRounds - 1; k++) {
            List<IMatch> minor = lb.get(2 * (k - 1));
            List<IMatch> major = lb.get(2 * (k - 1) + 1);
            for (int i = 0; i < minor.size(); i++) linkWinner(minor.get(i), major.get(i), 1);

            List<IMatch> wbSource = wb.get(k);
            boolean reverse = k % 2 == 1;
            for (int i = 0; i < wbSource.size(); i++) linkLoser(wbSource.get(i), major.get(reverse ? major.size() - 1 - i : i), 2);

            if (k < wbRounds - 1) {
                List<IMatch> nextMinor = lb.get(2 * k);
                for (int i = 0; i < major.size(); i++) linkWinner(major.get(i), nextMinor.get(i / 2), i % 2 + 1);
            }
        }
    }

    @Override
    public List<IMatch> onMatchDecided(IPhase phase, IMatch match) {
        List<IMatch> changed = new ArrayList<>(propagate(phase, match));
        changed.addAll(resolveByes(phase));
        if (match.getBracketSide() == BracketSide.GRAND_FINAL) changed.addAll(maybeReset(phase, match));
        return changed;
    }

    /**
     * The winners-bracket entrant arrives at the grand final unbeaten. If the losers-bracket
     * entrant takes it, both sides have one loss and a decider is played.
     */
    private List<IMatch> maybeReset(IPhase phase, IMatch grandFinal) {
        if (!phase.isGrandFinalReset() || !grandFinal.getState().isDecided()) return List.of();
        if (!Objects.equals(grandFinal.getWinnerID(), grandFinal.getParticipantID2())) return List.of();
        if (!phase.getMatches(BracketSide.GRAND_FINAL_RESET).isEmpty()) return List.of();
        IMatch reset = newMatch(phase, BracketSide.GRAND_FINAL_RESET, 1, 0);
        reset.setLabel("Grand Final (reset)");
        reset.setBestOf(grandFinal.getBestOf());
        reset.setMatchNumber(phase.getMatches().stream().mapToInt(IMatch::getMatchNumber).max().orElse(0) + 1);
        reset.setParticipantID1(grandFinal.getParticipantID1());
        reset.setParticipantID2(grandFinal.getParticipantID2());
        log.info("Grand final reset triggered in phase '{}' - bracket reset match {} created", phase.getName(), reset.getMatchNumber());
        return List.of(reset);
    }

    @Override
    public boolean isComplete(IPhase phase) {
        if (!phase.allMatchesDecided()) return false;
        List<IMatch> gf = phase.getMatches(BracketSide.GRAND_FINAL);
        // A reset only becomes necessary once the grand final resolves, so create it here if missed.
        return gf.isEmpty() || maybeReset(phase, gf.getFirst()).isEmpty();
    }

    /**
     * Elimination order on a single scale: the losers-bracket round you went out in, with the
     * grand final ranking above every losers round.
     */
    @Override
    protected Map<Long, Integer> exitRounds(IPhase phase) {
        int lbRounds = Math.max(0, 2 * phase.getTotalRounds() - 2);
        Map<Long, Integer> exit = new HashMap<>();
        for (IMatch m : phase.getMatches()) {
            if (!m.getState().isDecided() || m.getLoserID() == null) continue;
            int key = switch (m.getBracketSide()) {
                case LOSERS -> m.getRound();
                case GRAND_FINAL, GRAND_FINAL_RESET -> lbRounds + 1;
                // A winners-bracket loss is not an elimination unless there is no losers bracket at all.
                case WINNERS -> lbRounds == 0 ? 1 : -1;
                default -> -1;
            };
            if (key >= 0) exit.merge(m.getLoserID(), key, Math::max);
        }
        return exit;
    }
}
