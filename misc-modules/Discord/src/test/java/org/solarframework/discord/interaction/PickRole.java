package org.solarframework.discord.interaction;

import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import org.solarframework.discord.core.EntitySelectCMD;
import org.solarframework.discord.core.annotation.EntityCommand;

import java.util.List;

@EntityCommand(id = "pick_role", placeholder = "pick-a-role", type = EntitySelectMenu.SelectTarget.ROLE, minValues = 1, maxValues = 2)
public class PickRole extends EntitySelectCMD {
    @Override
    public void onEntitySelect(EntitySelectInteractionEvent e, List<IMentionable> entities, String[] metadata) {
        e.reply("Picked " + entities.size() + " for " + metadata[0]).queue();
    }
}
