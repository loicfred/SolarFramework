package org.solarframework.discord.utils;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.requests.restaction.CacheRestAction;
import net.dv8tion.jda.api.utils.ImageProxy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.Formatter;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.solarframework.discord.core.BotBuilder.DiscordAccount;
import static org.solarframework.core.util.NumberUtils.takeOnlyDigits;

public class UserUtils {

    public static void getUserByID(Long userId, Consumer<User> user) {
        getUserByID(String.valueOf(userId), user);
    }
    public static void getUserByID(String userId, Consumer<User> user) {
        DiscordAccount.retrieveUserById(takeOnlyDigits(userId)).queue(user);
    }

    public static User getUserByID(Long userId) {
       return getUserByID(String.valueOf(userId));
    }
    public static User getUserByID(String userId) {
        try {
            return DiscordAccount.retrieveUserById(takeOnlyDigits(userId)).submit().orTimeout(5, TimeUnit.SECONDS).get();
        } catch (Exception ex) {
            return new User() {
                @Override
                public @NotNull String getName() {
                    return "???";
                }

                @Override
                public @Nullable String getGlobalName() {
                    return "???";
                }

                @Override
                public @NotNull String getEffectiveName() {
                    return User.super.getEffectiveName();
                }

                @Override
                public @NotNull String getDiscriminator() {
                    return "???";
                }

                @Override
                public @Nullable String getAvatarId() {
                    return "";
                }

                @Override
                public @Nullable String getAvatarUrl() {
                    return User.super.getAvatarUrl();
                }

                @Override
                public @Nullable ImageProxy getAvatar() {
                    return User.super.getAvatar();
                }

                @Override
                public @NotNull String getEffectiveAvatarUrl() {
                    return User.super.getEffectiveAvatarUrl();
                }

                @Override
                public @NotNull ImageProxy getEffectiveAvatar() {
                    return User.super.getEffectiveAvatar();
                }

                @Override
                public @NotNull CacheRestAction<Profile> retrieveProfile() {
                    return null;
                }

                @Override
                public @NotNull String getAsTag() {
                    return "";
                }

                @Override
                public boolean hasPrivateChannel() {
                    return false;
                }

                @Override
                public @NotNull CacheRestAction<PrivateChannel> openPrivateChannel() {
                    return null;
                }

                @Override
                public @NotNull @Unmodifiable List<Guild> getMutualGuilds() {
                    return List.of();
                }

                @Override
                public boolean isBot() {
                    return false;
                }

                @Override
                public boolean isSystem() {
                    return false;
                }

                @Override
                public @NotNull JDA getJDA() {
                    return null;
                }

                @Override
                public @NotNull EnumSet<UserFlag> getFlags() {
                    return null;
                }

                @Override
                public int getFlagsRaw() {
                    return 0;
                }

                @Override
                public @Nullable PrimaryGuild getPrimaryGuild() {
                    return null;
                }

                @Override
                public @NotNull String getDefaultAvatarId() {
                    return "";
                }

                @Override
                public @NotNull String getDefaultAvatarUrl() {
                    return User.super.getDefaultAvatarUrl();
                }

                @Override
                public @NotNull ImageProxy getDefaultAvatar() {
                    return User.super.getDefaultAvatar();
                }

                @Override
                public @NotNull String getAsMention() {
                    return "<@974675718975946853>";
                }

                @Override
                public void formatTo(Formatter formatter, int flags, int width, int precision) {
                    User.super.formatTo(formatter, flags, width, precision);
                }

                @Override
                public @NotNull String getId() {
                    return User.super.getId();
                }

                @Override
                public long getIdLong() {
                    return 974675718975946853L;
                }

                @Override
                public @NotNull OffsetDateTime getTimeCreated() {
                    return User.super.getTimeCreated();
                }
            };
        }
    }

}
