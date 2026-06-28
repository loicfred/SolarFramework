package org.solarframework.discord.core.annotation;

import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static net.dv8tion.jda.api.interactions.IntegrationType.GUILD_INSTALL;
import static net.dv8tion.jda.api.interactions.IntegrationType.USER_INSTALL;
import static net.dv8tion.jda.api.interactions.InteractionContextType.*;

@Retention(RetentionPolicy.RUNTIME)
public @interface SlashCommand {
    String name();
    String description();
    IntegrationType[] integrationType() default {GUILD_INSTALL, USER_INSTALL};
    InteractionContextType[] integrationContextType() default {GUILD, BOT_DM, PRIVATE_CHANNEL};
    boolean nsfw() default false;
}