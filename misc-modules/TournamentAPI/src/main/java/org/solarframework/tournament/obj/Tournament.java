package org.solarframework.tournament.obj;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.solarframework.db.api.Lazy;
import org.solarframework.db.spring.DatabaseObject;
import org.solarframework.tournament.api.*;
import org.solarframework.tournament.api.PhaseEngines;
import org.solarframework.tournament.obj.*;
import org.solarframework.tournament.obj.convert.MatchDecisionModeConverter;
import org.solarframework.tournament.obj.convert.SeedingMethodConverter;
import org.solarframework.tournament.obj.convert.TournamentStatusConverter;
import org.solarframework.tournament.util.Ids;
import org.solarframework.tournament.util.Seeder;

/**
 * Root aggregate. Holds the ruleset shared by every phase (points, tiebreakers, best-of, team size)
 * plus the registration window and the final podium once play is over.
 *
 * <p>Fields, getters/setters and JPA mapping live here; the behavior below is declared abstract and
 * implemented by the single concrete {@code Tournament} in the Impl module, so callers holding this
 * type get real polymorphic dispatch instead of a downcast.
 */
@Entity
@Table(name = "tournament")
public class Tournament extends DatabaseObject.ID_RECORD_OBJ<Long, Tournament> {

    // @OrderBy so the play order comes out of the load, not out of a sort on every read: getPhases() is shared
    // by every request thread and re-sorting bumps the list's modCount, killing any iteration already in flight.
    @OneToMany(mappedBy = "tournament", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("orderIndex")
    private List<Phase> phases = new ArrayList<>();

    @OneToMany(mappedBy = "tournament", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Participant> participants = new ArrayList<>();

    // ---- identity -------------------------------------------------------------------------------
    @Column(name = "Name", nullable = false, length = 200)
    private String name;
    @Column(name = "Slug", length = 200)
    private String slug;
    @Column(name = "Description", length = 4000)
    private String description;
    @Column(name = "GameName", length = 120)
    private String gameName;
    @Column(name = "Discipline", length = 120)
    private String discipline;
    @Column(name = "OwnerRef", length = 120)
    private String ownerRef;
    @Column(name = "ExternalRef", length = 200, unique = true)
    private String externalRef;
    @Column(name = "Region", length = 80)
    private String region;
    @Column(name = "Timezone", length = 80)
    private String timezone;
    @Column(name = "RulesUrl", length = 512)
    private String rulesUrl;
    @Column(name = "BannerUrl", length = 512)
    private String bannerUrl;
    @Column(name = "IconUrl", length = 512)
    private String iconUrl;
    @Column(name = "AccentColor", length = 9)
    private String accentColor;

    // ---- state ----------------------------------------------------------------------------------
    @Convert(converter = TournamentStatusConverter.class)
    @Column(name = "Status", nullable = false, length = 32, columnDefinition = "VARCHAR(32) NOT NULL DEFAULT 'DRAFT'")
    private TournamentStatus status = TournamentStatus.DRAFT;
    @Column(name = "CurrentPhaseIndex", columnDefinition = "INT DEFAULT 0")
    private Integer currentPhaseIndex = 0;
    /** Off, a finished phase is still ranked and closed but the next one is not drawn and the run is not
     *  finished either: something outside decides when to move on. Nullable so rows written before the
     *  column existed - and every consumer that never touches it - keep advancing on their own. */
    @Column(name = "AutoAdvancePhases")
    private Boolean autoAdvancePhases;
    @Column(name = "IsPublic", nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 1")
    private boolean isPublic = true;
    @Column(name = "BracketVisible", nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 1")
    private boolean bracketVisible = true;

    // ---- format ---------------------------------------------------------------------------------
    /** 1 for 1v1, 2 for 2v2, 5 for 5v5... Drives how many members a participant may roster. */
    @Column(name = "TeamSize", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 1")
    private int teamSize = 1;
    @Column(name = "MinParticipants", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 2")
    private int minParticipants = 2;
    @Column(name = "MaxParticipants", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 0")
    private int maxParticipants = 0;
    /** With a cap set, entrants arriving into a full field queue up instead of being turned away. */
    @Column(name = "WaitlistEnabled", nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 1")
    private boolean waitlistEnabled = true;
    /** A team may register before its roster is filled, but with this set it may not take the floor short. */
    @Column(name = "RequireCompleteRosters", nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 1")
    private boolean requireCompleteRosters = true;
    /** Default series length for matches; 1 is a single match, 3 is a BO3. */
    @Column(name = "DefaultBestOf", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 1")
    private int defaultBestOf = 1;
    @Column(name = "ThirdPlaceMatch", nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 1")
    private boolean thirdPlaceMatch = true;
    @Column(name = "GrandFinalReset", nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 1")
    private boolean grandFinalReset = true;
    @Column(name = "DrawAllowed", nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 0")
    private boolean drawAllowed = false;
    @Convert(converter = SeedingMethodConverter.class)
    @Column(name = "SeedingMethod", nullable = false, length = 32, columnDefinition = "VARCHAR(32) NOT NULL DEFAULT 'MANUAL'")
    private SeedingMethod seedingMethod = SeedingMethod.MANUAL;
    @Column(name = "RandomSeed", nullable = false, columnDefinition = "BIGINT NOT NULL DEFAULT 0")
    private long randomSeed = 0L;
    /** How a match's winner is decided; each phase inherits this as its default and may override it. */
    @Convert(converter = MatchDecisionModeConverter.class)
    @Column(name = "MatchDecisionMode", nullable = false, length = 32, columnDefinition = "VARCHAR(32) NOT NULL DEFAULT 'GAMES_WON'")
    private MatchDecisionMode matchDecisionMode = MatchDecisionMode.GAMES_WON;

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
    private String tiebreakers = TournamentEnums.join(Tiebreaker.DEFAULT_GROUP);

    // ---- registration ---------------------------------------------------------------------------
    @Column(name = "CheckInRequired", nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 0")
    private boolean checkInRequired = false;
    @Column(name = "CheckInMinutes", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 30")
    private int checkInMinutes = 30;
    @Column(name = "AllowLateRegistration", nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 0")
    private boolean allowLateRegistration = false;
    @Column(name = "RegistrationOpensAt")
    private Instant registrationOpensAt;
    @Column(name = "RegistrationClosesAt")
    private Instant registrationClosesAt;
    @Column(name = "StartsAt")
    private Instant startsAt;
    @Column(name = "EndsAt")
    private Instant endsAt;

    // ---- prizing / podium -----------------------------------------------------------------------
    @Column(name = "PrizePool")
    private Double prizePool;
    @Column(name = "Currency", length = 8)
    private String currency;
    @Column(name = "EntryFee")
    private Double entryFee;
    // No podium columns: the placings are Participant.finalRank, which finish() writes for every entrant and
    // which is what the standings, the profile pages and the exports already read. Three columns holding the
    // top of that same list could only ever disagree with it - a reset match, a re-ranked phase or an imported
    // bracket updated one and not the other - so the podium is derived below instead of stored twice.

    protected Tournament() {}

    public Tournament(String name) {
        this.ID = Ids.next();
        this.name = name;
        this.slug = slugify(name);
        this.randomSeed = this.ID;
    }

    public static String slugify(String s) {
        return s == null ? null : s.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    // ---- children -------------------------------------------------------------------------------

    /**
     * Phases in play order. Loaded from the database on first access when one is registered.
     *
     * <p>The sort belongs to the load and to nothing else. A read must not structurally modify the list it
     * hands back: one {@code Tournament} instance is shared by every thread (the identity map keys on the row),
     * so {@code List.sort} — which bumps {@code modCount} on every call, sorted or not — threw
     * {@code ConcurrentModificationException} out of any iteration another thread had in flight, which is
     * {@link #getMatches()} on every page that draws a bracket. {@code @OrderBy} covers the mapped load;
     * {@code addPhase} appends at {@code size()} and {@code removePhase} reindexes, so the order is kept by
     * the mutators rather than restored by the reader.
     */
    public List<Phase> getPhases() {
        if (phases != null) return phases;
        phases = new ArrayList<>(retrieveEntityServiceFor(Phase.class).getAllWhere("TournamentID = ?", ID));
        phases.sort(Comparator.comparingInt(Phase::getOrderIndex));
        return phases;
    }

    public List<Participant> getParticipants() {
        if (participants == null) participants = new ArrayList<>(retrieveEntityServiceFor(Participant.class).getAllWhere("TournamentID = ?", ID));
        return participants;
    }

    /** Entrants holding a slot in the field. The waiting list sits outside the cap and is deliberately not counted here. */
    public List<Participant> getEntrants() {
        return getParticipants().stream().filter(p -> !p.isWaitlisted()).toList();
    }

    /** The waiting list in join order - {@link #promoteFromWaitlist()} takes from the front. */
    public List<Participant> getWaitlist() {
        return getParticipants().stream().filter(Participant::isWaitlisted)
                .sorted(Comparator.comparing(Participant::getRegisteredAt, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(Participant::getID)).toList();
    }

    /** 1-based place in the queue, 0 for an entrant that is not waitlisted. */
    public int getWaitlistPosition(Participant p) {
        List<Participant> queue = getWaitlist();
        for (int i = 0; i < queue.size(); i++) if (Objects.equals(queue.get(i).getID(), p.getID())) return i + 1;
        return 0;
    }

    /** Entrants that may still be slotted into a phase, in seed order. */
    public List<Participant> getActiveParticipants() {
        return getParticipants().stream().filter(p -> p.getStatus().isPlayable()).sorted(Comparator.comparingInt(Participant::getSeed)).toList();
    }

    public Optional<Participant> getParticipant(Long participantID) {
        return participantID == null ? Optional.empty() : getParticipants().stream().filter(p -> Objects.equals(p.getID(), participantID)).findFirst();
    }

    public Optional<Participant> getParticipantByName(String name) {
        return getParticipants().stream().filter(p -> p.getName().equalsIgnoreCase(name)).findFirst();
    }

    /** Every match of every phase. */
    public List<Match> getMatches() {
        return getPhases().stream().flatMap(p -> p.getMatches().stream()).toList();
    }

    public Optional<Phase> getPhase(int orderIndex) {
        return getPhases().stream().filter(p -> p.getOrderIndex() == orderIndex).findFirst();
    }

    public Optional<Phase> getPhaseById(Long phaseID) {
        return phaseID == null ? Optional.empty() : getPhases().stream().filter(p -> Objects.equals(p.getID(), phaseID)).findFirst();
    }

    /** The phase currently being played, i.e. the first one that is not complete. */
    public Optional<Phase> getCurrentPhase() {
        return getPhases().stream().filter(p -> !p.getStatus().isComplete()).findFirst();
    }

    public Optional<Phase> getFinalPhase() {
        List<Phase> p = getPhases();
        return p.isEmpty() ? Optional.empty() : Optional.of(p.getLast());
    }

    /** Drops a phase for good: its matches, games and table are hard-deleted, there is nothing to keep a record of. */
    public void removePhase(Phase phase) {
        getPhases().remove(phase);
        for (Match m : phase.getMatches()) { m.getGames().forEach(MatchGame::TrueDelete); m.TrueDelete(); }
        phase.getStandings().forEach(Standing::TrueDelete);
        phase.TrueDelete();
        for (int i = 0; i < phases.size(); i++) phases.get(i).setOrderIndex(i);
        UpsertAll(phases);
    }

    public boolean isSinglePhase() { return getPhases().size() <= 1; }
    public boolean isDoublePhase() { return getPhases().size() == 2; }

    // ---- registration helpers -------------------------------------------------------------------

    /** Whether the registration window itself is open, regardless of how full the field is - a full field can still take waiting list entries. */
    public boolean isRegistrationWindowOpen() {
        if (!getStatus().acceptsRegistration() && !(allowLateRegistration && getStatus().isLive())) return false;
        Instant now = Instant.now();
        if (registrationOpensAt != null && now.isBefore(registrationOpensAt)) return false;
        return registrationClosesAt == null || !now.isAfter(registrationClosesAt);
    }

    /** Whether an entrant registering right now takes a slot in the field rather than a place in the queue. */
    public boolean isRegistrationOpen() { return isRegistrationWindowOpen() && !isFull(); }

    public boolean isFull() { return maxParticipants > 0 && getEntrants().size() >= maxParticipants; }
    public boolean hasEnoughParticipants() { return getActiveParticipants().size() >= minParticipants; }

    /** Entrants still short of {@link #getTeamSize()} players. Always empty in a 1v1 tournament. */
    public List<Participant> getIncompleteTeams() {
        return getActiveParticipants().stream().filter(p -> !p.isRosterComplete()).toList();
    }

    /** Whether every entrant that would take the floor has a full roster - what {@code start()} enforces. */
    public boolean hasCompleteRosters() { return getIncompleteTeams().isEmpty(); }

    @JsonIgnore
    public int getFreeSlots() { return maxParticipants <= 0 ? Integer.MAX_VALUE : Math.max(0, maxParticipants - getEntrants().size()); }

    // ---- behavior, implemented by the concrete Tournament ----------------------------------------

    /** @param hard {@code true} really removes every row, {@code false} only stamps {@code DeletedAt} on them. */
    /** Restamps {@code finalRank} for the whole field off the phases as they stand, settling nothing else. Returns how many entrants moved. */
    /**
     * Puts a run whose state drifted back into a consistent one, without ever discarding a result.
     * See the implementation for what it repairs and what it only reports.
     *
     * @return one human-readable line per thing it fixed or refused to fix; empty when nothing was wrong
     */

    /** Registers a team entrant and rosters its members in one call. */
    /** Moves as many waiting entrants as there is room for into the field; called automatically when a slot frees up. */
    /** Removes an entrant mid-play, awarding walkovers on their remaining matches. */

    /** Applies this tournament's seeding method, assigning 1..n seed numbers. */
    /** Appends a phase, inheriting this tournament's ruleset as its defaults. */

    /** Matches that can be played right now across the whole tournament. */
    /** Final placement of every entrant, best first. */

    // ---- accessors ------------------------------------------------------------------------------

    public String getName() { return name; }
    public void setName(String name) { this.name = name; if (slug == null) slug = slugify(name); }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getGameName() { return gameName; }
    public void setGameName(String gameName) { this.gameName = gameName; }
    public String getDiscipline() { return discipline; }
    public void setDiscipline(String discipline) { this.discipline = discipline; }
    public String getOwnerRef() { return ownerRef; }
    public void setOwnerRef(String ownerRef) { this.ownerRef = ownerRef; }
    public String getExternalRef() { return externalRef; }
    public void setExternalRef(String externalRef) { this.externalRef = externalRef; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public String getRulesUrl() { return rulesUrl; }
    public void setRulesUrl(String rulesUrl) { this.rulesUrl = rulesUrl; }
    public String getBannerUrl() { return bannerUrl; }
    public void setBannerUrl(String bannerUrl) { this.bannerUrl = bannerUrl; }
    public String getIconUrl() { return iconUrl; }
    public void setIconUrl(String iconUrl) { this.iconUrl = iconUrl; }
    public String getAccentColor() { return accentColor; }
    public void setAccentColor(String accentColor) { this.accentColor = accentColor; }

    public TournamentStatus getStatus() { return status; }
    public void setStatus(TournamentStatus status) { this.status = status; }
    public int getCurrentPhaseIndex() { return currentPhaseIndex == null ? 0 : currentPhaseIndex; }
    public void setCurrentPhaseIndex(int currentPhaseIndex) { this.currentPhaseIndex = currentPhaseIndex; }
    public boolean isAutoAdvancePhases() { return autoAdvancePhases == null || autoAdvancePhases; }
    public void setAutoAdvancePhases(boolean autoAdvancePhases) { this.autoAdvancePhases = autoAdvancePhases; }
    public boolean isPublic() { return isPublic; }
    public void setPublic(boolean isPublic) { this.isPublic = isPublic; }
    public boolean isBracketVisible() { return bracketVisible; }
    public void setBracketVisible(boolean bracketVisible) { this.bracketVisible = bracketVisible; }

    public int getTeamSize() { return teamSize; }
    public void setTeamSize(int teamSize) { this.teamSize = Math.max(1, teamSize); }
    public int getMinParticipants() { return minParticipants; }
    public void setMinParticipants(int minParticipants) { this.minParticipants = Math.max(2, minParticipants); }
    public int getMaxParticipants() { return maxParticipants; }
    public void setMaxParticipants(int maxParticipants) { this.maxParticipants = Math.max(0, maxParticipants); }
    public boolean isWaitlistEnabled() { return waitlistEnabled; }
    public void setWaitlistEnabled(boolean waitlistEnabled) { this.waitlistEnabled = waitlistEnabled; }
    public boolean isRequireCompleteRosters() { return requireCompleteRosters; }
    public void setRequireCompleteRosters(boolean requireCompleteRosters) { this.requireCompleteRosters = requireCompleteRosters; }
    public int getDefaultBestOf() { return defaultBestOf; }
    public void setDefaultBestOf(int defaultBestOf) { this.defaultBestOf = Math.max(1, defaultBestOf); }
    public boolean isThirdPlaceMatch() { return thirdPlaceMatch; }
    /** Also pushed into phases that have not generated their matches yet, so the flag still counts when set after {@code create()}. */
    public void setThirdPlaceMatch(boolean thirdPlaceMatch) { this.thirdPlaceMatch = thirdPlaceMatch; ungenerated().forEach(p -> p.setThirdPlaceMatch(thirdPlaceMatch)); }
    public boolean isGrandFinalReset() { return grandFinalReset; }
    public void setGrandFinalReset(boolean grandFinalReset) { this.grandFinalReset = grandFinalReset; ungenerated().forEach(p -> p.setGrandFinalReset(grandFinalReset)); }

    /** Phases whose bracket has not been built yet - the only ones a tournament-level format flag can still change. */
    private List<Phase> ungenerated() { return getPhases().stream().filter(p -> p.getMatches().isEmpty()).toList(); }
    public boolean isDrawAllowed() { return drawAllowed; }
    public void setDrawAllowed(boolean drawAllowed) { this.drawAllowed = drawAllowed; }
    public SeedingMethod getSeedingMethod() { return seedingMethod; }
    public void setSeedingMethod(SeedingMethod seedingMethod) { this.seedingMethod = seedingMethod; }
    public long getRandomSeed() { return randomSeed; }
    public void setRandomSeed(long randomSeed) { this.randomSeed = randomSeed; }
    public MatchDecisionMode getMatchDecisionMode() { return matchDecisionMode; }
    public void setMatchDecisionMode(MatchDecisionMode matchDecisionMode) { this.matchDecisionMode = matchDecisionMode; }

    public double getPointsPerWin() { return pointsPerWin; }
    public void setPointsPerWin(double pointsPerWin) { this.pointsPerWin = pointsPerWin; }
    public double getPointsPerDraw() { return pointsPerDraw; }
    public void setPointsPerDraw(double pointsPerDraw) { this.pointsPerDraw = pointsPerDraw; }
    public double getPointsPerLoss() { return pointsPerLoss; }
    public void setPointsPerLoss(double pointsPerLoss) { this.pointsPerLoss = pointsPerLoss; }
    public double getPointsPerBye() { return pointsPerBye; }
    public void setPointsPerBye(double pointsPerBye) { this.pointsPerBye = pointsPerBye; }
    public double getPointsPerForfeit() { return pointsPerForfeit; }
    public void setPointsPerForfeit(double pointsPerForfeit) { this.pointsPerForfeit = pointsPerForfeit; }
    public List<Tiebreaker> getTiebreakers() {
        List<Tiebreaker> t = TournamentEnums.split(Tiebreaker.class, tiebreakers);
        return t.isEmpty() ? Tiebreaker.DEFAULT_GROUP : t;
    }
    public void setTiebreakers(List<Tiebreaker> tiebreakers) { this.tiebreakers = TournamentEnums.join(tiebreakers); }

    public boolean isCheckInRequired() { return checkInRequired; }
    public void setCheckInRequired(boolean checkInRequired) { this.checkInRequired = checkInRequired; }
    public int getCheckInMinutes() { return checkInMinutes; }
    public void setCheckInMinutes(int checkInMinutes) { this.checkInMinutes = checkInMinutes; }
    public boolean isAllowLateRegistration() { return allowLateRegistration; }
    public void setAllowLateRegistration(boolean allowLateRegistration) { this.allowLateRegistration = allowLateRegistration; }
    public Instant getRegistrationOpensAt() { return registrationOpensAt; }
    public void setRegistrationOpensAt(Instant registrationOpensAt) { this.registrationOpensAt = registrationOpensAt; }
    public Instant getRegistrationClosesAt() { return registrationClosesAt; }
    public void setRegistrationClosesAt(Instant registrationClosesAt) { this.registrationClosesAt = registrationClosesAt; }
    public Instant getStartsAt() { return startsAt; }
    public void setStartsAt(Instant startsAt) { this.startsAt = startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public void setEndsAt(Instant endsAt) { this.endsAt = endsAt; }

    public Double getPrizePool() { return prizePool; }
    public void setPrizePool(Double prizePool) { this.prizePool = prizePool; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public Double getEntryFee() { return entryFee; }
    public void setEntryFee(Double entryFee) { this.entryFee = entryFee; }
    /** The entrant holding a placing, or empty while the run is unfinished - {@code finalRank} is 0 until {@link #finish()}. */
    public Optional<Participant> getByFinalRank(int rank) {
        return rank < 1 ? Optional.empty() : getParticipants().stream().filter(p -> p.getFinalRank() == rank).findFirst();
    }
    public Optional<Participant> getWinner() { return getByFinalRank(1); }
    public Optional<Participant> getRunnerUp() { return getByFinalRank(2); }
    public Optional<Participant> getThirdPlace() { return getByFinalRank(3); }
    public Long getWinnerID() { return getWinner().map(Participant::getID).orElse(null); }
    public Long getRunnerUpID() { return getRunnerUp().map(Participant::getID).orElse(null); }
    public Long getThirdPlaceID() { return getThirdPlace().map(Participant::getID).orElse(null); }

    /**
     * Podium in finishing order. Placings are shared, so this is not capped at three entries: a bracket with
     * no third place match ranks both semifinal losers third and both belong on it.
     */
    public List<Participant> getPodium() {
        return getParticipants().stream().filter(p -> p.getFinalRank() >= 1 && p.getFinalRank() <= 3)
                .sorted(java.util.Comparator.comparingInt(Participant::getFinalRank).thenComparingInt(Participant::getSeed)).toList();
    }

    public String toString() { return "Tournament[" + ID + " " + name + " " + status + "]"; }

    private static final Logger log = LoggerFactory.getLogger(Tournament.class);



    // ---- lifecycle ------------------------------------------------------------------------------

    public static Tournament create(String name) {
        Tournament t = new Tournament(name);
        log.info("Created tournament '{}' ({})", name, t.getID());
        t.save();
        return t;
    }

    public static Tournament create(String name, PhaseType type) {
        Tournament t = create(name);
        t.addPhase(type.defaultName(), type);
        t.save();
        return t;
    }

    public static Tournament create(String name, PhaseType firstPhase, PhaseType secondPhase) {
        Tournament t = create(name);
        Phase a = t.addPhase(firstPhase.defaultName(), firstPhase);
        a.setAdvancePerGroup(2);
        t.addPhase(secondPhase.defaultName(), secondPhase);
        t.save();
        return t;
    }

    public static Optional<Tournament> load(Long tournamentID) {
        return retrieveEntityServiceFor(Tournament.class).getById(tournamentID).map(t -> (Tournament) t);
    }

    /**
     * Writes the whole aggregate: tournament, entrants, rosters, phases, tables, matches, games.
     *
     * <p>One batched statement per entity type instead of one per row. A full save used to be
     * {@code 1 + entrants + roster + phases * (1 + standings + matches * (1 + games))} round trips, which
     * is several hundred for a 64-entrant run and is paid on every start, every reported score and every
     * phase advance.
     */
    public void save() {
        Upsert();
        List<Participant> entrants = getParticipants();
        DatabaseObject.UpsertAll(entrants);
        DatabaseObject.UpsertAll(entrants.stream().flatMap(p -> p.getMembers().stream()).toList());
        List<Phase> phases = getPhases();
        DatabaseObject.UpsertAll(phases);
        DatabaseObject.UpsertAll(phases.stream().flatMap(p -> p.getStandings().stream()).toList());
        List<Match> matches = phases.stream().flatMap(p -> p.getMatches().stream()).toList();
        DatabaseObject.UpsertAll(matches);
        DatabaseObject.UpsertAll(matches.stream().flatMap(m -> m.getGames().stream()).toList());
    }

    public void deleteCascade(boolean hard) {
        log.info("Deleting tournament '{}' ({}) - {}", getName(), getID(), hard ? "hard" : "soft");
        // Every row of a run is an ID_RECORD_OBJ, so Delete() only stamps DeletedAt: the aggregate stops being
        // read but its matches, entrants and table stay in the table for good. Hard is what a caller offering
        // "delete it for real" needs - nothing is left to be found by an id lookup afterwards.
        java.util.function.Consumer<DatabaseObject.RECORD_OBJ<?>> drop = hard ? DatabaseObject.RECORD_OBJ::TrueDelete : DatabaseObject.RECORD_OBJ::Delete;
        for (Phase phase : getPhases()) {
            for (Match m : phase.getMatches()) { m.getGames().forEach(drop); drop.accept(m); }
            phase.getStandings().forEach(drop);
            drop.accept(phase);
        }
        for (Participant p : getParticipants()) { p.getMembers().forEach(drop); drop.accept(p); }
        drop.accept(this);
    }

    public void openRegistration() {
        if (getStatus().isLive() || getStatus().isFinished()) throw TournamentException.of("Cannot reopen registration on a %s tournament", getStatus());
        setStatus(TournamentStatus.REGISTRATION_OPEN);
        if (getRegistrationOpensAt() == null) setRegistrationOpensAt(Instant.now());
        Upsert();
        log.info("Registration open for '{}'", getName());
    }

    public void closeRegistration() {
        setStatus(isCheckInRequired() ? TournamentStatus.CHECK_IN : TournamentStatus.REGISTRATION_CLOSED);
        if (getRegistrationClosesAt() == null) setRegistrationClosesAt(Instant.now());
        Upsert();
        log.info("Registration closed for '{}' with {} entrants", getName(), getParticipants().size());
    }

    public void start() {
        if (getStatus().isLive()) throw TournamentException.of("Tournament '%s' is already running", getName());
        if (getStatus().isFinished()) throw TournamentException.of("Tournament '%s' is %s", getName(), getStatus());
        List<Participant> field = seed();
        if (field.size() < getMinParticipants()) throw TournamentException.of("'%s' needs %d entrants to start, has %d", getName(), getMinParticipants(), field.size());
        requireCompleteRosters(field);
        if (getPhases().isEmpty()) {
            log.info("'{}' has no phase defined - adding a single elimination bracket", getName());
            addPhase("Playoffs", PhaseType.SINGLE_ELIMINATION);
        }
        for (Participant p : field) if (p.getStatus() != ParticipantStatus.ACTIVE) p.setStatus(ParticipantStatus.ACTIVE);
        setStatus(TournamentStatus.RUNNING);
        setCurrentPhaseIndex(0);
        if (getStartsAt() == null) setStartsAt(Instant.now());
        getPhases().getFirst().generate(field);
        save();
        log.info("Started '{}' with {} entrants over {} phase(s)", getName(), field.size(), getPhases().size());
    }

    /**
     * A team may sign up with an empty or half-filled roster and complete it later, but it cannot play
     * short: the field is checked once, at the start, against the entrants that would actually take the
     * floor. Set {@code requireCompleteRosters} to false to let short teams play anyway.
     */
    private void requireCompleteRosters(List<Participant> field) {
        if (!isRequireCompleteRosters() || getTeamSize() <= 1) return;
        List<Participant> shortTeams = field.stream().filter(p -> !p.isRosterComplete()).toList();
        if (shortTeams.isEmpty()) return;
        String detail = shortTeams.stream().map(p -> "'" + p.getName() + "' (" + p.getRosterSize() + "/" + getTeamSize() + ")").collect(Collectors.joining(", "));
        throw TournamentException.of("'%s' cannot start with %d incomplete roster(s): %s", getName(), shortTeams.size(), detail);
    }

    /**
     * Writes every entrant's {@code finalRank} from the phases as they stand, and <em>only</em> that - none of
     * the status, timestamp or podium settling {@link #finish()} does around it. Split out so a run whose
     * placings predate a change to how a format ranks can be restamped without being finished a second time,
     * which would re-announce it and pay it out again.
     *
     * @return how many entrants actually moved; 0 means the stored placings were already right
     */
    public int recomputeFinalRanks() {
        LinkedHashMap<Participant, String> placings = rankingWithTieKeys();
        List<Participant> ranking = new ArrayList<>(placings.keySet());
        List<Participant> moved = new ArrayList<>();
        // A phase that ranked two entrants equal (both semifinal losers of a bracket with no third place match)
        // places them equal here too, and the tie then skips the ranks it consumed: 1, 2, 3, 3, 5.
        for (int i = 0, rank = 0; i < ranking.size(); i++) {
            String key = placings.get(ranking.get(i));
            if (key == null || i == 0 || !key.equals(placings.get(ranking.get(i - 1)))) rank = i + 1;
            if (ranking.get(i).getFinalRank() == rank) continue;
            ranking.get(i).setFinalRank(rank);
            moved.add(ranking.get(i));
        }
        // The placements are read back by callers and after a restart, so they have to be written, not just held
        // in memory - and the podium is derived from them. UpsertAll takes its table off the head of the list.
        if (!moved.isEmpty()) DatabaseObject.UpsertAll(moved);
        // rankingWithTieKeys() ranked every started phase's table on the way here; without this the rows are correct
        // in memory and stale on disk, and the next read shows the old numbering beside the new finalRank.
        for (Phase phase : getPhases())
            if (phase.getStatus().hasStarted() && !phase.getStandings().isEmpty()) DatabaseObject.UpsertAll(phase.getStandings());
        return moved.size();
    }

    public void finish() {
        recomputeFinalRanks();
        setStatus(TournamentStatus.COMPLETE);
        setEndsAt(Instant.now());
        save();
        log.info("'{}' complete - winner: {}", getName(), getWinner().map(Participant::getDisplayName).orElse("none"));
    }

    public void cancel(String reason) {
        setStatus(TournamentStatus.CANCELLED);
        setEndsAt(Instant.now());
        save();
        log.info("'{}' cancelled: {}", getName(), reason);
    }

    /**
     * Rebuilds everything about a run that is <em>derived</em> from its matches, for a run whose state
     * drifted: a phase imported match by match rather than generated (so it never got a table), a bracket
     * whose later match was reported before the one feeding it (so the winner was never pushed forward),
     * a phase that played itself out but was never closed.
     *
     * <p><b>Nothing here discards a reported result.</b> Every step only ever fills something in:
     * standings rows are created, never dropped; a downstream slot is filled only when empty; a phase is
     * regenerated only when it holds no decided match at all, so there is nothing to lose. A phase that is
     * structurally broken <em>and</em> already has results is reported instead of touched — that call
     * belongs to the organiser, not to a repair button.
     *
     * <p>It also stops one step short of the end: a phase that is finished gets ranked and closed
     * ({@link Phase#tryComplete()}), but the tournament itself is never finished. {@link #finish()} settles
     * the podium and is what consumers hang their payouts and announcements off, so it stays an explicit
     * action — repair only reports that the run is ready for it.
     *
     * @return one line per thing fixed or refused, ready to show as-is; empty when the run was already sound
     */
    public List<String> repair() {
        List<String> report = new ArrayList<>();
        for (Phase phase : getPhases()) {
            if (!phase.getStatus().hasStarted()) continue;
            String tag = "Phase " + (phase.getOrderIndex() + 1) + " '" + phase.getName() + "': ";

            // A standings row is only ever created by the engine in generate(); a phase built any other way has
            // none, and StandingsCalculator re-ranks the rows it finds rather than creating them - so a phase
            // with no table stays empty through every recount until the rows exist. The field is read off the
            // matches, not off getParticipants(), which is itself derived from the very standings that are missing.
            List<Long> known = new ArrayList<>(phase.getStandings().stream().map(Standing::getParticipantID).toList());
            List<Standing> added = new ArrayList<>();
            for (Match m : phase.getMatches())
                for (Long pid : List.of(m.getParticipantID1() == null ? -1L : m.getParticipantID1(), m.getParticipantID2() == null ? -1L : m.getParticipantID2())) {
                    if (pid < 0 || known.contains(pid) || getParticipant(pid).isEmpty()) continue;
                    known.add(pid);
                    added.add(new Standing(phase, pid, m.getGroupIndex() == null ? 0 : m.getGroupIndex(), known.size()));
                }
            if (!added.isEmpty()) { phase.getStandings().addAll(added); report.add(tag + "created " + added.size() + " missing standings row(s)"); }

            // A phase that never drew its matches: safe to rebuild only while there is no result in it.
            if (phase.getMatches().isEmpty()) {
                if (phase.getParticipants().isEmpty()) report.add(tag + "no matches and no entrants to rebuild from - not touched");
                else { phase.regenerate(); report.add(tag + "no matches at all - rebuilt the phase from its " + phase.getParticipants().size() + " entrant(s)"); }
                continue;
            }

            int relinked = PhaseEngines.of(phase.getType()).repair(phase).size();
            if (relinked > 0) report.add(tag + "filled " + relinked + " match slot(s) the results had already decided");

            // Dangling progression links are the one structural break that cannot be inferred back: the target
            // match is simply not there, and which pairing it was is not recoverable from what is left.
            long dangling = phase.getMatches().stream().filter(m -> (m.getNextMatchID() != null && phase.getMatch(m.getNextMatchID()).isEmpty())
                    || (m.getNextLoserMatchID() != null && phase.getMatch(m.getNextLoserMatchID()).isEmpty())).count();
            if (dangling > 0) report.add(tag + dangling + " match(es) point at a match that is missing from this phase - it has results, so the bracket was left alone. Rebuild the phase by hand if the pairings are wrong");

            phase.recomputeStandings();
            if (phase.tryComplete()) report.add(tag + "every match is decided - ranked and closed the phase");
        }

        // Deliberately not finish(): the podium, the payouts and the announcements ride on it.
        if (getStatus() == TournamentStatus.RUNNING && !getPhases().isEmpty() && getPhases().stream().allMatch(p -> p.getStatus().isComplete()))
            report.add("Every phase is complete - the run is ready to be completed, which is left to you as it settles the podium.");

        save();
        log.info("Repaired '{}': {}", getName(), report.isEmpty() ? "nothing to fix" : String.join(" | ", report));
        return report;
    }

    // ---- registration ---------------------------------------------------------------------------

    public Participant register(String name) { return register(name, 0); }

    /** Takes a slot in the field, or a place in the queue behind it when the field is already full. */
    public Participant register(String name, int seed) {
        boolean full = isFull();
        if (!isRegistrationWindowOpen()) throw TournamentException.of("Registration is closed for '%s' (%s)", getName(), getStatus());
        if (full && !isWaitlistEnabled()) throw TournamentException.of("Registration is closed for '%s' (%s, field full)", getName(), getStatus());
        if (getParticipantByName(name).isPresent()) throw TournamentException.of("'%s' is already registered in '%s'", name, getName());
        Participant p = new Participant(this, name, seed);
        if (full) p.setStatus(ParticipantStatus.WAITLISTED);
        getParticipants().add(p);
        p.Upsert();
        if (full) log.info("'{}' joined the waiting list of '{}' at position {}", name, getName(), getWaitlistPosition(p));
        else log.info("Registered '{}' in '{}' ({} entrants)", name, getName(), getEntrants().size());
        return p;
    }

    /**
     * Moves waiting entrants into the field while there is room, in join order. Runs on every
     * unregistration, and can be called directly after raising the cap or clearing an entrant out
     * some other way. A started tournament has its bracket built already, so nobody is pulled in
     * once play is under way unless late registration is explicitly allowed.
     */
    public List<Participant> promoteFromWaitlist() {
        if (getStatus().isFinished() || (getStatus().isLive() && !isAllowLateRegistration())) return List.of();
        List<Participant> promoted = new ArrayList<>();
        for (Participant p : getWaitlist()) {
            if (isFull()) break;
            p.setStatus(p.isCheckedIn() ? ParticipantStatus.CHECKED_IN : ParticipantStatus.REGISTERED);
            p.Upsert();
            promoted.add(p);
            log.info("Promoted '{}' off the waiting list of '{}' ({} entrants, {} still waiting)", p.getName(), getName(), getEntrants().size(), getWaitlist().size());
        }
        return promoted;
    }

    /** Registers a team entrant and rosters its members in one call. */
    public Participant registerTeam(String teamName, List<String> memberNames) {
        Participant p = register(teamName);
        if (memberNames != null) for (String m : memberNames) p.addMember(m);
        p.getMembers().forEach(DatabaseObject::Upsert);
        if (!p.isRosterComplete()) log.warn("Team '{}' rostered {} of the required {} players", teamName, p.getMembers().size(), getTeamSize());
        return p;
    }

    /** Pre-start only, so the entrant never played: they and their roster are hard-deleted rather than kept as a soft-deleted record. */
    public void unregister(Participant p) {
        if (getStatus().isLive()) { withdraw(p); return; }
        getParticipants().remove(p);
        p.getMembers().forEach(ParticipantMember::TrueDelete);
        p.TrueDelete();
        log.info("Unregistered '{}' from '{}'", p.getName(), getName());
        promoteFromWaitlist();
    }

    /** Removes an entrant mid-play, awarding walkovers on their remaining matches. */
    public void withdraw(Participant p) {
        p.withdraw();
        forfeitRemaining(p);
        log.info("'{}' withdrew from '{}'", p.getName(), getName());
    }

    public void disqualify(Participant p, String reason) {
        p.setStatus(ParticipantStatus.DISQUALIFIED);
        forfeitRemaining(p);
        log.info("'{}' disqualified from '{}': {}", p.getName(), getName(), reason);
    }

    /** Every unplayed match involving the entrant is awarded to their opponent. */
    private void forfeitRemaining(Participant p) {
        for (Phase phase : getPhases()) {
            for (Match m : new ArrayList<>(phase.getMatches())) {
                if (m.getState().isDecided() || !m.hasParticipant(p.getID())) continue;
                Long opponent = m.getOpponentID(p.getID()).orElse(null);
                if (opponent == null) { m.setState(MatchState.CANCELLED); m.Upsert(); continue; }
                m.awardWalkover(opponent);
                ((Match) m).afterResult();
            }
        }
        p.Upsert();
        save();
    }

    // ---- seeding & generation -------------------------------------------------------------------

    /** Applies this tournament's seeding method, assigning 1..n seed numbers. */
    public List<Participant> seed() {
        List<Participant> field = getParticipants().stream()
                .filter(p -> p.getStatus().isPlayable())
                .filter(p -> !isCheckInRequired() || p.isCheckedIn())
                .toList();
        List<Participant> ordered = Seeder.applySeedNumbers(Seeder.order(field, getSeedingMethod(), getRandomSeed(), null));
        ordered.forEach(DatabaseObject::Upsert);
        log.debug("Seeded {} entrants for '{}' using {}", ordered.size(), getName(), getSeedingMethod());
        return ordered;
    }

    /** Appends a phase, inheriting this tournament's ruleset as its defaults. */
    public Phase addPhase(String name, PhaseType type) {
        Phase phase = new Phase(this, name, type, getPhases().size());
        getPhases().add(phase);
        return phase;
    }

    // ---- queries --------------------------------------------------------------------------------

    /** Matches that can be played right now across the whole tournament. */
    public List<Match> getPlayableMatches() {
        return getPhases().stream().filter(p -> p.getStatus().hasStarted()).flatMap(p -> p.getPlayableMatches().stream()).toList();
    }

    public List<Match> getMatchesFor(Participant p) {
        return getMatches().stream().filter(m -> m.hasParticipant(p.getID())).toList();
    }

    public Optional<Match> getNextMatchFor(Participant p) {
        return getMatchesFor(p).stream().filter(m -> !m.getState().isDecided() && m.getState() != MatchState.CANCELLED)
                .min(Comparator.comparingInt(Match::getRound).thenComparingInt(Match::getPosition));
    }

    /** Final placement of every entrant, best first. */
    public List<Participant> getFinalRanking() {
        if (getStatus() == TournamentStatus.COMPLETE) {
            List<Participant> ranked = new ArrayList<>(getParticipants().stream().filter(p -> p.getFinalRank() > 0).toList());
            ranked.sort(Comparator.comparingInt(Participant::getFinalRank));
            if (!ranked.isEmpty()) return ranked;
        }
        return computeFinalRanking();
    }

    private List<Participant> computeFinalRanking() { return new ArrayList<>(rankingWithTieKeys().keySet()); }

    /**
     * Ranks the last phase that has been played, then appends everyone eliminated earlier in
     * reverse phase order, so entrants knocked out in the group stage still get a placement.
     *
     * <p>Each entrant is mapped to the phase and table rank it was placed by: two entrants carrying the
     * same key were ranked equal by that phase and share a placing. A null key is an entrant no phase
     * ranked at all, which ties with nobody.
     */
    private LinkedHashMap<Participant, String> rankingWithTieKeys() {
        LinkedHashMap<Participant, String> out = new LinkedHashMap<>();
        List<Phase> phases = new ArrayList<>(getPhases());
        Collections.reverse(phases);
        for (Phase phase : phases) {
            if (!phase.getStatus().hasStarted()) continue;
            for (Standing s : PhaseEngines.of(phase.getType()).rank(phase).stream().sorted(Comparator.comparingInt(Standing::getRank)).toList()) {
                s.getParticipant().filter(p -> !out.containsKey(p)).ifPresent(p -> out.put(p, phase.getID() + ":" + s.getGroupIndex() + ":" + s.getRank()));
            }
        }
        getParticipants().stream().filter(p -> !out.containsKey(p)).forEach(p -> out.put(p, null));
        return out;
    }

    // ---- called back into from Phase / Match ------------------------------------------------------

    /** Marks every entrant not among {@code qualifiers} as eliminated in {@code phase}. */
    void markEliminated(Phase phase, List<Participant> qualifiers) {
        for (Participant p : getParticipants()) {
            if (!p.getStatus().isPlayable() || qualifiers.contains(p)) continue;
            p.eliminate(phase.getID(), phase.getCurrentRound());
            p.Upsert();
        }
    }
}
