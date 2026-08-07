package org.solarframework.tournament.obj;

import jakarta.persistence.*;
import org.hibernate.annotations.DiscriminatorFormula;
import org.solarframework.db.spring.DatabaseObject;
import org.solarframework.tournament.api.ParticipantStatus;
import org.solarframework.tournament.obj.convert.ParticipantStatusConverter;
import org.solarframework.tournament.util.Ids;
import org.solarframework.db.api.Lazy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * An entrant. For a 1v1 tournament this is a single player; for 2v2/3v3/5v5 it is the team,
 * with its roster held in {@link IParticipantMember}. Matches always reference a Participant,
 * never an individual member, so the same match code serves every team size.
 */
@Entity
@Table(name = "tournament_participant")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorFormula("'0'")
public abstract class IParticipant extends DatabaseObject.ID_RECORD_OBJ<Long, IParticipant> {

    @ManyToOne(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    @JoinColumn(referencedColumnName = "ID", name = "TournamentID", nullable = false, insertable = false, updatable = false)
    private ITournament tournament;

    @OneToMany(mappedBy = "participant", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<IParticipantMember> members = new ArrayList<>();

    @Column(name = "TournamentID", nullable = false)
    private Long tournamentID;
    @Column(name = "Name", nullable = false, length = 160)
    private String name;
    @Column(name = "Tag", length = 32)
    private String tag;
    /** 1-based; 1 is the strongest entrant. 0 means unseeded. */
    @Column(name = "Seed", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 0")
    private int seed = 0;
    @Convert(converter = ParticipantStatusConverter.class)
    @Column(name = "Status", nullable = false, length = 32, columnDefinition = "VARCHAR(32) NOT NULL DEFAULT 'REGISTERED'")
    private ParticipantStatus status = ParticipantStatus.REGISTERED;
    @Column(name = "Rating")
    private Double rating;
    @Column(name = "Country", length = 4)
    private String country;
    @Column(name = "AvatarUrl", length = 512)
    private String avatarUrl;
    @Column(name = "ColorCode", length = 9)
    private String colorCode;
    @Column(name = "ExternalRef", length = 200)
    private String externalRef;
    @Column(name = "ContactEmail", length = 200)
    private String contactEmail;
    @Column(name = "Notes", length = 2000)
    private String notes;

    @Column(name = "CheckedIn", nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 0")
    private boolean checkedIn = false;
    @Column(name = "CheckedInAt")
    private Instant checkedInAt;
    @Column(name = "RegisteredAt")
    private Instant registeredAt;
    @Column(name = "WithdrawnAt")
    private Instant withdrawnAt;

    /** Final placement across the whole tournament, 1 = champion. 0 while still playing. */
    @Column(name = "FinalRank", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 0")
    private int finalRank = 0;
    @Column(name = "EliminatedInPhaseID")
    private Long eliminatedInPhaseID;
    @Column(name = "EliminatedInRound")
    private Integer eliminatedInRound;

    protected IParticipant() {}

    protected IParticipant(ITournament tournament, String name) {
        this.ID = Ids.next();
        this.tournament = tournament;
        this.tournamentID = tournament.getID();
        this.name = name;
        this.registeredAt = Instant.now();
    }

    protected IParticipant(ITournament tournament, String name, int seed) {
        this(tournament, name);
        this.seed = seed;
    }

    public ITournament getTournament() {
        return tournament == null ? tournament = retrieveEntityServiceFor(ITournament.class).getById(tournamentID).orElse(null) : tournament;
    }
    public void setTournament(ITournament t) { this.tournament = t; this.tournamentID = t == null ? null : t.getID(); }

    // ---- roster ---------------------------------------------------------------------------------

    public List<IParticipantMember> getMembers() {
        if (members == null) members = new ArrayList<>(retrieveEntityServiceFor(IParticipantMember.class).getAllWhere("ParticipantID = ?", ID));
        return members;
    }

    public boolean removeMember(String memberName) { return getMembers().removeIf(m -> m.getName().equalsIgnoreCase(memberName)); }

    public Optional<IParticipantMember> getCaptain() { return getMembers().stream().filter(IParticipantMember::isCaptain).findFirst(); }

    /** Rostered players that count toward the team size; substitutes are extra and never do. */
    public int getRosterSize() { return (int) getMembers().stream().filter(m -> !m.isSubstitute()).count(); }

    /** True when the roster matches the tournament's required team size. */
    public boolean isRosterComplete() {
        ITournament t = getTournament();
        int required = t == null ? 1 : t.getTeamSize();
        return required <= 1 || getRosterSize() >= required;
    }

    /** How many players this entrant still has to sign before it can take the floor. */
    public int getMissingMemberCount() {
        ITournament t = getTournament();
        return Math.max(0, (t == null ? 1 : t.getTeamSize()) - getRosterSize());
    }

    // ---- state ----------------------------------------------------------------------------------

    public ParticipantStatus getStatus() { return status; }
    public void setStatus(ParticipantStatus status) {
        this.status = status;
        if (status == ParticipantStatus.WITHDRAWN || status == ParticipantStatus.DISQUALIFIED) withdrawnAt = Instant.now();
    }

    public void checkIn() {
        this.checkedIn = true;
        this.checkedInAt = Instant.now();
        if (getStatus() == ParticipantStatus.REGISTERED) setStatus(ParticipantStatus.CHECKED_IN);
    }

    public void withdraw() { setStatus(ParticipantStatus.WITHDRAWN); }

    public void eliminate(Long phaseID, int round) {
        setStatus(ParticipantStatus.ELIMINATED);
        this.eliminatedInPhaseID = phaseID;
        this.eliminatedInRound = round;
    }

    public boolean isEliminated() { return getStatus() == ParticipantStatus.ELIMINATED; }
    public boolean isWaitlisted() { return getStatus() == ParticipantStatus.WAITLISTED; }
    public boolean isActive() { return getStatus().isPlayable(); }

    // ---- cumulative record across the whole tournament, derived from decided matches -------------

    private java.util.stream.Stream<IMatch> decidedMatches() {
        ITournament t = getTournament();
        return t == null ? java.util.stream.Stream.empty() : t.getMatches().stream().filter(m -> m.getState().isDecided() && m.hasParticipant(ID));
    }

    public int getMatchesWon() { return (int) decidedMatches().filter(m -> m.isWinner(ID)).count(); }
    public int getMatchesLost() { return (int) decidedMatches().filter(m -> m.isLoser(ID)).count(); }
    public int getMatchesDrawn() { return (int) decidedMatches().filter(IMatch::isTie).count(); }
    public int getGamesWon() { return decidedMatches().mapToInt(m -> m.getScoreFor(ID)).sum(); }
    public int getGamesLost() { return decidedMatches().mapToInt(m -> m.getScoreAgainst(ID)).sum(); }

    public int getMatchesPlayed() { return (int) decidedMatches().count(); }
    public double getWinRate() { return getMatchesPlayed() == 0 ? 0 : (double) getMatchesWon() / getMatchesPlayed(); }
    public int getGameDiff() { return getGamesWon() - getGamesLost(); }

    /** Effective seed for ordering - unseeded entrants sort after seeded ones. */
    public int getEffectiveSeed() { return seed > 0 ? seed : Integer.MAX_VALUE; }

    /** Display label: "TAG Name" when a tag is set. */
    public String getDisplayName() { return tag == null || tag.isBlank() ? name : tag + " " + name; }

    // ---- behavior, implemented by the concrete Participant ----------------------------------------

    public abstract IParticipantMember addMember(String memberName);

    // ---- accessors ------------------------------------------------------------------------------

    public Long getTournamentID() { return tournamentID; }
    public void setTournamentID(Long tournamentID) { this.tournamentID = tournamentID; this.tournament = null; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }
    public int getSeed() { return seed; }
    public void setSeed(int seed) { this.seed = Math.max(0, seed); }
    public Double getRating() { return rating; }
    public void setRating(Double rating) { this.rating = rating; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getColorCode() { return colorCode; }
    public void setColorCode(String colorCode) { this.colorCode = colorCode; }
    public String getExternalRef() { return externalRef; }
    public void setExternalRef(String externalRef) { this.externalRef = externalRef; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public boolean isCheckedIn() { return checkedIn; }
    public void setCheckedIn(boolean checkedIn) { this.checkedIn = checkedIn; }
    public Instant getCheckedInAt() { return checkedInAt; }
    public void setCheckedInAt(Instant checkedInAt) { this.checkedInAt = checkedInAt; }
    public Instant getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(Instant registeredAt) { this.registeredAt = registeredAt; }
    public Instant getWithdrawnAt() { return withdrawnAt; }
    public void setWithdrawnAt(Instant withdrawnAt) { this.withdrawnAt = withdrawnAt; }
    public int getFinalRank() { return finalRank; }
    public void setFinalRank(int finalRank) { this.finalRank = finalRank; }
    public Long getEliminatedInPhaseID() { return eliminatedInPhaseID; }
    public void setEliminatedInPhaseID(Long id) { this.eliminatedInPhaseID = id; }
    public Integer getEliminatedInRound() { return eliminatedInRound; }
    public void setEliminatedInRound(Integer r) { this.eliminatedInRound = r; }

    @Override
    public String toString() { return "Participant[" + ID + " " + name + " seed=" + seed + "]"; }
}
