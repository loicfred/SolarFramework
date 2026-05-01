package org.solarframework.discord.interaction;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.solarframework.discord.core.ButtonCMD;
import org.solarframework.discord.core.annotation.ButtonCommand;

// This is the button attached to the slash command above. It responds with the metadata when clicked.
@ButtonCommand(id = "click_me", label = "Click me!")
public class ClickMe extends ButtonCMD {
    @Override
    public void onPressed(ButtonInteractionEvent e, String[] metadata) {
        e.reply("Here's the data: " + metadata[0]).queue();
    }
}