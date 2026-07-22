package org.solarframework.discord.obj;

import club.minnced.discord.webhook.receive.ReadonlyMessage;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import jakarta.persistence.*;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.solarframework.db.spring.DatabaseObject;
import org.solarframework.discord.obj.other.ActionServerID;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.solarframework.discord.core.BotBuilder.DiscordAccount;
import static org.solarframework.discord.utils.WebhookUtils.getWebhookOfChannel;

@Entity
@Table(name = "discord_messageinfo")
@IdClass(ActionServerID.class)
public class Discord_MessageInfo extends DatabaseObject<Discord_MessageInfo> {
    @ManyToOne
    @JoinColumns({
            @JoinColumn(referencedColumnName = "Action", name = "ChannelAction", nullable = false, insertable = false, updatable = false),
            @JoinColumn(referencedColumnName = "ServerID", name = "ServerID", nullable = false, insertable = false, updatable = false)
    })
    private Discord_ChannelInfo DCI;

    @ManyToOne
    @JoinColumn(referencedColumnName = "ID", name = "ServerID", nullable = false, insertable = false, updatable = false)
    private Discord_GuildInfo DGI;

    private transient Guild Guild;
    private transient StandardGuildMessageChannel C = null;
    private transient Message M = null;

    @Id
    @Column(name = "Action", length = 64, nullable = false)
    private String Action;
    @Id
    @Column(name = "ServerID", nullable = false)
    private Long ServerID;

    @Column(name = "ChannelID", nullable = false)
    private Long ChannelID;
    @Column(name = "MessageID", unique = true)
    private Long MessageID;

    @Column(name = "ChannelAction", length = 64, nullable = false)
    private String ChannelAction;

    public Discord_MessageInfo() {}
    public Discord_MessageInfo(String channelAction, Long serverId, Long channelId) {
        this.ServerID = serverId;
        this.ChannelID = channelId;
        this.ChannelAction = channelAction;
    }
    public Discord_MessageInfo(String channelAction, Long serverId, Long channelId, Long messageId) {
        this.ServerID = serverId;
        this.ChannelID = channelId;
        this.MessageID = messageId;
        this.ChannelAction = channelAction;
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

    public void ModifyWebhookMessageElseCreate(WebhookMessageBuilder e, Consumer<ReadonlyMessage> m) {
        try {
            if (MessageID == null) {
                getWebhookOfChannel(getChannel(), WC
                        -> WC.send(e.build()).whenComplete((msg, _)
                        -> {if (msg != null) MessageID = msg.getId();m.accept(msg);}
                ));
                return;
            }
            getChannel().retrieveMessageById(MessageID).queue(ignored
                            -> getWebhookOfChannel(getChannel(), WC
                            -> WC.edit(MessageID, e.build()).whenComplete((msg, _)
                            -> {MessageID = msg.getId();m.accept(msg);})),
                    new ErrorHandler().handle(ErrorResponse.UNKNOWN_MESSAGE, _
                            -> getWebhookOfChannel(getChannel(), WC
                            -> WC.send(e.build()).whenComplete((msg, _)
                            -> {if (msg != null) MessageID = msg.getId();m.accept(msg);
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

    public String getAction() {
        return Action;
    }
    public void setAction(String action) {
        Action = action;
    }

    public Long getServerID() {
        return ServerID;
    }
    public void setServerID(Long serverID) {
        ServerID = serverID;
    }

    public Long getChannelID() {
        return ChannelID;
    }
    public void setChannelID(Long channelID) {
        ChannelID = channelID;
    }

    public Long getMessageID() {
        return MessageID;
    }
    public void setMessageID(Long messageID) {
        MessageID = messageID;
    }

    public String getChannelAction() {
        return ChannelAction;
    }

    public void setChannelAction(String channelAction) {
        ChannelAction = channelAction;
    }
}
