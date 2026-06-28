package org.solarframework.discord.utils;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.solarframework.core.util.NumberUtils.takeOnlyDigits;

public class MemberUtils {

    public static Member getMember(Guild guild, String memberId) {
        try {
            return guild.retrieveMemberById(takeOnlyDigits(memberId)).submit().orTimeout(5, TimeUnit.SECONDS).get();
        } catch (Exception ex) {
            return null;
        }
    }
    public static void getMember(Guild guild, String memberId, Consumer<Member> callback) {
        guild.retrieveMemberById(takeOnlyDigits(memberId)).queue(callback);
    }

}
