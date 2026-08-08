package org.solarframework.tournament.obj.convert;

import jakarta.persistence.Converter;
import org.solarframework.tournament.api.MatchState;

@Converter
public class MatchStateConverter extends EnumAttributeConverter<MatchState> {
    public MatchStateConverter() { super(MatchState.class, MatchState.PENDING); }
}
