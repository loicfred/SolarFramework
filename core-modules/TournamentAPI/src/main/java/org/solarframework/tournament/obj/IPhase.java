package org.solarframework.tournament.obj;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.DiscriminatorFormula;
import org.solarframework.db.spring.DatabaseObject;
import org.solarframework.tournament.api.*;
import org.solarframework.tournament.obj.convert.PhaseStatusConverter;
import org.solarframework.tournament.obj.convert.PhaseTypeConverter;
import org.solarframework.tournament.obj.convert.MatchDecisionModeConverter;
import org.solarframework.tournament.obj.convert.SeedingMethodConverter;
import org.solarframework.tournament.util.Ids;
import org.solarframework.db.api.Lazy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One stage of a tournament. A two-phase event is typically a GROUP phase feeding a
 * SINGLE_ELIMINATION playoff; qualifiers carry over via {@link #getAdvanceTotal()}.
 * Every rule defaults to the parent tournament's value and can be overridden per phase.
 */
@Entity
@Table(name = "tournament_phase")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorFormula("'0'")
public abstract class IPhase extends DatabaseObject.ID_RECORD_OBJ<Long, IPhase> {

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(referencedColumnName = "ID", name = "TournamentID", nullable = false, insertable = false, updatable = false)
    private ITournament tournament;

    @OneToMany(mappedBy = "phase", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<IMatch> matches = new ArrayList<>();

    @OneToMany(mappedBy = "phase", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<IStanding> standings = new ArrayList<>();

    @Column(name = "TournamentID", nullable = false)
    private Long tournamentID;
    @Column(name = "OrderIndex", nullable = false)
    private int orderIndex = 0;
    @Column(name = "Name", nullable = false, length = 160)
    private String name;
    @Convert(converter = PhaseTypeConverter.class)
    @Column(name = "Type", nullable = false, length = 32)
    private PhaseType type = PhaseType.SINGLE_ELIMINATION;
    @Convert(converter = PhaseStatusConverter.class)
    @Column(name = "Status", nullable = false, length = 32)
    private PhaseStatus status = PhaseStatus.PENDING;
    /** Convenience mirror of {@code status == COMPLETE}, kept so the column is queryable. */
    @Column(name = "Complete", nullable = false, columnDefinition = "TINYINT(1)")
    private boolean complete = false;
    @Column(name = "Started", nullable = false, columnDefinition = "TINYINT(1)")
    private boolean started = false;

    // ---- format ---------------------------------------------------------------------------------
    @Column(name = "BestOf", nullable = false)
    private int bestOf = 1;
    @Column(name = "DrawAllowed", nullable = false)
    private boolean drawAllowed = false;
    @Column(name = "ThirdPlaceMatch", nullable = false)
    private boolean thirdPlaceMatch = false;
    /** Double elimination only: replay the grand final if the losers-bracket entrant wins it. */
    @Column(name = "GrandFinalReset", nullable = false)
    private boolean grandFinalReset = true;
    @Convert(converter = SeedingMethodConverter.class)
    @Column(name = "SeedingMethod", nullable = false, length = 32)
    private SeedingMethod seedingMethod = SeedingMethod.STANDINGS;
    /** How a match's winner is decided in this phase; defaults to the tournament's setting. */
    @Convert(converter = MatchDecisionModeConverter.class)
    @Column(name = "MatchDecisionMode", nullable = false, length = 32)
    private MatchDecisionMode matchDecisionMode = MatchDecisionMode.GAMES_WON;

    // ---- group / round robin --------------------------------------------------------------------
    @Column(name = "GroupCount", nullable = false)
    private int groupCount = 1;
    /** 0 = derive from participant count / groupCount. */
    @Column(name = "GroupSize", nullable = false)
    private int groupSize = 0;
    /** Entrants qualifying out of each group into the next phase. */
    @Column(name = "AdvancePerGroup", nullable = false)
    private int advancePerGroup = 2;
    /** Hard cap on qualifiers across the whole phase; 0 = advancePerGroup * groupCount. */
    @Column(name = "AdvanceTotal", nullable = false)
    private int advanceTotal = 0;
    @Column(name = "DoubleRoundRobin", nullable = false)
    private boolean doubleRoundRobin = false;

    // ---- swiss ----------------------------------------------------------------------------------
    /** 0 = ceil(log2(entrants)). */
    @Column(name = "SwissRounds", nullable = false)
    private int swissRounds = 0;
    /** Stop pairing an entrant once they reach this many losses; 0 disables. */
    @Column(name = "SwissCutLosses", nullable = false)
    private int swissCutLosses = 0;

    // ---- points ---------------------------------------------------------------------------------
    @Column(name = "PointsPerWin", nullable = false)
    private double pointsPerWin = 3;
    @Column(name = "PointsPerDraw", nullable = false)
    private double pointsPerDraw = 1;
    @Column(name = "PointsPerLoss", nullable = false)
    private double pointsPerLoss = 0;
    @Column(name = "PointsPerBye", nullable = false)
    private double pointsPerBye = 3;
    @Column(name = "PointsPerForfeit", nullable = false)
    private double pointsPerForfeit = 0;
    @Column(name = "Tiebreakers", length = 400)
    private String tiebreakers;

    // ---- generated shape ------------------------------------------------------------------------
    /** Power-of-two bracket size for elimination phases, 0 otherwise. */
    @Column(name = "BracketSize", nullable = false)
    private int bracketSize = 0;
    @Column(name = "TotalRounds", nullable = false)
    private int totalRounds = 0;
    @Column(name = "CurrentRound", nullable = false)
    private int currentRound = 0;
    @Column(name = "ParticipantCount", nullable = false)
    private int participantCount = 0;
    @Column(name = "StartedAt")
    private Instant startedAt;
    @Column(name = "CompletedAt")
    private Instant completedAt;

    protected IPhase() {}

    protected IPhase(ITournament tournament, String name, PhaseType type, int orderIndex) {
        this.ID = Ids.next();
        this.tournament = tournament;
        this.tournamentID = tournament.getID();
        this.name = name;
        this.type = type;
        this.orderIndex = orderIndex;
        this.bestOf = tournament.getDefaultBestOf();
        this.drawAllowed = tournament.isDrawAllowed();
        this.thirdPlaceMatch = tournament.isThirdPlaceMatch();
        this.grandFinalReset = tournament.isGrandFinalReset();
        this.pointsPerWin = tournament.getPointsPerWin();
        this.pointsPerDraw = tournament.getPointsPerDraw();
        this.pointsPerLoss = tournament.getPointsPerLoss();
        this.pointsPerBye = tournament.getPointsPerBye();
        this.pointsPerForfeit = tournament.getPointsPerForfeit();
        this.tiebreakers = TournamentEnums.join(type == PhaseType.SWISS ? Tiebreaker.DEFAULT_SWISS : tournament.getTiebreakers());
        this.seedingMethod = orderIndex == 0 ? tournament.getSeedingMethod() : SeedingMethod.STANDINGS;
        this.matchDecisionMode = tournament.getMatchDecisionMode();
    }

    // ---- children -------------------------------------------------------------------------------

    public ITournament getTournament() {
        return tournament == null ? tournament = retrieveEntityServiceFor(ITournament.class).getById(tournamentID).orElse(null) : tournament;
    }
    public void setTournament(ITournament t) { this.tournament = t; this.tournamentID = t == null ? null : t.getID(); }

    /** Derived from {@link #orderIndex} rather than stored, so it can never drift from the real phase order. */
    public Optional<IPhase> getNextPhase() { return getTournament() == null ? Optional.empty() : getTournament().getPhase(orderIndex + 1); }
    public Optional<IPhase> getPreviousPhase() { return getTournament() == null ? Optional.empty() : getTournament().getPhase(orderIndex - 1); }
    public boolean hasNextPhase() { return getNextPhase().isPresent(); }

    public List<IMatch> getMatches() {
        if (matches == null) matches = new ArrayList<>(retrieveEntityServiceFor(IMatch.class).getAllWhere("PhaseID = ?", ID));
        return matches;
    }

    public List<IStanding> getStandings() {
        if (standings == null) standings = new ArrayList<>(retrieveEntityServiceFor(IStanding.class).getAllWhere("PhaseID = ?", ID));
        return standings;
    }

    public Optional<IMatch> getMatch(Long matchID) {
        return matchID == null ? Optional.empty() : getMatches().stream().filter(m -> Objects.equals(m.getID(), matchID)).findFirst();
    }

    public List<IMatch> getMatches(BracketSide side) {
        return getMatches().stream().filter(m -> m.getBracketSide() == side).toList();
    }

    public List<IMatch> getMatches(BracketSide side, int round) {
        return getMatches().stream().filter(m -> m.getBracketSide() == side && m.getRound() == round).sorted(Comparator.comparingInt(IMatch::getPosition)).toList();
    }

    public List<IMatch> getRound(int round) {
        return getMatches().stream().filter(m -> m.getRound() == round).sorted(Comparator.comparingInt(IMatch::getPosition)).toList();
    }

    public List<IMatch> getGroupMatches(int groupIndex) {
        return getMatches().stream().filter(m -> m.getGroupIndex() != null && m.getGroupIndex() == groupIndex).toList();
    }

    /** Matches that can be played right now. */
    public List<IMatch> getPlayableMatches() {
        return getMatches().stream().filter(m -> m.getState().isPlayable()).toList();
    }

    public List<IMatch> getPendingMatches() {
        return getMatches().stream().filter(m -> !m.getState().isDecided() && m.getState() != MatchState.CANCELLED).toList();
    }

    public boolean allMatchesDecided() {
        return !getMatches().isEmpty() && getMatches().stream().allMatch(m -> m.getState().isDecided() || m.getState() == MatchState.CANCELLED);
    }

    public boolean isRoundComplete(int round) {
        List<IMatch> r = getRound(round);
        return !r.isEmpty() && r.stream().allMatch(m -> m.getState().isDecided() || m.getState() == MatchState.CANCELLED);
    }

    public Optional<IStanding> getStanding(Long participantID) {
        return participantID == null ? Optional.empty() : getStandings().stream().filter(s -> Objects.equals(s.getParticipantID(), participantID)).findFirst();
    }

    public List<IStanding> getStandings(int groupIndex) {
        return getStandings().stream().filter(s -> s.getGroupIndex() == groupIndex).sorted(Comparator.comparingInt(IStanding::getRank)).toList();
    }

    /** Entrants slotted into this phase, resolved against the parent tournament. */
    public List<IParticipant> getParticipants() {
        ITournament t = getTournament();
        if (t == null) return List.of();
        return getStandings().stream().map(s -> t.getParticipant(s.getParticipantID())).flatMap(Optional::stream).toList();
    }

    public List<IStanding> getQualified() {
        return getStandings().stream().filter(IStanding::isQualified).sorted(Comparator.comparingInt(IStanding::getRank)).toList();
    }

    // ---- derived --------------------------------------------------------------------------------

    public PhaseType getType() { return type; }
    public void setType(PhaseType type) { this.type = type; }
    public PhaseStatus getStatus() { return status; }
    public void setStatus(PhaseStatus status) {
        this.status = status;
        this.complete = status == PhaseStatus.COMPLETE;
        this.started = status != null && status.hasStarted();
        if (this.started && startedAt == null) startedAt = Instant.now();
        if (this.complete && completedAt == null) completedAt = Instant.now();
    }

    /** Effective qualifier count out of this phase. */
    public int getEffectiveAdvanceTotal() {
        return advanceTotal > 0 ? advanceTotal : Math.max(1, advancePerGroup * Math.max(1, groupCount));
    }

    /** Effective number of swiss rounds for the given field size. */
    public int getEffectiveSwissRounds(int entrants) {
        if (swissRounds > 0) return swissRounds;
        int n = Math.max(2, entrants), rounds = 0, size = 1;
        while (size < n) { size <<= 1; rounds++; }
        return rounds;
    }

    @JsonIgnore
    public double pointsFor(int wins, int draws, int losses, int byes, int forfeits) {
        return wins * pointsPerWin + draws * pointsPerDraw + losses * pointsPerLoss + byes * pointsPerBye + forfeits * pointsPerForfeit;
    }

    // ---- behavior, implemented by the concrete Phase ----------------------------------------------

    public abstract void save();
    /** Slots entrants into this phase and generates its matches. */
    public abstract void generate(List<IParticipant> entrants);
    /** Regenerates this phase from scratch, discarding all its matches and standings. */
    public abstract void regenerate();
    /** Recomputes, persists and returns this phase's table, ranked. */
    public abstract List<IStanding> recomputeStandings();
    public abstract List<IStanding> recomputeStandings(int groupIndex);
    /** Ranks and closes the phase once all its matches are decided, then advances unless the tournament
     *  turned {@link ITournament#isAutoAdvancePhases()} off. */
    public abstract boolean tryComplete();
    /** Hands this phase's qualifiers to the next one - or finishes the tournament when it was the last.
     *  Public so a consumer that advances by hand can trigger the same step {@link #tryComplete()} would. */
    public abstract boolean advanceToNext();

    // ---- accessors ------------------------------------------------------------------------------

    public Long getTournamentID() { return tournamentID; }
    public void setTournamentID(Long tournamentID) { this.tournamentID = tournamentID; this.tournament = null; }
    public int getOrderIndex() { return orderIndex; }
    public void setOrderIndex(int orderIndex) { this.orderIndex = orderIndex; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isComplete() { return complete; }
    public boolean isStarted() { return started; }
    public int getBestOf() { return bestOf; }
    public void setBestOf(int bestOf) { this.bestOf = Math.max(1, bestOf); }
    public boolean isDrawAllowed() { return drawAllowed; }
    public void setDrawAllowed(boolean drawAllowed) { this.drawAllowed = drawAllowed; }
    public boolean isThirdPlaceMatch() { return thirdPlaceMatch; }
    public void setThirdPlaceMatch(boolean thirdPlaceMatch) { this.thirdPlaceMatch = thirdPlaceMatch; }
    public boolean isGrandFinalReset() { return grandFinalReset; }
    public void setGrandFinalReset(boolean grandFinalReset) { this.grandFinalReset = grandFinalReset; }
    public SeedingMethod getSeedingMethod() { return seedingMethod; }
    public void setSeedingMethod(SeedingMethod seedingMethod) { this.seedingMethod = seedingMethod; }
    public MatchDecisionMode getMatchDecisionMode() { return matchDecisionMode; }
    public void setMatchDecisionMode(MatchDecisionMode matchDecisionMode) { this.matchDecisionMode = matchDecisionMode; }
    public int getGroupCount() { return groupCount; }
    public void setGroupCount(int groupCount) { this.groupCount = Math.max(1, groupCount); }
    public int getGroupSize() { return groupSize; }
    public void setGroupSize(int groupSize) { this.groupSize = Math.max(0, groupSize); }
    public int getAdvancePerGroup() { return advancePerGroup; }
    public void setAdvancePerGroup(int advancePerGroup) { this.advancePerGroup = Math.max(0, advancePerGroup); }
    public int getAdvanceTotal() { return advanceTotal; }
    public void setAdvanceTotal(int advanceTotal) { this.advanceTotal = Math.max(0, advanceTotal); }
    public boolean isDoubleRoundRobin() { return doubleRoundRobin; }
    public void setDoubleRoundRobin(boolean doubleRoundRobin) { this.doubleRoundRobin = doubleRoundRobin; }
    public int getSwissRounds() { return swissRounds; }
    public void setSwissRounds(int swissRounds) { this.swissRounds = Math.max(0, swissRounds); }
    public int getSwissCutLosses() { return swissCutLosses; }
    public void setSwissCutLosses(int swissCutLosses) { this.swissCutLosses = Math.max(0, swissCutLosses); }
    public double getPointsPerWin() { return pointsPerWin; }
    public void setPointsPerWin(double v) { this.pointsPerWin = v; }
    public double getPointsPerDraw() { return pointsPerDraw; }
    public void setPointsPerDraw(double v) { this.pointsPerDraw = v; }
    public double getPointsPerLoss() { return pointsPerLoss; }
    public void setPointsPerLoss(double v) { this.pointsPerLoss = v; }
    public double getPointsPerBye() { return pointsPerBye; }
    public void setPointsPerBye(double v) { this.pointsPerBye = v; }
    public double getPointsPerForfeit() { return pointsPerForfeit; }
    public void setPointsPerForfeit(double v) { this.pointsPerForfeit = v; }
    public List<Tiebreaker> getTiebreakers() {
        List<Tiebreaker> t = TournamentEnums.split(Tiebreaker.class, tiebreakers);
        if (!t.isEmpty()) return t;
        return getType() == PhaseType.SWISS ? Tiebreaker.DEFAULT_SWISS : Tiebreaker.DEFAULT_GROUP;
    }
    public void setTiebreakers(List<Tiebreaker> tiebreakers) { this.tiebreakers = TournamentEnums.join(tiebreakers); }
    public int getBracketSize() { return bracketSize; }
    public void setBracketSize(int bracketSize) { this.bracketSize = bracketSize; }
    public int getTotalRounds() { return totalRounds; }
    public void setTotalRounds(int totalRounds) { this.totalRounds = totalRounds; }
    public int getCurrentRound() { return currentRound; }
    public void setCurrentRound(int currentRound) { this.currentRound = currentRound; }
    public int getParticipantCount() { return participantCount; }
    public void setParticipantCount(int participantCount) { this.participantCount = participantCount; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    @Override
    public String toString() { return "Phase[" + ID + " #" + orderIndex + " " + name + " " + type + " " + status + "]"; }
}
