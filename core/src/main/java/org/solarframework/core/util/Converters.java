package org.solarframework.core.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.solarframework.core.util.NumberUtils.isNumeric;
import static org.solarframework.core.Constants.ProgramZoneId;

public class Converters {

    public static long DateHourToEpochSecond(String d) {
        return LocalDateTime.parse(d, DateTimeFormatter.ofPattern("dd/MM/yyyy - HH:mm")).atZone(ProgramZoneId).toInstant().getEpochSecond();
    }
    public static String EpochSecondToPattern(long epoch, String pattern) {
        return Instant.ofEpochSecond(epoch).atZone(ProgramZoneId).format(DateTimeFormatter.ofPattern(pattern));
    }
    public static long PatternToEpochSecond(String time, String pattern) throws DateTimeParseException {
        try {
            if (isNumeric(time) && time.length() >= 8) {
                return Long.parseLong(time);
            }
            if (pattern.contains("s") || pattern.contains("m")) {
                return LocalDateTime.parse(time, DateTimeFormatter.ofPattern(pattern)).atZone(ProgramZoneId).toInstant().getEpochSecond();
            } else {
                return LocalDate.parse(time, DateTimeFormatter.ofPattern("dd/MM/yyyy")).atStartOfDay().atZone(ProgramZoneId).toInstant().getEpochSecond();
            }
        } catch (Exception ignored) {}
        return Instant.now().getEpochSecond();
    }
    public static long PatternToEpochMilli(String time, String pattern) throws DateTimeParseException {
        try {
            if (isNumeric(time) && time.length() >= 8) {
                return Long.parseLong(time);
            }
            if (pattern.contains("s") || pattern.contains("m")) {
                return LocalDateTime.parse(time, DateTimeFormatter.ofPattern(pattern)).atZone(ProgramZoneId).toInstant().getEpochSecond();
            } else {
                return LocalDate.parse(time, DateTimeFormatter.ofPattern("dd/MM/yyyy")).atStartOfDay().atZone(ProgramZoneId).toInstant().getEpochSecond();
            }
        } catch (Exception ignored) {}
        return Instant.now().toEpochMilli();
    }


    public static String CharFix(String s) {
        return s
                .replaceAll(
                        "[\\p{InEmoticons}"
                                + "\\p{InMiscellaneousSymbolsAndPictographs}"
                                + "\\p{InTransportAndMapSymbols}"
                                + "\\p{InSupplementalSymbolsAndPictographs}"
                                + "\\p{InMiscellaneousSymbols}"
                                + "\\p{InDingbats}]+",
                        ""
                )
                .replaceAll("Đ", "D")
                .replaceAll("ℝ", "R")
                .replaceAll("ℂ", "C")
                .replaceAll("Ƭ", "T")
                .replaceAll("Ł", "L")
                .replaceAll("§", "S")
                .replaceAll("ō", "o")
                .replaceAll("∅", "O")
                .replaceAll("Ø", "O")
                .replaceAll("\uD835\uDCDE", "O")
                .replaceAll("\uD835\uDCDB", "L")
                .replaceAll("\uD835\uDCDF", "P")
                .replaceAll("Ꮢ", "R")
                .replaceAll("\uD835\uDE74", "E")
                .replaceAll("\uD835\uDE7C", "M")
                .replaceAll("\uD835\uDD43", "L");
    }
    public static String RemoveNumbers(String s) {
        String output = "";
        for (char c : s.toCharArray()) {
            if (!Character.isDigit(c)) {
                output = output + c;
            }
        }
        return output;
    }
    public static List<String> parseStringToList(String str) {
        // Remove the square brackets
        str = str.substring(1, str.length() - 1);

        // Split the string by the comma and trim any whitespace
        String[] elements = str.split(",\\s*");

        // Convert the array to a list and return it
        return new ArrayList<>(Arrays.asList(elements));
    }


}
