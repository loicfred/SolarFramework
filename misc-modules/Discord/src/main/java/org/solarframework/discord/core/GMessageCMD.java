package org.solarframework.discord.core;

import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import org.solarframework.discord.core.annotation.GMessageCommand;

import java.util.List;

public abstract class GMessageCMD extends CMD {
    private final GMessageCommand interactionData = this.getClass().getAnnotation(GMessageCommand.class);

    private List<Long> serverIds = null;
    protected List<Long> getServerIDs() {
        return serverIds == null ? serverIds = serverIds() : serverIds;
    }
    public abstract List<Long> serverIds();
    /** Drops the memoized guild list so the next registration pass re-reads it. */
    public void invalidateServerIDs() { serverIds = null; }

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