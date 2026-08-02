package org.solarframework.tournament.obj;

import jakarta.persistence.*;
import org.hibernate.annotations.DiscriminatorFormula;
import org.solarframework.db.spring.DatabaseObject;
import org.solarframework.tournament.util.Ids;
import org.solarframework.db.api.Lazy;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * One game inside a best-of series. A BO3 match holds up to three of these; a BO1 match needs none
 * (the series score on the match is the result), though one may still be added to record a map.
 *
 * <p>For team formats, a side's score for this game can be built up from individual member
 * contributions instead of being reported as a single number - see {@link #reportPlayerPoints}.
 * Contributions are kept as a compact "memberID=points" CSV column (same idea as
 * {@code Standing.opponents}) rather than a child table, since a team is only ever a handful of
 * players and the per-side total is a trivial sum, not something worth its own relation.
 */
@Entity
@Table(name = "tournament_match_game")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorFormula("'0'")
public abstract class IMatchGame extends DatabaseObject.ID_RECORD_OBJ<Long, IMatchGame> {

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(referencedColumnName = "ID", name = "MatchID", nullable = false, insertable = false, updatable = false)
    private IMatch match;

    @Column(name = "MatchID", nullable = false)
    private Long matchID;
    /** 1-based game number within the series. */
    @Column(name = "GameNumber", nullable = false)
    private int gameNumber = 1;
    @Column(name = "Score1", nullable = false)
    private int score1 = 0;
    @Column(name = "Score2", nullable = false)
    private int score2 = 0;
    @Column(name = "WinnerID")
    private Long winnerID;
    @Column(name = "IsTie", nullable = false, columnDefinition = "TINYINT(1)")
    private boolean isTie = false;
    @Column(name = "Forfeited", nullable = false, columnDefinition = "TINYINT(1)")
    private boolean forfeited = false;
    /** Map, stage, board colour - whatever varies game to game. */
    @Column(name = "Stage", length = 160)
    private String stage;
    @Column(name = "StartedAt")
    private Instant startedAt;
    @Column(name = "CompletedAt")
    private Instant completedAt;
    @Column(name = "DurationSeconds")
    private Integer durationSeconds;
    @Column(name = "Notes", length = 1000)
    private String notes;
    /** CSV of "participantMemberID=points" - a team's score for this game is the sum of its own members' entries. */
    @Column(name = "PlayerPoints", length = 2000)
    private String playerPoints;
    @Column(name = "ExternalRef", length = 200)
    private String externalRef;

    protected IMatchGame() {}

    protected IMatchGame(IMatch match, int gameNumber, int score1, int score2) {
        this.ID = Ids.next();
        this.match = match;
        this.matchID = match.getID();
        this.gameNumber = gameNumber;
        this.score1 = score1;
        this.score2 = score2;
        this.completedAt = Instant.now();
        decide();
    }

    /** Derives the game winner from its score, honouring the phase's draw rule. */
    public void decide() {
        IMatch m = getMatch();
        if (score1 > score2) { winnerID = m == null ? null : m.getParticipantID1(); isTie = false; }
        else if (score2 > score1) { winnerID = m == null ? null : m.getParticipantID2(); isTie = false; }
        else { winnerID = null; isTie = true; }
    }

    public IMatch getMatch() {
        return match == null ? match = retrieveEntityServiceFor(IMatch.class).getById(matchID).orElse(null) : match;
    }
    public void setMatch(IMatch m) { this.match = m; this.matchID = m == null ? null : m.getID(); }

    public Optional<IParticipant> getWinner() {
        IMatch m = getMatch();
        return m == null || winnerID == null ? Optional.empty() : (Objects.equals(winnerID, m.getParticipantID1()) ? m.getParticipant1() : m.getParticipant2());
    }

    public void setScore(int score1, int score2) { this.score1 = score1; this.score2 = score2; decide(); }

    // ---- per-player contributions ------------------------------------------------------------------

    /** Individual member contributions for this game: participantMemberID to points. */
    public Map<Long, Integer> getPlayerPoints() {
        if (playerPoints == null || playerPoints.isBlank()) return Map.of();
        Map<Long, Integer> out = new LinkedHashMap<>();
        for (String part : playerPoints.split(",")) {
            String[] kv = part.split("=");
            if (kv.length == 2) out.put(Long.valueOf(kv[0].trim()), Integer.valueOf(kv[1].trim()));
        }
        return out;
    }

    /** Replaces every member contribution and rolls this game's score up from the new totals. */
    public void setPlayerPoints(Map<Long, Integer> points) {
        this.playerPoints = points == null || points.isEmpty() ? null
                : points.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(","));
        recomputeFromPlayerPoints();
    }

    /** Records (or overwrites) one team member's contribution to this game. */
    public void reportPlayerPoints(IParticipantMember member, int points) {
        Map<Long, Integer> current = new LinkedHashMap<>(getPlayerPoints());
        current.put(member.getID(), points);
        setPlayerPoints(current);
    }

    /**
     * Rolls this game's score up from its individual player contributions, splitting them by which
     * side each member's team is on. A no-op while no per-player points have been reported, so plain
     * {@link #setScore(int, int)} keeps working unchanged for matches that never use this.
     */
    private void recomputeFromPlayerPoints() {
        Map<Long, Integer> points = getPlayerPoints();
        if (points.isEmpty()) return;
        IMatch m = getMatch();
        if (m == null) return;
        setScore(sumFor(m.getParticipant1().orElse(null), points), sumFor(m.getParticipant2().orElse(null), points));
    }

    private int sumFor(IParticipant side, Map<Long, Integer> points) {
        if (side == null) return 0;
        int total = 0;
        for (IParticipantMember member : side.getMembers()) total += points.getOrDefault(member.getID(), 0);
        return total;
    }

    public Long getMatchID() { return matchID; }
    public void setMatchID(Long matchID) { this.matchID = matchID; this.match = null; }
    public int getGameNumber() { return gameNumber; }
    public void setGameNumber(int gameNumber) { this.gameNumber = gameNumber; }
    public int getScore1() { return score1; }
    public void setScore1(int score1) { this.score1 = score1; }
    public int getScore2() { return score2; }
    public void setScore2(int score2) { this.score2 = score2; }
    public Long getWinnerID() { return winnerID; }
    public void setWinnerID(Long winnerID) { this.winnerID = winnerID; }
    public boolean isTie() { return isTie; }
    public void setTie(boolean tie) { this.isTie = tie; }
    public boolean isForfeited() { return forfeited; }
    public void setForfeited(boolean forfeited) { this.forfeited = forfeited; }
    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getExternalRef() {
        return externalRef;
    }
    public void setExternalRef(String externalRef) {
        this.externalRef = externalRef;
    }

    @Override
    public String toString() { return "Game" + gameNumber + "[" + score1 + "-" + score2 + "]"; }
}
