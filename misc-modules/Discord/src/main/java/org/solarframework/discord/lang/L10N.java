package org.solarframework.discord.lang;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.solarframework.discord.obj.Discord_GuildInfo;
import org.solarframework.discord.obj.Discord_Profile;
import org.solarframework.lang.Nationalities;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public class L10N extends org.solarframework.lang.L10N {
    public static ResourceBundle getSystemLanguageBundle(DiscordLocale lang) {
        return getSystemLanguageBundle(lang.getLanguageName());
    }
    public static ResourceBundle getLanguageBundle(DiscordLocale lang) {
        return getLanguageBundle(lang.getLanguageName());
    }
    public static ResourceBundle getSystemLanguageBundle(Nationalities lang) {
        return getSystemLanguageBundle(lang.name());
    }
    public static ResourceBundle getLanguageBundle(Nationalities lang) {
        return getLanguageBundle(lang.name());
    }
    public static ResourceBundle getSystemLanguageBundle(String lang) {
        try {
            Locale locale = getLocale(lang);
            return ResourceBundle.getBundle("lang/discords/system", locale, L10N.class.getClassLoader());
        } catch (Exception ignored) {
            return null;
        }
    }
    public static ResourceBundle getLanguageBundle(String lang) {
        try {
            Locale locale = getLocale(lang);
            return ResourceBundle.getBundle("lang/discord/texts", locale, L10N.class.getClassLoader());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Locale getLocale(String lang) {
        Locale locale;
        if (lang.contains(DiscordLocale.FRENCH.getLanguageName())) {
            locale = Locale.of("fr", "FR");
        } else if (lang.contains(DiscordLocale.ITALIAN.getLanguageName())) {
            locale = Locale.of("it", "IT");
        } else if (lang.contains(DiscordLocale.SPANISH.getLanguageName())) {
            locale = Locale.of("es", "ES");
        } else if (lang.contains(DiscordLocale.GERMAN.getLanguageName())) {
            locale = Locale.of("de", "DE");
        } else if (lang.contains("Brazil") || lang.contains("Portuguese")) {
            locale = Locale.of("pt", "PT");
        } else {
            locale = Locale.of("en", "US");
        }
        return locale;
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
    public static String SYSLG(Discord_GuildInfo GI, String key, Object... var) {
        return SYSLG(GI == null ? null : GI.getGuild(), key, var);
    }

    public static String TLG(Guild G, String key, Object... var) {
        try {
            RB = getLanguageBundle(G == null ? DiscordLocale.ENGLISH_UK : G.getLocale());
            return TL(key, var);
        } catch (MissingResourceException | NullPointerException | IllegalArgumentException e) {
            return key;
        }
    }
    public static String TLG(Discord_GuildInfo GI, String key, Object... var) {
        return TLG(GI == null ? null : GI.getGuild(), key, var);
    }
}