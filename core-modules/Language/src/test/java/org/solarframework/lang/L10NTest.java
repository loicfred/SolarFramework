package org.solarframework.lang;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class L10NTest {

    @AfterEach
    void resetBundle() {
        L10N.RB = null;
    }

    @Test
    void tlReturnsOriginalKeyWhenNoBundleIsLoaded() {
        assertEquals("Some Key", L10N.TL("Some Key"));
    }

    @Test
    void tlReturnsOriginalKeyForMissingResource() {
        L10N.RB = L10N.getLanguageBundle(Nationalities.International);
        assertEquals("no-such-key", L10N.TL("no-such-key"));
    }

    @Test
    void tlLooksUpKeyCaseInsensitivelyAndReplacesSpacesWithDashes() {
        L10N.RB = L10N.getLanguageBundle(Nationalities.International);
        assertEquals("Hello there", L10N.TL("Greeting"));
    }

    @Test
    void tlSubstitutesVariables() {
        L10N.RB = L10N.getLanguageBundle(Nationalities.International);
        assertEquals("Goodbye World", L10N.TL("farewell", "World"));
    }

    @Test
    void tlConvertsBrTagsToNewlines() {
        L10N.RB = L10N.getLanguageBundle(Nationalities.International);
        assertEquals("Line1\nLine2", L10N.TL("line-break"));
    }

    @Test
    void tlSanitizesRegexSpecialCharactersInVariables() {
        L10N.RB = L10N.getLanguageBundle(Nationalities.International);
        assertEquals("Value: valueSwithstuff", L10N.TL("regex-chars", "value$with{stuff}^"));
    }

    @Test
    void getLanguageBundleSelectsFrenchTranslationsForFrenchSpeakingNationalities() {
        L10N.RB = L10N.getLanguageBundle(Nationalities.French);
        assertEquals("Bonjour", L10N.TL("greeting"));
        assertEquals("Au revoir Alice", L10N.TL("farewell", "Alice"));
    }
    @Test
    void frenchBundleFallsBackToRootForKeysItDoesNotOverride() {
        L10N.RB = L10N.getLanguageBundle(Nationalities.French);
        assertEquals("Line1\nLine2", L10N.TL("line-break"));
    }

    @Test
    void getLanguageBundleFallsBackToRootForUnconfiguredLanguages() {
        L10N.RB = L10N.getLanguageBundle(Nationalities.Italian);
        assertEquals("Hello there", L10N.TL("greeting"));
    }

    @Test
    void getLanguageBundleByStringDelegatesToNationalitiesLookup() {
        L10N.RB = L10N.getLanguageBundle("French");
        assertEquals("Bonjour", L10N.TL("greeting"));
    }
    @Test
    void getLanguageBundleByStringReturnsNullForUnknownNationality() {
        assertNull(L10N.getLanguageBundle("Atlantis"));
    }
}
