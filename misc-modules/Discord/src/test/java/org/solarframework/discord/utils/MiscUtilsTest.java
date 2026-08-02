package org.solarframework.discord.utils;

import net.dv8tion.jda.api.interactions.InteractionHook;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MiscUtilsTest {

    // the happy path never touches the hook, so null is the strongest assertion available: an NPE means a regression
    @Test
    void validInputNeverTouchesTheHook() {
        assertTrue(MiscUtils.isColorcodeValid(null, "#A1B2C3"));
        assertTrue(MiscUtils.isURLValid(null, "https://example.com"));
        assertTrue(MiscUtils.isDateValid(null, "15/01/2024"));
        assertTrue(MiscUtils.isSyntaxValid(null, "My Tournament"));
        assertTrue(MiscUtils.isInviteLinkValid(null, "https://discord.gg/abc123"));
    }

    @Test
    void syntaxRejectsEveryReservedCharacter() {
        for (String c : new String[]{"\\", "|", ":", "*", "?", "\"", "<", ">"}) assertFalse(MiscUtils.isSyntaxValid(hook(), "a" + c + "b"), c);
    }
    // "/" is deliberately allowed even though command ids use it as a metadata separator
    @Test
    void syntaxAllowsTheSlash() {
        assertTrue(MiscUtils.isSyntaxValid(null, "a/b"));
    }

    @Test
    void inviteLinkRequiresTheCanonicalShortForm() {
        assertFalse(MiscUtils.isInviteLinkValid(hook(), "http://discord.gg/abc123"));
        assertFalse(MiscUtils.isInviteLinkValid(hook(), "https://discord.com/invite/abc123"));
    }

    @Test
    void nullIsRejectedByEveryValidator() {
        assertFalse(MiscUtils.isColorcodeValid(hook(), null));
        assertFalse(MiscUtils.isURLValid(hook(), null));
        assertFalse(MiscUtils.isDateValid(hook(), null));
        assertFalse(MiscUtils.isSyntaxValid(hook(), null));
        assertFalse(MiscUtils.isInviteLinkValid(hook(), null));
    }

    @Test
    void invalidInputRepliesOnTheHook() {
        assertReplies(H -> MiscUtils.isColorcodeValid(H, "nope"));
        assertReplies(H -> MiscUtils.isURLValid(H, "nope"));
        assertReplies(H -> MiscUtils.isDateValid(H, "31/02/2024"));
        assertReplies(H -> MiscUtils.isSyntaxValid(H, "a|b"));
        assertReplies(H -> MiscUtils.isInviteLinkValid(H, "nope"));
    }

    private void assertReplies(java.util.function.Predicate<InteractionHook> validator) {
        InteractionHook H = hook();
        assertFalse(validator.test(H));
        verify(H).editOriginal(anyString());
    }

    private InteractionHook hook() {
        return mock(InteractionHook.class, RETURNS_DEEP_STUBS);
    }
}
