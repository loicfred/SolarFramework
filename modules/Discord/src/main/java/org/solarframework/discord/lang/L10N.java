package org.solarframework.discord.lang;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.InteractionHook;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public class L10N extends org.solarframework.core.lang.L10N {
    protected static ResourceBundle getSystemLanguageBundle(DiscordLocale lang) {
        try {
            Locale locale;
            if (lang.getLanguageName().contains(DiscordLocale.FRENCH.getLanguageName())) {
                locale = Locale.of("fr", "FR");
            } else if (lang.getLanguageName().contains(DiscordLocale.ITALIAN.getLanguageName())) {
                locale = Locale.of("it", "IT");
            } else if (lang.getLanguageName().contains(DiscordLocale.SPANISH.getLanguageName())) {
                locale = Locale.of("es", "ES");
            } else if (lang.getLanguageName().contains(DiscordLocale.GERMAN.getLanguageName())) {
                locale = Locale.of("de", "DE");
            } else if (lang.getLanguageName().contains(DiscordLocale.PORTUGUESE_BRAZILIAN.getLanguageName())) {
                locale = Locale.of("pt", "PT");
            } else {
                locale = Locale.of("en", "US");
            }
            return ResourceBundle.getBundle("lang/discords/system", locale, L10N.class.getClassLoader());
        } catch (Exception ignored) {
            return null;
        }
    }

    public static ResourceBundle getLanguageBundle(DiscordLocale lang) {
        try {
            Locale locale;
            if (lang.getLanguageName().contains(DiscordLocale.FRENCH.getLanguageName())) {
                locale = Locale.of("fr", "FR");
            } else if (lang.getLanguageName().contains(DiscordLocale.ITALIAN.getLanguageName())) {
                locale = Locale.of("it", "IT");
            } else if (lang.getLanguageName().contains(DiscordLocale.SPANISH.getLanguageName())) {
                locale = Locale.of("es", "ES");
            } else if (lang.getLanguageName().contains(DiscordLocale.GERMAN.getLanguageName())) {
                locale = Locale.of("de", "DE");
            } else if (lang.getLanguageName().contains(DiscordLocale.PORTUGUESE_BRAZILIAN.getLanguageName())) {
                locale = Locale.of("pt", "PT");
            } else {
                locale = Locale.of("en", "US");
            }
            return ResourceBundle.getBundle("lang/discord/texts", locale, L10N.class.getClassLoader());
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String SYSL(InteractionHook m, String key, Object... var) {
        RB = getSystemLanguageBundle(m.getInteraction().getUserLocale());
        return TL(key, var);
    }
    public static String SYSL(Interaction event, String key, Object... var) {
        RB = getSystemLanguageBundle(event.getUserLocale());
        return TL(key, var);
    }
    public static String TL(InteractionHook m, String key, Object... var) {
        RB = getLanguageBundle(m.getInteraction().getUserLocale());
        return TL(key, var);
    }
    public static String TL(Interaction event, String key, Object... var) {
        RB = getLanguageBundle(event.getUserLocale());
        return TL(key, var);
    }

    public static String SYSLG(Guild G, String key, Object... var) {
        try {
            RB = getSystemLanguageBundle(G == null ? DiscordLocale.ENGLISH_UK : G.getLocale());
            return TL(key, var);
        } catch (MissingResourceException | NullPointerException | IllegalArgumentException e) {
            return key;
        }
    }
    public static String TLG(Guild G, String key, Object... var) {
        try {
            RB = getLanguageBundle(G == null ? DiscordLocale.ENGLISH_UK : G.getLocale());
            return TL(key, var);
        } catch (MissingResourceException | NullPointerException | IllegalArgumentException e) {
            return key;
        }
    }
}