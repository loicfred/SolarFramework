package org.solarframework.db.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class Row {
    protected transient Map<String, Object> columns;

    public Row(Map<String, Object> qp) {
        this.columns = qp;
    }

    public Map<String, Object> getColumns() {
        return columns;
    }

    public <T> T get(Class<T> clazz, String fieldName) {
        return clazz.cast(get(fieldName));
    }

    public Optional<Object> get(String fieldName) {
        return Optional.ofNullable(columns.get(fieldName));
    }

    /**
     * The value under this column in whatever case the driver answered with. MariaDB echoes a column as it was
     * written and SQLite as it was declared, so a caller that asked for "Total" can be handed "total" - which is the
     * row's own business to sort out rather than every caller's.
     */
    public Object valueOf(String column) {
        return columns.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(column)).map(Map.Entry::getValue).findFirst().orElse(null);
    }

    public Optional<String> getAsStringOptional(String fieldName) {
        Optional<Object> value = get(fieldName);
        if (value.isEmpty()) return Optional.empty();
        return Optional.of(value.orElseThrow().toString());
    }
    public String getAsString(String fieldName) {
        return getAsStringOptional(fieldName).orElse(null);
    }

    public Optional<Integer> getAsIntOptional(String fieldName) {
        return getAsStringOptional(fieldName).map(Integer::parseInt);
    }
    public Integer getAsInt(String fieldName) {
        return getAsIntOptional(fieldName).orElseThrow();
    }

    public Optional<Long> getAsLongOptional(String fieldName) {
        return getAsStringOptional(fieldName).map(Long::parseLong);
    }
    public Long getAsLong(String fieldName) {
        return getAsLongOptional(fieldName).orElse(null);
    }

    public Optional<Double> getAsDoubleOptional(String fieldName) {
        return getAsStringOptional(fieldName).map(Double::parseDouble);
    }
    public Double getAsDouble(String fieldName) {
        return getAsDoubleOptional(fieldName).orElse(null);
    }

    public Optional<Float> getAsFloatOptional(String fieldName) {
        return getAsStringOptional(fieldName).map(Float::parseFloat);
    }
    public Float getAsFloat(String fieldName) {
        return getAsFloatOptional(fieldName).orElse(null);
    }

    public Optional<Short> getAsShortOptional(String fieldName) {
        return getAsStringOptional(fieldName).map(Short::parseShort);
    }
    public Short getAsShort(String fieldName) {
        return getAsShortOptional(fieldName).orElse(null);
    }

    public Optional<Byte> getAsByteOptional(String fieldName) {
        return getAsStringOptional(fieldName).map(Byte::parseByte);
    }
    public Byte getAsByte(String fieldName) {
        return getAsByteOptional(fieldName).orElse(null);
    }

    public Optional<Boolean> getAsBooleanOptional(String fieldName) {
        return get(fieldName).map(v -> switch (v) {
            case Boolean b -> b;
            case Number n -> n.intValue() != 0;
            default -> { String s = v.toString(); yield s.equals("1") || s.equalsIgnoreCase("true")
                    || s.equalsIgnoreCase("y") || s.equalsIgnoreCase("yes")
                    || s.equalsIgnoreCase("p") || s.equalsIgnoreCase("position"); }
        });
    }
    public Boolean getAsBoolean(String fieldName) {
        return getAsBooleanOptional(fieldName).orElse(null);
    }

    public Optional<UUID> getAsUUIDOptional(String fieldName) {
        return getAsStringOptional(fieldName).map(UUID::fromString);
    }
    public UUID getAsUUID(String fieldName) {
        return getAsUUIDOptional(fieldName).orElse(null);
    }

    public Optional<Instant> getAsInstantOptional(String fieldName) {
        return getAsStringOptional(fieldName).map(Instant::parse);
    }
    public Instant getAsInstant(String fieldName) {
        return getAsInstantOptional(fieldName).orElse(null);
    }

    public Optional<LocalDate> getAsLocalDateOptional(String fieldName) {
        return getAsStringOptional(fieldName).map(LocalDate::parse);
    }
    public LocalDate getAsLocalDate(String fieldName) {
        return getAsLocalDateOptional(fieldName).orElse(null);
    }

    public Optional<LocalDateTime> getAsLocalDateTimeOptional(String fieldName) {
        return getAsStringOptional(fieldName).map(LocalDateTime::parse);
    }
    public LocalDateTime getAsLocalDateTime(String fieldName) {
        return getAsLocalDateTimeOptional(fieldName).orElse(null);
    }
}
