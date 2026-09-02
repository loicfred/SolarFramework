package org.solarframework.tournament.api;

/** Shape of a phase's schedule. A tournament chains one or more of these. */
public enum PhaseType {
    /** Every entrant plays every other entrant once (or twice when double round robin). */
    ROUND_ROBIN,
    /** Entrants are split into N groups, each group being an independent round robin. */
    GROUP,
    /** Fixed number of rounds, pairing entrants on equal score without rematches. */
    SWISS,
    /** One loss and you are out. */
    SINGLE_ELIMINATION,
    /** Two losses and you are out - winners bracket, losers bracket, grand final. */
    DOUBLE_ELIMINATION;

    public boolean isBracket() { return this == SINGLE_ELIMINATION || this == DOUBLE_ELIMINATION; }
    public boolean isTable() { return this == ROUND_ROBIN || this == GROUP || this == SWISS; }
    /** Whether the whole schedule is known up front (swiss pairs round by round). */
    public boolean isFullyPregenerated() { return this != SWISS; }

    public static PhaseType of(String name) { return TournamentEnums.parse(PhaseType.class, name, SINGLE_ELIMINATION); }

    /** The name a phase of this type gets when none is given explicitly. */
    public String defaultName() {
        return switch (this) {
            case GROUP -> "Group Stage";
            case ROUND_ROBIN -> "Round Robin";
            case SWISS -> "Swiss";
            case SINGLE_ELIMINATION, DOUBLE_ELIMINATION -> "Playoffs";
        };
    }
}
