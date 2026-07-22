package org.solarframework.lang;

import java.util.MissingResourceException;
import java.util.ResourceBundle;

public class L10N {
    protected static ResourceBundle RB;

    protected static String TL(String key, Object... var) {
        try {
            String s = RB.getString(key.toLowerCase().replace(" ", "-")).replace("<br>", "\n");
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
