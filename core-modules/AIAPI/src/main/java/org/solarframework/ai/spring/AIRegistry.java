package org.solarframework.ai.spring;

import org.solarframework.ai.IAIManager;
import org.solarframework.ai.IAIService;

/**
 * Where the running implementation lives, mirroring {@code DatabaseRegistry}. AIImpl assigns both on
 * {@code ApplicationReadyEvent}; they stay null in an application that never adds it.
 * <pre>
 * import static org.solarframework.ai.spring.AIRegistry.DefaultAIService;
 * DefaultAIService.createChatbot().build().prompt("Hi");
 * SolarAIManager.getService("summarizer").createChatbot().build().prompt("...");
 * </pre>
 */
public class AIRegistry {

    /** The service seeded from {@code application.properties}. The one most callers want. */
    public static IAIService DefaultAIService;

    /** Every service, including the default. */
    public static IAIManager SolarAIManager;
}
