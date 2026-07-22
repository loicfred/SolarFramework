package org.solarframework.discord.obj;

import jakarta.persistence.*;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import org.solarframework.db.spring.DatabaseObject;

import java.util.List;
import java.util.Optional;

import static org.solarframework.db.spring.DatabaseRegistry.DefaultDBService;
import static org.solarframework.db.spring.DatabaseRegistry.SolarDBManager;
import static org.solarframework.discord.utils.UserUtils.getUserByID;

@Entity
@Table(name = "discord_profile")
public class Discord_Profile extends DatabaseObject.ID_OBJ<Long, Discord_Profile> {
    private transient User User;

    @OneToMany(mappedBy = "DP", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Discord_ProfileVariable> Variables;

    private transient Object extender;

    @Column(name = "Name", nullable = false, length = 32)
    private String name;

    @Column(name = "EffectiveName", length = 64)
    private String effectiveName;

    @Column(name = "GlobalName", length = 32)
    private String globalName;

    @Column(name = "Discriminator", length = 8)
    private String discriminator;

    @Column(name = "AvatarUrl", length = 512)
    private String avatarUrl;

    @Column(name = "EffectiveAvatarUrl", length = 512)
    private String effectiveAvatarUrl;

    @Column(name = "AsTag", length = 64)
    private String asTag;


    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    public String getGlobalName() {
        return globalName;
    }
    public void setGlobalName(String globalName) {
        this.globalName = globalName;
    }

    public String getEffectiveName() {
        return effectiveName;
    }
    public void setEffectiveName(String effectiveName) {
        this.effectiveName = effectiveName;
    }

    public String getDiscriminator() {
        return discriminator;
    }
    public void setDiscriminator(String discriminator) {
        this.discriminator = discriminator;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }
    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getEffectiveAvatarUrl() {
        return effectiveAvatarUrl;
    }
    public void setEffectiveAvatarUrl(String effectiveAvatarUrl) {
        this.effectiveAvatarUrl = effectiveAvatarUrl;
    }

    public String getAsTag() {
        return asTag;
    }
    public void setAsTag(String asTag) {
        this.asTag = asTag;
    }

    public String getAsMention() {
        return "<@" + getID() + ">";
    }

    public EmbedBuilder makeProfileEmbed() {
        EmbedBuilder E = new EmbedBuilder();
        E.setAuthor(" • " + getEffectiveName(), null, getEffectiveAvatarUrl());
        E.setThumbnail(getEffectiveAvatarUrl());
        return E;
    }

    public User getUser() {
        return User == null ? User = getUserByID(getID()) : User;
    }
    public List<Discord_ProfileVariable> getVariables() {
        return SolarDBManager.getAllWhere(Discord_ProfileVariable.class, "UserID = ?", getID());
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
        getVariables().add(new Discord_ProfileVariable(getID(), name, value));
    }
    public Discord_ProfileVariable getVariable(String name) {
        return getVariables().stream().filter(V -> V.getName().equalsIgnoreCase(name)).findFirst().orElseGet(() -> new Discord_ProfileVariable(getID(), name, null));
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

    public Discord_Profile() {}
    public Discord_Profile(User user) {
        this.User = user;
        if (this.User == null) throw new NullPointerException("No User with given id.");
        refreshUserData();
    }
    public Discord_Profile(long userId) {
        this.User = getUserByID(userId);
        if (this.User == null) throw new NullPointerException("No User with given id.");
        refreshUserData();
    }

    public void refreshUserData() {
        try {
            setID(getUser().getIdLong());
            setName(getUser().getName());
            setEffectiveName(getUser().getEffectiveName());
            setGlobalName(getUser().getGlobalName());
            setDiscriminator(getUser().getDiscriminator());
            setAvatarUrl(getUser().getAvatarUrl());
            setEffectiveAvatarUrl(getUser().getEffectiveAvatarUrl());
            setAsTag(getUser().getAsTag());
        } catch (Exception ignored) {}
        Upsert();
    }

    public static List<Discord_Profile> list() {
        return DefaultDBService.getAll(Discord_Profile.class);
    }
}
