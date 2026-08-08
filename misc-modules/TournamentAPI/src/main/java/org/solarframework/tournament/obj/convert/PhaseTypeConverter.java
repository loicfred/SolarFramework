package org.solarframework.tournament.obj.convert;

import jakarta.persistence.Converter;
import org.solarframework.tournament.api.PhaseType;

@Converter
public class PhaseTypeConverter extends EnumAttributeConverter<PhaseType> {
    public PhaseTypeConverter() { super(PhaseType.class, PhaseType.SINGLE_ELIMINATION); }
}
