package org.solarframework.discord.utils;

import net.dv8tion.jda.api.interactions.InteractionHook;

import static org.solarframework.discord.lang.L10N.SYSL;

public class MiscUtils {

    public static boolean isColorcodeValid(InteractionHook M, String text) {
        if (text != null && org.solarframework.core.util.OtherUtils.isColorcodeValid(text)) return true;
        M.editOriginal(SYSL(M, "error-invalid-colorcode")).queue();
        return false;
    }
    
    public static boolean isSyntaxValid(InteractionHook M, String text) {
        if (text != null && !text.matches(".*[\\\\|:*?\"<>].*")) return true;
        M.editOriginal(SYSL(M, "error-illegal-character")).queue();
        return false;
    }

    public static boolean isInviteLinkValid(InteractionHook M, String text) {
        if (text != null && text.startsWith("https://discord.gg/")) return true;
        M.editOriginal(SYSL(M, "error-invalid-invite-link")).queue();
        return false;
    }

    public static boolean isURLValid(InteractionHook M, String text) {
        if (text != null && org.solarframework.core.util.OtherUtils.isURLValid(text)) return true;
        M.editOriginal(SYSL(M, "error-invalid-url-link")).queue();
        return false;
    }
    
    public static boolean isDateValid(InteractionHook M, String text) {
        if (text != null && org.solarframework.core.util.TimeUtils.isDateValid(text)) return true;
        M.editOriginal(SYSL(M, "error-invalid-date")).queue();
        return false;
    }

}
