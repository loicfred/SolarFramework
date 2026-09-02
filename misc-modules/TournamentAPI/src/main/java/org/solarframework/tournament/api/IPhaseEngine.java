package org.solarframework.tournament.api;

import org.solarframework.tournament.obj.Match;
import org.solarframework.tournament.obj.Participant;
import org.solarframework.tournament.obj.Phase;
import org.solarframework.tournament.obj.Standing;
import org.solarframework.tournament.util.StandingsCalculator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Format-specific behaviour: how a phase is laid out and what happens after each result. */
public interface IPhaseEngine {

    PhaseType type();

    /**
     * Builds the phase's standings rows and its matches from an already ordered field
     * ({@code entrants.getFirst()} is seed 1). Swiss only generates round 1 here.
     */
    void generate(Phase phase, List<Participant> entrants);

    /**
     * Called after a match in this phase is decided. Pushes the winner (and, in double
     * elimination, the loser) into the next match, and creates the next swiss round when due.
     * @return matches newly made playable by this result
     */
    List<Match> onMatchDecided(Phase phase, Match match);

    /** True once nothing is left to play. A format with a conditional last match overrides this. */
    default boolean isComplete(Phase phase) { return phase.allMatchesDecided(); }

    /** Recomputes and re-ranks the phase's table; the returned list is in rank order. */
    default List<Standing> rank(Phase phase) { return StandingsCalculator.recompute(phase); }

    /**
     * Entrants that qualify out of this phase, in the order they should be seeded into the next.
     * Takes the top {@code phase.getEffectiveAdvanceTotal()} of the table and marks every row
     * qualified or eliminated; a format that advances per group overrides this.
     */
    default List<Participant> getQualifiers(Phase phase) {
        List<Standing> ranked = rank(phase).stream().sorted(Comparator.comparingInt(Standing::getRank)).toList();
        int limit = Math.min(ranked.size(), phase.getEffectiveAdvanceTotal());
        List<Participant> out = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            Standing s = ranked.get(i);
            boolean in = i < limit;
            s.setQualified(in);
            s.setEliminated(!in);
            if (in) s.getParticipant().ifPresent(out::add);
        }
        return out;
    }

    /**
     * Re-derives the progression a phase's current results imply, for a phase whose links were never
     * followed or were followed only halfway — an import written match by match, or a run whose later
     * match was reported before the one feeding it. Non-destructive: it only ever <em>fills</em> an
     * empty slot, so no reported result is disturbed.
     *
     * @return the matches it changed
     */
    List<Match> repair(Phase phase);
}
