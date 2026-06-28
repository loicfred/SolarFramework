package org.solarframework.discord.core;

import org.solarframework.discord.core.annotation.ButtonCommand;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

public abstract class ButtonCMD extends CMD {
    private final ButtonCommand buttonData = this.getClass().getAnnotation(ButtonCommand.class);

    public abstract void onPressed(ButtonInteractionEvent event, String[] metadata);

    protected ButtonCMD() {
        if (!this.getClass().isAnnotationPresent(ButtonCommand.class)) {
            throw new RuntimeException(this.getClass().getName() + " must be annotated with @ButtonCommand");
        }
    }

    public ButtonCommand getData() {
        return buttonData;
    }

}