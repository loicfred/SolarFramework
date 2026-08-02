package org.solarframework.discord.obj;

import jakarta.persistence.*;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import org.solarframework.db.api.Lazy;
import org.solarframework.db.spring.DatabaseObject;

import java.util.ArrayList;
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
    /**
     * Fetched once per profile instance and held in the mapped field, not once per variable: a profile card
     * reads ~20 names, and every {@code getVariableAsX} used to be its own full SELECT of this user's whole
     * variable set. The {@link Lazy} guard is what makes reusing the association safe — Hibernate hydrates it
     * with a PersistentBag that {@code != null} never catches and that throws on first read once the
     * EntityManager is closed, while an instance built with {@code new} leaves it genuinely null.
     */
    public List<Discord_ProfileVariable> getVariables() {
        return Variables == null ? Variables = new ArrayList<>(SolarDBManager.getAllWhere(Discord_ProfileVariable.class, "UserID = ?", getID())) : Variables;
    }
    /** Drops the fetched association, for the rare caller that has to see a write made through another instance. */
    public void invalidateVariables() {
        Variables = null;
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

    /** A value equal to the stored one is not rewritten — a settings form posts every field on every save. */
    public void setVariable(String name, Object value) {
        List<Discord_ProfileVariable> L = getVariables();
        String v = value != null ? value.toString() : null;
        if (L.stream().anyMatch(V -> V.getName().equalsIgnoreCase(name) && java.util.Objects.equals(V.getValue(), v))) return;
        L.removeIf(V -> V.getName().equalsIgnoreCase(name));
        L.add(new Discord_ProfileVariable(getID(), name, v)); // the constructor is what writes it
    }
    /** A name nobody has set reads as an empty row, and is <em>not</em> written — see the 4-arg constructor. */
    public Discord_ProfileVariable getVariable(String name) {
        return getVariables().stream().filter(V -> V.getName().equalsIgnoreCase(name)).findFirst().orElseGet(() -> new Discord_ProfileVariable(getID(), name, null, false));
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
