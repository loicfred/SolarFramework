package org.solarframework.tournament.obj;

import jakarta.persistence.*;
import jakarta.persistence.Entity;
import java.time.Instant;
import org.solarframework.db.api.Lazy;
import org.solarframework.db.spring.DatabaseObject;
import org.solarframework.tournament.util.Ids;

/** One player on a team entrant. Only used when the tournament's team size is above 1. */
@Entity
@Table(name = "tournament_participant_member")
public class ParticipantMember extends DatabaseObject.ID_RECORD_OBJ<Long, ParticipantMember> {

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(referencedColumnName = "ID", name = "ParticipantID", nullable = false, insertable = false, updatable = false)
    private Participant participant;

    @Column(name = "ParticipantID", nullable = false)
    private Long participantID;
    @Column(name = "Name", nullable = false, length = 160)
    private String name;
    @Column(name = "Role", length = 80)
    private String role;
    @Column(name = "Captain", nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 0")
    private boolean captain = false;
    @Column(name = "Substitute", nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 0")
    private boolean substitute = false;
    @Column(name = "Rating")
    private Double rating;
    @Column(name = "Country", length = 4)
    private String country;
    @Column(name = "AvatarUrl", length = 512)
    private String avatarUrl;
    @Column(name = "ExternalRef", length = 200)
    private String externalRef;
    @Column(name = "ContactEmail", length = 200)
    private String contactEmail;
    @Column(name = "JoinedAt")
    private Instant joinedAt;

    protected ParticipantMember() {}

    public ParticipantMember(Participant participant, String name) {
        this.ID = Ids.next();
        this.participant = participant;
        this.participantID = participant.getID();
        this.name = name;
        this.joinedAt = Instant.now();
    }

    public Participant getParticipant() {
        return participant == null ? participant = retrieveEntityServiceFor(Participant.class).getById(participantID).orElse(null) : participant;
    }
    public void setParticipant(Participant p) { this.participant = p; this.participantID = p == null ? null : p.getID(); }

    public Long getParticipantID() { return participantID; }
    public void setParticipantID(Long participantID) { this.participantID = participantID; this.participant = null; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public boolean isCaptain() { return captain; }
    public void setCaptain(boolean captain) { this.captain = captain; }
    public boolean isSubstitute() { return substitute; }
    public void setSubstitute(boolean substitute) { this.substitute = substitute; }
    public Double getRating() { return rating; }
    public void setRating(Double rating) { this.rating = rating; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getExternalRef() { return externalRef; }
    public void setExternalRef(String externalRef) { this.externalRef = externalRef; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
    public Instant getJoinedAt() { return joinedAt; }
    public void setJoinedAt(Instant joinedAt) { this.joinedAt = joinedAt; }

    public String toString() { return "Member[" + name + (captain ? " (C)" : "") + "]"; }



}
