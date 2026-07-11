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
}
