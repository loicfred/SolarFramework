package org.solarframework.discord.lang;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.Interaction;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.solarframework.lang.Nationalities;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class L10NTest {

    private static Locale DEFAULT;

    // ResourceBundle falls back to the JVM default locale before it falls back to the base bundle, so anything
    // asserting the English fallback is machine-dependent unless the default is pinned first.
    @BeforeAll
    static void pinDefaultLocale() {
        DEFAULT = Locale.getDefault();
        Locale.setDefault(Locale.US);
        ResourceBundle.clearCache(L10N.class.getClassLoader());
    }
    @AfterAll
    static void restoreDefaultLocale() {
        Locale.setDefault(DEFAULT);
        ResourceBundle.clearCache(L10N.class.getClassLoader());
    }

    @Test
    void everySupportedLanguageResolvesToItsOwnBundle() {
        assertEquals(Locale.of("fr", "FR"), L10N.getSystemLanguageBundle(DiscordLocale.FRENCH).getLocale());
        assertEquals(Locale.of("de", "DE"), L10N.getSystemLanguageBundle(DiscordLocale.GERMAN).getLocale());
        assertEquals(Locale.of("es", "ES"), L10N.getSystemLanguageBundle(DiscordLocale.SPANISH).getLocale());
        assertEquals(Locale.of("it", "IT"), L10N.getSystemLanguageBundle(DiscordLocale.ITALIAN).getLocale());
        assertEquals(Locale.of("pt", "PT"), L10N.getSystemLanguageBundle(DiscordLocale.PORTUGUESE_BRAZILIAN).getLocale());
    }
    // there is no system_en_US.properties: English is served by the base bundle, which is what unknown languages get too
    @Test
    void englishAndUnknownLanguagesUseTheBaseBundle() {
        assertEquals(Locale.ROOT, L10N.getSystemLanguageBundle(DiscordLocale.ENGLISH_US).getLocale());
        assertEquals(Locale.ROOT, L10N.getSystemLanguageBundle(DiscordLocale.JAPANESE).getLocale());
    }
    @Test
    void theNationalityOverloadAgreesWithTheDiscordLocaleOne() {
        assertSame(L10N.getSystemLanguageBundle(DiscordLocale.FRENCH), L10N.getSystemLanguageBundle(Nationalities.French));
        assertSame(L10N.getSystemLanguageBundle(DiscordLocale.GERMAN), L10N.getSystemLanguageBundle(Nationalities.German));
    }
    // "Spanish, LATAM" and "Portuguese, Brazilian" match by substring, not by exact language name
    @Test
    void regionalVariantsShareTheirParentBundle() {
        assertSame(L10N.getSystemLanguageBundle(DiscordLocale.SPANISH), L10N.getSystemLanguageBundle(DiscordLocale.SPANISH_LATAM));
        assertSame(L10N.getSystemLanguageBundle(DiscordLocale.PORTUGUESE_BRAZILIAN), L10N.getSystemLanguageBundle(Nationalities.Portuguese));
    }

    // this module ships no lang/discord/texts bundle, which is why every TL falls through to the system one
    @Test
    void theTextsBundleIsAbsentFromThisModule() {
        assertNull(L10N.getLanguageBundle(DiscordLocale.FRENCH));
        assertNull(L10N.getLanguageBundle(Nationalities.French));
    }

    @Test
    void translationFollowsTheInteractionLocale() {
        assertEquals(L10N.getSystemLanguageBundle(DiscordLocale.FRENCH).getString("error"), L10N.TL(interaction(DiscordLocale.FRENCH), "error"));
        assertEquals(L10N.getSystemLanguageBundle(DiscordLocale.GERMAN).getString("error"), L10N.TL(interaction(DiscordLocale.GERMAN), "error"));
    }
    @Test
    void unknownKeysAreReturnedAsIs() {
        assertEquals("no-such-key", L10N.TL(interaction(DiscordLocale.FRENCH), "no-such-key"));
    }
    @Test
    void guildTranslationDefaultsToEnglishWithoutAGuild() {
        assertEquals(L10N.getSystemLanguageBundle(DiscordLocale.ENGLISH_UK).getString("error"), L10N.TLG((Guild) null, "error"));
    }
    @Test
    void placeholdersAreSubstituted() {
        assertTrue(L10N.TL(interaction(DiscordLocale.ENGLISH_US), "user-not-part-of-guild", "<@42>").startsWith("<@42>"));
    }

    @Test
    void everyLocaleBundleDeclaresTheSameKeys() throws IOException {
        Set<Object> base = keysOf("system.properties");
        assertFalse(base.isEmpty());
        for (String L : new String[]{"de_DE", "es_ES", "fr_FR", "it_IT", "pt_PT"}) assertEquals(base, keysOf("system_" + L + ".properties"), L);
    }

    private Set<Object> keysOf(String file) throws IOException {
        try (InputStream in = L10N.class.getClassLoader().getResourceAsStream("lang/discord/" + file)) {
            assertNotNull(in, file);
            Properties P = new Properties();
            P.load(in);
            return P.keySet();
        }
    }

    private Interaction interaction(DiscordLocale locale) {
        Interaction IT = mock(Interaction.class);
        when(IT.getUserLocale()).thenReturn(locale);
        return IT;
    }
}
