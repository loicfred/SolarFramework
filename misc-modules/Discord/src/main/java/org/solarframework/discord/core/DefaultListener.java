package org.solarframework.discord.core;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.solarframework.core.util.ClassUtils;

import static org.solarframework.discord.core.BotBuilder.*;
import static org.solarframework.discord.core.BotBuilder.LogChannel;

public class DefaultListener extends ListenerAdapter {
    private final static Logger log = LoggerFactory.getLogger(DefaultListener.class);

    protected static List<SlashCMD> SlashCommands;
    protected static List<GSlashCMD> GSlashCommands;
    protected static List<GUserCMD> GUserCommands;
    protected static List<GMessageCMD> GMessageCommands;
    protected static List<UserCMD> UserCommands;
    protected static List<MessageCMD> MessageCommands;
    protected static List<ButtonCMD> ButtonCommands;
    protected static List<ModalCMD> ModalCommands;
    protected static List<StringSelectCMD> StringSelectCommands;
    protected static List<EntitySelectCMD> EntitySelectCommands;

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        try {
            SetupGlobalCommands();
            onReady.get();
            DiscordAccount.getPresence().setActivity(Activity.customStatus("✅ Bot start-up done!"));
            log.info("Finished bot start-up!");
        } catch (Exception e) {
            log.error("Failed to start bot: {}", e.getMessage());
            System.exit(1);
        }
    }
    private final ExecutorService onGuildReady = Executors.newFixedThreadPool(20);
    @Override
    public void onGuildReady(@NotNull GuildReadyEvent event) {
        onGuildReady.execute(() -> {
            try {
                SetupGuildCommands(event.getGuild());
                log.info("Done setting up commands in: {} ({})", event.getGuild().getName(), event.getGuild().getId());
            } catch (Exception e) {
                log.error("Failed to setup commands of guild: {} - {}", event.getGuild().getName(), e.getMessage());
            }
        });
    }

    /**
     * The registered instances are prototypes: they are matched against (cheap — {@code getData()} is a cached
     * annotation) but never dispatched on, because {@link CMD} carries per-interaction state ({@code IT}, and the
     * memoized {@code GI}). Reusing one instance leaks the previous invocation's interaction and guild into the next.
     * Dispatch therefore runs on a fresh instance, the same way {@link CMD} already builds its buttons and modals.
     */
    @SuppressWarnings("unchecked")
    private static <T extends CMD> Optional<T> callCommand(Optional<T> prototype) {
        return prototype.flatMap(p -> {
            try {
                Constructor<?> c = p.getClass().getDeclaredConstructor();
                c.setAccessible(true); // registration accepts non-public handlers, so dispatch has to be able to build one too
                return Optional.of((T) c.newInstance());
            } catch (Exception ex) {
                log.error("Failed to instantiate {} for dispatch", p.getClass().getName(), ex);
                return Optional.empty();
            }
        });
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent e) {
        callCommand(GSlashCommands.stream().filter(cmd -> e.getName().equals(cmd.getData().name())).findFirst()).ifPresent(cmd -> {
            cmd.IT = e;
            cmd.onSlash(e);
        });
        callCommand(SlashCommands.stream().filter(cmd -> e.getName().equals(cmd.getData().name())).findFirst()).ifPresent(cmd -> {
            cmd.IT = e;
            cmd.onSlash(e);
        });
        LogCommand(e);
    }

    @Override
    public void onUserContextInteraction(@NotNull UserContextInteractionEvent e) {
        callCommand(UserCommands.stream().filter(cmd -> e.getName().equals(cmd.getData().name())).findFirst()).ifPresent(cmd -> {
            cmd.IT = e;
            cmd.onUserCommandClick(e);
        });
        LogCommand(e);
    }

    @Override
    public void onMessageContextInteraction(@NotNull MessageContextInteractionEvent e) {
        callCommand(MessageCommands.stream().filter(cmd -> e.getName().equals(cmd.getData().name())).findFirst()).ifPresent(cmd -> {
            cmd.IT = e;
            cmd.onMessageContextCommand(e);
        });
        LogCommand(e);
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent e) {
        String[] options = e.getComponentId().replaceFirst(e.getComponentId().split("/")[0] + "/", "").split("/");
        callCommand(ButtonCommands.stream().filter(cmd -> e.getComponentId().startsWith(cmd.getData().id())).findFirst()).ifPresent(cmd -> {
            cmd.IT = e;
            cmd.onPressed(e, options);
        });
        LogCommand(e);
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent e) {
        String[] options = e.getModalId().replaceFirst(e.getModalId().split("/")[0] + "/", "").split("/");
        callCommand(ModalCommands.stream().filter(cmd -> e.getModalId().startsWith(cmd.getData().id())).findFirst()).ifPresent(cmd -> {
            cmd.IT = e;
            cmd.onSubmit(e, options);
        });
        LogCommand(e);
    }

    @Override
    public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent e) {
        String[] options = e.getComponentId().replaceFirst(e.getComponentId().split("/")[0] + "/", "").split("/");
        callCommand(StringSelectCommands.stream().filter(cmd -> e.getComponentId().startsWith(cmd.getData().id())).findFirst()).ifPresent(cmd -> {
            cmd.IT = e;
            cmd.onStringSelect(e, e.getValues(), options);
        });
        LogCommand(e);
    }

    @Override
    public void onEntitySelectInteraction(@NotNull EntitySelectInteractionEvent e) {
        String[] options = e.getComponentId().replaceFirst(e.getComponentId().split("/")[0] + "/", "").split("/");
        callCommand(EntitySelectCommands.stream().filter(cmd -> e.getComponentId().startsWith(cmd.getData().id())).findFirst()).ifPresent(cmd -> {
            cmd.IT = e;
            cmd.onEntitySelect(e, e.getValues(), options);
        });
        LogCommand(e);
    }

    public DefaultListener(String commandPackage) {
        log.info("Loaded {} slash commands.", (SlashCommands = loadClasses(SlashCMD.class, commandPackage)).size());
        log.info("Loaded {} guild slash commands.", (GSlashCommands = loadClasses(GSlashCMD.class, commandPackage)).size());
        log.info("Loaded {} user commands.", (UserCommands = loadClasses(UserCMD.class, commandPackage)).size());
        log.info("Loaded {} guild user commands.", (GUserCommands = loadClasses(GUserCMD.class, commandPackage)).size());
        log.info("Loaded {} message commands.", (MessageCommands = loadClasses(MessageCMD.class, commandPackage)).size());
        log.info("Loaded {} guild message commands.", (GMessageCommands = loadClasses(GMessageCMD.class, commandPackage)).size());
        log.info("Loaded {} buttons.", (ButtonCommands = loadClasses(ButtonCMD.class, commandPackage)).size());
        log.info("Loaded {} modals.", (ModalCommands = loadClasses(ModalCMD.class, commandPackage)).size());
        log.info("Loaded {} string select menus.", (StringSelectCommands = loadClasses(StringSelectCMD.class, commandPackage)).size());
        log.info("Loaded {} entity select menus.", (EntitySelectCommands = loadClasses(EntitySelectCMD.class, commandPackage)).size());
    }

    /**
     * One shared thread for the whole bot. This used to allocate a {@code newCachedThreadPool} per interaction and
     * hand it to {@code ShutdownAfterAction}, which calls {@link System#gc()} in its finally block — a full
     * stop-the-world collection on every single command, which pauses the gateway threads and is enough on its own
     * to trip JDA's missed-heartbeat reconnect. The work here is cache reads plus a queued REST call, so it neither
     * needs a pool nor blocks.
     */
    private static final ExecutorService LogPool = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "JDA-CommandLog");
        t.setDaemon(true);
        return t;
    });

    public static void LogCommand(Interaction e) {
        LogPool.execute(() -> {
            Guild G = e.getGuild();
            Channel C = e.getChannel();
            User U = e.getUser();
            String LOG = (G != null && G.isDetached() ? "[Detached]" : G != null ? "[" + G.getName() + "/" + G.getId() + "]" : "[DM]") + (C != null && !C.getName().isEmpty() ? "[#" + C.getName() + "/" + C.getId() + "]" : "[DM]") + ":** `" + U.getEffectiveName() + " (" + U.getId() + ")` : `";
            switch (e) {
                case SlashCommandInteractionEvent CMD -> LOG = "**[Slash Command]" + LOG + CMD.getCommandString() + "`";
                case UserContextInteractionEvent CMD -> LOG = "**[User Context]" + LOG + "@" + CMD.getTarget().getName() + "`";
                case MessageContextInteractionEvent CMD -> LOG = "**[Message Context]" + LOG + CMD.getTarget().getId() + "`";
                case ButtonInteractionEvent CMD -> LOG = "**[Button]" + LOG + CMD.getButton().getCustomId() + "`";
                case ModalInteractionEvent CMD -> LOG = "**[Modal]" + LOG + CMD.getModalId() + " --> " + CMD.getValues().stream().map(s -> "[" + s.getCustomId() + "=" + s.getAsString() + "]").collect(Collectors.joining(",")) + "`";
                case StringSelectInteractionEvent CMD -> LOG = "**[String Selection]" + LOG + CMD.getComponentId() + " --> [" + CMD.getSelectedOptions().stream().map(SelectOption::getValue).collect(Collectors.joining(",")) + "]`";
                case EntitySelectInteractionEvent CMD -> LOG = "**[Entity Selection]" + LOG + CMD.getComponentId() + " --> [" + CMD.getValues().stream().map(IMentionable::getAsMention).collect(Collectors.joining(",")) + "]`";
                default -> {}
            }
            if (LogChannel != null) LogChannel.sendMessage(LOG).queue();
            log.info(LOG.replaceAll("\\*", ""));
        });
    }

    /**
     * {@code ignoreClassVisibility()} is not optional: ClassGraph only records <b>public</b> classes by default, so a
     * non-public class anywhere in the chain breaks it — a {@code public static} handler extending a {@code private
     * abstract} base in its own outer class is simply never seen as a subclass of {@link CMD}, and it registers no
     * command. Nothing fails at build or boot: the component renders, the click dispatches to no handler, and the
     * interaction is never acknowledged. Same reason the constructor is forced accessible — a package-private or
     * private handler has a constructor to match.
     */
    @SuppressWarnings("unchecked")
    private static <T> List<T> loadClasses(Class<T> clazz, String commandPackage) {
        List<T> L = new ArrayList<>();
        try (ScanResult scanResult = new ClassGraph().enableClassInfo().enableAnnotationInfo().ignoreClassVisibility().overrideClassLoaders(ClassUtils.scannable(classLoaders)).acceptPackages(commandPackage, CMD.class.getPackageName()).scan()) {
            for (ClassInfo classInfo : scanResult.getSubclasses(clazz).stream().filter(c -> !c.isAbstract()).toList()) {
                try {
                    Constructor<?> c = classInfo.loadClass().getDeclaredConstructor();
                    c.setAccessible(true);
                    L.add((T) c.newInstance());
                } catch (Exception ignored) {
                    log.error("Failed to load class {}", classInfo.getName());
                }
            }
        }
        return L;
    }
    public static void SetupGlobalCommands() {
        List<CommandData> CMD = new ArrayList<>();
        for (SlashCMD cmd : SlashCommands) {
            // Discord rejects a command carrying both, so subcommands win when a command declares any.
            List<SubcommandData> subs = cmd.commandSubcommands();
            CMD.add(Commands.slash(cmd.getData().name(), cmd.getData().description())
                    .addSubcommands(subs)
                    .addOptions(subs.isEmpty() ? cmd.commandParameters() : List.of())
                    .setNSFW(cmd.getData().nsfw())
                    .setIntegrationTypes(cmd.getData().integrationType())
                    .setContexts(cmd.getData().integrationContextType()));
        }
        for (UserCMD cmd : UserCommands) {
            CMD.add(Commands.user(cmd.getData().name())
                    .setNSFW(cmd.getData().nsfw())
                    .setIntegrationTypes(cmd.getData().integrationType())
                    .setContexts(cmd.getData().integrationContextType()));
        }
        for (MessageCMD cmd : MessageCommands) {
            CMD.add(Commands.message(cmd.getData().name())
                    .setNSFW(cmd.getData().nsfw())
                    .setIntegrationTypes(cmd.getData().integrationType())
                    .setContexts(cmd.getData().integrationContextType()));
        }
        DiscordAccount.updateCommands().addCommands(CMD).queue();
    }
    public static void SetupGuildCommands(Guild guild) {
        List<CommandData> CMD = new ArrayList<>();
        for (GSlashCMD cmd : GSlashCommands.stream().filter(g -> g.getServerIDs().contains(guild.getIdLong())).toList()) {
            List<SubcommandData> subs = cmd.commandSubcommands(guild);
            CMD.add(Commands.slash(cmd.getData().name(), cmd.getData().description())
                    .addSubcommands(subs)
                    .addOptions(subs.isEmpty() ? cmd.commandParameters(guild) : List.of())
                    .setNSFW(cmd.getData().nsfw())
                    .setIntegrationTypes(IntegrationType.GUILD_INSTALL)
                    .setContexts(InteractionContextType.GUILD));
        }
        for (GUserCMD cmd : GUserCommands.stream().filter(g -> g.getServerIDs().contains(guild.getIdLong())).toList()) {
            CMD.add(Commands.user(cmd.getData().name())
                    .setNSFW(cmd.getData().nsfw())
                    .setIntegrationTypes(IntegrationType.GUILD_INSTALL)
                    .setContexts(InteractionContextType.GUILD));
        }
        for (GMessageCMD cmd : GMessageCommands.stream().filter(g -> g.getServerIDs().contains(guild.getIdLong())).toList()) {
            CMD.add(Commands.message(cmd.getData().name())
                    .setNSFW(cmd.getData().nsfw())
                    .setIntegrationTypes(IntegrationType.GUILD_INSTALL)
                    .setContexts(InteractionContextType.GUILD));
        }
        guild.updateCommands().addCommands(CMD).queue(); // pushed even when empty: a guild that lost its last eligible command has to have the stale one cleared
    }

    /**
     * Re-reads every guild-scoped command's {@code serverIds()} and re-pushes the whole set to each guild.
     * Guild commands are otherwise registered once at guild-ready off a memoized list, so a command whose
     * eligibility depends on live data (an open tournament, a clan, a league) only appears after a restart.
     */
    public static void RefreshGuildCommands() {
        GSlashCommands.forEach(GSlashCMD::invalidateServerIDs);
        GUserCommands.forEach(GUserCMD::invalidateServerIDs);
        GMessageCommands.forEach(GMessageCMD::invalidateServerIDs);
        for (Guild g : DiscordAccount.getGuilds()) {
            try {
                SetupGuildCommands(g);
            } catch (Exception e) {
                log.error("Failed to refresh commands of guild: {} - {}", g.getName(), e.getMessage());
            }
        }
    }

    public static void addGuildSlashCommand(Guild guild, SlashCMD cmd) {
        List<SubcommandData> subs = cmd.commandSubcommands();
        guild.upsertCommand(Commands.slash(cmd.getData().name(), cmd.getData().description())
                .addSubcommands(subs)
                .addOptions(subs.isEmpty() ? cmd.commandParameters() : List.of())
                .setNSFW(cmd.getData().nsfw())
                .setIntegrationTypes(cmd.getData().integrationType())
                .setContexts(cmd.getData().integrationContextType())).queue();
    }
}
