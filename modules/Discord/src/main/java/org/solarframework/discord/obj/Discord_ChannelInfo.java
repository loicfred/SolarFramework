package org.solarframework.discord.obj;

import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import jakarta.persistence.*;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.solarframework.db.spring.DatabaseObject;

import java.util.List;

import static org.solarframework.db.spring.DatabaseService.dbService;
import static org.solarframework.discord.core.BotBuilder.DiscordAccount;

@Table(name = "discord_channelinfo", uniqueConstraints = @UniqueConstraint(columnNames = {"ServerID", "ChannelID"}))
public class Discord_ChannelInfo extends DatabaseObject.ID_OBJ<Long, Discord_ChannelInfo> {
    @OneToMany
    @JoinColumn(referencedColumnName = "ID", name = "ServerID")
    private transient List<Discord_MessageInfo> Messages;

    private transient Guild G;
    private transient GuildChannel C;

    @Id
    private String Action;
    @Id
    private Long ServerID;

    private Long ChannelID;

    public Discord_ChannelInfo(Guild G, GuildChannel channel, String action) {
        this.G = G;
        this.C = channel;
        this.ServerID = G.getIdLong();
        this.Action = action;
        this.ChannelID = channel != null ? channel.getIdLong() : null;
        if (channel != null) Upsert();
        else if (dbService.getWhere(Discord_ChannelInfo.class, "ServerID = ? AND Action = ?", ServerID, action).orElse(null) instanceof Discord_ChannelInfo RI) RI.Delete();
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

    public String getAction() {
        return Action;
    }
    public void setAction(String action) {
        Action = action;
    }

    public GuildChannel getChannel() {
        try {
            return C == null ? C = getGuild().getGuildChannelById(ChannelID) : C;
        } catch (Exception e) {
            return null;
        }
    }
    private Guild getGuild() {
        return G == null ? G = DiscordAccount.getGuildById(ServerID) : G;
    }

    public boolean canTalk() {
        return C != null && C instanceof GuildMessageChannel CT && CT.canTalk();
    }

    public MessageCreateAction sendMessage(String message) {
        if (C instanceof TextChannel TC) return TC.sendMessage(message);
        throw new RuntimeException("Channel is not a text channel");
    }

    public List<Discord_MessageInfo> getMessages() {
        return Messages == null ? Messages = dbService.getAllWhere(Discord_MessageInfo.class, "ChannelID = ? AND ChannelAction = ?", getID(), getAction()) : Messages;
    }

    public Discord_MessageInfo newEmptyMessage() {
        return new Discord_MessageInfo(getAction(), getServerID(), getChannelID());
    }
}