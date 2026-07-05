package org.solarframework.discord.obj;

import jakarta.persistence.*;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import org.solarframework.db.spring.DatabaseObject;
import org.solarframework.discord.obj.other.ActionServerID;

import static org.solarframework.discord.core.BotBuilder.DiscordDBService;
import static org.solarframework.discord.core.BotBuilder.DiscordAccount;

@Entity
@Table(name = "discord_roleinfo")
@IdClass(ActionServerID.class)
public class Discord_RoleInfo extends DatabaseObject<Discord_RoleInfo> {
    @ManyToOne
    @JoinColumn(referencedColumnName = "ID", name = "ServerID", nullable = false, insertable = false, updatable = false)
    private Discord_GuildInfo DGI;

    private transient Guild G;
    private transient Role R;

    @Id
    @Column(name = "Action", length = 32, nullable = false)
    private String Action;
    @Id
    @Column(name = "ServerID", nullable = false)
    private Long ServerID;

    @Column(name = "RoleID", nullable = false)
    private Long RoleID;

    @Column(name = "EmojiID")
    private Long EmojiID;

    protected Discord_RoleInfo() {}
    protected Discord_RoleInfo(Guild G, Role role, String action) {
        this.G = G;
        this.R = role;
        this.ServerID = G.getIdLong();
        this.Action = action;
        this.RoleID = role != null ? role.getIdLong() : null;
        if (role != null) Upsert();
        else if (DiscordDBService.getWhere(Discord_RoleInfo.class, "ServerID = ? AND Action = ?", ServerID, action).orElse(null) instanceof Discord_RoleInfo RI) RI.Delete();
    }

    public Long getServerID() {
        return ServerID;
    }
    public void setServerID(Long serverID) {
        ServerID = serverID;
    }

    public Long getRoleID() {
        return RoleID;
    }
    public void setRoleID(Long roleID) {
        RoleID = roleID;
    }

    public Long getEmojiID() {
        return EmojiID;
    }
    public void setEmojiID(Long emojiID) {
        EmojiID = emojiID;
    }

    public String getAction() {
        return Action;
    }
    public void setAction(String action) {
        Action = action;
    }

    public Role getRole() {
        try {
            return R == null ? R = getGuild().getRoleById(RoleID) : R;
        } catch (Exception e) {
            return null;
        }
    }
    private Guild getGuild() {
        return G == null ? G = DiscordAccount.getGuildById(ServerID) : G;
    }

    public Discord_BotEmoji getEmoji() {
        return retrieveEntityServiceFor(Discord_BotEmoji.class).getById(EmojiID).orElse(null);
    }
    public void setEmoji(Discord_BotEmoji emoji) {
        EmojiID = emoji != null ? emoji.getID() : null;
    }

    public String getAsMention() {
        return getRole() != null ? getRole().getAsMention() : null;
    }
}