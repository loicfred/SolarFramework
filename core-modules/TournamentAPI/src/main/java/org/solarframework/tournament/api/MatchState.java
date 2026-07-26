package org.solarframework.tournament.api;

public enum MatchState {
    /** At least one slot is still waiting on an upstream result. */
    PENDING,
    /** Both slots filled, playable. */
    READY,
    RUNNING,
    COMPLETE,
    /** Only one entrant was ever going to be here - auto advanced, never played. */
    BYE,
    /** Decided without play because an entrant failed to show or was disqualified. */
    WALKOVER,
    CANCELLED;

    public boolean isDecided() { return this == COMPLETE || this == BYE || this == WALKOVER; }
    public boolean isPlayable() { return this == READY || this == RUNNING; }

    public static MatchState of(String name) { return TournamentEnums.parse(MatchState.class, name, PENDING); }
}
