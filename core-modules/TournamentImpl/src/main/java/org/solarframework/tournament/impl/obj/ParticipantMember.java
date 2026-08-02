package org.solarframework.tournament.impl.obj;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import org.solarframework.tournament.obj.IParticipant;
import org.solarframework.tournament.obj.IParticipantMember;

/** Concrete {@link IParticipantMember}. */
@Entity
@DiscriminatorValue("0")
public class ParticipantMember extends IParticipantMember {

    protected ParticipantMember() {}

    public ParticipantMember(IParticipant participant, String name) { super(participant, name); }
}
