package org.solarframework.discord.core.annotation;

import net.dv8tion.jda.api.components.selections.EntitySelectMenu;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface EntityCommand {
    String id();
    String placeholder();
    EntitySelectMenu.SelectTarget type();
    int minValues() default 1;
    int maxValues() default 1;
    boolean required() default true;
}