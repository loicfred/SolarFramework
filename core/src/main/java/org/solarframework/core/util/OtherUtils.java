package org.solarframework.core.util;

import java.awt.*;
import java.net.URI;

public class OtherUtils {

    /** True only for an absolute http(s) URL with a host — {@code new URI("")} and bare words are legal relative URIs, and are rejected here. */
    public static boolean isURLValid(String url) {
        try {
            URI u = new URI(url);
            return u.isAbsolute() && u.getHost() != null && u.getScheme().matches("(?i)https?");
        } catch (Exception e) {
            return false;
        }
    }
    public static boolean isColorcodeValid(String text) {
        return text.matches("#[0-9A-Fa-f]{6}");
    }
    public static String getHexValue(Color color) {
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }

    public static boolean isEmoji(String input) {
        if (input == null || input.isEmpty()) return false;
        return isEmojiCodePoint(Integer.parseInt(input.toLowerCase().replace("u+", ""), 16));
    }
    private static boolean isEmojiCodePoint(int codePoint) {
        return (codePoint >= 0x1F600 && codePoint <= 0x1F64F) // Emoticons
                || (codePoint >= 0x1F300 && codePoint <= 0x1F5FF) // Miscellaneous Symbols and Pictographs
                || (codePoint >= 0x1F680 && codePoint <= 0x1F6FF) // Transport and Map Symbols
                || (codePoint >= 0x1F700 && codePoint <= 0x1F77F) // Alchemical Symbols
                || (codePoint >= 0x1FA70 && codePoint <= 0x1FAFF) // Symbols and Pictographs Extended-A
                || (codePoint >= 0x2600 && codePoint <= 0x26FF)   // Miscellaneous Symbols
                || (codePoint >= 0x2700 && codePoint <= 0x27BF)   // Dingbats
                || (codePoint >= 0xFE00 && codePoint <= 0xFE0F)   // Variation Selectors
                || (codePoint >= 0x1F900 && codePoint <= 0x1F9FF) // Supplemental Symbols and Pictographs
                || (codePoint >= 0x1F018 && codePoint <= 0x1F270) // Various symbols
                || (codePoint >= 0x238C && codePoint <= 0x2454)   // Miscellaneous Technical
                || (codePoint >= 0x1F004 && codePoint <= 0x1F251); // Enclosed Ideographic Supplement
    }
}
