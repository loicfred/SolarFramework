package org.solarframework.discord.core;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.solarframework.db.api.IDatabaseService;

import java.util.List;
import java.util.function.Supplier;

public class BotBuilder {
    private static final Logger log = LoggerFactory.getLogger(BotBuilder.class);

    protected static Supplier<Void> onReady = () -> null;

    public static JDA DiscordAccount;
    public static Guild BotGuild;
    public static StandardGuildMessageChannel TemporaryFilesChannel;
    public static StandardGuildMessageChannel LogChannel;
    public static boolean IsTestMode = false;
    protected static List<ClassLoader> classLoaders = List.of(Thread.currentThread().getContextClassLoader());

    private final String token;
    private final String commandPackage;

    private Long BotGuildID;
    private Long TemporaryFilesChannelID;
    private Long LogChannelID;
    private Supplier<Void> AfterReadyAction;

    public BotBuilder(String token, String commandPackage) {
        this.token = token;
        this.commandPackage = commandPackage;
    }
    public BotBuilder(String token, String commandPackage, boolean testMode) {
        this.token = token;
        this.commandPackage = commandPackage;
        IsTestMode = testMode;
    }

    public JDA build() {
        DiscordAccount = JDABuilder.createDefault(token).setStatus(OnlineStatus.ONLINE).
                setChunkingFilter(ChunkingFilter.ALL).
                setMemberCachePolicy(MemberCachePolicy.ALL).
                enableIntents(GatewayIntent.GUILD_MEMBERS).
                enableIntents(GatewayIntent.GUILD_MESSAGES).
                enableIntents(GatewayIntent.GUILD_MESSAGE_REACTIONS).
                enableIntents(GatewayIntent.GUILD_MODERATION).
                enableIntents(GatewayIntent.SCHEDULED_EVENTS).
                enableIntents(GatewayIntent.GUILD_EXPRESSIONS).
                setActivity(Activity.customStatus("⚙️ Rebooting, please wait a little longer...")).
                addEventListeners(new DefaultListener(commandPackage)).build();
        onReady = () -> {
            BotGuild = DiscordAccount.getGuildById(BotGuildID);
            TemporaryFilesChannel = BotGuild.getTextChannelById(TemporaryFilesChannelID);
            LogChannel = BotGuild.getTextChannelById(LogChannelID);
            AfterReadyAction.get();
            return null;
        };
        log.info("Starting bot...");
        return DiscordAccount;
    }

    public void setBotGuild(Long BotGuildID) {
        this.BotGuildID = BotGuildID;
    }
    public void setTemporaryFilesChannel(Long TemporaryFilesChannelID) {
        this.TemporaryFilesChannelID = TemporaryFilesChannelID;
    }
    public void setLogChannel(Long LogChannelID) {
        this.LogChannelID = LogChannelID;
    }
    public void setAfterReadyAction(Supplier<Void> AfterReadyAction) {
        this.AfterReadyAction = AfterReadyAction;
    }
    public void setClassLoaders(List<ClassLoader> loaders) {
        classLoaders.addAll(loaders);
    }

}
