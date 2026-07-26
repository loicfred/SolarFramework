package org.solarframework.tournament.obj.convert;

import jakarta.persistence.Converter;
import org.solarframework.tournament.api.ParticipantStatus;

@Converter
public class ParticipantStatusConverter extends EnumAttributeConverter<ParticipantStatus> {
    public ParticipantStatusConverter() { super(ParticipantStatus.class, ParticipantStatus.REGISTERED); }
}
