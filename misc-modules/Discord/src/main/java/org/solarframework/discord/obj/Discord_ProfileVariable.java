package org.solarframework.discord.obj;

import jakarta.persistence.*;
import org.solarframework.db.spring.DatabaseObject;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name = "discord_profilevariable")
@IdClass(Discord_ProfileVariable.UserNameID.class)
public class Discord_ProfileVariable extends DatabaseObject<Discord_ProfileVariable> {
    @ManyToOne
    @JoinColumn(referencedColumnName = "ID", name = "UserID", nullable = false, insertable = false, updatable = false)
    private Discord_Profile DP;


    @Column(name = "UserID", nullable = false)
    @Id
    private Long userID;

    @Column(name = "Name", nullable = false)
    @Id
    private String name;

    @Column(name = "Value", length = 2048)
    private String value;

    protected Discord_ProfileVariable() {}
    protected Discord_ProfileVariable(Long userId, String name, Object value) {
        this.userID = userId;
        this.name = name;
        this.value = value != null ? value.toString() : null;
        Upsert();
    }

    public Long getUserID() {
        return userID;
    }
    public void setUserID(Long userID) {
        this.userID = userID;
    }

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    public Optional<String> getValueOptional() {
        return Optional.ofNullable(value);
    }
    public String getValue() {
        return value;
    }
    public void setValue(String value) {
        this.value = value;
    }

    public Optional<Integer> getAsIntOptional() {
        return getValueOptional().map(Integer::parseInt);
    }
    public Integer getAsInt() {
        return getAsIntOptional().orElseThrow();
    }

    public Optional<Long> getAsLongOptional() {
        return getValueOptional().map(Long::parseLong);
    }
    public Long getAsLong() {
        return getAsLongOptional().orElse(null);
    }

    public Optional<Double> getAsDoubleOptional() {
        return getValueOptional().map(Double::parseDouble);
    }
    public Double getAsDouble() {
        return getAsDoubleOptional().orElse(null);
    }

    public Optional<Float> getAsFloatOptional() {
        return getValueOptional().map(Float::parseFloat);
    }
    public Float getAsFloat() {
        return getAsFloatOptional().orElse(null);
    }

    public Optional<Short> getAsShortOptional() {
        return getValueOptional().map(Short::parseShort);
    }
    public Short getAsShort() {
        return getAsShortOptional().orElse(null);
    }

    public Optional<Byte> getAsByteOptional() {
        return getValueOptional().map(Byte::parseByte);
    }
    public Byte getAsByte() {
        return getAsByteOptional().orElse(null);
    }

    public Optional<Boolean> getAsBooleanOptional() {
        return getValueOptional().map(Boolean::parseBoolean);
    }
    public Boolean getAsBoolean() {
        return getAsBooleanOptional().orElse(false);
    }

    public Optional<UUID> getAsUUIDOptional() {
        return getValueOptional().map(UUID::fromString);
    }
    public UUID getAsUUID() {
        return getAsUUIDOptional().orElse(null);
    }

    public Optional<Instant> getAsInstantOptional() {
        return getValueOptional().map(Instant::parse);
    }
    public Instant getAsInstant() {
        return getAsInstantOptional().orElse(null);
    }

    public Optional<LocalDate> getAsLocalDateOptional() {
        return getValueOptional().map((String s) -> LocalDate.parse(s.split("T")[0]));
    }
    public LocalDate getAsLocalDate() {
        return getAsLocalDateOptional().orElse(null);
    }

    public Optional<LocalDateTime> getAsLocalDateTimeOptional() {
        return getValueOptional().map(LocalDateTime::parse);
    }
    public LocalDateTime getAsLocalDateTime() {
        return getAsLocalDateTimeOptional().orElse(null);
    }

    public static class UserNameID implements Serializable {
        private String name;
        private Long userID;

        public String getName() {
            return name;
        }
        public void setName(String name) {
            this.name = name;
        }

        public Long getUserID() {
            return userID;
        }
        public void setUserID(Long userID) {
            this.userID = userID;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof UserNameID that)) return false;
            return Objects.equals(this.name, that.name) && Objects.equals(this.userID, that.userID);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, userID);
        }
    }

    @Override
    public String toString() {
        return getValue();
    }
}
