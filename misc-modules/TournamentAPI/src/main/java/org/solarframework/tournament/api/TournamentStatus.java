package org.solarframework.tournament.api;

/** Lifecycle of a {@code Tournament}, from creation to archival. */
public enum TournamentStatus {
    DRAFT,
    REGISTRATION_OPEN,
    REGISTRATION_CLOSED,
    CHECK_IN,
    SEEDING,
    RUNNING,
    PAUSED,
    COMPLETE,
    CANCELLED;

    public boolean acceptsRegistration() { return this == DRAFT || this == REGISTRATION_OPEN; }
    public boolean isLive() { return this == RUNNING || this == PAUSED; }
    public boolean isFinished() { return this == COMPLETE || this == CANCELLED; }

    public static TournamentStatus of(String name) { return TournamentEnums.parse(TournamentStatus.class, name, DRAFT); }
}
