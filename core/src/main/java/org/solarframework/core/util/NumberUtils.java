package org.solarframework.core.util;

import java.util.Random;

public class NumberUtils {
    public static int GenerateRandomNumber(int min, int max) {
        return new Random().nextInt((max - min) + 1) + min;
    }
    public static double GenerateRandomNumber(double min, double max) {
        return new Random().nextDouble() * (max - min) + min;
    }
    public static boolean Range(double input, double x, double y) {
        return input >= x && input <= y;
    }

    public static long takeOnlyDigits(String str) {
        // Converting the given string
        // into a character array
        if (str == null || str.isEmpty()) {return 0;}
        char[] charArray = str.toCharArray();
        StringBuilder result = new StringBuilder();

        // Traverse the character array
        for (char c : charArray) {

            // Check if the specified character is not digit
            // then add this character into result variable
            if (Character.isDigit(c)) {
                result.append(c);
            }
        }

        // Return result
        return Long.parseLong(result.toString());
    }
    public static Integer takeOnlyInts(String str) {
        if (str == null) return null;
        // Converting the given string
        // into a character array
        char[] charArray = str.toCharArray();
        StringBuilder result = new StringBuilder();

        // Traverse the character array
        for (char c : charArray) {

            // Check if the specified character is not digit
            // then add this character into result variable
            if (Character.isDigit(c)) {
                result.append(c);
            }
        }

        // Return result
        return Integer.valueOf(result.toString());
    }
    public static String takeOnlyNumberStr(String str) {
        if (str == null) return null;
        // Converting the given string
        // into a character array
        char[] charArray = str.toCharArray();
        StringBuilder result = new StringBuilder();

        // Traverse the character array
        for (char c : charArray) {

            // Check if the specified character is not digit
            // then add this character into result variable
            if (Character.isDigit(c)) {
                result.append(c);
            }
        }

        // Return result
        return result.toString();
    }


    public static long factorial(int n) {
        long f = 1;
        for (int i = 1; i <= n; i++) {
            f *= i;
        }
        return f;
    }
    public static long permutation(int n, int r) {
        return factorial(n) / factorial(n - r);
    }
    public static long combination(int n, int r) {
        return factorial(n) / (factorial(r) * factorial(n - r));
    }

    public static boolean isNumeric(String string) {
        try {
            Long.parseLong(string);
            return true;
        } catch (IllegalArgumentException e) {
            try {
                Double.parseDouble(string);
                return true;
            } catch (IllegalArgumentException ee) {
                return false;
            }
        }
    }
    public static boolean isNumeric(char string) {
        try {
            Double.parseDouble(String.valueOf(string));
            return true;
        } catch (IllegalArgumentException e) {
            try {
                Double.parseDouble(String.valueOf(string));
                return true;
            } catch (IllegalArgumentException ee) {
                return false;
            }
        }
    }

}
