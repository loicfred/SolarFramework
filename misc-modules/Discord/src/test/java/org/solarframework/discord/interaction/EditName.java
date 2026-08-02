package org.solarframework.discord.interaction;

import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import org.solarframework.discord.core.ModalCMD;
import org.solarframework.discord.core.annotation.ModalCommand;

@ModalCommand(id = "edit_name", title = "edit-name-title")
public class EditName extends ModalCMD {
    @Override
    public void onSubmit(ModalInteractionEvent e, String[] metadata) {
        e.reply("Renamed " + metadata[0]).queue();
    }
}
