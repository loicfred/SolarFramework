package org.solarframework.discord.core;

import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import org.solarframework.discord.core.annotation.GUserCommand;

import java.util.List;

public abstract class GUserCMD extends CMD {
    private final GUserCommand interactionData = this.getClass().getAnnotation(GUserCommand.class);

    private List<Long> serverIds = null;
    protected List<Long> getServerIDs() {
        return serverIds == null ? serverIds = serverIds() : serverIds;
    }
    public abstract List<Long> serverIds();
    /** Drops the memoized guild list so the next registration pass re-reads it. */
    public void invalidateServerIDs() { serverIds = null; }

    public abstract void onUserCommandClick(UserContextInteractionEvent event);

    protected GUserCMD() {
        if (!this.getClass().isAnnotationPresent(GUserCommand.class)) {
            throw new RuntimeException(this.getClass().getName() + " must be annotated with @GUserCommand");
        }
    }

    public GUserCommand getData() {
        return interactionData;
    }
}