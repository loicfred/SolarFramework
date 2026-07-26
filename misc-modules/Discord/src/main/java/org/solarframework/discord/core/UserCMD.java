package org.solarframework.discord.core;

import org.solarframework.discord.core.annotation.UserCommand;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;

import java.util.List;

public abstract class UserCMD extends CMD {
    private final UserCommand interactionData = this.getClass().getAnnotation(UserCommand.class);

    public abstract void onUserCommandClick(UserContextInteractionEvent event);

    protected UserCMD() {
        if (!this.getClass().isAnnotationPresent(UserCommand.class)) {
            throw new RuntimeException(this.getClass().getName() + " must be annotated with @UserCommand");
        }
    }

    public UserCommand getData() {
        return interactionData;
    }
}