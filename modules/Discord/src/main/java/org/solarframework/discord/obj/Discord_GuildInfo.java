package org.solarframework.discord.obj;

import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import jakarta.persistence.*;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import org.solarframework.core.lang.Nationalities;
import org.solarframework.db.spring.DatabaseObject;

import java.awt.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.solarframework.core.util.ImageUtils.getDominantColor;
import static org.solarframework.core.util.OtherUtils.getHexValue;
import static org.solarframework.discord.core.BotBuilder.DiscordDBService;
import static org.solarframework.discord.core.BotBuilder.DiscordAccount;
import static org.solarframework.discord.lang.L10N.SYSLG;

@Entity
@Table(name = "discord_guildinfo")
public class Discord_GuildInfo extends DatabaseObject.ID_OBJ<Long, Discord_GuildInfo> {
    private transient Guild Guild;

    @OneToMany
    @JoinColumn(referencedColumnName = "ID", name = "ServerID")
    private transient List<Discord_RoleInfo> Roles;
    @OneToMany
    @JoinColumn(referencedColumnName = "ID", name = "ServerID")
    private transient List<Discord_ChannelInfo> Channels;


    @Column(name = "OwnerID", nullable = false)
    private Long ownerID;

    @Column(name = "MemberCount", nullable = false)
    private Integer memberCount;

    @Column(name = "Name", nullable = false, length = 256)
    private String name;

    @Column(name = "Description", nullable = false, length = 2048)
    private String description;

    @Column(name = "Story", length = 2048)
    private String story;

    @Column(name = "DominantColorcode", nullable = false, length = 7)
    private String dominantColorcode;

    @Column(name = "InviteLink", length = 128)
    private String inviteLink;

    @Column(name = "IconUrl", nullable = false, length = 256)
    private String iconUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "Nationality", nullable = false, length = 32)
    private Nationalities nationality = Nationalities.International;

    @Column(name = "WebsiteURL", length = 256)
    private String websiteURL;

    @Column(name = "TwitterURL", length = 256)
    private String twitterURL;

    @Column(name = "TwitchURL", length = 256)
    private String twitchURL;

    @Column(name = "YouTubeURL", length = 256)
    private String youTubeURL;

    @Column(name = "InstagramURL", length = 256)
    private String instagramURL;

    @Column(name = "TiktokURL", length = 256)
    private String tiktokURL;

    @Column(name = "Public", nullable = false)
    private Boolean publicField;

    @Column(name = "Trusted", nullable = false)
    private Boolean trusted;


    public Guild getGuild() {
        return Guild == null ? Guild = DiscordAccount.getGuildById(ID) : Guild;
    }
    public List<Discord_RoleInfo> getRoles() {
        return Roles;
    }
    public List<Discord_ChannelInfo> getChannels() {
        return Channels;
    }

    public Long getOwnerID() {
        return ownerID;
    }
    public void setOwnerID(Long ownerID) {
        this.ownerID = ownerID;
    }

    public Integer getMemberCount() {
        return memberCount;
    }
    public void setMemberCount(Integer memberCount) {
        this.memberCount = memberCount;
    }

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }

    public String getStory() {
        return story;
    }
    public void setStory(String story) {
        this.story = story;
    }

    public String getDominantColorcode() {
        return dominantColorcode;
    }
    public void setDominantColorcode(String dominantColorcode) {
        this.dominantColorcode = dominantColorcode;
    }

    public String getInviteLink() {
        return inviteLink;
    }
    public void setInviteLink(String inviteLink) {
        this.inviteLink = inviteLink;
    }

    public String getIconUrl() {
        return iconUrl;
    }
    public void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
    }

    public Nationalities getNationality() {
        return nationality;
    }
    public void setNationality(Nationalities nationality) {
        this.nationality = nationality;
    }

    public String getWebsiteURL() {
        return websiteURL;
    }
    public void setWebsiteURL(String websiteURL) {
        this.websiteURL = websiteURL;
    }

    public String getTwitterURL() {
        return twitterURL;
    }
    public void setTwitterURL(String twitterURL) {
        this.twitterURL = twitterURL;
    }

    public String getTwitchURL() {
        return twitchURL;
    }
    public void setTwitchURL(String twitchURL) {
        this.twitchURL = twitchURL;
    }

    public String getYouTubeURL() {
        return youTubeURL;
    }
    public void setYouTubeURL(String youTubeURL) {
        this.youTubeURL = youTubeURL;
    }

    public String getInstagramURL() {
        return instagramURL;
    }
    public void setInstagramURL(String instagramURL) {
        this.instagramURL = instagramURL;
    }

    public String getTiktokURL() {
        return tiktokURL;
    }
    public void setTiktokURL(String tiktokURL) {
        this.tiktokURL = tiktokURL;
    }

    public Boolean getPublic() {
        return publicField;
    }
    public void setPublic(Boolean publicField) {
        this.publicField = publicField;
    }

    public Boolean getTrusted() {
        return trusted;
    }
    public void setTrusted(Boolean trusted) {
        this.trusted = trusted;
    }

    public Color getColor() {
        try {
            return Color.decode(getDominantColorcode());
        } catch (Exception e) {
            return Color.GRAY;
        }
    }

    public EmbedBuilder makeServerEmbed() {
        EmbedBuilder E = new EmbedBuilder();
        E.setAuthor(" • " + getGuild().getName(), null, getGuild().getIconUrl());
        E.setThumbnail(getGuild().getIconUrl());
        E.setColor(getColor());
        return E;
    }
    public WebhookMessageBuilder makeServedWebhook() {
        return new WebhookMessageBuilder().setUsername(getGuild().getName()).setAvatarUrl(getGuild().getIconUrl());
    }


    public Discord_GuildInfo() {}
    public Discord_GuildInfo(Guild guild) {
        this.Guild = guild;
        if (this.Guild == null || getGuild().isDetached()) throw new NullPointerException("No Guild with given id.");
        refreshGuildData();
        Write();
    }
    public Discord_GuildInfo(long serverId) {
        this.Guild = DiscordAccount.getGuildById(serverId);
        if (this.Guild == null || getGuild().isDetached()) throw new NullPointerException("No Guild with given id.");
        refreshGuildData();
        Write();
    }

    public void refreshGuildData() {
        try {
            setID(getGuild().getIdLong());
            setName(getGuild().getName());
            setOwnerID(getGuild().getOwnerIdLong());
            setMemberCount(getGuild().getMemberCount());
            setDescription(getGuild().getDescription());
            setIconUrl(getGuild().getIconUrl());
            if (getGuild().getFeatures().contains("COMMUNITY") && getNationality() == Nationalities.International) setNationality(Nationalities.get(getGuild().getLocale().getLanguageName()));
            if (getGuild().getVanityCode() != null) setInviteLink("https://discord.gg/" + getGuild().getVanityCode());
            setDominantColorcode(getHexValue(getDominantColor(getGuild().getIconUrl())));
        } catch (Exception ignored) {}
        Update();
    }

    public void LogGuild(String string) {
        if (getLogChannel() != null && getLogChannel().canTalk()) getLogChannel().sendMessage(string).queue();
        else new Discord_ChannelInfo(getGuild(), null, "LOG");
    }

    public Discord_RoleInfo getUsageRole(String action) {
        return DiscordDBService.getWhere(Discord_RoleInfo.class, "ServerID = ? AND Action LIKE ?", getID(), action).orElse(null);
    }
    public Discord_ChannelInfo getUsageChannel(String action) {
        return DiscordDBService.getWhere(Discord_ChannelInfo.class, "ServerID = ? AND Action LIKE ?", getID(), action).orElse(null);
    }
    public Discord_ChannelInfo getLogChannel() {
        return getUsageChannel("LOG");
    }

    public synchronized Discord_RoleInfo createRole(String name, Discord_BotEmoji emoji, String action, Color color, Icon icon) {
        try {
            if (getGuild().getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
                Discord_RoleInfo DRI;
                if (getGuild().getRolesByName(name, false).isEmpty()) {
                    Role newrole = getGuild().createRole()
                            .setName(name)
                            .setColor(color)
                            .setHoisted(true)
                            .submit().orTimeout(10, TimeUnit.SECONDS).get();
                    LogGuild(SYSLG(getGuild(),"role-create", emoji + " **__" + name + "__**"));
                    DRI = new Discord_RoleInfo(getGuild(), newrole, action);
                } else {
                    DRI = new Discord_RoleInfo(getGuild(), getGuild().getRolesByName(name, false).getFirst(), action);
                }
                setRoleIcon(DRI, icon, false);
                return DRI;
            } else {
                LogGuild(SYSLG(getGuild(),"role-create-permission-fail",  emoji + " **__" + name + "__**"));
            }
        } catch (Exception ignored) {}
        return null;
    }
    public synchronized boolean setRoleIcon(Discord_RoleInfo DRI, Icon icon, boolean replace) {
        try {
            if (icon != null && getGuild().getFeatures().contains("ROLE_ICONS")) {
                if (getGuild().getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
                    if (DRI.getRole().getIcon() == null || replace) { // if not exist, then true, and true if replace too
                        if (getGuild().getSelfMember().canInteract(DRI.getRole())) {
                            DRI.getRole().getManager().setIcon(icon).queue(_ ->  LogGuild(SYSLG(getGuild(),"role-icon-success", DRI.getEmoji() + " **__" + DRI.getRole().getName() + "__**")));
                            return true;
                        } else {
                            LogGuild(SYSLG(getGuild(),"role-icon-permission-fail", DRI.getEmoji() + " **__" + DRI.getRole().getName() + "__**"));
                        }
                    }
                } else {
                    LogGuild(SYSLG(getGuild(),"role-icon-interact-fail", DRI.getEmoji() + " **__" + DRI.getRole().getName() + "__**"));
                }
            }
        } catch (Exception ignored) {}
        return false;
    }
    public synchronized boolean setRoleColor(Discord_RoleInfo DRI, Color color) {
        try {
            if (DRI.getRole().getColors().getPrimary() == null || !getHexValue(DRI.getRole().getColors().getPrimary()).equals(getHexValue(color))) {
                if (getGuild().getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
                    if (getGuild().getSelfMember().canInteract(DRI.getRole())) {
                        DRI.getRole().getManager().setColor(color).queue(_ -> LogGuild(SYSLG(getGuild(),"role-recolor-success",DRI.getEmoji() + " **__" + DRI.getRole().getName() + "__**", "**" + getHexValue(color) + "**")));
                        return true;
                    } else {
                        LogGuild(SYSLG(getGuild(),"role-recolor-interact-fail", DRI.getEmoji() + " **__" + DRI.getRole().getName() + "__**"));
                    }
                } else {
                    LogGuild(SYSLG(getGuild(),"role-recolor-permission-fail", DRI.getEmoji() + " **__" + DRI.getRole().getName() + "__**"));
                }
            }
        } catch (Exception ignored) {}
        return false;
    }
    public synchronized boolean renameRole(Discord_RoleInfo DRI, String newname) {
        try {
            if (getGuild().getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
                if (getGuild().getSelfMember().canInteract(DRI.getRole())) {
                    String oldName = DRI.getRole().getName();
                    DRI.getRole().getManager().setName(newname).queue(_ -> LogGuild(SYSLG(getGuild(),"role-rename-success","**" + oldName + "**", DRI.getEmoji() + " **__" + newname + "__**")));
                    return true;
                } else {
                    LogGuild(SYSLG(getGuild(),"role-rename-interact-fail", DRI.getEmoji() + " **__" + DRI.getRole().getName() + "__**"));
                }
            } else {
                LogGuild(SYSLG(getGuild(),"role-rename-permission-fail", DRI.getEmoji() + " **__" + DRI.getRole().getName() + "__**"));
            }
        } catch (Exception ignored) {}
        return false;
    }
    public synchronized boolean deleteRole(Discord_RoleInfo DRI) {
        try {
            if (getGuild().getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
                if (getGuild().getSelfMember().canInteract(DRI.getRole())) {
                    DRI.getRole().delete().queue(_ ->  LogGuild(SYSLG(getGuild(),"role-delete-success",DRI.getEmoji() + " **__" + DRI.getRole().getName() + "__**")));
                    return true;
                } else {
                    LogGuild(SYSLG(getGuild(),"role-delete-interact-fail", DRI.getEmoji() + " **__" + DRI.getRole().getName() + "__**"));
                }
            } else {
                LogGuild(SYSLG(getGuild(),"role-delete-permission-fail", DRI.getEmoji() + " **__" + DRI.getRole().getName() + "__**"));
            }
        } catch (Exception ignored) {}
        return false;
    }
    public synchronized boolean AddRoleToMember(Discord_RoleInfo DRI, Member member) {
        try {
            if (member != null && member.getRoles().stream().noneMatch(R -> R.getIdLong() == DRI.getRoleID())) {
                if (getGuild().getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
                    if (getGuild().getSelfMember().canInteract(DRI.getRole())) {
                        getGuild().removeRoleFromMember(member, DRI.getRole()).queue();
                        getGuild().addRoleToMember(member, DRI.getRole()).queue(_ -> LogGuild(SYSLG(getGuild(),"role-add-success", DRI.getEmoji() + " **__" + DRI.getRole().getName() + "__**", "**" + member.getEffectiveName() + "**")));
                        return true;
                    } else {
                        LogGuild(SYSLG(getGuild(),"role-add-interact-fail", DRI.getEmoji() + " **__" + DRI.getRole().getName() + "__**", "**" + member.getEffectiveName() + "**"));
                    }
                } else {
                    LogGuild(SYSLG(getGuild(),"role-add-permission-fail", DRI.getEmoji() + " **__" + DRI.getRole().getName() + "__**", "**" + member.getEffectiveName() + "**"));
                }
            }
        } catch (Exception ignored) {}
        return false;
    }
    public synchronized boolean RemoveRoleFromMember(Discord_RoleInfo DRI, Member member) {
        try {
            if (member.getRoles().stream().anyMatch(R -> R.getIdLong() == DRI.getRoleID())) {
                if (getGuild().getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
                    if (getGuild().getSelfMember().canInteract(DRI.getRole())) {
                        getGuild().addRoleToMember(member, DRI.getRole()).queue();
                        getGuild().removeRoleFromMember(member, DRI.getRole()).queue(_ -> LogGuild(SYSLG(getGuild(),"role-remove-success",DRI.getEmoji() + " **__" + DRI.getRole().getName() + "__**", "**" + member.getEffectiveName() + "**")));
                        return true;
                    } else {
                        LogGuild(SYSLG(getGuild(),"role-remove-interact-fail", DRI.getEmoji() + " **__" + DRI.getRole().getName() + "__**", "**" + member.getEffectiveName() + "**"));
                    }
                } else {
                    LogGuild(SYSLG(getGuild(),"role-remove-permission-fail", DRI.getEmoji() + " **__" + DRI.getRole().getName() + "__**", "**" + member.getEffectiveName() + "**"));
                }
            }
        } catch (Exception ignored) {}
        return false;
    }
}
