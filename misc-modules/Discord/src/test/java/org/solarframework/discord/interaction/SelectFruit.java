package org.solarframework.discord.interaction;

import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import org.solarframework.discord.core.StringSelectCMD;
import org.solarframework.discord.core.annotation.SelectCommand;

import java.util.List;

@SelectCommand(id = "fruit", placeholder = "pick-a-fruit", minValues = 1, maxValues = 3)
public class SelectFruit extends StringSelectCMD {
    @Override
    public void onStringSelect(StringSelectInteractionEvent e, List<String> values, String[] metadata) {
        e.reply("Picked: " + String.join(",", values) + " for " + metadata[0]).queue();
    }
}
