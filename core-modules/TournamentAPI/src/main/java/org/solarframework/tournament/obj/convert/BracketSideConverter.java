package org.solarframework.tournament.obj.convert;

import jakarta.persistence.Converter;
import org.solarframework.tournament.api.BracketSide;

@Converter
public class BracketSideConverter extends EnumAttributeConverter<BracketSide> {
    public BracketSideConverter() { super(BracketSide.class, BracketSide.WINNERS); }
}
