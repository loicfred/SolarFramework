package org.solarframework.discord.core;

import org.solarframework.discord.core.annotation.SelectCommand;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;

import java.util.List;

public abstract class StringSelectCMD extends CMD {
    private final SelectCommand selectData = this.getClass().getAnnotation(SelectCommand.class);

    public abstract void onStringSelect(StringSelectInteractionEvent event, List<String> values, String[] metadata);

    protected StringSelectCMD() {
        if (!this.getClass().isAnnotationPresent(SelectCommand.class)) {
            throw new RuntimeException(this.getClass().getName() + " must be annotated with @SelectCommand");
        }
    }

    public SelectCommand getData() {
        return selectData;
    }

}