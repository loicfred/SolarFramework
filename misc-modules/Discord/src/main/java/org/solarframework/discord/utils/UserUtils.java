package org.solarframework.discord.utils;

import net.dv8tion.jda.api.entities.User;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.solarframework.discord.core.BotBuilder.DiscordAccount;
import static org.solarframework.core.util.NumberUtils.takeOnlyDigits;

public class UserUtils {

    public static void getUserByID(Long userId, Consumer<User> user) {
        getUserByID(String.valueOf(userId), user);
    }
    public static void getUserByID(String userId, Consumer<User> user) {
        DiscordAccount.retrieveUserById(takeOnlyDigits(userId)).queue(user);
    }

    public static User getUserByID(Long userId) {
       return getUserByID(String.valueOf(userId));
    }
    public static User getUserByID(String userId) {
        try {
            return DiscordAccount.retrieveUserById(takeOnlyDigits(userId)).submit().orTimeout(5, TimeUnit.SECONDS).get();
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

}
