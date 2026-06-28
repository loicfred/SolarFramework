package org.solarframework.discord.core;

import org.solarframework.discord.core.annotation.SlashCommand;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.List;

public abstract class SlashCMD extends CMD {
    private final SlashCommand interactionData = this.getClass().getAnnotation(SlashCommand.class);

    public List<OptionData> commandParameters() {
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