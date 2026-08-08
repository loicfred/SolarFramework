package org.solarframework.tournament.obj.convert;

import jakarta.persistence.Converter;
import org.solarframework.tournament.api.PhaseStatus;

@Converter
public class PhaseStatusConverter extends EnumAttributeConverter<PhaseStatus> {
    public PhaseStatusConverter() { super(PhaseStatus.class, PhaseStatus.PENDING); }
}
