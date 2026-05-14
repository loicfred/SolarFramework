package org.solarframework.discord;

import org.solarframework.discord.core.BotBuilder;

public class Discord_Main {

    public static void main(String[] args) {
        BotBuilder b = new BotBuilder("shhhh", "org.solarframework.discord.interaction");
        b.build();
    }
}
