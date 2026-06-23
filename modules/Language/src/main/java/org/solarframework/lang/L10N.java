package org.solarframework.lang;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public class L10N {
    protected static ResourceBundle RB;

    protected static ResourceBundle getLanguageBundle(String nat) {
        return getLanguageBundle(Nationalities.get(nat));
    }
    protected static ResourceBundle getLanguageBundle(Nationalities nat) {
        try {
            Locale locale;
            if (nat.equals(Nationalities.French) || nat.equals(Nationalities.Swiss) || nat.equals(Nationalities.Moroccan) || nat.equals(Nationalities.Tunisian) || nat.equals(Nationalities.Guadeloupean) || nat.equals(Nationalities.Belgian)) {
                locale = new Locale.Builder().setLanguage("fr").setRegion("FR").build();
            } else if (nat.equals(Nationalities.Italian)) {
                locale = new Locale.Builder().setLanguage("it").setRegion("IT").build();
            } else if (nat.equals(Nationalities.Spanish) || nat.equals(Nationalities.Mexican)) {
                locale = new Locale.Builder().setLanguage("es").setRegion("ES").build();
            } else if (nat.equals(Nationalities.German) || nat.equals(Nationalities.Australian)) {
                locale = new Locale.Builder().setLanguage("de").setRegion("DE").build();
            } else if (nat.equals(Nationalities.Brazilian) || nat.equals(Nationalities.Portuguese)) {
                locale = new Locale.Builder().setLanguage("pt").setRegion("PT").build();
            } else {
                locale = new Locale.Builder().setLanguage("en").setRegion("US").build();
            }
            return ResourceBundle.getBundle("lang/texts", locale, L10N.class.getClassLoader());
        } catch (Exception ignored) {
            return null;
        }
    }

    protected static String TL(String key, Object... var) {
        try {
            String s = RB.getString(key.toLowerCase().replaceAll(" ", "-")).replaceAll("<br>", "\n");
            if (var == null || var.length == 0) return s;
            s = s.replaceAll("<v1>", removeRegex(var[0] + ""));
            if (var.length > 1) s = s.replaceAll("<v2>", removeRegex(var[1] + ""));
            if (var.length > 2) s = s.replaceAll("<v3>", removeRegex(var[2] + ""));
            if (var.length > 3) s = s.replaceAll("<v4>", removeRegex(var[3] + ""));
            if (var.length > 4) s = s.replaceAll("<v5>", removeRegex(var[4] + ""));
            if (var.length > 5) s = s.replaceAll("<v6>", removeRegex(var[5] + ""));
            return s;
        } catch (MissingResourceException | NullPointerException | IllegalArgumentException e) {
            return key;
        }
    }

    protected static String removeRegex(String input) {
        if (input != null) {
            input = input.replaceAll("[$]", "S");
            return input.replaceAll("[{}^]", "");
        } else {
            return "???";
        }
    }
}
