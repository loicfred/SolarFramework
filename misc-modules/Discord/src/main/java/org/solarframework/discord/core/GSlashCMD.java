package org.solarframework.discord.core;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.solarframework.discord.core.annotation.GSlashCommand;
import org.solarframework.discord.core.annotation.SlashCommand;
import org.solarframework.discord.obj.Discord_GuildInfo;

import java.util.List;

public abstract class GSlashCMD extends CMD {
    private final GSlashCommand interactionData = this.getClass().getAnnotation(GSlashCommand.class);

    private List<Long> serverIds = null;
    protected List<Long> getServerIDs() {
        return serverIds == null ? serverIds = serverIds() : serverIds;
    }
    public abstract List<Long> serverIds();

    public List<OptionData> commandParameters(Guild guild) {
        return List.of();
    }

    public abstract void onSlash(SlashCommandInteractionEvent event);

    protected GSlashCMD() {
        if (!this.getClass().isAnnotationPresent(GSlashCommand.class)) {
            throw new RuntimeException(this.getClass().getName() + " must be annotated with @GSlashCommand");
        }
    }

    public GSlashCommand getData() {
        return interactionData;
    }
}