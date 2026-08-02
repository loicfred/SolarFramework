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
import static org.solarframework.discord.core.BotBuilder.LogChannel;

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
                user.openPrivateChannel().queue(channel -> // MY DM
                        channel.sendMessage(M.build()).queue(MSG -> {
                            if (LogChannel != null) LogChannel.sendMessage(M.build()).setContent("**[DM][" + user.getEffectiveName() + "/" + user.getId() + "]**: " + M.getContent()).queue();
                            if (callback != null) callback.accept(MSG, null);
                        }, error -> failedToMessage(user, error, callback)),
                        error -> failedToMessage(user, error, callback));
            }
        } catch (Exception ignored) {}
    }

    /**
     * A DM that cannot be delivered is never fatal, so every failure is swallowed here instead of only
     * CANNOT_SEND_TO_USER (50007): closed DMs, blocked bot, deleted account and 50278 (no mutual guild —
     * which JDA has no {@link ErrorResponse} constant for, so an {@link ErrorHandler} would let it through
     * to the default handler and log it as an error).
     */
    private static void failedToMessage(User user, Throwable error, BiConsumer<Message, ErrorResponseException> callback) {
        ErrorResponseException E = error instanceof ErrorResponseException e ? e : null;
        System.out.println("[" + getNow("HH:mm:ss") + "] Failed to message " + user.getEffectiveName() + (E != null ? " (" + E.getErrorCode() + " " + E.getMeaning() + ")" : ""));
        if (callback != null) callback.accept(null, E);
    }

}
