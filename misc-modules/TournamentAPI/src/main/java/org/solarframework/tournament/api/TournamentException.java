package org.solarframework.tournament.api;

/** Thrown when an operation is refused by the tournament rules (bad state, illegal score, full field...). */
public class TournamentException extends RuntimeException {
    public TournamentException(String message) { super(message); }
    public TournamentException(String message, Throwable cause) { super(message, cause); }

    public static TournamentException of(String format, Object... args) { return new TournamentException(String.format(format, args)); }
}
