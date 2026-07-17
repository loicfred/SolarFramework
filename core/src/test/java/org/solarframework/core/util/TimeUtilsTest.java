package org.solarframework.core.util;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.solarframework.core.Constants.ProgramZoneId;

class TimeUtilsTest {

    @Test
    void isDateValidAcceptsWellFormedDates() {
        // "uuuu" (year, not year-of-era) is required for STRICT resolution to succeed without an era field
        assertTrue(TimeUtils.isDateValid("15/01/2024", "dd/MM/uuuu"));
    }
    @Test
    void isDateValidRejectsImpossibleDates() {
        assertFalse(TimeUtils.isDateValid("32/01/2024", "dd/MM/uuuu"));
        assertFalse(TimeUtils.isDateValid("not-a-date", "dd/MM/uuuu"));
    }
    @Test
    void isDateValidFailsWithYearOfEraPatternUnderStrictResolution() {
        // documents a real gotcha in this method: "yyyy" (year-of-era) can never resolve
        // under STRICT without an era field, so isDateValid always returns false for it
        assertFalse(TimeUtils.isDateValid("15/01/2024", "dd/MM/yyyy"));
    }

    @Test
    void getTimeFormatsInstantInProgramZone() {
        Instant instant = LocalDate.of(2024, 1, 15).atStartOfDay(ProgramZoneId).toInstant();
        assertEquals("15/01/2024", TimeUtils.getTime("dd/MM/yyyy", instant));
    }

    @Test
    void getNowMatchesTodayInProgramZone() {
        String expected = LocalDate.now(ProgramZoneId).toString();
        assertEquals(expected, TimeUtils.getNow("yyyy-MM-dd"));
    }
    @Test
    void getYesterdayIsOneDayBeforeToday() {
        String expected = LocalDate.now(ProgramZoneId).minusDays(1).toString();
        assertEquals(expected, TimeUtils.getYesterday("yyyy-MM-dd"));
    }
    @Test
    void getTomorrowIsOneDayAfterToday() {
        String expected = LocalDate.now(ProgramZoneId).plusDays(1).toString();
        assertEquals(expected, TimeUtils.getTomorrow("yyyy-MM-dd"));
    }

    @Test
    void getTomorrowMidnightIsStartOfNextDay() {
        Instant expected = LocalDate.now(ProgramZoneId).plusDays(1).atStartOfDay(ProgramZoneId).toInstant();
        assertEquals(expected, TimeUtils.getTomorrowMidnight());
    }

    @Test
    void getNextMondayMidnightIsAlwaysAMonday() {
        Instant next = TimeUtils.getNextMondayMidnight();
        assertEquals(DayOfWeek.MONDAY, next.atZone(ProgramZoneId).getDayOfWeek());
        assertTrue(next.isAfter(Instant.now()));
    }

    @Test
    void getNextMonthStartMidnightIsFirstOfNextMonth() {
        Instant next = TimeUtils.getNextMonthStartMidnight();
        assertEquals(1, next.atZone(ProgramZoneId).getDayOfMonth());
        assertTrue(next.isAfter(Instant.now()));
    }

    @Test
    void isYesterdayOrBeforeDetectsOldInstants() {
        assertTrue(TimeUtils.isYesterdayOrBefore(Instant.now().minus(2, ChronoUnit.DAYS)));
        assertFalse(TimeUtils.isYesterdayOrBefore(Instant.now()));
    }

    @Test
    void isWithinDurationChecksBothBounds() {
        Instant target = Instant.now().minusSeconds(30);
        assertTrue(TimeUtils.isWithinDuration(Instant.now(), target, Duration.ofMinutes(1)));
        assertFalse(TimeUtils.isWithinDuration(Instant.now(), target, Duration.ofSeconds(10)));
    }

    @Test
    void isWithinLastXDaysDetectsRecentPastInstants() {
        assertTrue(TimeUtils.isWithinLastXDays(Instant.now().minus(2, ChronoUnit.DAYS), 5));
        assertFalse(TimeUtils.isWithinLastXDays(Instant.now().minus(10, ChronoUnit.DAYS), 5));
    }
    @Test
    void isWithinNextXDaysDetectsUpcomingInstants() {
        assertTrue(TimeUtils.isWithinNextXDays(Instant.now().plus(2, ChronoUnit.DAYS), 5));
        assertFalse(TimeUtils.isWithinNextXDays(Instant.now().plus(10, ChronoUnit.DAYS), 5));
    }

    @Test
    void getTimeBetweenNowReturnsPeriodBetweenPastDateAndToday() {
        Instant past = LocalDate.now(ProgramZoneId).minusDays(10).atStartOfDay(ProgramZoneId).toInstant();
        Period p = TimeUtils.getTimeBetweenNow(past);
        assertEquals(LocalDate.now(ProgramZoneId), past.atZone(ProgramZoneId).toLocalDate().plus(p));
    }
    @Test
    void getTimeBetweenNowReturnsPeriodBetweenTodayAndFutureDate() {
        Instant future = LocalDate.now(ProgramZoneId).plusDays(10).atStartOfDay(ProgramZoneId).toInstant();
        Period p = TimeUtils.getTimeBetweenNow(future);
        assertEquals(future.atZone(ProgramZoneId).toLocalDate(), LocalDate.now(ProgramZoneId).plus(p));
    }

    @Test
    void daysUntilDayOfNextYearForFutureDateThisYear() {
        Instant future = LocalDate.now(ProgramZoneId).plusDays(30).atStartOfDay(ProgramZoneId).toInstant();
        assertEquals(30, TimeUtils.DaysUntilDayOfNextYear(future));
    }
}
