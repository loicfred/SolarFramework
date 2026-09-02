package org.solarframework.ai;

import java.lang.annotation.*;

/**
 * Marks a method the model may call. Declaring tools with this rather than Spring AI's own
 * annotation is what keeps a consumer off the Spring AI classpath.
 * <p>An object with no {@code @AITool} method is handed to the backend untouched, so objects
 * annotated with a backend's native tool annotation keep working.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AITool {

    /** Defaults to the method name. */
    String name() default "";

    /** Shown to the model — this is what it picks the tool by, so describe when to use it. */
    String description();
}
