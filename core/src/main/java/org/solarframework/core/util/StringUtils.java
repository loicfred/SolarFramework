package org.solarframework.core.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class StringUtils {

    public static String replaceLast(String text, String searchString, String replacement) {
        if (text == null || searchString == null || replacement == null) {
            return text;
        }
        int lastIndex = text.lastIndexOf(searchString);
        if (lastIndex == -1) {
            return text;
        }
        String beforeLastOccurrence = text.substring(0, lastIndex);
        String afterLastOccurrence = text.substring(lastIndex + searchString.length());
        return beforeLastOccurrence + replacement + afterLastOccurrence;
    }
    public static void pack(String sourceDirPath, String zipFilePath) throws IOException {
        Path p = Files.createFile(Paths.get(zipFilePath));
        try (ZipOutputStream zs = new ZipOutputStream(Files.newOutputStream(p))) {
            Path pp = Paths.get(sourceDirPath);
            Files.walk(pp)
                    .filter(path -> !Files.isDirectory(path))
                    .forEach(path -> {
                        ZipEntry zipEntry = new ZipEntry(pp.relativize(path).toString());
                        try {
                            zs.putNextEntry(zipEntry);
                            Files.copy(path, zs);
                            zs.closeEntry();
                        } catch (IOException e) {
                            System.err.println(e);
                        }
                    });
        }
    }
    public static File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName());

        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }

        return destFile;
    }



    public static double similarity(String s1, String s2, boolean ignorecase) {
        if (s1 == null && s2 == null) {
            return 100;
        } else if (s1 == null || s2 == null) {
            return 0;
        }
        return similarityCheck(ignorecase ? s1.toLowerCase() : s1, ignorecase ? s2.toLowerCase() : s2);
    }
    public static double similarity(String s1, String s2) {
        if (s1 == null && s2 == null) {
            return 100;
        } else if (s1 == null || s2 == null) {
            return 0;
        }
        return similarityCheck(s1, s2);
    }
    private static double similarityCheck(String s1, String s2) {
        String longer = s1, shorter = s2;
        if (s1.length() < s2.length()) { // longer should always have greater length
            longer = s2;
            shorter = s1;
        }
        int longerLength = longer.length();
        if (longerLength == 0) {
            return 1.0; /* both strings are zero length */
        }
        return ((longerLength - editDistance(longer, shorter)) / (double) longerLength) * 100;
    }
    private static int editDistance(String s1, String s2) {
        s1 = s1.toLowerCase();
        s2 = s2.toLowerCase();

        int[] costs = new int[s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            int lastValue = i;
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0)
                    costs[j] = j;
                else {
                    if (j > 0) {
                        int newValue = costs[j - 1];
                        if (s1.charAt(i - 1) != s2.charAt(j - 1))
                            newValue = Math.min(Math.min(newValue, lastValue),
                                    costs[j]) + 1;
                        costs[j - 1] = lastValue;
                        lastValue = newValue;
                    }
                }
            }
            if (i > 0)
                costs[s2.length()] = lastValue;
        }
        return costs[s2.length()];
    }




    public static String StopString(String input, int maxCharactersPerLine) {
        return input == null ? "" : input.length() > maxCharactersPerLine ? input.substring(0, maxCharactersPerLine-3) + "..." : input;
    }
    public static String CutString(String input, int maxCharactersPerLine) {
        return input == null ? "" : input.length() > maxCharactersPerLine ? input.substring(0, maxCharactersPerLine) : input;
    }

    public static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
    public static String decapitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return Character.toLowerCase(str.charAt(0)) + str.substring(1);
    }

    /** A programmer's name as a person reads it: phoneNumber -> "Phone number", DateOfBirth -> "Date of birth". */
    public static String readable(String name) {
        if (name == null || name.isBlank()) return name;
        return capitalize(name.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replace('_', ' ').trim().toLowerCase());
    }

    /** English plural of a single word: city -> cities, box -> boxes, invoice -> invoices. */
    public static String plural(String str) {
        if (str == null || str.isEmpty()) return str;
        if (str.endsWith("y")) return str.substring(0, str.length() - 1) + "ies";
        return str + (str.endsWith("s") || str.endsWith("x") ? "es" : "s");
    }

    /** A count with its word, pluralized as needed: (1, "message") -> "1 message", (2, "message") -> "2 messages". */
    public static String plural(int count, String str) {
        return count + " " + (count == 1 ? str : plural(str));
    }

    /** Null, empty or whitespace only. */
    public static boolean isBlank(String str) {
        return str == null || str.isBlank();
    }

    /** Trim that treats null as empty, so callers can compare without null checks. */
    public static String trimOrEmpty(String str) {
        return str == null ? "" : str.trim();
    }
    /** Comparison key for user-typed names: trimmed and lower-cased. */
    public static String trimLower(String str) {
        return trimOrEmpty(str).toLowerCase();
    }
    /** Trim for a column that means "not given" by holding null: an empty box on a form must not be stored as an empty string, or every reader has to test for both. */
    public static String trimToNull(String str) {
        return isBlank(str) ? null : str.trim();
    }

    /** Lower-case, punctuation collapsed to single dashes and no dash at either end - what an HTML anchor or a URL segment needs from a title. */
    public static String slug(String text) {
        return text == null ? "" : text.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    /** What follows the last separator, or the whole string when there is none - e.g. the simple name of a.b.C. */
    public static String substringAfterLast(String text, String separator) {
        if (text == null || separator == null || separator.isEmpty()) return text;
        int lastIndex = text.lastIndexOf(separator);
        return lastIndex == -1 ? text : text.substring(lastIndex + separator.length());
    }

    public static String PlusMinusSign(double num) {
        if (num > 0) return "+";
        return "";
    }
    public static String PlusMinusSign(String num) {
        if (Double.parseDouble(num) > 0) return "+";
        return "";
    }

    public static String PlusMinusSignWithNum(double num) {
        if (num > 0) return "+" + num;
        return "" + num;
    }
    public static String PlusMinusSignWithNum(String num) {
        if (Double.parseDouble(num) > 0) return "+" + num;
        return num;
    }


    public static String takeMostRepeatedWord(List<String> strings) {
        if (strings == null || strings.isEmpty()) {return "";}
        Map<String, Integer> frequencyMap = new HashMap<>();
        for (String str : strings) {
            frequencyMap.put(str, frequencyMap.getOrDefault(str, 0) + 1);
        }
        String mostRepeatedString = null;
        int maxCount = 0;
        for (Map.Entry<String, Integer> entry : frequencyMap.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                mostRepeatedString = entry.getKey();
            }
        }
        return mostRepeatedString;
    }
    public static String takeMostRepeatedWord(String myStr) {
        String[] splited = myStr.split(" ");
        Arrays.sort(splited);
        int max = 0;
        int count = 1;
        String word = splited[0];
        String curr = splited[0];
        for (int i = 1; i < splited.length; i++) {
            if (splited[i].equals(curr)) {
                count++;
            } else {
                count = 1;
                curr = splited[i];
            }
            if (max < count) {
                max = count;
                word = splited[i];
            }
        }
        return word;
    }
    public static int countWord(String input, String wordToCount) {
        if (input == null || wordToCount == null || input.isEmpty() || wordToCount.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (char c : input.toCharArray()) {
            String chara = c + "";
            if (wordToCount.equals(chara)) {
                count++;
            }
        }
        return count;
    }

    public static String stringLineChangerByLength(String input, int maxCharactersPerLine) {
        StringBuilder result = new StringBuilder();
        StringBuilder currentLine = new StringBuilder();
        int charCount = 0;
        for (char c : input.toCharArray()) {
            if (charCount >= maxCharactersPerLine && Character.isWhitespace(c)) {
                result.append(currentLine.toString().trim()).append('\n');
                currentLine.setLength(0);
                charCount = 0;
            } else {
                currentLine.append(c);
                charCount++;
            }
        }
        return result.append(currentLine.toString().trim()).toString();
    }

    public static String takeOnlyNonDigits(String str) {
        // Converting the given string
        // into a character array
        char[] charArray = str.toCharArray();
        String result = "";

        // Traverse the character array
        for (int i = 0; i < charArray.length; i++) {

            // Check if the specified character is not digit
            // then add this character into result variable
            if (!Character.isDigit(charArray[i])) {
                result = result + charArray[i];
            }
        }

        // Return result
        return result;
    }
    public static String takeOnlyLetters(String str) {
        // Converting the given string
        // into a character array
        char[] charArray = str.toCharArray();
        String result = "";

        // Traverse the character array
        for (int i = 0; i < charArray.length; i++) {

            // Check if the specified character is not digit
            // then add this character into result variable
            if (Character.isAlphabetic(charArray[i]) || Character.isWhitespace(charArray[i])) {
                result = result + charArray[i];
            }
        }

        // Return result
        return result;
    }
    public static String takeOnlyLettersExcept(String str, char exception) {
        // Converting the given string
        // into a character array
        char[] charArray = str.toCharArray();
        String result = "";

        // Traverse the character array
        for (int i = 0; i < charArray.length; i++) {

            // Check if the specified character is not digit
            // then add this character into result variable
            if (Character.isAlphabetic(charArray[i]) || (charArray[i] == exception)) {
                result = result + charArray[i];
            }
        }

        // Return result
        return result;
    }


    public static boolean isStringAlphabetic(String str) {
        // Converting the given string
        // into a character array
        char[] charArray = str.toCharArray();
        boolean isAlphabetic = true;
        // Traverse the character array
        for (int i = 0; i < charArray.length; i++) {

            // Check if the specified character is not digit
            // then add this character into result variable
            if (!Character.isAlphabetic(charArray[i]) && !Character.isWhitespace(charArray[i])) {
                isAlphabetic = false;
            }
        }

        // Return result
        return isAlphabetic;
    }
    public static boolean isStringAlphabeticAndNumeric(String str) {
        // Converting the given string
        // into a character array
        char[] charArray = str.toCharArray();
        boolean isAlphabetic = true;
        // Traverse the character array
        for (int i = 0; i < charArray.length; i++) {

            // Check if the specified character is not digit
            // then add this character into result variable
            if (!Character.isAlphabetic(charArray[i]) && !Character.isWhitespace(charArray[i]) && !Character.isDigit(charArray[i])) {
                isAlphabetic = false;
                System.out.println(charArray[i]);
            } else {
            }
        }

        // Return result
        return isAlphabetic;
    }


    public static <T> T getMostSimilar(List<T> objects, String string) {
        if (objects == null || objects.isEmpty()) return null;
        List<T> M = new ArrayList<>(objects);
        M.sort(Comparator.comparingDouble((T obj) -> similarity(obj.toString(), string, true)).reversed());
        return M.getFirst();
    }
    public static <T> T getMostSimilar(T[] objects, String item) {
        if (objects == null || objects.length == 0) return null;
        List<T> M = new ArrayList<>(Arrays.asList(objects));
        M.sort(Comparator.comparingDouble((T obj) -> similarity(obj.toString(), item, true)).reversed());
        return M.getFirst();
    }
    public static <T> T getMostSimilar(List<T> objects, T item) {
        return getMostSimilar(objects, item.toString());
    }
    public static <T> T getMostSimilar(T[] objects, T item) {
        return getMostSimilar(objects, item.toString());
    }

}
