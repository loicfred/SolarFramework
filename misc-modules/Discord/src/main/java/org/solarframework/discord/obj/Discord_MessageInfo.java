package org.solarframework.discord.obj;

import jakarta.persistence.*;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.WebhookClient;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.solarframework.db.spring.DatabaseObject;
import org.solarframework.discord.obj.other.ActionServerID;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.solarframework.discord.core.BotBuilder.DiscordAccount;
import static org.solarframework.discord.utils.WebhookUtils.getWebhookOfChannel;
import static org.solarframework.discord.utils.WebhookUtils.served;

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

    public void ModifyWebhookMessageElseCreate(MessageCreateData e, Consumer<Message> m) {
        ModifyWebhookMessageElseCreate(e, null, null, m);
    }
    /** Same, but the created message is posted under a custom webhook identity instead of the guild's (a webhook edit cannot change username/avatar, so those only apply on creation). */
    public void ModifyWebhookMessageElseCreate(MessageCreateData e, String username, String avatarUrl, Consumer<Message> m) {
        if (MessageID == null) {sendWebhookMessage(e, username, avatarUrl, m); return;}
        getWebhookOfChannel(getChannel(), WC -> {
            try {
                WC.editMessageById(MessageID, MessageEditData.fromCreateData(e)).queue(msg -> {MessageID = msg.getIdLong();m.accept(msg);},
                        new ErrorHandler().handle(ErrorResponse.UNKNOWN_MESSAGE, _ -> sendWebhookMessage(e, username, avatarUrl, m)));
            } catch (Exception ignored) {m.accept(null);}
        });
    }
    public void ModifyWebhookMessageElseCreate(EmbedBuilder e, Consumer<Message> m) {
        ModifyWebhookMessageElseCreate(new MessageCreateBuilder().setEmbeds(e.build()).build(), m);
    }
    private void sendWebhookMessage(MessageCreateData e, String username, String avatarUrl, Consumer<Message> m) {
        getWebhookOfChannel(getChannel(), WC -> {
            try {
                WebhookMessageCreateAction<Message> A = username == null ? served(WC.sendMessage(e), getGuild()) : WC.sendMessage(e).setUsername(username).setAvatarUrl(avatarUrl);
                A.queue(msg -> {MessageID = msg.getIdLong();m.accept(msg);}, _ -> m.accept(null));
            } catch (Exception ignored) {m.accept(null);}
        });
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
