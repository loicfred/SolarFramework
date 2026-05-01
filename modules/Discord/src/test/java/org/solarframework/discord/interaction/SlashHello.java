package org.solarframework.discord.interaction;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.solarframework.discord.core.SlashCMD;
import org.solarframework.discord.core.annotation.SlashCommand;

// This is a slash command /hello that has a button "Click me!" attached to it with metadata attached.
@SlashCommand(name = "hello", description = "Says hello to the user.")
public class SlashHello extends SlashCMD {
    @Override
    public void onSlash(SlashCommandInteractionEvent e) {
        Button Btn = makeButton(ClickMe.class,  "This is some data."); // makeButton is part of SlashCMD
        e.reply("Hello, " + e.getUser().getAsMention() + "!").setComponents(ActionRow.of(Btn)).queue();
    }
}
