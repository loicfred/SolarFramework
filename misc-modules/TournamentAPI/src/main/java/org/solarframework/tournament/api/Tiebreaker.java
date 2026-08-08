package org.solarframework.tournament.api;

import java.util.List;

/** Ordered criteria used to break equal standings inside a group / swiss table. */
public enum Tiebreaker {
    POINTS,
    WINS,
    /** Result of the matches played between the tied entrants only. */
    HEAD_TO_HEAD,
    /** gamesWon - gamesLost across the phase. */
    GAME_DIFF,
    GAMES_WON,
    /** scoreFor - scoreAgainst (raw points, e.g. rounds or goals). */
    SCORE_DIFF,
    SCORE_FOR,
    /** Sum of every opponent's points - rewards a harder schedule. */
    BUCHHOLZ,
    /** Buchholz with the best and worst opponent dropped. */
    MEDIAN_BUCHHOLZ,
    /** Sum of the points of beaten opponents, plus half for draws. */
    SONNEBORN_BERGER,
    /** Fewest forfeits. */
    FORFEITS,
    /** Better (lower) initial seed. */
    SEED,
    /** Deterministic shuffle from the tournament's random seed - always put this last. */
    RANDOM;

    public static final List<Tiebreaker> DEFAULT_GROUP = List.of(POINTS, HEAD_TO_HEAD, GAME_DIFF, SCORE_DIFF, GAMES_WON, SEED);
    public static final List<Tiebreaker> DEFAULT_SWISS = List.of(POINTS, BUCHHOLZ, SONNEBORN_BERGER, GAME_DIFF, SEED);

    public static Tiebreaker of(String name) { return TournamentEnums.parse(Tiebreaker.class, name, POINTS); }
}
