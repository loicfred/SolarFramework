package org.solarframework.tournament.impl.obj;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.solarframework.db.spring.DatabaseObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.solarframework.tournament.api.*;
import org.solarframework.tournament.impl.StandingsCalculator;
import org.solarframework.tournament.impl.engine.PhaseEngines;
import org.solarframework.tournament.obj.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Concrete {@link IMatch}. Reporting a result cascades: bracket progression, standings, phase close-out. */
@Entity
@DiscriminatorValue("0")
public class Match extends IMatch {
    private static final Logger log = LoggerFactory.getLogger(Match.class);

    protected Match() {}

    public Match(IPhase phase, BracketSide side, int round, int position) { super(phase, side, round, position); }

    /** Writes this match and its games. */
    @Override
    public void save() {
        Upsert();
        DatabaseObject.UpsertAll(getGames());
    }

    /** Appends a game to the series and folds it into the running series score. */
    @Override
    public MatchGame addGame(int gameScore1, int gameScore2) {
        MatchGame g = new MatchGame(this, getGames().size() + 1, gameScore1, gameScore2);
        getGames().add(g);
        recomputeFromGames();
        return g;
    }

    // ---- results --------------------------------------------------------------------------------

    /** Reports a series score (games won by each side) and cascades progression. */
    @Override
    public void reportResult(int score1, int score2) {
        requirePlayable();
        IPhase phase = getPhase();
        if (score1 == score2 && (phase == null || !phase.isDrawAllowed())) throw TournamentException.of("Match %d cannot end level - draws are not allowed in '%s'", getMatchNumber(), phase == null ? "?" : phase.getName());
        if (score1 < 0 || score2 < 0) throw TournamentException.of("Negative score reported on match %d", getMatchNumber());
        setScore(score1, score2);
        setState(MatchState.COMPLETE);
        log.info("Match {} reported {}-{} ({})", getMatchNumber(), score1, score2, getWinner().map(IParticipant::getDisplayName).orElse("draw"));
        afterResult();
    }

    /** Appends one game of a series; the match completes automatically once clinched. */
    @Override
    public MatchGame reportGame(int gameScore1, int gameScore2) { return reportGame(gameScore1, gameScore2, null); }

    @Override
    public MatchGame reportGame(int gameScore1, int gameScore2, String stage) {
        requirePlayable();
        requireNotClinched();
        MatchGame g = addGame(gameScore1, gameScore2);
        return finalizeGame(g, stage);
    }

    /** Appends one game reported as individual player contributions; each side's game score is the sum of its own members'. */
    @Override
    public MatchGame reportGame(Map<IParticipantMember, Integer> playerPoints) { return reportGame(playerPoints, null); }

    @Override
    public MatchGame reportGame(Map<IParticipantMember, Integer> playerPoints, String stage) {
        requirePlayable();
        requireNotClinched();
        MatchGame g = addGame(0, 0);
        g.setPlayerPoints(playerPoints.entrySet().stream().collect(Collectors.toMap(e -> e.getKey().getID(), Map.Entry::getValue)));
        recomputeFromGames(); // fold this game's derived score back into the series totals
        return finalizeGame(g, stage);
    }

    // Under TOTAL_POINTS, a game-count clinch is meaningless - every game must be played so the
    // points total is real, so only "all bestOf games played" ends the match in that mode.
    private boolean decidesByPoints() { return getPhase() != null && getPhase().getMatchDecisionMode() == MatchDecisionMode.TOTAL_POINTS; }

    private void requireNotClinched() {
        if (!decidesByPoints() && isSeriesClinched()) throw TournamentException.of("Match %d series is already clinched", getMatchNumber());
    }

    /** Shared tail of every reportGame overload: stamps the stage, persists, and completes the match if this game finished it. */
    private MatchGame finalizeGame(MatchGame g, String stage) {
        g.setStage(stage);
        setState(MatchState.RUNNING);
        g.Upsert();
        log.debug("Match {} game {} reported {}-{} (series {}-{})", getMatchNumber(), g.getGameNumber(), g.getScore1(), g.getScore2(), getScore1(), getScore2());
        if (getGames().size() >= getBestOf() || (!decidesByPoints() && isSeriesClinched())) {
            decide();
            setState(MatchState.COMPLETE);
            log.info("Match {} complete {}-{} ({})", getMatchNumber(), getScore1(), getScore2(), getWinner().map(IParticipant::getDisplayName).orElse("draw"));
            afterResult();
        } else Upsert();
        return g;
    }

    /** Decides a match without play - the named entrant advances. */
    @Override
    public void reportWalkover(IParticipant winner) {
        requirePlayable();
        if (!hasParticipant(winner.getID())) throw TournamentException.of("'%s' is not in match %d", winner.getName(), getMatchNumber());
        awardWalkover(winner.getID());
        log.info("Match {} awarded to '{}' by walkover", getMatchNumber(), winner.getDisplayName());
        afterResult();
    }

    private void requirePlayable() {
        if (getState() == MatchState.CANCELLED) throw TournamentException.of("Match %d is cancelled", getMatchNumber());
        if (getState().isDecided()) throw TournamentException.of("Match %d is already decided - reset it first", getMatchNumber());
        if (!isFilled()) throw TournamentException.of("Match %d is still waiting on an entrant", getMatchNumber());
    }

    /** Advances the bracket, recounts the table, refreshes entrant records and closes out the phase. */
    void afterResult() {
        IPhase phase = getPhase();
        if (phase == null) { Upsert(); return; }
        Phase p = (Phase) phase;
        List<IMatch> changed = PhaseEngines.of(phase.getType()).onMatchDecided(phase, this);
        Upsert();
        DatabaseObject.UpsertAll(changed);
        StandingsCalculator.recompute(p); // in memory only - the save() below persists it
        p.save();
        p.tryComplete();
    }

    /** Clears a reported result and rolls back everything it advanced downstream. */
    @Override
    public void reset() {
        IPhase phase = getPhase();
        if (phase == null) throw new TournamentException("Match is not attached to a phase");
        Phase p = (Phase) phase;
        clearDownstream();
        clearResult();
        StandingsCalculator.recompute(p); // in memory only - the save() below persists it
        if (phase.getStatus() == PhaseStatus.COMPLETE) phase.setStatus(PhaseStatus.RUNNING);
        ITournament t = phase.getTournament();
        if (t != null && t.getStatus() == TournamentStatus.COMPLETE) {
            t.setStatus(TournamentStatus.RUNNING);
            t.UpdateOnly("Status");
        }
        p.save();
        log.info("Match {} reset in phase '{}'", getMatchNumber(), phase.getName());
    }

    /** Pulls the entrants this match pushed forward back out, resetting those matches first. */
    private void clearDownstream() {
        IPhase phase = getPhase();
        clearFeed(phase, getNextMatchID(), getNextMatchSlot(), getWinnerID());
        clearFeed(phase, getNextLoserMatchID(), getNextLoserMatchSlot(), getLoserID());
    }

    private void clearFeed(IPhase phase, Long targetID, Integer slot, Long participantID) {
        if (targetID == null || slot == null || participantID == null) return;
        phase.getMatch(targetID).ifPresent(next -> {
            if (!Objects.equals(next.getParticipantID(slot), participantID)) return;
            Match n = (Match) next;
            if (n.getState().isDecided()) { n.clearDownstream(); n.clearResult(); }
            n.setParticipant(slot, null);
            n.Upsert();
        });
    }

    private void clearResult() {
        getGames().forEach(DatabaseObject::Delete);
        getGames().clear();
        setScore1(0);
        setScore2(0);
        setPointsFor1(0);
        setPointsFor2(0);
        setWinnerID(null);
        setLoserID(null);
        setForfeit1(false);
        setForfeit2(false);
        setCompletedAt(null);
        setStartedAt(null);
        setState(isFilled() ? MatchState.READY : MatchState.PENDING);
        Upsert();
    }
}
