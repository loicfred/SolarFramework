package org.solarframework.discord.obj;

import iecompbot.Config;
import net.dv8tion.jda.api.entities.emoji.ApplicationEmoji;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import org.jetbrains.annotations.NotNull;
import org.solarframework.db.api.DatabaseObject;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.solarframework.discord.core.BotBuilder.DiscordDBService;
import static org.solarframework.discord.core.BotBuilder.*;

public class Discord_BotEmoji extends DatabaseObject.ID_OBJ<Long, Discord_BotEmoji> {
    public static final Emoji EmptyEmoji = Emoji.fromUnicode("U+25AA");
    private transient Emoji emoji;

    public Long ServerID = null;
    public String Name;
    public String Formatted;

    protected Discord_BotEmoji() {}
    public Discord_BotEmoji(ApplicationEmoji emoji) {
        Name = emoji.getName();
        ID = emoji.getIdLong();
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

    public static Discord_BotEmoji get(@NotNull String emojiFormatted, Long serverID) {
        if (Config.isUsingTestBot && serverID == null && !emojiFormatted.toLowerCase().contains("u+")) return DiscordDBService.getById(BotEmoji.class, TestBotEmpty).orElse(null);
        Discord_BotEmoji E = DiscordDBService.getWhere(BotEmoji.class, "(ID = ? OR Name = ? OR Formatted = ?) AND ServerID = ?", emojiFormatted, emojiFormatted, emojiFormatted, serverID).orElse(null);
        if (E != null && E.Name.toLowerCase().startsWith("u+")) return E;
        else if (E != null && E.ServerID != null && DiscordAccount.getGuildById(E.ServerID) != null) return E;
        else if (E == null && Config.isUsingTestBot) return DiscordDBService.getById(BotEmoji.class, TestBotEmpty).orElse(null);
        else if (E == null) return DiscordDBService.getById(BotEmoji.class, NormalBotEmpty).orElse(null);
        else if (Objects.equals(E.ID, TestBotEmpty) && !Config.isUsingTestBot) return DiscordDBService.getById(BotEmoji.class, NormalBotEmpty).orElse(null);
        else if (Objects.equals(E.ID, NormalBotEmpty) && Config.isUsingTestBot) return DiscordDBService.getById(BotEmoji.class, TestBotEmpty).orElse(null);
        return E;
    }
    public static Discord_BotEmoji get(long emojiId) {
        return get(String.valueOf(emojiId), null);
    }
    public static Discord_BotEmoji get(String emojiFormatted) {
        return get(emojiFormatted, null);
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

    public String getFormatted() {
        return Formatted;
    }

    @Override
    public String toString() {
        return getFormatted();
    }
}
