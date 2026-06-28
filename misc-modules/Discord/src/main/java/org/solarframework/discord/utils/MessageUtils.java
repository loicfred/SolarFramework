package org.solarframework.discord.utils;

import net.dv8tion.jda.api.components.MessageTopLevelComponentUnion;
import net.dv8tion.jda.api.components.actionrow.ActionRowChildComponentUnion;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.internal.components.actionrow.ActionRowImpl;
import net.dv8tion.jda.internal.components.buttons.ButtonImpl;
import net.dv8tion.jda.internal.components.selections.SelectMenuImpl;
import net.dv8tion.jda.internal.components.selections.StringSelectMenuImpl;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.solarframework.discord.core.BotBuilder.DiscordAccount;

public class MessageUtils {

    public static Message getMessageByLink(String link) {
        try {
            link = link.replaceAll("https://discord.com/channels/", "");
            Guild G = DiscordAccount.getGuildById(link.split("/")[1]);
            TextChannel channel = G != null ? G.getTextChannelById(link.split("/")[1]) : null;
            return getMessage(channel, link.split("/")[2]);
        } catch (Exception ignored) {
            return null;
        }
    }
    public static void getMessageByLink(String link, Consumer<Message> message) {
        try {
            link = link.replaceAll("https://discord.com/channels/", "");
            Guild G = DiscordAccount.getGuildById(link.split("/")[1]);
            TextChannel channel = G != null ? G.getTextChannelById(link.split("/")[1]) : null;
            getMessage(channel, link.split("/")[2], message);
        } catch (Exception ignored) {}
    }

    public static Message getMessage(MessageChannel channel, Long messageId) {
        return getMessage(channel, String.valueOf(messageId));
    }
    public static void getMessage(MessageChannel channel, Long messageId, Consumer<Message> message) {
        channel.retrieveMessageById(messageId).queue(message);
    }

    public static Message getMessage(MessageChannel channel, String messageId) {
        try {
            return channel.retrieveMessageById(messageId).submit().orTimeout(10, TimeUnit.SECONDS).get();
        } catch (Exception ex) {
            return null;
        }
    }
    public static void getMessage(MessageChannel channel, String messageId, Consumer<Message> message) {
        channel.retrieveMessageById(messageId).queue(message);
    }

    public static String getComponentFullID(Message M, String id) {
        return getComponentFullID(M.getComponents(), id);
    }
    public static String getComponentFullID(List<MessageTopLevelComponentUnion> comps, String id) {
        if (comps != null) for (MessageTopLevelComponentUnion ARs : comps) {
            if (ARs instanceof ActionRowImpl AR) {
                for (ActionRowChildComponentUnion compo : AR.getComponents()) {
                    if (compo instanceof StringSelectMenuImpl B && B.getCustomId().startsWith(id))
                        return B.getCustomId();
                    else if (compo instanceof SelectMenuImpl B && B.getCustomId().equals(id))
                        return B.getCustomId();
                    else if (compo instanceof ButtonImpl B && B.getCustomId() != null && B.getCustomId().startsWith(id))
                        return B.getCustomId();
                }
            }
        }
        return id;
    }
}
