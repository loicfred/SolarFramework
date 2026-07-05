package org.solarframework.discord.obj;

import jakarta.persistence.*;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.solarframework.db.spring.DatabaseObject;
import org.solarframework.discord.obj.other.ActionServerID;

import java.util.List;

import static org.solarframework.discord.core.BotBuilder.DiscordDBService;
import static org.solarframework.discord.core.BotBuilder.DiscordAccount;

@Entity
@Table(name = "discord_channelinfo")
@IdClass(ActionServerID.class)
public class Discord_ChannelInfo extends DatabaseObject<Discord_ChannelInfo> {
    @ManyToOne
    @JoinColumn(referencedColumnName = "ID", name = "ServerID", nullable = false, insertable = false, updatable = false)
    private Discord_GuildInfo DGI;

    @OneToMany(mappedBy = "DCI", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Discord_MessageInfo> Messages;

    private transient Guild G;
    private transient GuildChannel C;

    @Id
    @Column(name = "Action", length = 32, nullable = false)
    private String Action;
    @Id
    @Column(name = "ServerID", nullable = false)
    private Long ServerID;

    @Column(name = "ChannelID", nullable = false)
    private Long ChannelID;

    protected Discord_ChannelInfo() {}
    protected Discord_ChannelInfo(Guild G, GuildChannel channel, String action) {
        this.G = G;
        this.C = channel;
        this.ServerID = G.getIdLong();
        this.Action = action;
        this.ChannelID = channel != null ? channel.getIdLong() : null;
        if (channel != null) Upsert();
        else if (DiscordDBService.getWhere(Discord_ChannelInfo.class, "ServerID = ? AND Action = ?", ServerID, action).orElse(null) instanceof Discord_ChannelInfo RI) RI.Delete();
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
        return Messages == null ? Messages = DiscordDBService.getAllWhere(Discord_MessageInfo.class, "ChannelID = ? AND ChannelAction = ?", getChannelID(), getAction()) : Messages;
    }

    public String getAsMention() {
        return getChannel() != null ? getChannel().getAsMention() : null;
    }
    public String getJumpUrl() {
        return getChannel() != null ? getChannel().getJumpUrl() : null;
    }


}