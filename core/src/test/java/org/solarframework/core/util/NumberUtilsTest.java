package org.solarframework.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NumberUtilsTest {

    @Test
    void generateRandomNumberIntStaysWithinBounds() {
        for (int i = 0; i < 100; i++) {
            int n = NumberUtils.GenerateRandomNumber(5, 10);
            assertTrue(n >= 5 && n <= 10);
        }
    }
    @Test
    void generateRandomNumberDoubleStaysWithinBounds() {
        for (int i = 0; i < 100; i++) {
            double n = NumberUtils.GenerateRandomNumber(1.0, 2.0);
            assertTrue(n >= 1.0 && n <= 2.0);
        }
    }

    @Test
    void rangeIsInclusiveOnBothEnds() {
        assertTrue(NumberUtils.Range(5, 5, 10));
        assertTrue(NumberUtils.Range(10, 5, 10));
        assertTrue(NumberUtils.Range(7, 5, 10));
        assertFalse(NumberUtils.Range(4.999, 5, 10));
        assertFalse(NumberUtils.Range(10.001, 5, 10));
    }

    @Test
    void takeOnlyDigitsExtractsDigitsAsLong() {
        assertEquals(123456L, NumberUtils.takeOnlyDigits("abc123def456"));
    }
    @Test
    void takeOnlyDigitsReturnsZeroForNullOrEmpty() {
        assertEquals(0L, NumberUtils.takeOnlyDigits(null));
        assertEquals(0L, NumberUtils.takeOnlyDigits(""));
    }

    @Test
    void takeOnlyIntsExtractsDigitsAsInteger() {
        assertEquals(123, NumberUtils.takeOnlyInts("a1b2c3"));
    }
    @Test
    void takeOnlyIntsReturnsNullForNull() {
        assertNull(NumberUtils.takeOnlyInts(null));
    }

    @Test
    void takeOnlyNumberStrKeepsOnlyDigitCharacters() {
        assertEquals("123", NumberUtils.takeOnlyNumberStr("a1b2c3"));
        assertEquals("", NumberUtils.takeOnlyNumberStr("abc"));
    }

    @Test
    void factorialComputesCorrectly() {
        assertEquals(1L, NumberUtils.factorial(0));
        assertEquals(120L, NumberUtils.factorial(5));
    }
    @Test
    void permutationComputesCorrectly() {
        assertEquals(20L, NumberUtils.permutation(5, 2));
    }
    @Test
    void combinationComputesCorrectly() {
        assertEquals(10L, NumberUtils.combination(5, 2));
    }

    @Test
    void isNumericStringDetectsIntegersAndDecimals() {
        assertTrue(NumberUtils.isNumeric("123"));
        assertTrue(NumberUtils.isNumeric("12.5"));
        assertTrue(NumberUtils.isNumeric("-4"));
        assertFalse(NumberUtils.isNumeric("abc"));
    }
    @Test
    void isNumericCharDetectsDigits() {
        assertTrue(NumberUtils.isNumeric('5'));
        assertFalse(NumberUtils.isNumeric('a'));
    }
}
