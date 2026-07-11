package org.solarframework.json;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Transient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.*;

import static org.junit.jupiter.api.Assertions.*;

class JSONItemTest {

    static class Dummy extends JSONItem<Dummy> {
        String name;
        LocalDate date;
        LocalTime time;
        LocalDateTime dateTime;
        OffsetDateTime offsetDateTime;
        ZonedDateTime zonedDateTime;
        Instant instant;
        @Transient
        String transientField = "should-not-serialize";
        @JsonIgnore
        String ignoredField = "should-not-serialize-either";

        Dummy() {}
        Dummy(String name) {
            this.name = name;
        }
    }

    @TempDir
    Path tempDir;

    @Test
    void writeThenReadRoundTripsAllFields() {
        Dummy original = new Dummy("Loic");
        original.date = LocalDate.of(2024, 1, 15);
        original.time = LocalTime.of(10, 30, 0);
        original.dateTime = LocalDateTime.of(2024, 1, 15, 10, 30, 0);
        original.offsetDateTime = OffsetDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneOffset.ofHours(1));
        original.zonedDateTime = ZonedDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneId.of("Europe/Paris"));
        original.instant = Instant.ofEpochSecond(1700000000);

        Path file = tempDir.resolve("dummy.json");
        assertSame(original, original.WriteJSON(file.toString()));

        Dummy read = JSONItem.ReadJSON(file.toString(), Dummy.class);

        assertNotNull(read);
        assertEquals(original.name, read.name);
        assertEquals(original.date, read.date);
        assertEquals(original.time, read.time);
        assertEquals(original.dateTime, read.dateTime);
        assertEquals(original.offsetDateTime, read.offsetDateTime);
        assertEquals(original.zonedDateTime, read.zonedDateTime);
        assertEquals(original.instant, read.instant);
    }

    @Test
    void transientAndJsonIgnoreFieldsAreExcludedFromOutput() {
        Dummy d = new Dummy("Loic");
        String json = JSONItem.SimpleGSON.toJson(d);
        assertFalse(json.contains("should-not-serialize"));
    }

    @Test
    void readJsonReturnsNullForMissingFile() {
        assertNull(JSONItem.ReadJSON(tempDir.resolve("does-not-exist.json").toString(), Dummy.class));
    }

    @Test
    void nullDatesSerializeToNullAndDeserializeToNull() {
        Dummy d = new Dummy("NoDate");
        String json = JSONItem.SimpleGSON.toJson(d);
        Dummy read = JSONItem.SimpleGSON.fromJson(json, Dummy.class);
        assertNull(read.date);
        assertNull(read.dateTime);
        assertNull(read.instant);
    }

    @Test
    void localDateTimeAdapterAcceptsBothIsoAndDbFormats() {
        LocalDateTime expected = LocalDateTime.of(2024, 1, 15, 10, 30, 0);
        Dummy isoRead = JSONItem.SimpleGSON.fromJson("{\"dateTime\":\"2024-01-15T10:30:00\"}", Dummy.class);
        Dummy dbRead = JSONItem.SimpleGSON.fromJson("{\"dateTime\":\"2024-01-15 10:30:00\"}", Dummy.class);
        assertEquals(expected, isoRead.dateTime);
        assertEquals(expected, dbRead.dateTime);
    }
}
