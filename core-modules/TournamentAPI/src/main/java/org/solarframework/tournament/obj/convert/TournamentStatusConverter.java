package org.solarframework.tournament.obj.convert;

import jakarta.persistence.Converter;
import org.solarframework.tournament.api.TournamentStatus;

@Converter
public class TournamentStatusConverter extends EnumAttributeConverter<TournamentStatus> {
    public TournamentStatusConverter() { super(TournamentStatus.class, TournamentStatus.DRAFT); }
}
