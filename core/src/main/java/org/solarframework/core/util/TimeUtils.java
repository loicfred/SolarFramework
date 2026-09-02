package org.solarframework.core.util;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.IntStream;

import static org.solarframework.core.Constants.ProgramZoneId;

public class TimeUtils {

    // Month series - the x-axis of a trend chart
    /** Short chart-axis label for a month: "Aug 26". */
    public static String monthLabel(YearMonth month) {
        return month.format(DateTimeFormatter.ofPattern("MMM yy"));
    }
    /** The last {@code months} months ending with the current one, oldest first. */
    public static List<YearMonth> lastMonths(int months) {
        return IntStream.rangeClosed(1 - months, 0).mapToObj(i -> YearMonth.now().plusMonths(i)).toList();
    }


    // Format Check
    public static boolean isDateValid(String text, String pattern) {
        try {
            LocalDate.parse(text, DateTimeFormatter.ofPattern(pattern).withResolverStyle(ResolverStyle.STRICT));
            return true;
        } catch (Exception e) {
            return false;
        }
    }


    // Shortcuts
    public static String getTime(String pattern, Instant instant) {
        return DateTimeFormatter.ofPattern(pattern).format(instant.atZone(ProgramZoneId));
    }
    public static String getYesterday(String pattern) {
        return DateTimeFormatter.ofPattern(pattern).format(Instant.now().minus(1, ChronoUnit.DAYS).atZone(ProgramZoneId));
    }
    public static String getNow(String pattern) {
        return DateTimeFormatter.ofPattern(pattern).format(Instant.now().atZone(ProgramZoneId));
    }
    public static String getTomorrow(String pattern) {
        return DateTimeFormatter.ofPattern(pattern).format(Instant.now().plus(1, ChronoUnit.DAYS).atZone(ProgramZoneId));
    }


//    public static String getDayOfTheWeek(Instant instant) {
//        return DateTimeFormatter.ofPattern("EEEE").format(instant.atZone(ProgramZoneId));
//    }
//    public static String getDayOfTheWeekShort(Instant instant) {
//        return DateTimeFormatter.ofPattern("EEE").format(instant.atZone(ProgramZoneId));
//    }
//    public static String getYear(Instant instant) {
//        return DateTimeFormatter.ofPattern("uuuu").format(instant.atZone(ProgramZoneId));
//    }
//    public static String getMonth(Instant instant) {
//        return DateTimeFormatter.ofPattern("MMM").format(instant.atZone(ProgramZoneId));
//    }
//    public static String getHHmmss(Instant instant) {
//        return DateTimeFormatter.ofPattern("HH:mm:ss").format(instant.atZone(ProgramZoneId));
//    }
//    public static String getHHmmssnn(Instant instant) {
//        return DateTimeFormatter.ofPattern("HH:mm:ss:nn").format(instant.atZone(ProgramZoneId));
//    }

    public static Instant getTomorrowMidnight() {
        return LocalDate.now(ProgramZoneId).plusDays(1).atStartOfDay().atZone(ProgramZoneId).toInstant();
    }
    public static Instant getNextMondayMidnight() {
        LocalDate today = LocalDate.now(ProgramZoneId);
        int daysToAdd = DayOfWeek.MONDAY.getValue() - today.getDayOfWeek().getValue();
        if (daysToAdd <= 0) {
            daysToAdd += 7;
        }
        return today.plusDays(daysToAdd).atStartOfDay().atZone(ProgramZoneId).toInstant();
    }
    public static Instant getNextMonthStartMidnight() {
        return LocalDate.now(ProgramZoneId).withDayOfMonth(1).plusMonths(1).atStartOfDay().atZone(ProgramZoneId).toInstant();
    }
    public static boolean isYesterdayOrBefore(Instant instant) {
        return !instant.atZone(ProgramZoneId).isAfter(ZonedDateTime.now(ProgramZoneId).minusDays(1));
    }


    public static boolean isWithinDuration(Instant currentInstant, Instant targetInstant, Duration duration) {
        return currentInstant.isAfter(targetInstant) && currentInstant.isBefore(targetInstant.plus(duration));
    }
    public static boolean isWithinLastXDays(Instant targetTime, int days) {
        return targetTime.isBefore(Instant.now()) && Duration.between(targetTime, Instant.now()).toDays() <= days;
    }
    public static boolean isWithinNextXDays(Instant targetTime, int days) {
        return targetTime.isAfter(Instant.now()) && Duration.between(Instant.now(), targetTime).toDays() <= days;
    }
    public static Period getTimeBetweenNow(Instant instant) {
        LocalDate time = instant.atZone(ProgramZoneId).toLocalDate();
        LocalDate currentDate = LocalDate.now(ProgramZoneId);
        if (time.isAfter(currentDate)) {
            return Period.between(currentDate, time);
        } else {
            return Period.between(time, currentDate);
        }
    }


    public static long DaysUntilDayOfNextYear(Instant day) {
        // Get the current date
        LocalDate currentDate = LocalDate.now(ProgramZoneId);

        // Get the next birthday
        LocalDate nextBirthday = day.atZone(ProgramZoneId).toLocalDate().withYear(currentDate.getYear());

        // If the birthday has already occurred this year, move to the next year
        if (nextBirthday.isBefore(currentDate) || nextBirthday.isEqual(currentDate)) {
            nextBirthday = nextBirthday.plusYears(1);
        }

        // Calculate the number of days until the next birthday
        return ChronoUnit.DAYS.between(currentDate, nextBirthday);
    }
}
