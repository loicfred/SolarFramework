package org.solarframework.tournament.api;

/** Which structure inside a phase a match belongs to. */
public enum BracketSide {
    WINNERS,
    LOSERS,
    GRAND_FINAL,
    /** Replayed grand final when the losers-bracket entrant wins the first one. */
    GRAND_FINAL_RESET,
    THIRD_PLACE,
    /** Round robin / group stage table match. */
    GROUP,
    SWISS;

    public boolean isFinal() { return this == GRAND_FINAL || this == GRAND_FINAL_RESET; }

    public static BracketSide of(String name) { return TournamentEnums.parse(BracketSide.class, name, WINNERS); }
}
