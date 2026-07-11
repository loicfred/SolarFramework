package org.solarframework.core.util;

import org.junit.jupiter.api.Test;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

class OtherUtilsTest {

    @Test
    void isURLValidAcceptsWellFormedURIs() {
        assertTrue(OtherUtils.isURLValid("https://example.com/path?query=1"));
    }
    @Test
    void isURLValidRejectsMalformedURIs() {
        assertFalse(OtherUtils.isURLValid(" not a uri "));
    }

    @Test
    void isColorcodeValidRequiresHashAndSixHexDigits() {
        assertTrue(OtherUtils.isColorcodeValid("#FFAA00"));
        assertTrue(OtherUtils.isColorcodeValid("#ffaa00"));
        assertFalse(OtherUtils.isColorcodeValid("FFAA00"));
        assertFalse(OtherUtils.isColorcodeValid("#FFAA0"));
        assertFalse(OtherUtils.isColorcodeValid("#GGAA00"));
    }

    @Test
    void getHexValueFormatsRGBAsUppercaseHex() {
        assertEquals("#FF0080", OtherUtils.getHexValue(new Color(255, 0, 128)));
    }

    @Test
    void isEmojiDetectsCodePointsInEmoticonRange() {
        assertTrue(OtherUtils.isEmoji("U+1F600"));
        assertFalse(OtherUtils.isEmoji("U+0041"));
    }
    @Test
    void isEmojiHandlesNullAndEmpty() {
        assertFalse(OtherUtils.isEmoji(null));
        assertFalse(OtherUtils.isEmoji(""));
    }
}
