package org.solarframework.tournament.obj.convert;

import jakarta.persistence.Converter;
import org.solarframework.tournament.api.SeedingMethod;

@Converter
public class SeedingMethodConverter extends EnumAttributeConverter<SeedingMethod> {
    public SeedingMethodConverter() { super(SeedingMethod.class, SeedingMethod.MANUAL); }
}
