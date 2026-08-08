package org.solarframework.tournament.api;

public enum PhaseStatus {
    /** Created but no entrants assigned yet. */
    PENDING,
    /** Entrants assigned and slotted, matches generated, not started. */
    SEEDED,
    RUNNING,
    COMPLETE,
    CANCELLED;

    public boolean isComplete() { return this == COMPLETE; }
    public boolean hasStarted() { return this == RUNNING || this == COMPLETE; }

    public static PhaseStatus of(String name) { return TournamentEnums.parse(PhaseStatus.class, name, PENDING); }
}
