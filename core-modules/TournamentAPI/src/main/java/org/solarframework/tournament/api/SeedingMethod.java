package org.solarframework.tournament.api;

/** How entrants are ordered before being slotted into a phase. */
public enum SeedingMethod {
    /** Registration order. */
    ORDER,
    /** Shuffled with the tournament's random seed so the draw is reproducible. */
    RANDOM,
    /** Descending rating / elo. */
    RATING,
    /** Explicit seed number set on each participant. */
    MANUAL,
    /** Carried over from the standings of the previous phase. */
    STANDINGS,
    /** Snake distribution across groups (1,2,3,3,2,1...) - only meaningful for group phases. */
    SNAKE;

    public static SeedingMethod of(String name) { return TournamentEnums.parse(SeedingMethod.class, name, MANUAL); }
}
