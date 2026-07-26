package org.solarframework.tournament.impl.obj;

import jakarta.persistence.Entity;
import org.solarframework.tournament.obj.IMatch;
import org.solarframework.tournament.obj.IMatchGame;

/** Concrete {@link IMatchGame}. */
@Entity
public class MatchGame extends IMatchGame {

    protected MatchGame() {}

    public MatchGame(IMatch match, int gameNumber, int score1, int score2) { super(match, gameNumber, score1, score2); }
}
