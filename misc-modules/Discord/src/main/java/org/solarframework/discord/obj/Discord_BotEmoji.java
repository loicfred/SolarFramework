package org.solarframework.discord.obj;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import net.dv8tion.jda.api.entities.emoji.ApplicationEmoji;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import org.solarframework.db.spring.DatabaseObject;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.solarframework.discord.core.BotBuilder.*;

@Entity
@Table(name = "discord_botemoji")
public class Discord_BotEmoji extends DatabaseObject.ID_OBJ<Long, Discord_BotEmoji> {
    private static final Emoji EmptyEmoji = Emoji.fromUnicode("U+25AA");
    private transient Emoji emoji;

    @Column(name = "Name", nullable = false)
    public String Name;
    @Column(name = "Formatted", nullable = false)
    public String Formatted;
    @Column(name = "ServerID")
    public Long ServerID = null;

    protected Discord_BotEmoji() {}
    public Discord_BotEmoji(ApplicationEmoji emoji) {
        ID = emoji.getIdLong();
        Name = emoji.getName();
        Formatted = emoji.getAsMention();
        if (!IsTestMode) Write();
    }
    public Discord_BotEmoji(CustomEmoji emoji, Long serverID) {
        ID = emoji.getIdLong();
        Name = emoji.getName();
        Formatted = emoji.getAsMention();
        ServerID = serverID;
        Write();
    }
    public Discord_BotEmoji(String unicode) {
        if (unicode.toLowerCase().startsWith("u+")) {
            ID = Instant.now().toEpochMilli();
            Name = unicode;
            Formatted = Emoji.fromUnicode(unicode).getFormatted();
            Write();
        }
    }

    public static Discord_BotEmoji getEmpty() {
        return new Discord_BotEmoji();
    }
    public static Discord_BotEmoji getById(long emojiId) {
        if (IsTestMode) return retrieveEntityServiceFor(Discord_BotEmoji.class).getWhere("Name LIKE ?", "U+25AA").orElseGet(() -> new Discord_BotEmoji("U+25AA"));
        return retrieveEntityServiceFor(Discord_BotEmoji.class).getWhere("ID = ?", emojiId).orElseGet(Discord_BotEmoji::new);
    }
    public static Discord_BotEmoji getByName(String name) {
        if (IsTestMode) return retrieveEntityServiceFor(Discord_BotEmoji.class).getWhere("Name LIKE ?", "U+25AA").orElseGet(() -> new Discord_BotEmoji("U+25AA"));
        return retrieveEntityServiceFor(Discord_BotEmoji.class).getWhere("Name = ?", name).orElseGet(Discord_BotEmoji::new);
    }
    public static Discord_BotEmoji getByFormatted(String formatted) {
        if (IsTestMode) return retrieveEntityServiceFor(Discord_BotEmoji.class).getWhere("Name LIKE ?", "U+25AA").orElseGet(() -> new Discord_BotEmoji("U+25AA"));
        return retrieveEntityServiceFor(Discord_BotEmoji.class).getWhere("Formatted = ?", formatted).orElseGet(Discord_BotEmoji::new);
    }

    public Emoji retrieve() {
        if (emoji == null) {
            try {
                if (Name.toLowerCase().startsWith("u+")) emoji = Emoji.fromUnicode(Name);
                else if (ServerID != null) emoji = DiscordAccount.getGuildById(ServerID).getEmojiById(ID);
                else emoji = DiscordAccount.retrieveApplicationEmojiById(ID).submit().orTimeout(5, TimeUnit.SECONDS).get();
            } catch (Exception e) {
                emoji = EmptyEmoji;
            }
        } return emoji;
    }

    public String getName() {
        return Name;
    }
    public void setName(String name) {
        Name = name;
    }

    public String getFormatted() {
        return Formatted;
    }
    public void setFormatted(String formatted) {
        Formatted = formatted;
    }

    public Long getServerID() {
        return ServerID;
    }
    public void setServerID(Long serverID) {
        ServerID = serverID;
    }

    @Override
    public String toString() {
        return getFormatted();
    }
}
