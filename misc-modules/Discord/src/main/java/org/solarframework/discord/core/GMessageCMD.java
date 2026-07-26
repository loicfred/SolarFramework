package org.solarframework.discord.core;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import org.solarframework.discord.core.annotation.GMessageCommand;
import org.solarframework.discord.core.annotation.MessageCommand;
import org.solarframework.discord.obj.Discord_GuildInfo;

import java.util.List;

public abstract class GMessageCMD extends CMD {
    private final GMessageCommand interactionData = this.getClass().getAnnotation(GMessageCommand.class);

    public abstract boolean conditionToAdd(Guild dgi);

    public abstract void onMessageContextCommand(MessageContextInteractionEvent event);

    private GMessageCMD() {
        if (!this.getClass().isAnnotationPresent(GMessageCommand.class)) {
            throw new RuntimeException(this.getClass().getName() + " must be annotated with @GMessageCommand");
        }
    }

    public GMessageCommand getData() {
        return interactionData;
    }
}