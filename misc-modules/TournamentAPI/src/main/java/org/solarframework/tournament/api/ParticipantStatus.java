package org.solarframework.tournament.api;

public enum ParticipantStatus {
    REGISTERED,
    CHECKED_IN,
    /** Queued behind a full field; neither in nor out until a slot frees up. */
    WAITLISTED,
    /** Still alive in the running tournament. */
    ACTIVE,
    ELIMINATED,
    /** Pulled out voluntarily. */
    WITHDRAWN,
    DISQUALIFIED,
    /** Registered but rejected / never let in. */
    REJECTED;

    public boolean isPlayable() { return this == REGISTERED || this == CHECKED_IN || this == ACTIVE; }
    public boolean isOut() { return this == ELIMINATED || this == WITHDRAWN || this == DISQUALIFIED || this == REJECTED; }

    public static ParticipantStatus of(String name) { return TournamentEnums.parse(ParticipantStatus.class, name, REGISTERED); }
}
