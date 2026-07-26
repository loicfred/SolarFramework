package org.solarframework.discord.core.annotation;

import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static net.dv8tion.jda.api.interactions.IntegrationType.GUILD_INSTALL;
import static net.dv8tion.jda.api.interactions.IntegrationType.USER_INSTALL;
import static net.dv8tion.jda.api.interactions.InteractionContextType.*;

@Retention(RetentionPolicy.RUNTIME)
public @interface GSlashCommand {
    String name();
    String description();
    boolean nsfw() default false;
}