package org.solarframework.discord.core;

import org.solarframework.discord.core.annotation.SelectCommand;
import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;

import java.util.List;

public abstract class EntitySelectCMD extends CMD {
    private final SelectCommand selectData = this.getClass().getAnnotation(SelectCommand.class);

    public abstract void onEntitySelect(EntitySelectInteractionEvent event, List<IMentionable> entities, String[] metadata);

    protected EntitySelectCMD() {
        if (!this.getClass().isAnnotationPresent(SelectCommand.class)) {
            throw new RuntimeException(this.getClass().getName() + " must be annotated with @SelectCommand");
        }
    }

    public SelectCommand getData() {
        return selectData;
    }

}