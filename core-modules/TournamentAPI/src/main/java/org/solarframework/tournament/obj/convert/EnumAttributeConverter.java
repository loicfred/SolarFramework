package org.solarframework.tournament.obj.convert;

import jakarta.persistence.AttributeConverter;
import org.solarframework.tournament.api.TournamentEnums;

/** Persists any tournament enum as its {@code name()}; null/garbage reads fall back instead of throwing. */
public abstract class EnumAttributeConverter<E extends Enum<E>> implements AttributeConverter<E, String> {
    private final Class<E> type;
    private final E fallback;

    protected EnumAttributeConverter(Class<E> type, E fallback) {
        this.type = type;
        this.fallback = fallback;
    }

    @Override
    public String convertToDatabaseColumn(E value) { return TournamentEnums.name(value); }
    @Override
    public E convertToEntityAttribute(String value) { return TournamentEnums.parse(type, value, fallback); }
}
