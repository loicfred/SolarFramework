package org.solarframework.discord.core.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface GMessageCommand {
    String name();
    boolean nsfw() default false;
}
