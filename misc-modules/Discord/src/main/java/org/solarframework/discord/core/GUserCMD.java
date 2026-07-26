package org.solarframework.discord.core;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import org.solarframework.discord.core.annotation.GUserCommand;
import org.solarframework.discord.core.annotation.UserCommand;
import org.solarframework.discord.obj.Discord_GuildInfo;

import java.util.List;

public abstract class GUserCMD extends CMD {
    private final GUserCommand interactionData = this.getClass().getAnnotation(GUserCommand.class);

    public abstract boolean conditionToAdd(Guild dgi);

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