package org.solarframework.discord.core.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface SelectCommand {
    String id();
    String placeholder();
    int minValues() default 1;
    int maxValues() default 1;
    boolean required() default true;
}