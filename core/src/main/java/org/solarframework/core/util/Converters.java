package org.solarframework.core.util;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.solarframework.core.util.NumberUtils.isNumeric;
import static org.solarframework.core.Constants.ProgramZoneId;

public class Converters {

    /**
     * Whether {@code value} reads as the named type - the check behind "is this text a usable value for this field".
     * Types with no textual rule of their own (String, enums, temporals) accept anything, so callers keep their own
     * format checks for those.
     */
    public static boolean canParse(String type, String value) {
        if (value == null) return false;
        String v = value.trim();
        try {
            return switch (type) {
                case "Byte", "byte" -> { Byte.parseByte(v); yield true; }
                case "Short", "short" -> { Short.parseShort(v); yield true; }
                case "Integer", "int" -> { Integer.parseInt(v); yield true; }
                case "Long", "long" -> { Long.parseLong(v); yield true; }
                case "Float", "float" -> { Float.parseFloat(v); yield true; }
                case "Double", "double" -> { Double.parseDouble(v); yield true; }
                case "BigDecimal" -> { new BigDecimal(v); yield true; }        // stricter than parseDouble: no NaN / Infinity
                case "BigInteger" -> { new BigInteger(v); yield true; }
                case "Boolean", "boolean" -> v.equals("true") || v.equals("false");
                case "Character", "char" -> v.length() == 1;
                default -> true;
            };
        } catch (NumberFormatException ex) { return false; }
    }

    /**
     * Text - a form field, a URL segment - to a value of the field's own type: the read side of {@link #canParse}.
     * Blank text is null for every type but String, so clearing a box clears a nullable column, and a primitive
     * gets its zero value instead of a null it cannot hold. Throws when the text does not read as the type.
     */
    public static Object parse(Class<?> type, String text) {
        if (type == String.class) return text;
        String v = text == null ? "" : text.trim();
        if (v.isEmpty()) return blankValueOf(type);
        if (type.isEnum()) return Enum.valueOf(type.asSubclass(Enum.class), v);
        return switch (type.getSimpleName()) {
            case "Byte", "byte" -> Byte.parseByte(v);
            case "Short", "short" -> Short.parseShort(v);
            case "Integer", "int" -> Integer.parseInt(v);
            case "Long", "long" -> Long.parseLong(v);
            case "Float", "float" -> Float.parseFloat(v);
            case "Double", "double" -> Double.parseDouble(v);
            case "BigDecimal" -> new BigDecimal(v);
            case "BigInteger" -> new BigInteger(v);
            case "Boolean", "boolean" -> v.equals("1") || v.equalsIgnoreCase("true") || v.equalsIgnoreCase("y") || v.equalsIgnoreCase("yes");
            case "Character", "char" -> v.charAt(0);
            case "UUID" -> UUID.fromString(v);
            case "LocalDate" -> LocalDate.parse(v);
            case "LocalTime" -> LocalTime.parse(v);
            case "LocalDateTime" -> parseLocalDateTime(v);
            case "Instant" -> parseInstant(v);
            case "byte[]" -> Base64.getDecoder().decode(v);
            default -> throw new IllegalArgumentException("No text rule for type " + type.getSimpleName() + ".");
        };
    }
    /** What an empty box means for a type that cannot be null: a primitive field would throw on a null write. */
    private static Object blankValueOf(Class<?> type) {
        if (!type.isPrimitive()) return null;
        return switch (type.getSimpleName()) {
            case "boolean" -> false;
            case "char" -> '\0';
            case "byte" -> (byte) 0;
            case "short" -> (short) 0;
            case "int" -> 0;
            case "long" -> 0L;
            case "float" -> 0f;
            default -> 0d;
        };
    }
    /** Accepts what a date-time form field sends (no seconds), what SQL sends (a space) and a plain date. */
    private static LocalDateTime parseLocalDateTime(String v) {
        String s = v.replace(' ', 'T');
        return s.length() == 10 ? LocalDate.parse(s).atStartOfDay() : LocalDateTime.parse(s);
    }
    /** An instant is stored as a moment, so a value carrying no zone is read in the program's own zone. */
    private static Instant parseInstant(String v) {
        if (v.endsWith("Z")) return Instant.parse(v);
        return parseLocalDateTime(v).atZone(ProgramZoneId).toInstant();
    }

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
