package org.solarframework.tournament.obj;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.DiscriminatorFormula;
import org.solarframework.db.spring.DatabaseObject;
import org.solarframework.tournament.api.BracketSide;
import org.solarframework.tournament.api.MatchDecisionMode;
import org.solarframework.tournament.api.MatchState;
import org.solarframework.tournament.obj.convert.BracketSideConverter;
import org.solarframework.tournament.obj.convert.MatchStateConverter;
import org.solarframework.tournament.util.Ids;
import org.solarframework.db.api.Lazy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A meeting between two entrants. {@code score1}/{@code score2} are the <em>series</em> score
 * (games won) - for a best-of-1 that is simply 1-0. Anything longer than a single game keeps the
 * individual games in {@link IMatchGame}, and {@link #recomputeFromGames()} folds them back into the
 * series score, so BO1 and BO5 are the same object with a different {@code bestOf}.
 *
 * <p>Progression is stored on the match itself: {@code nextMatchID}/{@code nextMatchSlot} says where
 * the winner goes, {@code nextLoserMatchID}/{@code nextLoserMatchSlot} where the loser drops to in a
 * double-elimination bracket. That makes advancing a result a local operation.
 */
@Entity
@Table(name = "tournament_match")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorFormula("'0'")
public abstract class IMatch extends DatabaseObject.ID_RECORD_OBJ<Long, IMatch> {

    @ManyToOne(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    @JoinColumn(referencedColumnName = "ID", name = "PhaseID", nullable = false, insertable = false, updatable = false)
    private IPhase phase;

    @OneToMany(mappedBy = "match", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<IMatchGame> games = new ArrayList<>();

    @Column(name = "TournamentID", nullable = false)
    private Long tournamentID;
    @Column(name = "PhaseID", nullable = false)
    private Long phaseID;

    // ---- position in the schedule ---------------------------------------------------------------
    @Convert(converter = BracketSideConverter.class)
    @Column(name = "BracketSide", nullable = false, length = 32, columnDefinition = "VARCHAR(32) NOT NULL DEFAULT 'WINNERS'")
    private BracketSide bracketSide = BracketSide.WINNERS;
    /** 1-based round within its bracket side. */
    @Column(name = "Round", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 1")
    private int round = 1;
    /** 0-based index within the round, top to bottom. */
    @Column(name = "Position", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 0")
    private int position = 0;
    /** Human-facing sequential number within the phase. */
    @Column(name = "MatchNumber", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 0")
    private int matchNumber = 0;
    @Column(name = "GroupIndex")
    private Integer groupIndex;
    @Column(name = "Label", length = 120)
    private String label;

    // ---- entrants -------------------------------------------------------------------------------
    @Column(name = "ParticipantID1")
    private Long participantID1;
    @Column(name = "ParticipantID2")
    private Long participantID2;
    /** Placeholder text shown while a slot is unresolved, e.g. "Winner of M12". */
    @Column(name = "Slot1Label", length = 120)
    private String slot1Label;
    @Column(name = "Slot2Label", length = 120)
    private String slot2Label;

    // ---- result ---------------------------------------------------------------------------------
    @Column(name = "Score1", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 0")
    private int score1 = 0;
    @Column(name = "Score2", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 0")
    private int score2 = 0;
    /** Raw points aggregated over every game (rounds, goals, kills...) - used for tiebreakers. */
    @Column(name = "PointsFor1", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 0")
    private int pointsFor1 = 0;
    @Column(name = "PointsFor2", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 0")
    private int pointsFor2 = 0;
    @Column(name = "BestOf", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 1")
    private int bestOf = 1;
    @Convert(converter = MatchStateConverter.class)
    @Column(name = "State", nullable = false, length = 32, columnDefinition = "VARCHAR(32) NOT NULL DEFAULT 'PENDING'")
    private MatchState state = MatchState.PENDING;
    @Column(name = "WinnerID")
    private Long winnerID;
    @Column(name = "LoserID")
    private Long loserID;
    @Column(name = "Forfeit1", nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 0")
    private boolean forfeit1 = false;
    @Column(name = "Forfeit2", nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 0")
    private boolean forfeit2 = false;

    // ---- progression ----------------------------------------------------------------------------
    @Column(name = "NextMatchID")
    private Long nextMatchID;
    /** 1 or 2 - which slot of the next match the winner takes. */
    @Column(name = "NextMatchSlot")
    private Integer nextMatchSlot;
    @Column(name = "NextLoserMatchID")
    private Long nextLoserMatchID;
    @Column(name = "NextLoserMatchSlot")
    private Integer nextLoserMatchSlot;
    @Column(name = "SourceMatchID1")
    private Long sourceMatchID1;
    @Column(name = "SourceMatchID2")
    private Long sourceMatchID2;

    // ---- scheduling -----------------------------------------------------------------------------
    @Column(name = "ScheduledAt")
    private Instant scheduledAt;
    @Column(name = "StartedAt")
    private Instant startedAt;
    @Column(name = "CompletedAt")
    private Instant completedAt;
    @Column(name = "Station", length = 80)
    private String station;
    @Column(name = "StreamUrl", length = 512)
    private String streamUrl;
    @Column(name = "Notes", length = 2000)
    private String notes;

    protected IMatch() {}

    protected IMatch(IPhase phase, BracketSide side, int round, int position) {
        this.ID = Ids.next();
        this.phase = phase;
        this.phaseID = phase.getID();
        this.tournamentID = phase.getTournamentID();
        this.bracketSide = side;
        this.round = round;
        this.position = position;
        this.bestOf = phase.getBestOf();
    }

    // ---- relations ------------------------------------------------------------------------------

    public IPhase getPhase() {
        return phase;
    }
    public void setPhase(IPhase p) { this.phase = p; this.phaseID = p == null ? null : p.getID(); }

    public Optional<IParticipant> getParticipant1() { return resolve(participantID1); }
    public Optional<IParticipant> getParticipant2() { return resolve(participantID2); }

    private Optional<IParticipant> resolve(Long id) {
        if (id == null) return Optional.empty();
        IPhase p = getPhase();
        ITournament t = p == null ? null : p.getTournament();
        return t != null ? t.getParticipant(id) : retrieveEntityServiceFor(IParticipant.class).getById(id);
    }

    /** Winner as an entity; empty while undecided or on a draw. */
    public Optional<IParticipant> getWinner() { return resolve(winnerID); }
    /** Loser as an entity; empty while undecided, on a draw, or on a bye. */
    public Optional<IParticipant> getLoser() { return resolve(loserID); }

    public Optional<IParticipant> getParticipant(int slot) { return slot == 1 ? getParticipant1() : getParticipant2(); }
    public Long getParticipantID(int slot) { return slot == 1 ? participantID1 : participantID2; }

    /** Puts an entrant into slot 1 or 2, flipping the match to READY once both are known. */
    public void setParticipant(int slot, Long participantID) {
        if (slot == 1) participantID1 = participantID; else participantID2 = participantID;
        refreshReadiness();
    }

    public boolean hasParticipant(Long participantID) {
        return participantID != null && (participantID.equals(participantID1) || participantID.equals(participantID2));
    }

    /** The other entrant in this match, given one of them. */
    public Optional<Long> getOpponentID(Long participantID) {
        if (participantID == null) return Optional.empty();
        if (participantID.equals(participantID1)) return Optional.ofNullable(participantID2);
        if (participantID.equals(participantID2)) return Optional.ofNullable(participantID1);
        return Optional.empty();
    }

    /** Marks the match READY when both slots are filled and it has not been decided yet. */
    public void refreshReadiness() {
        if (getState().isDecided() || getState() == MatchState.CANCELLED) return;
        setState(participantID1 != null && participantID2 != null ? MatchState.READY : MatchState.PENDING);
    }

    // ---- games ----------------------------------------------------------------------------------

    public List<IMatchGame> getGames() {
        if (games == null) games = new ArrayList<>(retrieveEntityServiceFor(IMatchGame.class).getAllWhere("MatchID = ?", ID));
        games.sort(Comparator.comparingInt(IMatchGame::getGameNumber));
        return games;
    }

    /** Recomputes series score, raw points and winner from the individual games. */
    public void recomputeFromGames() {
        List<IMatchGame> gs = getGames();
        if (gs.isEmpty()) return;
        int w1 = 0, w2 = 0, p1 = 0, p2 = 0;
        for (IMatchGame g : gs) {
            p1 += g.getScore1();
            p2 += g.getScore2();
            if (g.getScore1() > g.getScore2()) w1++;
            else if (g.getScore2() > g.getScore1()) w2++;
        }
        score1 = w1;
        score2 = w2;
        pointsFor1 = p1;
        pointsFor2 = p2;
    }

    /** Games needed to take the series: 1 for BO1, 2 for BO3, 3 for BO5. */
    @JsonIgnore
    public int getGamesToWin() { return bestOf / 2 + 1; }

    public boolean isSeries() { return bestOf > 1; }
    /** True once one side has clinched the series and any remaining games are dead rubbers. */
    public boolean isSeriesClinched() { return Math.max(score1, score2) >= getGamesToWin(); }

    // ---- result ---------------------------------------------------------------------------------

    /**
     * Records a series score and derives winner / loser / tie. Does not touch progression -
     * the concrete Match does that so it can cascade into the next match.
     */
    public void setScore(int score1, int score2) {
        this.score1 = score1;
        this.score2 = score2;
        // A one-shot report has no individual games to sum, so the reported score doubles as the
        // raw points total too - the only number decide() has to go on for TOTAL_POINTS mode.
        this.pointsFor1 = score1;
        this.pointsFor2 = score2;
        decide();
    }

    /** Recomputes winner and loser from the current series score; {@link #isTie()} follows from the result. */
    public void decide() {
        IPhase phase = getPhase();
        boolean byPoints = phase != null && phase.getMatchDecisionMode() == MatchDecisionMode.TOTAL_POINTS;
        int a = byPoints ? pointsFor1 : score1, b = byPoints ? pointsFor2 : score2;
        if (a > b) { winnerID = participantID1; loserID = participantID2; }
        else if (b > a) { winnerID = participantID2; loserID = participantID1; }
        else { winnerID = null; loserID = null; }
    }

    /** Score, winner and timestamps for a match decided without play. */
    public void awardWalkover(Long walkoverWinnerID) {
        this.winnerID = walkoverWinnerID;
        this.loserID = getOpponentID(walkoverWinnerID).orElse(null);
        if (Objects.equals(walkoverWinnerID, participantID1)) { score1 = getGamesToWin(); score2 = 0; forfeit2 = true; }
        else { score2 = getGamesToWin(); score1 = 0; forfeit1 = true; }
        setState(MatchState.WALKOVER);
        this.completedAt = Instant.now();
    }

    /** Single occupied slot with no opponent possible - advance without playing. */
    public void awardBye() {
        this.winnerID = participantID1 != null ? participantID1 : participantID2;
        this.loserID = null;
        setState(MatchState.BYE);
        this.completedAt = Instant.now();
    }

    /** The guards are the point: an unplayed match sits at 0-0, and a bye is 0-0 with a winner. */
    public boolean isTie() {
        IPhase phase = getPhase();
        if (winnerID != null || phase == null || !phase.isDrawAllowed() || !getState().isDecided()) return false;
        return phase.getMatchDecisionMode() == MatchDecisionMode.TOTAL_POINTS ? pointsFor1 == pointsFor2 : score1 == score2;
    }
    public boolean isComplete() { return getState().isDecided(); }
    public boolean isBye() { return getState() == MatchState.BYE; }
    public boolean isWalkover() { return getState() == MatchState.WALKOVER; }
    public boolean isPlayable() { return getState().isPlayable(); }
    /** Both slots resolved. */
    public boolean isFilled() { return participantID1 != null && participantID2 != null; }
    public boolean isEmpty() { return participantID1 == null && participantID2 == null; }
    public boolean isForfeited() { return forfeit1 || forfeit2; }

    public int getScoreFor(Long participantID) { return sideValue(participantID, score1, score2); }
    public int getScoreAgainst(Long participantID) { return sideValue(participantID, score2, score1); }
    public int getPointsFor(Long participantID) { return sideValue(participantID, pointsFor1, pointsFor2); }
    public int getPointsAgainst(Long participantID) { return sideValue(participantID, pointsFor2, pointsFor1); }

    private int sideValue(Long participantID, int ifSlot1, int ifSlot2) {
        if (Objects.equals(participantID, participantID1)) return ifSlot1;
        if (Objects.equals(participantID, participantID2)) return ifSlot2;
        return 0;
    }

    public boolean isWinner(Long participantID) { return participantID != null && participantID.equals(winnerID); }
    public boolean isLoser(Long participantID) { return participantID != null && participantID.equals(loserID); }

    /** "2 - 1", or "-" while unplayed. */
    public String getScoreLine() { return isComplete() ? score1 + " - " + score2 : "-"; }

    /** Label for a slot: the entrant's name if known, otherwise the placeholder. */
    public String getSlotLabel(int slot) {
        Optional<IParticipant> p = getParticipant(slot);
        if (p.isPresent()) return p.get().getDisplayName();
        String l = slot == 1 ? slot1Label : slot2Label;
        return l == null ? "" : l;
    }

    // ---- behavior, implemented by the concrete Match ----------------------------------------------

    public abstract void save();
    /** Appends a game to the series and folds it into the running series score. */
    public abstract IMatchGame addGame(int gameScore1, int gameScore2);
    /** Reports a series score (games won by each side) and cascades progression. */
    public abstract void reportResult(int score1, int score2);
    /** Appends one game of a series; the match completes automatically once clinched. */
    public abstract IMatchGame reportGame(int gameScore1, int gameScore2);
    public abstract IMatchGame reportGame(int gameScore1, int gameScore2, String stage);
    /** Appends one game reported as individual player contributions; each side's game score is the sum of its own members'. */
    public abstract IMatchGame reportGame(java.util.Map<IParticipantMember, Integer> playerPoints);
    public abstract IMatchGame reportGame(java.util.Map<IParticipantMember, Integer> playerPoints, String stage);
    /** Decides a match without play - the named entrant advances. */
    public abstract void reportWalkover(IParticipant winner);
    /** Clears a reported result and rolls back everything it advanced downstream. */
    public abstract void reset();

    // ---- accessors ------------------------------------------------------------------------------

    public Long getTournamentID() { return tournamentID; }
    public void setTournamentID(Long tournamentID) { this.tournamentID = tournamentID; }
    public Long getPhaseID() { return phaseID; }
    public void setPhaseID(Long phaseID) { this.phaseID = phaseID; this.phase = null; }
    public BracketSide getBracketSide() { return bracketSide; }
    public void setBracketSide(BracketSide side) { this.bracketSide = side; }
    public int getRound() { return round; }
    public void setRound(int round) { this.round = round; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
    public int getMatchNumber() { return matchNumber; }
    public void setMatchNumber(int matchNumber) { this.matchNumber = matchNumber; }
    public Integer getGroupIndex() { return groupIndex; }
    public void setGroupIndex(Integer groupIndex) { this.groupIndex = groupIndex; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public Long getParticipantID1() { return participantID1; }
    public void setParticipantID1(Long id) { this.participantID1 = id; refreshReadiness(); }
    public Long getParticipantID2() { return participantID2; }
    public void setParticipantID2(Long id) { this.participantID2 = id; refreshReadiness(); }
    public String getSlot1Label() { return slot1Label; }
    public void setSlot1Label(String slot1Label) { this.slot1Label = slot1Label; }
    public String getSlot2Label() { return slot2Label; }
    public void setSlot2Label(String slot2Label) { this.slot2Label = slot2Label; }
    public int getScore1() { return score1; }
    public void setScore1(int score1) { this.score1 = score1; }
    public int getScore2() { return score2; }
    public void setScore2(int score2) { this.score2 = score2; }
    public int getPointsFor1() { return pointsFor1; }
    public void setPointsFor1(int pointsFor1) { this.pointsFor1 = pointsFor1; }
    public int getPointsFor2() { return pointsFor2; }
    public void setPointsFor2(int pointsFor2) { this.pointsFor2 = pointsFor2; }
    public int getBestOf() { return bestOf; }
    public void setBestOf(int bestOf) { this.bestOf = Math.max(1, bestOf); }
    public MatchState getState() { return state; }
    public void setState(MatchState state) {
        this.state = state;
        if (state == MatchState.RUNNING && startedAt == null) startedAt = Instant.now();
        if (state != null && state.isDecided() && completedAt == null) completedAt = Instant.now();
    }
    public Long getWinnerID() { return winnerID; }
    public void setWinnerID(Long winnerID) { this.winnerID = winnerID; }
    public Long getLoserID() { return loserID; }
    public void setLoserID(Long loserID) { this.loserID = loserID; }
    public boolean isForfeit1() { return forfeit1; }
    public void setForfeit1(boolean forfeit1) { this.forfeit1 = forfeit1; }
    public boolean isForfeit2() { return forfeit2; }
    public void setForfeit2(boolean forfeit2) { this.forfeit2 = forfeit2; }
    public Long getNextMatchID() { return nextMatchID; }
    public void setNextMatchID(Long nextMatchID) { this.nextMatchID = nextMatchID; }
    public Integer getNextMatchSlot() { return nextMatchSlot; }
    public void setNextMatchSlot(Integer nextMatchSlot) { this.nextMatchSlot = nextMatchSlot; }
    public Long getNextLoserMatchID() { return nextLoserMatchID; }
    public void setNextLoserMatchID(Long id) { this.nextLoserMatchID = id; }
    public Integer getNextLoserMatchSlot() { return nextLoserMatchSlot; }
    public void setNextLoserMatchSlot(Integer slot) { this.nextLoserMatchSlot = slot; }
    public Long getSourceMatchID1() { return sourceMatchID1; }
    public void setSourceMatchID1(Long id) { this.sourceMatchID1 = id; }
    public Long getSourceMatchID2() { return sourceMatchID2; }
    public void setSourceMatchID2(Long id) { this.sourceMatchID2 = id; }
    public Instant getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public String getStation() { return station; }
    public void setStation(String station) { this.station = station; }
    public String getStreamUrl() { return streamUrl; }
    public void setStreamUrl(String streamUrl) { this.streamUrl = streamUrl; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    @Override
    public String toString() { return "Match[#" + matchNumber + " " + bracketSide + " R" + round + "." + position + " " + score1 + "-" + score2 + " " + state + "]"; }
}
