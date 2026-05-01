package org.solarframework.discord.core;

import org.solarframework.discord.core.annotation.ModalCommand;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;

public abstract class ModalCMD extends CMD {
    private final ModalCommand modalData = this.getClass().getAnnotation(ModalCommand.class);

    public abstract void onSubmit(ModalInteractionEvent event, String[] metadata);

    protected ModalCMD() {
        if (!this.getClass().isAnnotationPresent(ModalCommand.class)) {
            throw new RuntimeException(this.getClass().getName() + " must be annotated with @ModalCommand");
        }
    }

    public ModalCommand getData() {
        return modalData;
    }

}