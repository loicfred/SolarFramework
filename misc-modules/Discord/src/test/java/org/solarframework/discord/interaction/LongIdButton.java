package org.solarframework.discord.interaction;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.solarframework.discord.core.ButtonCMD;
import org.solarframework.discord.core.annotation.ButtonCommand;

// 95 characters, so any metadata at all pushes the custom id past Button.ID_MAX_LENGTH.
@ButtonCommand(id = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", label = "x")
public class LongIdButton extends ButtonCMD {
    @Override
    public void onPressed(ButtonInteractionEvent e, String[] metadata) {}
}
