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
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static org.solarframework.core.util.TimeUtils.getNow;
import static org.solarframework.discord.core.BotBuilder.*;
import static org.solarframework.discord.core.BotBuilder.LogChannel;
import static org.solarframework.core.util.ThreadUtils.ShutdownAfterAction;

public class DefaultListener extends ListenerAdapter {
    private final static Logger log = LoggerFactory.getLogger(DefaultListener.class);

    protected List<SlashCMD> SlashCommands;
    protected List<UserCMD> UserCommands;
    protected List<MessageCMD> MessageCommands;
    protected List<ButtonCMD> ButtonCommands;
    protected List<ModalCMD> ModalCommands;
    protected List<StringSelectCMD> StringSelectCommands;
    protected List<EntitySelectCMD> EntitySelectCommands;

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        try {
            SetupGlobalCommands();
            onReady.get();
            DiscordAccount.getPresence().setActivity(Activity.customStatus("✅ Bot start-up done!"));
            System.out.println("[Discord] Finished bot start-up!");
        } catch (Exception e) {
            log.error("Failed to start bot: {}", e.getMessage());
            System.exit(1);
        }
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent e) {
        SlashCommands.stream().filter(cmd -> e.getName().equals(cmd.getData().name())).findFirst().ifPresent(cmd -> {
            cmd.IT = e;
            cmd.onSlash(e);
        });
        LogCommand(e);
    }

    @Override
    public void onUserContextInteraction(@NotNull UserContextInteractionEvent e) {
        UserCommands.stream().filter(cmd -> e.getName().equals(cmd.getData().name())).findFirst().ifPresent(cmd -> {
            cmd.IT = e;
            cmd.onUserCommandClick(e);
        });
        LogCommand(e);
    }

    @Override
    public void onMessageContextInteraction(@NotNull MessageContextInteractionEvent e) {
        MessageCommands.stream().filter(cmd -> e.getName().equals(cmd.getData().name())).findFirst().ifPresent(cmd -> {
            cmd.IT = e;
            cmd.onMessageContextCommand(e);
        });
        LogCommand(e);
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent e) {
        String[] options = e.getComponentId().replaceFirst(e.getComponentId().split("/")[0] + "/", "").split("/");
        ButtonCommands.stream().filter(cmd -> e.getComponentId().startsWith(cmd.getData().id())).findFirst().ifPresent(cmd -> {
            cmd.IT = e;
            cmd.onPressed(e, options);
        });
        LogCommand(e);
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent e) {
        String[] options = e.getModalId().replaceFirst(e.getModalId().split("/")[0] + "/", "").split("/");
        ModalCommands.stream().filter(cmd -> e.getModalId().startsWith(cmd.getData().id())).findFirst().ifPresent(cmd -> {
            cmd.IT = e;
            cmd.onSubmit(e, options);
        });
        LogCommand(e);
    }

    @Override
    public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent e) {
        String[] options = e.getComponentId().replaceFirst(e.getComponentId().split("/")[0] + "/", "").split("/");
        StringSelectCommands.stream().filter(cmd -> e.getComponentId().startsWith(cmd.getData().id())).findFirst().ifPresent(cmd -> {
            cmd.IT = e;
            cmd.onStringSelect(e, e.getValues(), options);
        });
        LogCommand(e);
    }

    @Override
    public void onEntitySelectInteraction(@NotNull EntitySelectInteractionEvent e) {
        String[] options = e.getComponentId().replaceFirst(e.getComponentId().split("/")[0] + "/", "").split("/");
        EntitySelectCommands.stream().filter(cmd -> e.getComponentId().startsWith(cmd.getData().id())).findFirst().ifPresent(cmd -> {
            cmd.IT = e;
            cmd.onEntitySelect(e, e.getValues(), options);
        });
        LogCommand(e);
    }

    public DefaultListener(String commandPackage) {
        System.out.println("[Discord] Loaded " + (SlashCommands = loadClasses(SlashCMD.class, commandPackage)).size() + " slash commands.");
        System.out.println("[Discord] Loaded " + (UserCommands = loadClasses(UserCMD.class, commandPackage)).size() + " user commands.");
        System.out.println("[Discord] Loaded " + (MessageCommands = loadClasses(MessageCMD.class, commandPackage)).size() + " message commands.");
        System.out.println("[Discord] Loaded " + (ButtonCommands = loadClasses(ButtonCMD.class, commandPackage)).size() + " buttons.");
        System.out.println("[Discord] Loaded " + (ModalCommands = loadClasses(ModalCMD.class, commandPackage)).size() + " modals.");
        System.out.println("[Discord] Loaded " + (StringSelectCommands = loadClasses(StringSelectCMD.class, commandPackage)).size() + " string select menus.");
        System.out.println("[Discord] Loaded " + (EntitySelectCommands = loadClasses(EntitySelectCMD.class, commandPackage)).size() + " entity select menus.");
    }

    public static void LogCommand(Interaction e) {
        ExecutorService E = Executors.newCachedThreadPool();
        ShutdownAfterAction(E, 0.1, "Log", E.submit(() -> {
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
            System.out.println("[" + getNow("HH:mm:ss") + "] " + LOG.replaceAll("(\\*_)", ""));
        }));
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> loadClasses(Class<T> clazz, String commandPackage) {
        List<T> L = new ArrayList<>();
        try (ScanResult scanResult = new ClassGraph().enableClassInfo().enableAnnotationInfo().overrideClassLoaders(classLoaders.toArray(new ClassLoader[]{})).acceptPackages(commandPackage).scan()) {
            for (ClassInfo classInfo : scanResult.getSubclasses(clazz).stream().toList()) {
                try {
                    L.add((T) classInfo.loadClass().getDeclaredConstructor().newInstance());
                } catch (Exception ignored) {
                    System.err.println("[Discord] Failed to load class " + classInfo.getName());
                }
            }
        }
        return L;
    }
    private void SetupGlobalCommands() {
        List<CommandData> CMD = new ArrayList<>();
        for (SlashCMD cmd : SlashCommands) {
            if (Arrays.stream(cmd.getData().integrationType()).allMatch(i -> i == IntegrationType.GUILD_INSTALL)) continue;
            if (Arrays.stream(cmd.getData().integrationContextType()).allMatch(i -> i == InteractionContextType.GUILD)) continue;
            CMD.add(Commands.slash(cmd.getData().name(), cmd.getData().description())
                    .addOptions(cmd.commandParameters())
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
}
