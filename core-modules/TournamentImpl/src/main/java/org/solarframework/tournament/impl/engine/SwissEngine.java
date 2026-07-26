package org.solarframework.tournament.impl.engine;

import org.solarframework.tournament.api.BracketSide;
import org.solarframework.tournament.api.PhaseType;
import org.solarframework.tournament.api.TournamentException;
import org.solarframework.tournament.impl.StandingsCalculator;
import org.solarframework.tournament.obj.IMatch;
import org.solarframework.tournament.obj.IParticipant;
import org.solarframework.tournament.obj.IPhase;
import org.solarframework.tournament.obj.IStanding;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Swiss: a fixed number of rounds, pairing entrants on equal score without repeating a matchup.
 * Only the current round exists at any time - the next one is drawn as soon as the last result of
 * the previous round lands.
 *
 * <p>Pairing is a depth-first search over the score-ordered field. Perfect pairings are not always
 * possible late in a small event, so if the search exhausts, rematches are allowed as a fallback
 * rather than leaving the round undrawn.
 */
public class SwissEngine extends AbstractPhaseEngine {

    @Override
    public PhaseType type() { return PhaseType.SWISS; }

    @Override
    public void generate(IPhase phase, List<IParticipant> entrants) {
        if (entrants.size() < 2) throw TournamentException.of("A swiss phase needs at least 2 entrants, got %d", entrants.size());
        int rounds = phase.getEffectiveSwissRounds(entrants.size());
        phase.setTotalRounds(rounds);
        phase.setParticipantCount(entrants.size());
        phase.setSwissRounds(rounds);
        phase.setCurrentRound(1);
        seedStandings(phase, entrants);
        StandingsCalculator.recompute(phase);
        pairRound(phase, 1);
        numberMatches(phase);
        log.info("Generated swiss phase '{}': {} entrants, {} rounds, round 1 drawn with {} matches",
                phase.getName(), entrants.size(), rounds, phase.getRound(1).size());
    }

    @Override
    public List<IMatch> onMatchDecided(IPhase phase, IMatch match) {
        StandingsCalculator.recompute(phase);
        int round = match.getRound();
        if (!phase.isRoundComplete(round) || round >= phase.getTotalRounds()) return List.of();
        List<IMatch> drawn = pairRound(phase, round + 1);
        numberMatches(phase);
        phase.setCurrentRound(round + 1);
        log.info("Swiss phase '{}' round {} drawn: {} matches", phase.getName(), round + 1, drawn.size());
        return drawn;
    }

    @Override
    public boolean isComplete(IPhase phase) {
        return phase.getCurrentRound() >= phase.getTotalRounds() && phase.allMatchesDecided();
    }

    /** Draws one round from the current table. */
    private List<IMatch> pairRound(IPhase phase, int round) {
        List<IStanding> field = new ArrayList<>(phase.getStandings().stream().filter(s -> isStillPaired(phase, s)).toList());
        field.sort(pairingOrder(phase));
        List<IMatch> created = new ArrayList<>();

        if (field.size() % 2 == 1) {
            IStanding bye = pickBye(field);
            field.remove(bye);
            IMatch m = newMatch(phase, BracketSide.SWISS, round, created.size());
            m.setParticipantID1(bye.getParticipantID());
            m.awardBye();
            created.add(m);
        }

        List<IStanding[]> pairs = new ArrayList<>();
        if (!search(field, new boolean[field.size()], pairs, true) && !search(field, new boolean[field.size()], pairs, false)) {
            log.warn("Swiss phase '{}' round {}: no pairing found, falling back to adjacent pairing", phase.getName(), round);
            pairs.clear();
            for (int i = 0; i + 1 < field.size(); i += 2) pairs.add(new IStanding[]{field.get(i), field.get(i + 1)});
        }
        for (IStanding[] p : pairs) {
            IMatch m = newMatch(phase, BracketSide.SWISS, round, created.size());
            m.setParticipantID1(p[0].getParticipantID());
            m.setParticipantID2(p[1].getParticipantID());
            created.add(m);
        }
        StandingsCalculator.recompute(phase);
        return created;
    }

    /** Entrants dropped by the loss cutoff stop being drawn. */
    private boolean isStillPaired(IPhase phase, IStanding s) {
        return phase.getSwissCutLosses() <= 0 || s.getLosses() < phase.getSwissCutLosses();
    }

    private Comparator<IStanding> pairingOrder(IPhase phase) {
        return Comparator.comparingDouble(IStanding::getPoints).reversed()
                .thenComparing(Comparator.comparingDouble(IStanding::getBuchholz).reversed())
                .thenComparingInt(s -> s.getSeed() > 0 ? s.getSeed() : Integer.MAX_VALUE)
                .thenComparingLong(IStanding::getParticipantID);
    }

    /** The bye goes to the lowest-placed entrant who has not had one yet. */
    private IStanding pickBye(List<IStanding> field) {
        for (int i = field.size() - 1; i >= 0; i--) if (!field.get(i).isHadBye()) return field.get(i);
        return field.getLast();
    }

    /**
     * Depth-first pairing of the score-ordered field. Always pairs the highest unpaired entrant
     * with the closest allowed opponent below them, backtracking when a branch dead-ends.
     * @param avoidRematches when false, previous opponents are allowed - the last-resort pass
     */
    private boolean search(List<IStanding> field, boolean[] used, List<IStanding[]> out, boolean avoidRematches) {
        int first = -1;
        for (int i = 0; i < field.size(); i++) if (!used[i]) { first = i; break; }
        if (first < 0) return true;
        for (int j = first + 1; j < field.size(); j++) {
            if (used[j]) continue;
            if (avoidRematches && field.get(first).hasFaced(field.get(j).getParticipantID())) continue;
            used[first] = used[j] = true;
            out.add(new IStanding[]{field.get(first), field.get(j)});
            if (search(field, used, out, avoidRematches)) return true;
            out.removeLast();
            used[first] = used[j] = false;
        }
        return false;
    }
}
