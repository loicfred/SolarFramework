package org.solarframework.discord.obj;

import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import jakarta.persistence.*;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;
import org.solarframework.core.lang.Nationalities;
import org.solarframework.db.spring.DatabaseObject;

import java.awt.*;
import java.util.List;

import static org.solarframework.core.util.ImageUtils.getDominantColor;
import static org.solarframework.core.util.OtherUtils.getHexValue;
import static org.solarframework.db.spring.DatabaseService.dbService;
import static org.solarframework.discord.core.BotBuilder.DiscordAccount;
import static org.solarframework.discord.core.DefaultListener.LogCommand;
import static org.solarframework.discord.utils.UserUtils.getUserByID;

@Table(name = "discord_guildinfo")
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class Discord_GuildInfo extends DatabaseObject.ID_OBJ<Long, Discord_GuildInfo> {
    private transient Guild Guild;

    @OneToMany
    @JoinColumn(referencedColumnName = "ID", name = "ServerID")
    private transient List<Discord_RoleInfo> Roles;
    @OneToMany
    @JoinColumn(referencedColumnName = "ID", name = "ServerID")
    private transient List<Discord_ChannelInfo> Channels;

    public long OwnerID;
    public int MemberCount = 0;
    public String Name;
    public String Description;
    public String Story;
    public String DominantColorcode;
    public String InviteLink;
    public String IconUrl;
    public Nationalities Nationality;
    public String WebsiteURL;
    public String TwitterURL;
    public String TwitchURL;
    public String YouTubeURL;
    public String InstagramURL;
    public String TiktokURL;
    public boolean Public;
    public boolean Trusted;

    public Guild getGuild() {
        return Guild == null ? Guild = DiscordAccount.getGuildById(ID) : Guild;
    }
    public List<Discord_RoleInfo> getRoles() {
        return Roles;
    }
    public List<Discord_ChannelInfo> getChannels() {
        return Channels;
    }

    public long getOwnerID() {
        return OwnerID;
    }
    public int getMemberCount() {
        return MemberCount;
    }
    public String getName() {
        return Name;
    }
    public String getDescription() {
        return Description;
    }
    public String getStory() {
        return Story;
    }
    public String getDominantColorcode() {
        return DominantColorcode;
    }
    public String getInviteLink() {
        return InviteLink;
    }
    public String getIconUrl() {
        return IconUrl;
    }
    public Nationalities getNationality() {
        return Nationality;
    }
    public String getWebsiteURL() {
        return WebsiteURL;
    }
    public String getTwitterURL() {
        return TwitterURL;
    }
    public String getTwitchURL() {
        return TwitchURL;
    }
    public String getYouTubeURL() {
        return YouTubeURL;
    }
    public String getInstagramURL() {
        return InstagramURL;
    }
    public String getTiktokURL() {
        return TiktokURL;
    }
    public boolean isPublic() {
        return Public;
    }
    public boolean isTrusted() {
        return Trusted;
    }

    public Color getColor() {
        return Color.decode(DominantColorcode);
    }

    public void setOwnerID(long ownerID) {
        OwnerID = ownerID;
    }
    public void setMemberCount(int memberCount) {
        MemberCount = memberCount;
    }
    public void setName(String name) {
        Name = name;
    }
    public void setDescription(String description) {
        Description = description;
    }
    public void setStory(String story) {
        Story = story;
    }
    public void setDominantColorcode(String dominantColorcode) {
        DominantColorcode = dominantColorcode;
    }
    public void setInviteLink(String inviteLink) {
        InviteLink = inviteLink;
    }
    public void setIconUrl(String iconUrl) {
        IconUrl = iconUrl;
    }
    public void setNationality(Nationalities nationality) {
        Nationality = nationality;
    }
    public void setWebsiteURL(String websiteURL) {
        WebsiteURL = websiteURL;
    }
    public void setTwitterURL(String twitterURL) {
        TwitterURL = twitterURL;
    }
    public void setTwitchURL(String twitchURL) {
        TwitchURL = twitchURL;
    }
    public void setYouTubeURL(String youTubeURL) {
        YouTubeURL = youTubeURL;
    }
    public void setInstagramURL(String instagramURL) {
        InstagramURL = instagramURL;
    }
    public void setTiktokURL(String tiktokURL) {
        TiktokURL = tiktokURL;
    }
    public void setPublic(boolean aPublic) {
        Public = aPublic;
    }
    public void setTrusted(boolean trusted) {
        Trusted = trusted;
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


    protected Discord_GuildInfo() {}
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
            if (getGuild().getFeatures().contains("COMMUNITY") && Nationality == Nationalities.International) setNationality(Nationalities.get(getGuild().getLocale().getLanguageName()));
            if (getGuild().getVanityCode() != null) setInviteLink("https://discord.gg/" + getGuild().getVanityCode());
            setDominantColorcode(getHexValue(getDominantColor(getGuild().getIconUrl())));
        } catch (Exception ignored) {}
        Update();
    }

    public void LogGuild(String string) {
        if (getLogChannel() != null && getLogChannel().canTalk()) getLogChannel().sendMessage(string).queue();
        else new Discord_ChannelInfo(getGuild(), null, "LOG");
    }


    public Discord_ChannelInfo getUsageChannel(String action) {
        return dbService.getWhere(Discord_ChannelInfo.class, "ServerID = ? AND Action LIKE ?", getID(), action).orElse(null);
    }
    public Discord_ChannelInfo getLogChannel() {
        return getUsageChannel("LOG");
    }


}
