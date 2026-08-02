package org.solarframework.tournament.obj;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.DiscriminatorFormula;
import org.solarframework.db.spring.DatabaseObject;
import org.solarframework.tournament.api.*;
import org.solarframework.tournament.obj.convert.MatchDecisionModeConverter;
import org.solarframework.tournament.obj.convert.SeedingMethodConverter;
import org.solarframework.tournament.obj.convert.TournamentStatusConverter;
import org.solarframework.tournament.util.Ids;
import org.solarframework.db.api.Lazy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorFormula("'0'")
public abstract class ITournament extends DatabaseObject.ID_RECORD_OBJ<Long, ITournament> {

    @OneToMany(mappedBy = "tournament", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<IPhase> phases = new ArrayList<>();

    @OneToMany(mappedBy = "tournament", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<IParticipant> participants = new ArrayList<>();

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
    @Column(name = "ExternalRef", length = 200)
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
    @Column(name = "Status", nullable = false, length = 32)
    private TournamentStatus status = TournamentStatus.DRAFT;
    @Column(name = "CurrentPhaseIndex")
    private Integer currentPhaseIndex = 0;
    /** Off, a finished phase is still ranked and closed but the next one is not drawn and the run is not
     *  finished either: something outside decides when to move on. Nullable so rows written before the
     *  column existed - and every consumer that never touches it - keep advancing on their own. */
    @Column(name = "AutoAdvancePhases")
    private Boolean autoAdvancePhases;
    @Column(name = "IsPublic", nullable = false, columnDefinition = "TINYINT(1)")
    private boolean isPublic = true;
    @Column(name = "BracketVisible", nullable = false, columnDefinition = "TINYINT(1)")
    private boolean bracketVisible = true;

    // ---- format ---------------------------------------------------------------------------------
    /** 1 for 1v1, 2 for 2v2, 5 for 5v5... Drives how many members a participant may roster. */
    @Column(name = "TeamSize", nullable = false)
    private int teamSize = 1;
    @Column(name = "MinParticipants", nullable = false)
    private int minParticipants = 2;
    @Column(name = "MaxParticipants", nullable = false)
    private int maxParticipants = 0;
    /** With a cap set, entrants arriving into a full field queue up instead of being turned away. */
    @Column(name = "WaitlistEnabled", nullable = false, columnDefinition = "TINYINT(1)")
    private boolean waitlistEnabled = true;
    /** A team may register before its roster is filled, but with this set it may not take the floor short. */
    @Column(name = "RequireCompleteRosters", nullable = false, columnDefinition = "TINYINT(1)")
    private boolean requireCompleteRosters = true;
    /** Default series length for matches; 1 is a single match, 3 is a BO3. */
    @Column(name = "DefaultBestOf", nullable = false)
    private int defaultBestOf = 1;
    @Column(name = "ThirdPlaceMatch", nullable = false, columnDefinition = "TINYINT(1)")
    private boolean thirdPlaceMatch = true;
    @Column(name = "GrandFinalReset", nullable = false, columnDefinition = "TINYINT(1)")
    private boolean grandFinalReset = true;
    @Column(name = "DrawAllowed", nullable = false, columnDefinition = "TINYINT(1)")
    private boolean drawAllowed = false;
    @Convert(converter = SeedingMethodConverter.class)
    @Column(name = "SeedingMethod", nullable = false, length = 32)
    private SeedingMethod seedingMethod = SeedingMethod.MANUAL;
    @Column(name = "RandomSeed", nullable = false)
    private long randomSeed = 0L;
    /** How a match's winner is decided; each phase inherits this as its default and may override it. */
    @Convert(converter = MatchDecisionModeConverter.class)
    @Column(name = "MatchDecisionMode", nullable = false, length = 32)
    private MatchDecisionMode matchDecisionMode = MatchDecisionMode.GAMES_WON;

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
    private String tiebreakers = TournamentEnums.join(Tiebreaker.DEFAULT_GROUP);

    // ---- registration ---------------------------------------------------------------------------
    @Column(name = "CheckInRequired", nullable = false)
    private boolean checkInRequired = false;
    @Column(name = "CheckInMinutes", nullable = false)
    private int checkInMinutes = 30;
    @Column(name = "AllowLateRegistration", nullable = false)
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
    @Column(name = "WinnerID")
    private Long winnerID;
    @Column(name = "RunnerUpID")
    private Long runnerUpID;
    @Column(name = "ThirdPlaceID")
    private Long thirdPlaceID;

    protected ITournament() {}

    protected ITournament(String name) {
        this.ID = Ids.next();
        this.name = name;
        this.slug = slugify(name);
        this.randomSeed = this.ID;
    }

    public static String slugify(String s) {
        return s == null ? null : s.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    // ---- children -------------------------------------------------------------------------------

    /** Phases in play order. Loaded from the database on first access when one is registered. */
    public List<IPhase> getPhases() {
        if (phases == null) phases = new ArrayList<>(retrieveEntityServiceFor(IPhase.class).getAllWhere("TournamentID = ?", ID));
        phases.sort(Comparator.comparingInt(IPhase::getOrderIndex));
        return phases;
    }

    public List<IParticipant> getParticipants() {
        if (participants == null) participants = new ArrayList<>(retrieveEntityServiceFor(IParticipant.class).getAllWhere("TournamentID = ?", ID));
        return participants;
    }

    /** Entrants holding a slot in the field. The waiting list sits outside the cap and is deliberately not counted here. */
    public List<IParticipant> getEntrants() {
        return getParticipants().stream().filter(p -> !p.isWaitlisted()).toList();
    }

    /** The waiting list in join order - {@link #promoteFromWaitlist()} takes from the front. */
    public List<IParticipant> getWaitlist() {
        return getParticipants().stream().filter(IParticipant::isWaitlisted)
                .sorted(Comparator.comparing(IParticipant::getRegisteredAt, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(IParticipant::getID)).toList();
    }

    /** 1-based place in the queue, 0 for an entrant that is not waitlisted. */
    public int getWaitlistPosition(IParticipant p) {
        List<IParticipant> queue = getWaitlist();
        for (int i = 0; i < queue.size(); i++) if (Objects.equals(queue.get(i).getID(), p.getID())) return i + 1;
        return 0;
    }

    /** Entrants that may still be slotted into a phase, in seed order. */
    public List<IParticipant> getActiveParticipants() {
        return getParticipants().stream().filter(p -> p.getStatus().isPlayable()).sorted(Comparator.comparingInt(IParticipant::getSeed)).toList();
    }

    public Optional<IParticipant> getParticipant(Long participantID) {
        return participantID == null ? Optional.empty() : getParticipants().stream().filter(p -> Objects.equals(p.getID(), participantID)).findFirst();
    }

    public Optional<IParticipant> getParticipantByName(String name) {
        return getParticipants().stream().filter(p -> p.getName().equalsIgnoreCase(name)).findFirst();
    }

    /** Every match of every phase. */
    public List<IMatch> getMatches() {
        return getPhases().stream().flatMap(p -> p.getMatches().stream()).toList();
    }

    public Optional<IPhase> getPhase(int orderIndex) {
        return getPhases().stream().filter(p -> p.getOrderIndex() == orderIndex).findFirst();
    }

    public Optional<IPhase> getPhaseById(Long phaseID) {
        return phaseID == null ? Optional.empty() : getPhases().stream().filter(p -> Objects.equals(p.getID(), phaseID)).findFirst();
    }

    /** The phase currently being played, i.e. the first one that is not complete. */
    public Optional<IPhase> getCurrentPhase() {
        return getPhases().stream().filter(p -> !p.getStatus().isComplete()).findFirst();
    }

    public Optional<IPhase> getFinalPhase() {
        List<IPhase> p = getPhases();
        return p.isEmpty() ? Optional.empty() : Optional.of(p.getLast());
    }

    /** Drops a phase for good: its matches, games and table are hard-deleted, there is nothing to keep a record of. */
    public void removePhase(IPhase phase) {
        getPhases().remove(phase);
        for (IMatch m : phase.getMatches()) { m.getGames().forEach(IMatchGame::TrueDelete); m.TrueDelete(); }
        phase.getStandings().forEach(IStanding::TrueDelete);
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
    public List<IParticipant> getIncompleteTeams() {
        return getActiveParticipants().stream().filter(p -> !p.isRosterComplete()).toList();
    }

    /** Whether every entrant that would take the floor has a full roster - what {@code start()} enforces. */
    public boolean hasCompleteRosters() { return getIncompleteTeams().isEmpty(); }

    @JsonIgnore
    public int getFreeSlots() { return maxParticipants <= 0 ? Integer.MAX_VALUE : Math.max(0, maxParticipants - getEntrants().size()); }

    // ---- behavior, implemented by the concrete Tournament ----------------------------------------

    public abstract void save();
    public abstract void deleteCascade();
    public abstract void openRegistration();
    public abstract void closeRegistration();
    public abstract void start();
    public abstract void finish();
    public abstract void cancel(String reason);

    public abstract IParticipant register(String name);
    public abstract IParticipant register(String name, int seed);
    /** Registers a team entrant and rosters its members in one call. */
    public abstract IParticipant registerTeam(String teamName, List<String> memberNames);
    public abstract void unregister(IParticipant participant);
    /** Moves as many waiting entrants as there is room for into the field; called automatically when a slot frees up. */
    public abstract List<IParticipant> promoteFromWaitlist();
    /** Removes an entrant mid-play, awarding walkovers on their remaining matches. */
    public abstract void withdraw(IParticipant participant);
    public abstract void disqualify(IParticipant participant, String reason);

    /** Applies this tournament's seeding method, assigning 1..n seed numbers. */
    public abstract List<IParticipant> seed();
    /** Appends a phase, inheriting this tournament's ruleset as its defaults. */
    public abstract IPhase addPhase(String name, PhaseType type);

    /** Matches that can be played right now across the whole tournament. */
    public abstract List<IMatch> getPlayableMatches();
    public abstract List<IMatch> getMatchesFor(IParticipant participant);
    public abstract Optional<IMatch> getNextMatchFor(IParticipant participant);
    /** Final placement of every entrant, best first. */
    public abstract List<IParticipant> getFinalRanking();

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
    private List<IPhase> ungenerated() { return getPhases().stream().filter(p -> p.getMatches().isEmpty()).toList(); }
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
    public Long getWinnerID() { return winnerID; }
    public void setWinnerID(Long winnerID) { this.winnerID = winnerID; }
    public Long getRunnerUpID() { return runnerUpID; }
    public void setRunnerUpID(Long runnerUpID) { this.runnerUpID = runnerUpID; }
    public Long getThirdPlaceID() { return thirdPlaceID; }
    public void setThirdPlaceID(Long thirdPlaceID) { this.thirdPlaceID = thirdPlaceID; }

    public Optional<IParticipant> getWinner() { return getParticipant(winnerID); }
    public Optional<IParticipant> getRunnerUp() { return getParticipant(runnerUpID); }
    public Optional<IParticipant> getThirdPlace() { return getParticipant(thirdPlaceID); }

    /** Podium in finishing order, empty entries dropped. */
    public List<IParticipant> getPodium() {
        return java.util.stream.Stream.of(getWinner(), getRunnerUp(), getThirdPlace()).flatMap(Optional::stream).toList();
    }

    @Override
    public String toString() { return "Tournament[" + ID + " " + name + " " + status + "]"; }
}
