package org.solarframework.tournament.obj;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.persistence.Entity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.solarframework.db.api.Lazy;
import org.solarframework.db.spring.DatabaseObject;
import org.solarframework.tournament.api.*;
import org.solarframework.tournament.api.IPhaseEngine;
import org.solarframework.tournament.api.PhaseEngines;
import org.solarframework.tournament.api.PhaseStatus;
import org.solarframework.tournament.api.PhaseType;
import org.solarframework.tournament.api.TournamentException;
import org.solarframework.tournament.obj.*;
import org.solarframework.tournament.obj.convert.MatchDecisionModeConverter;
import org.solarframework.tournament.obj.convert.PhaseStatusConverter;
import org.solarframework.tournament.obj.convert.PhaseTypeConverter;
import org.solarframework.tournament.obj.convert.SeedingMethodConverter;
import org.solarframework.tournament.util.Ids;
import org.solarframework.tournament.util.Seeder;
import org.solarframework.tournament.util.StandingsCalculator;

/**
 * One stage of a tournament. A two-phase event is typically a GROUP phase feeding a
 * SINGLE_ELIMINATION playoff; qualifiers carry over via {@link #getAdvanceTotal()}.
 * Every rule defaults to the parent tournament's value and can be overridden per phase.
 */
@Entity
@Table(name = "tournament_phase")
public class Phase extends DatabaseObject.ID_RECORD_OBJ<Long, Phase> {

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(referencedColumnName = "ID", name = "TournamentID", nullable = false, insertable = false, updatable = false)
    private Tournament tournament;

    @OneToMany(mappedBy = "phase", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Match> matches = new ArrayList<>();

    @OneToMany(mappedBy = "phase", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Standing> standings = new ArrayList<>();

    @Column(name = "TournamentID", nullable = false)
    private Long tournamentID;
    @Column(name = "OrderIndex", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 0")
    private int orderIndex = 0;
    @Column(name = "Name", nullable = false, length = 160)
    private String name;
    @Convert(converter = PhaseTypeConverter.class)
    @Column(name = "Type", nullable = false, length = 32, columnDefinition = "VARCHAR(32) NOT NULL DEFAULT 'SINGLE_ELIMINATION'")
    private PhaseType type = PhaseType.SINGLE_ELIMINATION;
    @Convert(converter = PhaseStatusConverter.class)
    @Column(name = "Status", nullable = false, length = 32, columnDefinition = "VARCHAR(32) NOT NULL DEFAULT 'PENDING'")
    private PhaseStatus status = PhaseStatus.PENDING;
    /** Convenience mirror of {@code status == COMPLETE}, kept so the column is queryable. */
    @Column(name = "Complete", nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 0")
    private boolean complete = false;
    @Column(name = "Started", nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 0")
    private boolean started = false;

    // ---- format ---------------------------------------------------------------------------------
    @Column(name = "BestOf", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 1")
    private int bestOf = 1;
    @Column(name = "DrawAllowed", nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 0")
    private boolean drawAllowed = false;
    @Column(name = "ThirdPlaceMatch", nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 0")
    private boolean thirdPlaceMatch = false;
    /** Double elimination only: replay the grand final if the losers-bracket entrant wins it. */
    @Column(name = "GrandFinalReset", nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 1")
    private boolean grandFinalReset = true;
    @Convert(converter = SeedingMethodConverter.class)
    @Column(name = "SeedingMethod", nullable = false, length = 32, columnDefinition = "VARCHAR(32) NOT NULL DEFAULT 'STANDINGS'")
    private SeedingMethod seedingMethod = SeedingMethod.STANDINGS;
    /** How a match's winner is decided in this phase; defaults to the tournament's setting. */
    @Convert(converter = MatchDecisionModeConverter.class)
    @Column(name = "MatchDecisionMode", nullable = false, length = 32, columnDefinition = "VARCHAR(32) NOT NULL DEFAULT 'GAMES_WON'")
    private MatchDecisionMode matchDecisionMode = MatchDecisionMode.GAMES_WON;

    // ---- group / round robin --------------------------------------------------------------------
    @Column(name = "GroupCount", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 1")
    private int groupCount = 1;
    /** 0 = derive from participant count / groupCount. */
    @Column(name = "GroupSize", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 0")
    private int groupSize = 0;
    /** Entrants qualifying out of each group into the next phase. */
    @Column(name = "AdvancePerGroup", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 2")
    private int advancePerGroup = 2;
    /** Hard cap on qualifiers across the whole phase; 0 = advancePerGroup * groupCount. */
    @Column(name = "AdvanceTotal", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 0")
    private int advanceTotal = 0;
    @Column(name = "DoubleRoundRobin", nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 0")
    private boolean doubleRoundRobin = false;

    // ---- swiss ----------------------------------------------------------------------------------
    /** 0 = ceil(log2(entrants)). */
    @Column(name = "SwissRounds", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 0")
    private int swissRounds = 0;
    /** Stop pairing an entrant once they reach this many losses; 0 disables. */
    @Column(name = "SwissCutLosses", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 0")
    private int swissCutLosses = 0;

    // ---- points ---------------------------------------------------------------------------------
    @Column(name = "PointsPerWin", nullable = false, columnDefinition = "DOUBLE NOT NULL DEFAULT 3")
    private double pointsPerWin = 3;
    @Column(name = "PointsPerDraw", nullable = false, columnDefinition = "DOUBLE NOT NULL DEFAULT 1")
    private double pointsPerDraw = 1;
    @Column(name = "PointsPerLoss", nullable = false, columnDefinition = "DOUBLE NOT NULL DEFAULT 0")
    private double pointsPerLoss = 0;
    @Column(name = "PointsPerBye", nullable = false, columnDefinition = "DOUBLE NOT NULL DEFAULT 3")
    private double pointsPerBye = 3;
    @Column(name = "PointsPerForfeit", nullable = false, columnDefinition = "DOUBLE NOT NULL DEFAULT 0")
    private double pointsPerForfeit = 0;
    @Column(name = "Tiebreakers", length = 400)
    private String tiebreakers;

    // ---- generated shape ------------------------------------------------------------------------
    /** Power-of-two bracket size for elimination phases, 0 otherwise. */
    @Column(name = "BracketSize", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 0")
    private int bracketSize = 0;
    @Column(name = "TotalRounds", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 0")
    private int totalRounds = 0;
    @Column(name = "CurrentRound", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 0")
    private int currentRound = 0;
    @Column(name = "ParticipantCount", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 0")
    private int participantCount = 0;
    @Column(name = "StartedAt")
    private Instant startedAt;
    @Column(name = "CompletedAt")
    private Instant completedAt;

    protected Phase() {}

    public Phase(Tournament tournament, String name, PhaseType type, int orderIndex) {
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

    public Tournament getTournament() {
        return tournament == null ? tournament = retrieveEntityServiceFor(Tournament.class).getById(tournamentID).orElse(null) : tournament;
    }
    public void setTournament(Tournament t) { this.tournament = t; this.tournamentID = t == null ? null : t.getID(); }

    /** Derived from {@link #orderIndex} rather than stored, so it can never drift from the real phase order. */
    public Optional<Phase> getNextPhase() { return getTournament() == null ? Optional.empty() : getTournament().getPhase(orderIndex + 1); }
    public Optional<Phase> getPreviousPhase() { return getTournament() == null ? Optional.empty() : getTournament().getPhase(orderIndex - 1); }
    public boolean hasNextPhase() { return getNextPhase().isPresent(); }

    public List<Match> getMatches() {
        if (matches == null) matches = new ArrayList<>(retrieveEntityServiceFor(Match.class).getAllWhere("PhaseID = ?", ID));
        return matches;
    }

    public List<Standing> getStandings() {
        if (standings == null) standings = new ArrayList<>(retrieveEntityServiceFor(Standing.class).getAllWhere("PhaseID = ?", ID));
        return standings;
    }

    public Optional<Match> getMatch(Long matchID) {
        return matchID == null ? Optional.empty() : getMatches().stream().filter(m -> Objects.equals(m.getID(), matchID)).findFirst();
    }

    public List<Match> getMatches(BracketSide side) {
        return getMatches().stream().filter(m -> m.getBracketSide() == side).toList();
    }

    public List<Match> getMatches(BracketSide side, int round) {
        return getMatches().stream().filter(m -> m.getBracketSide() == side && m.getRound() == round).sorted(Comparator.comparingInt(Match::getPosition)).toList();
    }

    public List<Match> getRound(int round) {
        return getMatches().stream().filter(m -> m.getRound() == round).sorted(Comparator.comparingInt(Match::getPosition)).toList();
    }

    public List<Match> getGroupMatches(int groupIndex) {
        return getMatches().stream().filter(m -> m.getGroupIndex() != null && m.getGroupIndex() == groupIndex).toList();
    }

    /** Matches that can be played right now. */
    public List<Match> getPlayableMatches() {
        return getMatches().stream().filter(m -> m.getState().isPlayable()).toList();
    }

    public List<Match> getPendingMatches() {
        return getMatches().stream().filter(m -> !m.getState().isDecided() && m.getState() != MatchState.CANCELLED).toList();
    }

    public boolean allMatchesDecided() {
        return !getMatches().isEmpty() && getMatches().stream().allMatch(m -> m.getState().isDecided() || m.getState() == MatchState.CANCELLED);
    }

    public boolean isRoundComplete(int round) {
        List<Match> r = getRound(round);
        return !r.isEmpty() && r.stream().allMatch(m -> m.getState().isDecided() || m.getState() == MatchState.CANCELLED);
    }

    public Optional<Standing> getStanding(Long participantID) {
        return participantID == null ? Optional.empty() : getStandings().stream().filter(s -> Objects.equals(s.getParticipantID(), participantID)).findFirst();
    }

    public List<Standing> getStandings(int groupIndex) {
        return getStandings().stream().filter(s -> s.getGroupIndex() == groupIndex).sorted(Comparator.comparingInt(Standing::getRank)).toList();
    }

    /** Entrants slotted into this phase, resolved against the parent tournament. */
    public List<Participant> getParticipants() {
        Tournament t = getTournament();
        if (t == null) return List.of();
        return getStandings().stream().map(s -> t.getParticipant(s.getParticipantID())).flatMap(Optional::stream).toList();
    }

    public List<Standing> getQualified() {
        return getStandings().stream().filter(Standing::isQualified).sorted(Comparator.comparingInt(Standing::getRank)).toList();
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

    /** Slots entrants into this phase and generates its matches. */
    /** Regenerates this phase from scratch, discarding all its matches and standings. */
    /** Recomputes, persists and returns this phase's table, ranked. */
    /** Ranks and closes the phase once all its matches are decided, then advances unless the tournament
     *  turned {@link Tournament#isAutoAdvancePhases()} off. */
    /** Hands this phase's qualifiers to the next one - or finishes the tournament when it was the last.
     *  Public so a consumer that advances by hand can trigger the same step {@link #tryComplete()} would. */

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

    public String toString() { return "Phase[" + ID + " #" + orderIndex + " " + name + " " + type + " " + status + "]"; }

    private static final Logger log = LoggerFactory.getLogger(Phase.class);



    /**
     * Writes this phase, its table and every match (with its games) - four statements rather than one per
     * row, since a 64-entrant phase is otherwise well over a hundred round trips for a single save.
     */
    public void save() {
        Upsert();
        DatabaseObject.UpsertAll(getStandings());
        DatabaseObject.UpsertAll(getMatches());
        DatabaseObject.UpsertAll(getMatches().stream().flatMap(m -> m.getGames().stream()).toList());
    }

    /** Slots entrants into this phase and generates its matches. */
    public void generate(List<Participant> entrants) {
        IPhaseEngine engine = PhaseEngines.of(getType());
        engine.generate(this, entrants);
        setStatus(PhaseStatus.RUNNING);
        save();
    }

    /** Regenerates this phase from scratch, discarding all its matches and standings. */
    public void regenerate() {
        List<Participant> entrants = getParticipants();
        if (entrants.isEmpty()) throw TournamentException.of("Phase '%s' has no entrants to regenerate from", getName());
        for (Match m : getMatches()) { m.getGames().forEach(DatabaseObject::Delete); m.Delete(); }
        getStandings().forEach(DatabaseObject::Delete);
        getMatches().clear();
        getStandings().clear();
        setStatus(PhaseStatus.PENDING);
        setCurrentRound(0);
        generate(entrants);
        log.info("Regenerated phase '{}'", getName());
    }

    /**
     * Recomputes, persists and returns this phase's table, ranked <em>by the format's own engine</em> - not by
     * {@link StandingsCalculator} directly, which only knows the tiebreaker chain. A knockout table is ordered by
     * how far each entrant got and shares a placing between entrants who went out at the same stage; ranking it on
     * points would put a quarterfinalist with a big game difference above a semifinalist, and would renumber the
     * shared placings 1..n. {@code AbstractPhaseEngine.rank} <em>is</em> {@code StandingsCalculator.recompute}, so
     * every format but single and double elimination is unaffected by going through the engine.
     */
    public List<Standing> recomputeStandings() {
        List<Standing> rows = PhaseEngines.of(getType()).rank(this);
        DatabaseObject.UpsertAll(rows);
        return rows.stream().sorted(Comparator.comparingInt(Standing::getGroupIndex).thenComparingInt(Standing::getRank)).toList();
    }

    public List<Standing> recomputeStandings(int groupIndex) {
        return recomputeStandings().stream().filter(s -> s.getGroupIndex() == groupIndex).toList();
    }

    /** Ranks and closes the phase once all its matches are decided, then advances unless the tournament opted out. */
    public boolean tryComplete() {
        IPhaseEngine engine = PhaseEngines.of(getType());
        if (getStatus().isComplete() || !engine.isComplete(this)) return false;
        engine.rank(this);
        setStatus(PhaseStatus.COMPLETE);
        save();
        log.info("Phase '{}' complete", getName());

        Tournament tour = getTournament();
        if (tour != null && tour.isAutoAdvancePhases()) advanceToNext();
        return true;
    }

    /**
     * Hands this phase's qualifiers to the next one, or finishes the tournament when it was the last.
     * Split out of {@link #tryComplete()} so a consumer running with auto advance off can take the same
     * step on its own terms - the organiser pressing "next phase" rather than the last score doing it.
     *
     * @return false when there is nothing to advance: the phase is not closed yet, or the next one is drawn already.
     */
    public boolean advanceToNext() {
        Tournament tour = getTournament();
        if (tour == null || !getStatus().isComplete()) return false;
        Optional<Phase> next = tour.getPhase(getOrderIndex() + 1);
        if (next.isEmpty()) {
            if (!tour.getStatus().isFinished()) tour.finish();
            return true;
        }
        if (next.get().isStarted()) return false;
        List<Participant> qualifiers = PhaseEngines.of(getType()).getQualifiers(this);
        List<Participant> field = Seeder.applySeedNumbers(new ArrayList<>(qualifiers));
        ((Tournament) tour).markEliminated(this, qualifiers);
        tour.setCurrentPhaseIndex(next.get().getOrderIndex());
        next.get().generate(field);
        tour.Upsert();
        log.info("Advanced '{}' into phase '{}' with {} entrants", tour.getName(), next.get().getName(), field.size());
        return true;
    }
}
