package org.solarframework.tournament.impl.obj;

import jakarta.persistence.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.solarframework.tournament.obj.IParticipant;
import org.solarframework.tournament.obj.ITournament;

/** Concrete {@link IParticipant}. */
@Entity
public class Participant extends IParticipant {
    private static final Logger log = LoggerFactory.getLogger(Participant.class);

    protected Participant() {}

    public Participant(ITournament tournament, String name) { super(tournament, name); }

    public Participant(ITournament tournament, String name, int seed) { super(tournament, name, seed); }

    @Override
    public ParticipantMember addMember(String memberName) {
        ParticipantMember m = new ParticipantMember(this, memberName);
        m.setCaptain(getMembers().isEmpty());
        getMembers().add(m);
        m.Upsert(); // rosters are routinely completed after registration, so a late signing persists on its own
        return m;
    }

    @Override
    public void checkIn() {
        super.checkIn();
        Upsert();
        log.debug("'{}' checked in", getName());
    }
}
