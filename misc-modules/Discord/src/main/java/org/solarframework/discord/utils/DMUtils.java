package org.solarframework.discord.utils;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;

import java.util.function.BiConsumer;

import static org.solarframework.core.util.StringUtils.StopString;
import static org.solarframework.core.util.TimeUtils.getNow;

public class DMUtils {
    public static void sendPrivateMessage(User user, String message) {
        sendPrivateMessage(user, message, null);
    }
    public static void sendPrivateMessage(User user, String message, BiConsumer<Message, ErrorResponseException> callback) {
        sendPrivateMessage(user, new MessageCreateBuilder().addContent(StopString(message, 2000)), callback);
    }

    public static void sendPrivateMessage(User user, MessageCreateBuilder M) {
        sendPrivateMessage(user, M, null);
    }
    public static void sendPrivateMessage(User user, MessageCreateBuilder M, BiConsumer<Message, ErrorResponseException> callback) {
        try {
            if (user != null) {
                user.openPrivateChannel().queue(channel -> { // MY DM
                    channel.sendMessage(M.build()).queue(MSG -> {
                        if (callback != null) callback.accept(MSG, null);
                    }, new ErrorHandler().handle(ErrorResponse.CANNOT_SEND_TO_USER, error -> {
                        System.out.println("[" + getNow("HH:mm:ss") + "] Failed to message " + user.getEffectiveName());
                        if (callback != null) callback.accept(null, error);
                    }));
                });
            }
        } catch (Exception ignored) {}
    }

}
