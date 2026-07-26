package org.solarframework.tournament.obj;

import jakarta.persistence.*;
import org.solarframework.db.spring.DatabaseObject;
import org.solarframework.tournament.api.MatchState;
import org.solarframework.tournament.util.Ids;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * One entrant's row inside one phase: their slot (group / seed, assigned at generation time) plus
 * their placement once the phase completes (rank, qualified, eliminated - decided by the format's
 * engine). Everything else - the record, points, tiebreaker aggregates - is derived live from this
 * phase's matches rather than stored, so it can never drift from the actual results.
 */
@Entity
@Table(name = "tournament_standing")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
public abstract class IStanding extends DatabaseObject.ID_RECORD_OBJ<Long, IStanding> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(referencedColumnName = "ID", name = "PhaseID", nullable = false, insertable = false, updatable = false)
    private IPhase phase;

    @Column(name = "TournamentID", nullable = false)
    private Long tournamentID;
    @Column(name = "PhaseID", nullable = false)
    private Long phaseID;
    @Column(name = "ParticipantID", nullable = false)
    private Long participantID;
    /** 0 for a single-table phase, otherwise the group this entrant sits in. */
    @Column(name = "GroupIndex", nullable = false)
    private int groupIndex = 0;
    /** Seed within the phase (may differ from the tournament-wide seed). */
    @Column(name = "Seed", nullable = false)
    private int seed = 0;
    /** Set by the format's engine once the phase is ranked. */
    @Column(name = "Rank", nullable = false)
    private int rank = 0;
    @Column(name = "Qualified", nullable = false)
    private boolean qualified = false;
    @Column(name = "Eliminated", nullable = false)
    private boolean eliminated = false;

    protected IStanding() {}

    protected IStanding(IPhase phase, Long participantID, int groupIndex, int seed) {
        this.ID = Ids.next();
        this.phase = phase;
        this.phaseID = phase.getID();
        this.tournamentID = phase.getTournamentID();
        this.participantID = participantID;
        this.groupIndex = groupIndex;
        this.seed = seed;
    }

    public IPhase getPhase() {
        return phase == null ? phase = retrieveEntityServiceFor(IPhase.class).getById(phaseID).orElse(null) : phase;
    }
    public void setPhase(IPhase p) { this.phase = p; this.phaseID = p == null ? null : p.getID(); }

    public Optional<IParticipant> getParticipant() {
        IPhase p = getPhase();
        ITournament t = p == null ? null : p.getTournament();
        return t != null ? t.getParticipant(participantID) : retrieveEntityServiceFor(IParticipant.class).getById(participantID);
    }

    public String getName() { return getParticipant().map(IParticipant::getDisplayName).orElse("#" + participantID); }

    // ---- record, points and tiebreaker aggregates - all derived live from this phase's matches ----

    private Stream<IMatch> decidedMatches() {
        IPhase p = getPhase();
        return p == null ? Stream.empty() : p.getMatches().stream().filter(m -> m.getState().isDecided() && m.hasParticipant(participantID));
    }
    private Stream<IMatch> decidedNonByeMatches() { return decidedMatches().filter(m -> m.getState() != MatchState.BYE); }

    public int getByes() { return (int) decidedMatches().filter(m -> m.getState() == MatchState.BYE && m.isWinner(participantID)).count(); }
    public boolean isHadBye() { return getByes() > 0; }
    public int getPlayed() { return (int) decidedMatches().count(); }
    public int getWins() { return getByes() + (int) decidedNonByeMatches().filter(m -> m.isWinner(participantID)).count(); }
    public int getLosses() { return (int) decidedNonByeMatches().filter(m -> m.isLoser(participantID)).count(); }
    public int getDraws() { return (int) decidedNonByeMatches().filter(IMatch::isTie).count(); }
    public int getForfeits() { return (int) decidedNonByeMatches().filter(this::forfeitedBy).count(); }

    private boolean forfeitedBy(IMatch m) {
        return (Objects.equals(participantID, m.getParticipantID1()) && m.isForfeit1())
                || (Objects.equals(participantID, m.getParticipantID2()) && m.isForfeit2());
    }

    public int getGamesWon() { return decidedNonByeMatches().mapToInt(m -> m.getScoreFor(participantID)).sum(); }
    public int getGamesLost() { return decidedNonByeMatches().mapToInt(m -> m.getScoreAgainst(participantID)).sum(); }
    public int getScoreFor() { return decidedNonByeMatches().mapToInt(m -> m.getPointsFor(participantID)).sum(); }
    public int getScoreAgainst() { return decidedNonByeMatches().mapToInt(m -> m.getPointsAgainst(participantID)).sum(); }

    /** Byes score {@code pointsPerBye}; every other win scores {@code pointsPerWin}. */
    public double getPoints() {
        IPhase p = getPhase();
        return p == null ? 0 : p.pointsFor(getWins() - getByes(), getDraws(), getLosses(), getByes(), getForfeits());
    }

    // ---- opponents ------------------------------------------------------------------------------

    public List<Long> getOpponentIDs() {
        return decidedNonByeMatches().map(m -> m.getOpponentID(participantID).orElse(null)).filter(Objects::nonNull).toList();
    }

    public boolean hasFaced(Long id) { return id != null && getOpponentIDs().contains(id); }

    // ---- tiebreaker aggregates - each opponent's points are themselves derived the same way -------

    public double getBuchholz() {
        IPhase p = getPhase();
        if (p == null) return 0;
        return getOpponentIDs().stream().map(p::getStanding).flatMap(Optional::stream).mapToDouble(IStanding::getPoints).sum();
    }

    public double getMedianBuchholz() {
        IPhase p = getPhase();
        List<Double> opponentPoints = new ArrayList<>();
        if (p != null) for (Long oppID : getOpponentIDs()) p.getStanding(oppID).ifPresent(o -> opponentPoints.add(o.getPoints()));
        if (opponentPoints.size() <= 2) return getBuchholz();
        opponentPoints.sort(Double::compareTo);
        opponentPoints.removeFirst();
        opponentPoints.removeLast();
        return opponentPoints.stream().mapToDouble(Double::doubleValue).sum();
    }

    public double getSonnebornBerger() {
        IPhase p = getPhase();
        if (p == null) return 0;
        double sb = 0;
        for (Long oppID : getOpponentIDs()) sb += resultAgainst(oppID) * p.getStanding(oppID).map(IStanding::getPoints).orElse(0.0);
        return sb;
    }

    /** 1 for a win over that opponent, 0.5 for a draw, 0 otherwise - the sonneborn-berger weight. */
    private double resultAgainst(Long opponentID) {
        double best = 0;
        for (IMatch m : decidedNonByeMatches().filter(m -> m.hasParticipant(opponentID)).toList()) {
            if (m.isTie()) best = Math.max(best, 0.5);
            else if (m.isWinner(participantID)) best = Math.max(best, 1);
        }
        return best;
    }

    // ---- derived summaries ------------------------------------------------------------------------

    public int getGameDiff() { return getGamesWon() - getGamesLost(); }
    public int getScoreDiff() { return getScoreFor() - getScoreAgainst(); }
    public double getWinRate() { int played = getPlayed(); return played == 0 ? 0 : (double) getWins() / played; }
    public double getGameWinRate() { int gw = getGamesWon(), gl = getGamesLost(); return gw + gl == 0 ? 0 : (double) gw / (gw + gl); }
    /** "3-1-0" (W-L-D) or "3-1" when draws are not in play. */
    public String getRecord() { int d = getDraws(); return d > 0 ? getWins() + "-" + getLosses() + "-" + d : getWins() + "-" + getLosses(); }

    // ---- accessors ------------------------------------------------------------------------------

    public Long getTournamentID() { return tournamentID; }
    public void setTournamentID(Long tournamentID) { this.tournamentID = tournamentID; }
    public Long getPhaseID() { return phaseID; }
    public void setPhaseID(Long phaseID) { this.phaseID = phaseID; this.phase = null; }
    public Long getParticipantID() { return participantID; }
    public void setParticipantID(Long participantID) { this.participantID = participantID; }
    public int getGroupIndex() { return groupIndex; }
    public void setGroupIndex(int groupIndex) { this.groupIndex = groupIndex; }
    public int getSeed() { return seed; }
    public void setSeed(int seed) { this.seed = seed; }
    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }
    public boolean isQualified() { return qualified; }
    public void setQualified(boolean qualified) { this.qualified = qualified; }
    public boolean isEliminated() { return eliminated; }
    public void setEliminated(boolean eliminated) { this.eliminated = eliminated; }

    @Override
    public String toString() { return "Standing[" + getName() + " #" + rank + " " + getRecord() + " " + getPoints() + "pts]"; }
}
