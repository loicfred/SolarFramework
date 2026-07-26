package org.solarframework.tournament.api;

/** How a match's winner is decided once its games are in. */
public enum MatchDecisionMode {
    /** Best-of-N series: whichever side won more individual games (the default). */
    GAMES_WON,
    /** Whichever side scored more raw points/goals summed across every game. */
    TOTAL_POINTS;

    public static MatchDecisionMode of(String name) { return TournamentEnums.parse(MatchDecisionMode.class, name, GAMES_WON); }
}
