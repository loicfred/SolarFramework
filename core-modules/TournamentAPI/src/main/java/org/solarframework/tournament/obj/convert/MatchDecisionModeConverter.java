package org.solarframework.tournament.obj.convert;

import jakarta.persistence.Converter;
import org.solarframework.tournament.api.MatchDecisionMode;

@Converter
public class MatchDecisionModeConverter extends EnumAttributeConverter<MatchDecisionMode> {
    public MatchDecisionModeConverter() { super(MatchDecisionMode.class, MatchDecisionMode.GAMES_WON); }
}
