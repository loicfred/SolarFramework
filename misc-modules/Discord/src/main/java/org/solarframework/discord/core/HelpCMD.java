package org.solarframework.discord.core;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.solarframework.discord.core.annotation.SlashCommand;

import java.awt.Color;
import java.util.List;

@SlashCommand(name = "help", description = "Displays the list of all available commands")
public final class HelpCMD extends SlashCMD {

    @Override
    public void onSlash(SlashCommandInteractionEvent e) {
        e.deferReply(true).queue();
        Guild G = e.isFromAttachedGuild() ? e.getGuild() : null;
        List<String> guildCMD = G == null ? List.of() : DefaultListener.GSlashCommands.stream().filter(c -> c.conditionToAdd(G)).map(c -> line(c.getData().name(), c.getData().description())).sorted().toList();
        List<String> globalCMD = DefaultListener.SlashCommands.stream().map(c -> line(c.getData().name(), c.getData().description())).sorted().toList();

        EmbedBuilder E = new EmbedBuilder().setTitle(TL("help-title")).setColor(new Color(88, 101, 242));
        if (!guildCMD.isEmpty()) addFields(E, TL("help-guild-commands", G.getName()), guildCMD);
        if (!globalCMD.isEmpty()) addFields(E, TL("help-global-commands"), globalCMD);
        if (guildCMD.isEmpty() && globalCMD.isEmpty()) E.setDescription(TL("help-no-command"));
        e.getHook().editOriginalEmbeds(E.build()).queue();
    }

    private String line(String name, String description) {
        return "**/" + name + "** — " + description;
    }

    private void addFields(EmbedBuilder E, String title, List<String> lines) {
        StringBuilder SB = new StringBuilder();
        boolean first = true;
        for (String l : lines) {
            if (!SB.isEmpty() && SB.length() + l.length() + 1 > MessageEmbed.VALUE_MAX_LENGTH) {
                E.addField(first ? title : EmbedBuilder.ZERO_WIDTH_SPACE, SB.toString(), false);
                SB.setLength(0);
                first = false;
            }
            SB.append(SB.isEmpty() ? "" : "\n").append(l);
        }
        if (!SB.isEmpty()) E.addField(first ? title : EmbedBuilder.ZERO_WIDTH_SPACE, SB.toString(), false);
    }
}
