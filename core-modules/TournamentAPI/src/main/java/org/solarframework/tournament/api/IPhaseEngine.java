package org.solarframework.tournament.api;

import org.solarframework.tournament.obj.IMatch;
import org.solarframework.tournament.obj.IParticipant;
import org.solarframework.tournament.obj.IPhase;
import org.solarframework.tournament.obj.IStanding;

import java.util.List;

/** Format-specific behaviour: how a phase is laid out and what happens after each result. */
public interface IPhaseEngine {

    PhaseType type();

    /**
     * Builds the phase's standings rows and its matches from an already ordered field
     * ({@code entrants.get(0)} is seed 1). Swiss only generates round 1 here.
     */
    void generate(IPhase phase, List<IParticipant> entrants);

    /**
     * Called after a match in this phase is decided. Pushes the winner (and, in double
     * elimination, the loser) into the next match, and creates the next swiss round when due.
     * @return matches newly made playable by this result
     */
    List<IMatch> onMatchDecided(IPhase phase, IMatch match);

    /** True once nothing is left to play. */
    boolean isComplete(IPhase phase);

    /** Recomputes and re-ranks the phase's table; the returned list is in rank order. */
    List<IStanding> rank(IPhase phase);

    /** Entrants that qualify out of this phase, in the order they should be seeded into the next. */
    List<IParticipant> getQualifiers(IPhase phase);

    /**
     * Re-derives the progression a phase's current results imply, for a phase whose links were never
     * followed or were followed only halfway — an import written match by match, or a run whose later
     * match was reported before the one feeding it. Non-destructive: it only ever <em>fills</em> an
     * empty slot, so no reported result is disturbed.
     *
     * @return the matches it changed
     */
    List<IMatch> repair(IPhase phase);
}
