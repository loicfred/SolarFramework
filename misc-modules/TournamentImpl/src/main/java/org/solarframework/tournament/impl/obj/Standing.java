package org.solarframework.tournament.impl.obj;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import org.solarframework.tournament.obj.IPhase;
import org.solarframework.tournament.obj.IStanding;

/** Concrete {@link IStanding}. */
@Entity
@DiscriminatorValue("0")
public class Standing extends IStanding {

    protected Standing() {}

    public Standing(IPhase phase, Long participantID, int groupIndex, int seed) { super(phase, participantID, groupIndex, seed); }
}
