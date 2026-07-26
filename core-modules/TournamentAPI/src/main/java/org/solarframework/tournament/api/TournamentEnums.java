package org.solarframework.tournament.api;

import java.util.Arrays;
import java.util.List;

/** Null and case tolerant parsing helpers - enum columns are persisted as their {@code name()} string. */
public final class TournamentEnums {
    private TournamentEnums() {}

    public static <E extends Enum<E>> E parse(Class<E> type, String name, E fallback) {
        if (name == null || name.isBlank()) return fallback;
        String n = name.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return Arrays.stream(type.getEnumConstants()).filter(e -> e.name().equals(n)).findFirst().orElse(fallback);
    }

    public static String name(Enum<?> value) { return value == null ? null : value.name(); }

    /** Serialises an ordered enum chain (e.g. tiebreakers) to a CSV column value. */
    public static String join(List<? extends Enum<?>> values) {
        return values == null || values.isEmpty() ? null : String.join(",", values.stream().map(Enum::name).toList());
    }

    public static <E extends Enum<E>> List<E> split(Class<E> type, String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(s -> parse(type, s, null)).filter(java.util.Objects::nonNull).toList();
    }
}
