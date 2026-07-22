package org.solarframework.discord.core;

import org.solarframework.discord.core.annotation.MessageCommand;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;

import java.util.List;

public abstract class MessageCMD extends CMD {
    private final MessageCommand interactionData = this.getClass().getAnnotation(MessageCommand.class);

    public abstract void onMessageContextCommand(MessageContextInteractionEvent event);

    private MessageCMD() {
        if (!this.getClass().isAnnotationPresent(MessageCommand.class)) {
            throw new RuntimeException(this.getClass().getName() + " must be annotated with @MessageCommand");
        }
    }

    public MessageCommand getData() {
        return interactionData;
    }

    public List<Long> whitelistedGuilds() {return List.of();}
}