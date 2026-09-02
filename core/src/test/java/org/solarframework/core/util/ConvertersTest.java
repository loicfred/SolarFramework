package org.solarframework.core.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.solarframework.core.Constants.ProgramZoneId;

class ConvertersTest {

    @Test
    void dateHourToEpochSecondMatchesJavaTime() {
        long expected = LocalDateTime.of(2024, 1, 15, 10, 30).atZone(ProgramZoneId).toInstant().getEpochSecond();
        assertEquals(expected, Converters.DateHourToEpochSecond("15/01/2024 - 10:30"));
    }

    @Test
    void epochSecondToPatternFormatsInProgramZone() {
        long epoch = LocalDateTime.of(2024, 1, 15, 10, 30).atZone(ProgramZoneId).toInstant().getEpochSecond();
        assertEquals("15/01/2024", Converters.EpochSecondToPattern(epoch, "dd/MM/yyyy"));
    }

    @Test
    void patternToEpochSecondTreatsLongNumericStringsAsAlreadyEpoch() {
        assertEquals(1700000000L, Converters.PatternToEpochSecond("1700000000", "dd/MM/yyyy"));
    }
    @Test
    void patternToEpochSecondParsesDateOnlyPattern() {
        long expected = LocalDate.of(2024, 1, 15).atStartOfDay(ProgramZoneId).toInstant().getEpochSecond();
        assertEquals(expected, Converters.PatternToEpochSecond("15/01/2024", "dd/MM/yyyy"));
    }
    @Test
    void patternToEpochSecondParsesDateTimePatternContainingSeconds() {
        long expected = LocalDateTime.of(2024, 1, 15, 10, 30, 0).atZone(ProgramZoneId).toInstant().getEpochSecond();
        assertEquals(expected, Converters.PatternToEpochSecond("15/01/2024 10:30:00", "dd/MM/yyyy HH:mm:ss"));
    }

    @Test
    void charFixReplacesKnownSpecialCharacters() {
        assertEquals("O is not R", Converters.CharFix("Ø is not ℝ"));
    }
    @Test
    void charFixStripsEmoji() {
        assertEquals("Hello ", Converters.CharFix("Hello 😀"));
    }

    @Test
    void removeNumbersStripsDigits() {
        assertEquals("abcdef", Converters.RemoveNumbers("abc123def456"));
    }

    @Test
    void parseStringToListSplitsBracketedCommaList() {
        assertEquals(List.of("a", "b", "c"), Converters.parseStringToList("[a, b, c]"));
    }

    @Test
    void canParseAcceptsEachNumericType() {
        assertTrue(Converters.canParse("Integer", "42"));
        assertTrue(Converters.canParse("Short", "-7"));
        assertTrue(Converters.canParse("Float", "1.5"));
        assertTrue(Converters.canParse("BigDecimal", "10.25"));
        assertTrue(Converters.canParse("BigInteger", "900000000000000000000"));
    }

    @Test
    void canParseRejectsValuesOutOfTheirType() {
        assertFalse(Converters.canParse("Integer", "1.5"));
        assertFalse(Converters.canParse("Short", "99999999"));
        assertFalse(Converters.canParse("BigDecimal", "NaN"));
        assertFalse(Converters.canParse("Long", "abc"));
        assertFalse(Converters.canParse("Integer", null));
    }

    @Test
    void canParseHandlesBooleanAndCharacter() {
        assertTrue(Converters.canParse("Boolean", "true"));
        assertFalse(Converters.canParse("Boolean", "TRUE"));
        assertTrue(Converters.canParse("Character", "x"));
        assertFalse(Converters.canParse("Character", "xy"));
    }

    @Test
    void canParseAcceptsAnythingForTypesWithoutATextualRule() {
        assertTrue(Converters.canParse("String", "whatever"));
        assertTrue(Converters.canParse("LocalDate", "not a date"));
    }

    private enum Colour { RED, BLUE }

    @Test
    void parseReadsEveryNumericAndTextualType() {
        assertEquals(42, Converters.parse(int.class, "42"));
        assertEquals(42L, Converters.parse(Long.class, " 42 "));
        assertEquals(new java.math.BigDecimal("1.50"), Converters.parse(java.math.BigDecimal.class, "1.50"));
        assertEquals('x', Converters.parse(char.class, "x"));
        assertEquals("  kept  ", Converters.parse(String.class, "  kept  "));
        assertEquals(Colour.BLUE, Converters.parse(Colour.class, "BLUE"));
        assertEquals(java.util.UUID.fromString("0-0-0-0-1"), Converters.parse(java.util.UUID.class, "0-0-0-0-1"));
        assertArrayEquals(new byte[]{1, 2}, (byte[]) Converters.parse(byte[].class, java.util.Base64.getEncoder().encodeToString(new byte[]{1, 2})));
    }

    @Test
    void parseReadsBooleansTheWayAFormSendsThem() {
        assertEquals(true, Converters.parse(Boolean.class, "TRUE"));
        assertEquals(true, Converters.parse(boolean.class, "1"));
        assertEquals(false, Converters.parse(Boolean.class, "off"));
    }

    @Test
    void parseReadsTemporalsWithOrWithoutSeconds() {
        assertEquals(LocalDate.of(2026, 8, 20), Converters.parse(LocalDate.class, "2026-08-20"));
        assertEquals(LocalDateTime.of(2026, 8, 20, 10, 15), Converters.parse(LocalDateTime.class, "2026-08-20T10:15"));
        assertEquals(LocalDateTime.of(2026, 8, 20, 10, 15, 30), Converters.parse(LocalDateTime.class, "2026-08-20 10:15:30"));
        assertEquals(LocalDateTime.of(2026, 8, 20, 0, 0), Converters.parse(LocalDateTime.class, "2026-08-20"));
        assertEquals(java.time.LocalTime.of(10, 15), Converters.parse(java.time.LocalTime.class, "10:15"));
    }

    @Test
    void parseReadsAnInstantInTheProgramZoneUnlessTheTextCarriesOne() {
        assertEquals(LocalDateTime.of(2026, 8, 20, 10, 15).atZone(ProgramZoneId).toInstant(), Converters.parse(java.time.Instant.class, "2026-08-20T10:15"));
        assertEquals(java.time.Instant.parse("2026-08-20T10:15:30Z"), Converters.parse(java.time.Instant.class, "2026-08-20T10:15:30Z"));
    }

    @Test
    void parseTurnsBlankIntoNullExceptWhereNullCannotBeHeld() {
        assertNull(Converters.parse(Long.class, ""));
        assertNull(Converters.parse(LocalDate.class, "   "));
        assertNull(Converters.parse(Colour.class, null));
        assertEquals(0L, Converters.parse(long.class, ""));
        assertEquals(false, Converters.parse(boolean.class, ""));
    }

    @Test
    void parseRefusesTextThatIsNotTheType() {
        assertThrows(NumberFormatException.class, () -> Converters.parse(Integer.class, "abc"));
        assertThrows(IllegalArgumentException.class, () -> Converters.parse(Colour.class, "GREEN"));
        assertThrows(IllegalArgumentException.class, () -> Converters.parse(Thread.class, "anything"));
    }
}
