package org.solarframework.discord.obj;

import jakarta.persistence.*;
import net.dv8tion.jda.api.entities.Guild;
import org.solarframework.db.spring.DatabaseObject;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.solarframework.core.util.ImageUtils.getDominantColor;

@Entity
@Table(name = "discord_guildvariable")
@IdClass(Discord_GuildVariable.ServerNameID.class)
public class Discord_GuildVariable extends DatabaseObject<Discord_GuildVariable> {
    @ManyToOne
    @JoinColumn(referencedColumnName = "ID", name = "ServerID", nullable = false, insertable = false, updatable = false)
    private Discord_GuildInfo DGI;


    @Id
    @Column(name = "ServerID", nullable = false)
    private Long serverID;

    @Id
    @Column(name = "Name", nullable = false)
    private String name;

    @Column(name = "Value", length = 4095)
    private String value;

    public Discord_GuildVariable() {}
    protected Discord_GuildVariable(Long serverId, String name, Object value) {
        this(serverId, name, value, true);
    }
    /** {@code persist == false} builds the row without writing it — what a read of an unset variable needs, so
     *  {@link Discord_GuildInfo#getVariable} stops INSERTing a NULL row for every name nobody has ever set. */
    protected Discord_GuildVariable(Long serverId, String name, Object value, boolean persist) {
        this.serverID = serverId;
        this.name = name;
        this.value = value != null ? value.toString() : null;
        if (persist) Upsert();
    }

    public Long getServerID() {
        return serverID;
    }
    public void setServerID(Long serverID) {
        this.serverID = serverID;
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
        return getAsIntOptional().orElse(null);
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

    @Embeddable
    public static class ServerNameID implements Serializable {
        private String name;
        private Long serverID;

        public String getName() {
            return name;
        }
        public void setName(String name) {
            this.name = name;
        }

        public Long getServerID() {
            return serverID;
        }
        public void setServerID(Long serverID) {
            this.serverID = serverID;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ServerNameID that)) return false;
            return Objects.equals(this.name, that.name) && Objects.equals(this.serverID, that.serverID);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, serverID);
        }
    }

    @Override
    public String toString() {
        return getValue();
    }
}
