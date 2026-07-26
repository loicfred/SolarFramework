package org.solarframework.discord.core;

import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.solarframework.discord.core.annotation.ButtonCommand;

@ButtonCommand(id = "nothing", label = "...", style = ButtonStyle.SECONDARY)
public class NothingButton extends ButtonCMD {
    @Override
    public void onPressed(ButtonInteractionEvent event, String[] metadata) {
        event.deferEdit().queue();
    }

    public static Button build() {
        return new NothingButton().makeButton(NothingButton.class);
    }
}
