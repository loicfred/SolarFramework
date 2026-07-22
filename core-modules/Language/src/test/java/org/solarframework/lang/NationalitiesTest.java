package org.solarframework.lang;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NationalitiesTest {

    @Test
    void getFindsByEnumName() {
        assertEquals(Nationalities.French, Nationalities.get("French"));
    }
    @Test
    void getFindsByCountryNameCaseInsensitive() {
        assertEquals(Nationalities.French, Nationalities.get("france"));
    }
    @Test
    void getFindsByNativeName() {
        assertEquals(Nationalities.German, Nationalities.get("Deutsch"));
    }
    @Test
    void getReturnsNullForUnknownNationality() {
        assertNull(Nationalities.get("Atlantis Mars Saturn Neptune"));
    }

    @Test
    void getCodepointsDecodesFlagUnicodeSequence() {
        String expected = new String(Character.toChars(0x1f1fa)) + new String(Character.toChars(0x1f1f3));
        assertEquals(expected, Nationalities.International.getCodepoints());
    }

    @Test
    void accessorsReturnConstructorValues() {
        assertEquals("France", Nationalities.French.getCountry());
        assertEquals("Français", Nationalities.French.getNativeName());
        assertEquals("U+1f1eb U+1f1f7", Nationalities.French.getUnicode());
    }
}
