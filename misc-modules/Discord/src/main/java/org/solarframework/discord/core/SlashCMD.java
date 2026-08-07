package org.solarframework.discord.core;

import org.solarframework.discord.core.annotation.SlashCommand;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.List;

public abstract class SlashCMD extends CMD {
    private final SlashCommand interactionData = this.getClass().getAnnotation(SlashCommand.class);

    public List<OptionData> commandParameters() {
        return List.of();
    }

    /**
     * Subcommands, for a command that has outgrown Discord's 25-option ceiling — each subcommand carries its
     * own 25. Discord refuses a command holding both, so returning a non-empty list here means
     * {@link #commandParameters()} is ignored at registration. Dispatch is unaffected: the interaction still
     * arrives under the root name, so {@link #onSlash} branches on {@code event.getSubcommandName()}.
     */
    public List<SubcommandData> commandSubcommands() {
        return List.of();
    }

    public abstract void onSlash(SlashCommandInteractionEvent event);

    protected SlashCMD() {
        if (!this.getClass().isAnnotationPresent(SlashCommand.class)) {
            throw new RuntimeException(this.getClass().getName() + " must be annotated with @SlashCommand");
        }
    }

    public SlashCommand getData() {
        return interactionData;
    }
}