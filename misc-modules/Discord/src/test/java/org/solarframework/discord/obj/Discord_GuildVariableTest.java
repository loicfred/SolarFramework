package org.solarframework.discord.obj;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class Discord_GuildVariableTest {

    private Discord_GuildVariable var(String value) {
        Discord_GuildVariable V = new Discord_GuildVariable();
        V.setValue(value);
        return V;
    }

    @Test
    void numbersAreParsedFromTheStoredString() {
        assertEquals(42, var("42").getAsInt());
        assertEquals(42L, var("42").getAsLong());
        assertEquals(4.5, var("4.5").getAsDouble());
        assertEquals(4.5f, var("4.5").getAsFloat());
        assertEquals((short) 7, var("7").getAsShort());
        assertEquals((byte) 7, var("7").getAsByte());
    }
    @Test
    void identifiersAndTemporalsAreParsedFromTheStoredString() {
        UUID id = UUID.randomUUID();
        assertEquals(id, var(id.toString()).getAsUUID());
        assertEquals(Instant.parse("2024-01-15T10:30:00Z"), var("2024-01-15T10:30:00Z").getAsInstant());
        assertEquals(LocalDateTime.parse("2024-01-15T10:30"), var("2024-01-15T10:30").getAsLocalDateTime());
        assertEquals(LocalDate.of(2024, 1, 15), var("2024-01-15").getAsLocalDate());
    }
    // getAsLocalDate splits on "T", so a stored date-time is read back as its date part
    @Test
    void aStoredDateTimeIsReadableAsALocalDate() {
        assertEquals(LocalDate.of(2024, 1, 15), var("2024-01-15T10:30").getAsLocalDate());
    }
    @Test
    void trueIsTheOnlyTruthyValue() {
        assertTrue(var("true").getAsBoolean());
        assertTrue(var("TRUE").getAsBoolean());
        assertFalse(var("yes").getAsBoolean());
    }

    @Test
    void anUnsetValueIsEmpty() {
        assertTrue(var(null).getValueOptional().isEmpty());
        assertNull(var(null).getAsInt());
        assertNull(var(null).getAsUUID());
        assertNull(var(null).getAsLocalDate());
    }
    // the odd one out: every other accessor returns null when unset, this one returns false
    @Test
    void anUnsetBooleanIsFalseRatherThanNull() {
        assertFalse(var(null).getAsBoolean());
    }
    // the accessors are unguarded on purpose: a malformed value is a bug in whatever wrote it
    @Test
    void aMalformedValueThrows() {
        assertThrows(NumberFormatException.class, () -> var("abc").getAsInt());
        assertThrows(IllegalArgumentException.class, () -> var("abc").getAsUUID());
    }

    @Test
    void toStringIsTheRawValue() {
        assertEquals("42", var("42").toString());
        assertNull(var(null).toString());
    }

    @Test
    void serverNameIdIsEqualByBothParts() {
        assertEquals(id("a", 1L), id("a", 1L));
        assertEquals(id("a", 1L).hashCode(), id("a", 1L).hashCode());
        assertNotEquals(id("a", 1L), id("b", 1L));
        assertNotEquals(id("a", 1L), id("a", 2L));
        assertEquals(id(null, null), id(null, null));
        assertNotEquals("a1", id("a", 1L));
    }

    private Discord_GuildVariable.ServerNameID id(String name, Long serverID) {
        Discord_GuildVariable.ServerNameID I = new Discord_GuildVariable.ServerNameID();
        I.setName(name);
        I.setServerID(serverID);
        return I;
    }
}
