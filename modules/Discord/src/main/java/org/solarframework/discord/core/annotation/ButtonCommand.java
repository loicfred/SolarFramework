package org.solarframework.discord.core.annotation;

import net.dv8tion.jda.api.components.buttons.ButtonStyle;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface ButtonCommand {
    String id();
    String label();
    ButtonStyle style() default ButtonStyle.PRIMARY;
}