package org.solarframework.discord.obj;

import club.minnced.discord.webhook.receive.ReadonlyMessage;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.requests.ErrorResponse;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.solarframework.discord.core.BotBuilder.DiscordAccount;
import static org.solarframework.discord.utils.WebhookUtils.getWebhookOfChannel;

public class ChannelMessage {
    private transient Guild Guild;
    private transient StandardGuildMessageChannel C = null;
    private transient Message M1 = null;
    private transient Message M2 = null;
    private transient Message M3 = null;
    private transient Message M4 = null;

    public long ServerID;
    public Long ChannelID;
    public Long MessageID = null;
    public Long MessageID2 = null;
    public Long MessageID3 = null;
    public Long MessageID4 = null;



    public ChannelMessage(long serverId, Long channelId) {
        this.ServerID = serverId;
        this.ChannelID = channelId;
    }
    public ChannelMessage(long serverId, Long channelId, Long messageId) {
        this.ServerID = serverId;
        this.ChannelID = channelId;
        this.MessageID = messageId;
    }
    public ChannelMessage(long serverId, Long channelId, Long messageId, Long messageId2) {
        this.ServerID = serverId;
        this.ChannelID = channelId;
        this.MessageID = messageId;
        this.MessageID2 = messageId2;
    }
    public ChannelMessage(long serverId, Long channelId, Long messageId, Long messageId2, Long messageId3) {
        this.ServerID = serverId;
        this.ChannelID = channelId;
        this.MessageID = messageId;
        this.MessageID2 = messageId2;
        this.MessageID3 = messageId3;
    }
    public ChannelMessage(long serverId, long channelId, long messageID, long messageID2, long messageID3, long messageID4) {
        this.ServerID = serverId;
        this.ChannelID = channelId;
        this.MessageID = messageID;
        this.MessageID2 = messageID2;
        this.MessageID3 = messageID3;
        this.MessageID4 = messageID4;
    }

    public Guild getGuild() {
        return Guild == null ? Guild = DiscordAccount.getGuildById(ServerID) : Guild;
    }

    public StandardGuildMessageChannel getChannel() {
        if (C == null) {
            try {
                C = getGuild().getChannelById(StandardGuildMessageChannel.class, ChannelID);
            } catch (Exception ignored) {}
        }
        return C;
    }
    public Message getMessage() {
        if (M1 == null) {
            try {
                M1 = getChannel().retrieveMessageById(MessageID).submit().orTimeout(10, TimeUnit.SECONDS).get();
            } catch (Exception ignored) {}
        }
        return M1;
    }
    public Message getMessage2() {
        if (M2 == null) {
            try {
                M2 = getChannel().retrieveMessageById(MessageID2).submit().orTimeout(10, TimeUnit.SECONDS).get();
            } catch (Exception ignored) {}
        }
        return M2;
    }
    public Message getMessage3() {
        if (M3 == null) {
            try {
                M3 = getChannel().retrieveMessageById(MessageID3).submit().orTimeout(10, TimeUnit.SECONDS).get();
            } catch (Exception ignored) {}
        }
        return M3;
    }
    public Message getMessage4() {
        if (M4 == null) {
            try {
                M4 = getChannel().retrieveMessageById(MessageID4).submit().orTimeout(10, TimeUnit.SECONDS).get();
            } catch (Exception ignored) {}
        }
        return M4;
    }

    public Message getMessageElseCreate() {
        if (getMessage() == null) {
            try {
                M1 = getChannel().sendMessage("Waiting...").submit().orTimeout(10, TimeUnit.SECONDS).get();
                MessageID = M1.getIdLong();
            } catch (Exception ignored) {}
        }
        return getMessage();
    }
    public Message getMessage2ElseCreate() {
        if (getMessage2() == null) {
            try {
                M2 = getChannel().sendMessage("Waiting...").submit().orTimeout(10, TimeUnit.SECONDS).get();
                MessageID2 = M2.getIdLong();
            } catch (Exception ignored) {}
        }
        return getMessage2();
    }
    public Message getMessage3ElseCreate() {
        if (getMessage3() == null) {
            try {
                M3 = getChannel().sendMessage("Waiting...").submit().orTimeout(10, TimeUnit.SECONDS).get();
                MessageID3 = M3.getIdLong();
            } catch (Exception ignored) {}
        }
        return getMessage3();
    }
    public Message getMessage4ElseCreate() {
        if (getMessage4() == null) {
            try {
                M4 = getChannel().sendMessage("Waiting...").submit().orTimeout(10, TimeUnit.SECONDS).get();
                MessageID4 = M4.getIdLong();
            } catch (Exception ignored) {}
        }
        return getMessage4();
    }


    private void modifyWebhookMessageElseCreate(WebhookMessageBuilder e, Consumer<ReadonlyMessage> callback, Long messageID, Consumer<Long> idSetterIfNew) {
        try {
            if (messageID == null) {
                getWebhookOfChannel(getChannel(), WC -> WC.send(e.build()).whenComplete((msg, _) -> {
                    if (msg != null) idSetterIfNew.accept(msg.getId());
                    callback.accept(msg);
                }));
                return;
            }
            getChannel().retrieveMessageById(messageID).queue(ignored
                            -> getWebhookOfChannel(getChannel(), WC
                            -> WC.edit(messageID, e.build()).whenComplete((msg, _)
                            -> callback.accept(msg))),
                    new ErrorHandler().handle(ErrorResponse.UNKNOWN_MESSAGE, _
                            -> getWebhookOfChannel(getChannel(), WC
                            -> WC.send(e.build()).whenComplete((msg, _) -> {
                        if (msg != null) idSetterIfNew.accept(msg.getId());
                        callback.accept(msg);
                    })))
            );
        } catch (Exception ignored) {
            callback.accept(null);
        }
    }
    public void ModifyWebhookMessageElseCreate(WebhookMessageBuilder e, Consumer<ReadonlyMessage> cb) {
        modifyWebhookMessageElseCreate(e, cb, MessageID,  id -> MessageID = id);
    }
    public void ModifyWebhookMessage2ElseCreate(WebhookMessageBuilder e, Consumer<ReadonlyMessage> cb) {
        modifyWebhookMessageElseCreate(e, cb, MessageID2, id -> MessageID2 = id);
    }
    public void ModifyWebhookMessage3ElseCreate(WebhookMessageBuilder e, Consumer<ReadonlyMessage> cb) {
        modifyWebhookMessageElseCreate(e, cb, MessageID3, id -> MessageID3 = id);
    }
    public void ModifyWebhookMessage4ElseCreate(WebhookMessageBuilder e, Consumer<ReadonlyMessage> cb) {
        modifyWebhookMessageElseCreate(e, cb, MessageID4, id -> MessageID4 = id);
    }

    public String getMessageLink1() {
        return "https://discord.com/channels/" + ServerID + "/" + ChannelID + "/" + MessageID;
    }
    public String getMessageLink2() {
        return "https://discord.com/channels/" + ServerID + "/" + ChannelID + "/" + MessageID2;
    }
    public String getMessageLink3() {
        return "https://discord.com/channels/" + ServerID + "/" + ChannelID + "/" + MessageID3;
    }
    public String getMessageLink4() {
        return "https://discord.com/channels/" + ServerID + "/" + ChannelID + "/" + MessageID4;
    }

    public void DeleteMessage1() {
        try {
            getMessage().delete().queue();
            M1 = null;
            MessageID = null;
        } catch (Exception ignored) {}
    }
    public void DeleteMessage2() {
        try {
            getMessage2().delete().queue();
            M2 = null;
            MessageID2 = null;
        } catch (Exception ignored) {}
    }
    public void DeleteMessage3() {
        try {
            getMessage3().delete().queue();
            M3 = null;
            MessageID3 = null;
        } catch (Exception ignored) {}
    }
    public void DeleteMessage4() {
        try {
            getMessage4().delete().queue();
            M4 = null;
            MessageID4 = null;
        } catch (Exception ignored) {}
    }

}
