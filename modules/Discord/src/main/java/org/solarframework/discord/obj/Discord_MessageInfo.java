package org.solarframework.discord.obj;

import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.solarframework.db.spring.DatabaseObject;

import java.util.concurrent.TimeUnit;

import static org.solarframework.discord.core.BotBuilder.DiscordAccount;
import static org.solarframework.discord.utils.WebhookUtils.getWebhookOfChannel;

@Table
public class Discord_MessageInfo extends DatabaseObject<Discord_MessageInfo> {
    private transient Guild Guild;
    private transient StandardGuildMessageChannel C = null;
    private transient Message M = null;

    @Id
    public String Action;
    @Id
    public Long ServerID;

    public Long ChannelID;
    public Long MessageID;
    public String ChannelAction;

    public Discord_MessageInfo(String channelAction, Long serverId, Long channelId) {
        this.ServerID = serverId;
        this.ChannelID = channelId;
        this.ChannelAction = channelAction;
        Upsert();
    }
    public Discord_MessageInfo(String channelAction, Long serverId, Long channelId, Long messageId) {
        this.ServerID = serverId;
        this.ChannelID = channelId;
        this.MessageID = messageId;
        this.ChannelAction = channelAction;
        Upsert();
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
        if (M == null) {
            try {
                M = getChannel().retrieveMessageById(MessageID).submit().orTimeout(10, TimeUnit.SECONDS).get();
            } catch (Exception ignored) {}
        }
        return M;
    }

    public void ModifyWebhookMessageElseCreate(WebhookMessageBuilder e) {
        try {
            if (MessageID == null) {
                getWebhookOfChannel(getChannel(), WC
                        -> WC.send(e.build()).whenComplete((msg, _)
                        -> { if (msg != null) MessageID = msg.getId();
                }));
                return;
            }
            getChannel().retrieveMessageById(MessageID).queue(ignored
                            -> getWebhookOfChannel(getChannel(), WC
                            -> WC.edit(MessageID, e.build()).whenComplete((msg, _)
                            -> MessageID = msg.getId())),
                    new ErrorHandler().handle(ErrorResponse.UNKNOWN_MESSAGE, _
                            -> getWebhookOfChannel(getChannel(), WC
                            -> WC.send(e.build()).whenComplete((msg, _) -> {
                        if (msg != null) MessageID = msg.getId();
                    })))
            );
        } catch (Exception ignored) {}
    }

    public Message getMessageElseCreate() {
        if (getMessage() == null) {
            try {
                M = getChannel().sendMessage("Waiting...").submit().orTimeout(15, TimeUnit.SECONDS).get();
                MessageID = M.getIdLong();
            } catch (Exception ignored) {}
        }
        return getMessage();
    }
    public String getMessageLink() {
        return "https://discord.com/channels/" + ServerID + "/" + ChannelID + "/" + MessageID;
    }
    public void deleteMessage() {
        try {
            super.Delete();
            getMessage().delete().queue();
            M = null;
            MessageID = null;
        } catch (Exception ignored) {}
    }
}
