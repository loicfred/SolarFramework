package org.solarframework.core.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StringUtilsTest {

    @Test
    void replaceLastReplacesOnlyTheLastOccurrence() {
        assertEquals("a-b-x", StringUtils.replaceLast("a-b-c", "c", "x"));
        assertEquals("foo.bar.baz", StringUtils.replaceLast("foo,bar.baz", ",", "."));
    }
    @Test
    void replaceLastReturnsInputWhenSearchStringNotFound() {
        assertEquals("abc", StringUtils.replaceLast("abc", "z", "x"));
    }
    @Test
    void replaceLastReturnsInputOnNullArguments() {
        assertEquals("abc", StringUtils.replaceLast("abc", null, "x"));
        assertNull(StringUtils.replaceLast(null, "a", "x"));
    }

    @Test
    void similarityIsHundredForIdenticalStrings() {
        assertEquals(100.0, StringUtils.similarity("hello", "hello"), 0.0001);
    }
    @Test
    void similarityIsCaseInsensitive() {
        assertEquals(100.0, StringUtils.similarity("HELLO", "hello"), 0.0001);
    }
    @Test
    void similarityIsHundredWhenBothNull() {
        assertEquals(100.0, StringUtils.similarity(null, null), 0.0001);
    }
    @Test
    void similarityIsZeroWhenOneIsNull() {
        assertEquals(0.0, StringUtils.similarity("a", null), 0.0001);
        assertEquals(0.0, StringUtils.similarity(null, "a"), 0.0001);
    }
    @Test
    void similarityUsesLevenshteinDistance() {
        // edit distance between "kitten" and "sitting" is 3, longer length is 7
        assertEquals((7 - 3) / 7.0 * 100, StringUtils.similarity("kitten", "sitting"), 0.0001);
    }

    @Test
    void stopStringTruncatesAndAddsEllipsis() {
        assertEquals("hell...", StringUtils.StopString("hello world", 7));
    }
    @Test
    void stopStringLeavesShortStringsUntouched() {
        assertEquals("hi", StringUtils.StopString("hi", 10));
    }
    @Test
    void stopStringReturnsEmptyStringForNull() {
        assertEquals("", StringUtils.StopString(null, 10));
    }

    @Test
    void cutStringTruncatesWithoutEllipsis() {
        assertEquals("hello", StringUtils.CutString("hello world", 5));
    }

    @Test
    void capitalizeUppercasesFirstLetter() {
        assertEquals("Hello", StringUtils.capitalize("hello"));
    }
    @Test
    void capitalizeHandlesNullAndEmpty() {
        assertNull(StringUtils.capitalize(null));
        assertEquals("", StringUtils.capitalize(""));
    }

    @Test
    void decapitalizeLowercasesFirstLetter() {
        assertEquals("hello", StringUtils.decapitalize("Hello"));
        assertEquals("jobVacancies", StringUtils.decapitalize("JobVacancies"));
    }
    @Test
    void decapitalizeHandlesNullAndEmpty() {
        assertNull(StringUtils.decapitalize(null));
        assertEquals("", StringUtils.decapitalize(""));
    }

    @Test
    void readableTurnsAFieldNameIntoASentence() {
        assertEquals("Phone number", StringUtils.readable("phoneNumber"));
        assertEquals("Date of birth", StringUtils.readable("DateOfBirth"));
        assertEquals("Old value", StringUtils.readable("old_value"));
        assertEquals("City", StringUtils.readable("city"));
    }
    @Test
    void readableHandlesNullAndEmpty() {
        assertNull(StringUtils.readable(null));
        assertEquals("  ", StringUtils.readable("  "));
    }

    @Test
    void pluralAppliesEnglishEndings() {
        assertEquals("invoices", StringUtils.plural("invoice"));
        assertEquals("cities", StringUtils.plural("city"));
        assertEquals("boxes", StringUtils.plural("box"));
        assertEquals("classes", StringUtils.plural("class"));
    }
    @Test
    void pluralHandlesNullAndEmpty() {
        assertNull(StringUtils.plural(null));
        assertEquals("", StringUtils.plural(""));
    }

    @Test
    void isBlankCoversNullEmptyAndWhitespace() {
        assertTrue(StringUtils.isBlank(null));
        assertTrue(StringUtils.isBlank("   "));
        assertFalse(StringUtils.isBlank("a"));
    }

    @Test
    void trimOrEmptyTreatsNullAsEmpty() {
        assertEquals("", StringUtils.trimOrEmpty(null));
        assertEquals("abc", StringUtils.trimOrEmpty("  abc  "));
    }
    @Test
    void trimLowerTrimsAndLowercases() {
        assertEquals("", StringUtils.trimLower(null));
        assertEquals("jobvacancy", StringUtils.trimLower("  JobVacancy "));
    }
    @Test
    void trimToNullTreatsAnEmptyBoxAsNothingGiven() {
        assertNull(StringUtils.trimToNull(null));
        assertNull(StringUtils.trimToNull(""));
        assertNull(StringUtils.trimToNull("   "));
        assertEquals("abc", StringUtils.trimToNull("  abc  "));
    }

    @Test
    void substringAfterLastTakesTheTail() {
        assertEquals("C", StringUtils.substringAfterLast("a.b.C", "."));
        assertEquals("C", StringUtils.substringAfterLast("C", "."));
    }
    @Test
    void substringAfterLastReturnsInputOnNullOrEmptySeparator() {
        assertNull(StringUtils.substringAfterLast(null, "."));
        assertEquals("a.b.C", StringUtils.substringAfterLast("a.b.C", ""));
    }

    @Test
    void plusMinusSignOnlyForPositiveNumbers() {
        assertEquals("+", StringUtils.PlusMinusSign(5.0));
        assertEquals("", StringUtils.PlusMinusSign(0.0));
        assertEquals("", StringUtils.PlusMinusSign(-5.0));
    }
    @Test
    void plusMinusSignWithNumPrependsSign() {
        assertEquals("+5.0", StringUtils.PlusMinusSignWithNum(5.0));
        assertEquals("-5.0", StringUtils.PlusMinusSignWithNum(-5.0));
    }

    @Test
    void takeMostRepeatedWordFromList() {
        assertEquals("b", StringUtils.takeMostRepeatedWord(List.of("a", "b", "b", "c")));
    }
    @Test
    void takeMostRepeatedWordFromListReturnsEmptyForNullOrEmpty() {
        assertEquals("", StringUtils.takeMostRepeatedWord((List<String>) null));
        assertEquals("", StringUtils.takeMostRepeatedWord(List.of()));
    }
    @Test
    void takeMostRepeatedWordFromSentence() {
        assertEquals("a", StringUtils.takeMostRepeatedWord("a a b"));
    }

    @Test
    void countWordCountsCharacterOccurrences() {
        assertEquals(3, StringUtils.countWord("hello world", "l"));
        assertEquals(0, StringUtils.countWord("hello", "z"));
        assertEquals(0, StringUtils.countWord(null, "a"));
    }

    @Test
    void takeOnlyNonDigitsStripsDigits() {
        assertEquals("abcdef", StringUtils.takeOnlyNonDigits("a1b2c3d4e5f6"));
    }
    @Test
    void takeOnlyLettersKeepsLettersAndSpaces() {
        assertEquals("abc def", StringUtils.takeOnlyLetters("abc123 def456"));
    }
    @Test
    void takeOnlyLettersExceptKeepsLettersAndException() {
        assertEquals("abc-def", StringUtils.takeOnlyLettersExcept("abc-123def", '-'));
    }

    @Test
    void isStringAlphabeticDetectsNonAlphabeticCharacters() {
        assertTrue(StringUtils.isStringAlphabetic("Hello World"));
        assertFalse(StringUtils.isStringAlphabetic("Hello123"));
    }
    @Test
    void isStringAlphabeticAndNumericAllowsDigits() {
        assertTrue(StringUtils.isStringAlphabeticAndNumeric("Hello123"));
        assertFalse(StringUtils.isStringAlphabeticAndNumeric("Hello!"));
    }

    record Fruit(String name) {
        @Override
        public String toString() {
            return name;
        }
    }

    @Test
    void getMostSimilarPicksClosestMatch() {
        List<Fruit> options = List.of(new Fruit("apple"), new Fruit("banana"), new Fruit("grape"));
        assertEquals("grape", StringUtils.getMostSimilar(options, "graps").name());
    }
    @Test
    void getMostSimilarReturnsNullForEmptyList() {
        assertNull(StringUtils.getMostSimilar(List.<Fruit>of(), "anything"));
    }

    @Test
    void slugLowersAndCollapsesPunctuation() {
        assertEquals("data-integration", StringUtils.slug("Data & Integration"));
        assertEquals("getting-started", StringUtils.slug("  Getting Started!  "));
        assertEquals("staff-account", StringUtils.slug("Staff_Account"));
    }
    @Test
    void slugIsEmptyForNothingUsable() {
        assertEquals("", StringUtils.slug(null));
        assertEquals("", StringUtils.slug("---"));
    }
}
