package org.solarframework.discord.interaction;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.solarframework.discord.core.ButtonCMD;

// Deliberately missing @ButtonCommand: its constructor must throw, and the scanner must skip it rather than die.
public class Unannotated extends ButtonCMD {
    @Override
    public void onPressed(ButtonInteractionEvent e, String[] metadata) {}
}
