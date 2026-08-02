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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class BotBuilder {
    private static final Logger log = LoggerFactory.getLogger(BotBuilder.class);

    protected static Supplier<Void> onReady = () -> null;

    public static JDA DiscordAccount;
    public static Guild BotGuild;
    public static StandardGuildMessageChannel TemporaryFilesChannel;
    public static StandardGuildMessageChannel LogChannel;
    public static boolean IsTestMode = false;
    protected static List<ClassLoader> classLoaders = new ArrayList<>(List.of(Thread.currentThread().getContextClassLoader(), CMD.class.getClassLoader()));

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

    /** Daemon threads, named so they are identifiable in a stack trace or thread dump. */
    private static ThreadFactory namedThreads(String name) {
        AtomicInteger n = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, name + "-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }

    public JDA build() {
        DiscordAccount = JDABuilder.createDefault(token).setStatus(OnlineStatus.ONLINE).
                // Without an event pool JDA runs listeners on the gateway read thread, so a slow command stops
                // HEARTBEAT_ACK from being read and the connection is dropped as unresponsive. One thread keeps
                // events serialized exactly as they were before, while freeing the socket to keep reading.
                setEventPool(Executors.newSingleThreadExecutor(namedThreads("JDA-Events")), true).
                // Callbacks default to ForkJoinPool.commonPool, which assumes CPU-bound work; ours block on REST
                // and JDBC, which starves work-stealing and contends with every parallel stream in the JVM.
                setCallbackPool(Executors.newFixedThreadPool(32, namedThreads("JDA-Callbacks")), true).
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
    public void addClassLoaders(List<ClassLoader> loaders) {
        classLoaders.addAll(loaders);
    }

}
