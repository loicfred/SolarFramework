package org.solarframework.discord.obj;

import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import jakarta.persistence.*;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import org.solarframework.lang.Nationalities;
import org.solarframework.db.spring.DatabaseObject;

import java.awt.*;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.solarframework.core.util.ImageUtils.getDominantColor;
import static org.solarframework.core.util.OtherUtils.getHexValue;
import static org.solarframework.db.spring.DatabaseRegistry.DefaultDBService;
import static org.solarframework.db.spring.DatabaseRegistry.SolarDBManager;
import static org.solarframework.discord.core.BotBuilder.DiscordAccount;
import static org.solarframework.discord.lang.L10N.SYSLG;

@Entity
@Table(name = "discord_guildinfo")
public class Discord_GuildInfo extends DatabaseObject.ID_OBJ<Long, Discord_GuildInfo> {
    private transient Guild Guild;

    @OneToMany(mappedBy = "DGI", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Discord_RoleInfo> Roles;
    @OneToMany(mappedBy = "DGI", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Discord_ChannelInfo> Channels;
    @OneToMany(mappedBy = "DGI", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Discord_MessageInfo> Messages;
    @OneToMany(mappedBy = "DGI", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Discord_GuildVariable> Variables;

    private transient Object extender;

    @Column(name = "OwnerID", nullable = false)
    private Long ownerID;

    @Column(name = "MemberCount", nullable = false)
    private Integer memberCount;

    @Column(name = "Name", nullable = false, length = 256)
    private String name;

    @Column(name = "Description", length = 2048)
    private String description;
    
    @Column(name = "DominantColorcode", nullable = false, length = 7)
    private String dominantColorcode = "#808080";

    @Column(name = "InviteLink", length = 128)
    private String inviteLink;

    @Column(name = "IconUrl", length = 512)
    private String iconUrl;
    
    @Convert(converter = Nationality.class)
    @Column(name = "Nationality", length = 40, nullable = false)
    private Nationalities nationality = Nationalities.International;
    @Converter
    public static class Nationality implements AttributeConverter<Nationalities, String> {
        @Override
        public String convertToDatabaseColumn(Nationalities type) {
            return type == null ? null : type.name();
        }
        @Override
        public Nationalities convertToEntityAttribute(String name) {return name == null ? null : Nationalities.valueOf(name);}
    }

    @Column(name = "BannerUrl", length = 512)
    private String bannerUrl;

    @Column(name = "BoostTier")
    private Integer boostTier;

    @Column(name = "BoostCount")
    private Integer boostCount;

    @Column(name = "VerificationLevel", length = 32)
    private String verificationLevel;

    @Column(name = "NsfwLevel", length = 32)
    private String nsfwLevel;

    @Column(name = "ExplicitContentFilter", length = 32)
    private String explicitContentFilter;

    @Column(name = "MfaLevel", length = 32)
    private String mfaLevel;

    @Column(name = "SystemChannelID")
    private Long systemChannelID;

    @Column(name = "RulesChannelID")
    private Long rulesChannelID;

    @Column(name = "AfkChannelID")
    private Long afkChannelID;

    @Column(name = "AfkTimeout")
    private Integer afkTimeout;

    @Column(name = "PremiumTier")
    private Integer premiumTier;


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
        this.nationality = nationality == null ? Nationalities.International : nationality;
    }

    public String getBannerUrl() {
        return bannerUrl;
    }
    public void setBannerUrl(String bannerUrl) {
        this.bannerUrl = bannerUrl;
    }

    public Integer getBoostTier() {
        return boostTier;
    }
    public void setBoostTier(Integer boostTier) {
        this.boostTier = boostTier;
    }

    public Integer getBoostCount() {
        return boostCount;
    }
    public void setBoostCount(Integer boostCount) {
        this.boostCount = boostCount;
    }

    public String getVerificationLevel() {
        return verificationLevel;
    }
    public void setVerificationLevel(String verificationLevel) {
        this.verificationLevel = verificationLevel;
    }

    public String getNsfwLevel() {
        return nsfwLevel;
    }
    public void setNsfwLevel(String nsfwLevel) {
        this.nsfwLevel = nsfwLevel;
    }

    public String getExplicitContentFilter() {
        return explicitContentFilter;
    }
    public void setExplicitContentFilter(String explicitContentFilter) {
        this.explicitContentFilter = explicitContentFilter;
    }

    public String getMfaLevel() {
        return mfaLevel;
    }
    public void setMfaLevel(String mfaLevel) {
        this.mfaLevel = mfaLevel;
    }

    public Long getSystemChannelID() {
        return systemChannelID;
    }
    public void setSystemChannelID(Long systemChannelID) {
        this.systemChannelID = systemChannelID;
    }

    public Long getRulesChannelID() {
        return rulesChannelID;
    }
    public void setRulesChannelID(Long rulesChannelID) {
        this.rulesChannelID = rulesChannelID;
    }

    public Long getAfkChannelID() {
        return afkChannelID;
    }
    public void setAfkChannelID(Long afkChannelID) {
        this.afkChannelID = afkChannelID;
    }

    public Integer getAfkTimeout() {
        return afkTimeout;
    }
    public void setAfkTimeout(Integer afkTimeout) {
        this.afkTimeout = afkTimeout;
    }

    public Integer getPremiumTier() {
        return premiumTier;
    }
    public void setPremiumTier(Integer premiumTier) {
        this.premiumTier = premiumTier;
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

    public Guild getGuild() {
        return Guild == null ? Guild = DiscordAccount.getGuildById(ID) : Guild;
    }
    public List<Discord_RoleInfo> getRoles() {
        return Roles != null ? Roles = SolarDBManager.getAllWhere(Discord_RoleInfo.class, "ServerID = ?", getID()) : Roles;
    }
    public List<Discord_ChannelInfo> getChannels() {
        return Channels != null ? Channels = SolarDBManager.getAllWhere(Discord_ChannelInfo.class, "ServerID = ?", getID()) : Channels;
    }
    public List<Discord_GuildVariable> getVariables() {
        return SolarDBManager.getAllWhere(Discord_GuildVariable.class, "ServerID = ?", getID());
    }

    public Discord_GuildInfo() {}
    public Discord_GuildInfo(Guild guild) {
        this.Guild = guild;
        if (this.Guild == null || getGuild().isDetached()) throw new NullPointerException("No Guild with given id.");
        refreshGuildData();
    }
    public Discord_GuildInfo(long serverId) {
        this.Guild = DiscordAccount.getGuildById(serverId);
        if (this.Guild == null || getGuild().isDetached()) throw new NullPointerException("No Guild with given id.");
        refreshGuildData();
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
            setBannerUrl(getGuild().getBannerUrl());
            setBoostTier(getGuild().getBoostTier().getKey());
            setBoostCount(getGuild().getBoostCount());
            setVerificationLevel(getGuild().getVerificationLevel().name());
            setNsfwLevel(getGuild().getNSFWLevel().name());
            setExplicitContentFilter(getGuild().getExplicitContentLevel().name());
            setMfaLevel(getGuild().getRequiredMFALevel().name());
            setSystemChannelID(getGuild().getSystemChannel() != null ? getGuild().getSystemChannel().getIdLong() : null);
            setRulesChannelID(getGuild().getRulesChannel() != null ? getGuild().getRulesChannel().getIdLong() : null);
            setAfkChannelID(getGuild().getAfkChannel() != null ? getGuild().getAfkChannel().getIdLong() : null);
            setAfkTimeout(getGuild().getAfkTimeout().getSeconds());
            setPremiumTier(getGuild().getBoostTier().getKey());
        } catch (Exception ignored) {}
        Upsert();
    }

    public void LogGuild(String string) {
        if (getLogChannel().isPresent() && getLogChannel().get().canTalk()) getLogChannel().get().sendMessage(string).queue();
        else setUsageChannel("LOG", null);
    }

    public <T> T extender(Class<T> extenderClass) {
        try {
            if (extender == null) {
                try {extender = extenderClass.getDeclaredConstructor(getClass()).newInstance(this);
                } catch (Exception ignored) {
                    try {extender = extenderClass.getDeclaredConstructor().newInstance();
                    } catch (Exception ignored2) {}
                }
            }
            return (T) extender;
        } catch (Exception ignored) {
            return null;
        }
    }
    public <T> T extender(Class<T> extenderClass, T extender) {
        this.extender = extender;
        return extender(extenderClass);
    }

    public void setVariable(String name, Object value) {
        getVariables().removeIf(V -> V.getName().equalsIgnoreCase(name));
        getVariables().add(new Discord_GuildVariable(getID(), name, value));
    }
    public Discord_GuildVariable getVariable(String name) {
        return getVariables().stream().filter(V -> V.getName().equalsIgnoreCase(name)).findFirst().orElseGet(() -> new Discord_GuildVariable(getID(), name, null));
    }
    public Optional<String> getVariableAsString(String name) {
        return getVariable(name).getValueOptional();
    }
    public Optional<Boolean> getVariableAsBool(String name) {
        return getVariable(name).getAsBooleanOptional();
    }
    public Optional<Integer> getVariableAsInt(String name) {
        return getVariable(name).getAsIntOptional();
    }
    public Optional<Long> getVariableAsLong(String name) {
        return getVariable(name).getAsLongOptional();
    }
    public Optional<Double> getVariableAsDouble(String name) {
        return getVariable(name).getAsDoubleOptional();
    }

    public Optional<Discord_RoleInfo> getUsageRole(String action) {
        return getRoles().stream().filter(R -> R.getAction().equalsIgnoreCase(action)).findFirst();
    }
    public Discord_RoleInfo setUsageRole(String action, Role role) {
        getRoles().removeIf(V -> V.getAction().equalsIgnoreCase(action) && Objects.equals(V.getServerID(), getID()));
        getRoles().add(new Discord_RoleInfo(getGuild(), role, action));
        return getRoles().getLast();
    }
    
    public Optional<Discord_ChannelInfo> getUsageChannel(String action) {
        return getChannels().stream().filter(C -> C.getAction().equalsIgnoreCase(action)).findFirst();
    }
    public Discord_ChannelInfo setUsageChannel(String action, GuildChannel channel) {
        getChannels().removeIf(V -> V.getAction().equalsIgnoreCase(action) && Objects.equals(V.getServerID(), getID()));
        getChannels().add(new Discord_ChannelInfo(getGuild(), channel, action));
        return getChannels().getLast();
    }

    public Optional<Discord_ChannelInfo> getLogChannel() {
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
                    DRI = setUsageRole(action, newrole);
                } else {
                    DRI = setUsageRole(action, getGuild().getRolesByName(name, false).getFirst());
                }
                DRI.setEmoji(emoji);
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


    public static List<Discord_GuildInfo> list() {
        return DefaultDBService.getAll(Discord_GuildInfo.class);
    }
    public static List<Discord_GuildInfo> list(boolean isInGuild) {
        return list().stream().filter(s -> !isInGuild || s.getGuild() != null).collect(Collectors.toList());
    }
}
