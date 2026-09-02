package org.solarframework.ai.dto;

import java.util.List;

/**
 * An agent's configuration, flattened for a config file — everything that is data.
 * <p>Tools, memory strategy, store and the step and approval callbacks are live objects, not
 * settings, so they are never written here: a loaded agent gets its persona, model and bounds from
 * the file and is handed its tools in code.
 * <p>An empty {@code serviceName} means whichever service is default, so an agent written before
 * anybody chose one keeps following the default rather than being pinned to a name.
 * <p>{@code attributes} is whatever the application needs remembered and the framework has no field
 * for: a bare flag, or {@code "key:value"}. It is deliberately untyped, for the same reason memory
 * and tools are absent — a generic record should not have to know what "the data dictionary" is in
 * order to carry the fact that one agent may read it.
 */
public record ChatbotDefinition(String name, String serviceName, String systemPrompt, AIOptions options, AIOptions agentOptions, int maxSteps, List<String> attributes) {

    public ChatbotDefinition {
        serviceName = serviceName == null ? "" : serviceName.trim();
        options = options == null ? AIOptions.CONVERSATIONIST : options;
        agentOptions = agentOptions == null ? AIOptions.AGENT : agentOptions;
        maxSteps = maxSteps <= 0 ? 6 : maxSteps;
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
    }

    /** An agent that carries nothing of the application's own. */
    public ChatbotDefinition(String name, String serviceName, String systemPrompt, AIOptions options, AIOptions agentOptions, int maxSteps) { this(name, serviceName, systemPrompt, options, agentOptions, maxSteps, List.of()); }

    /** The value of a {@code "key:value"} attribute, or null when this agent carries none by that name. */
    public String attribute(String key) {
        for (String attribute : attributes) if (attribute.startsWith(key + ":")) return attribute.substring(key.length() + 1);
        return null;
    }
    /** The same read as a whole number, falling back when the attribute is absent or was written as something that is not one. */
    public int attribute(String key, int fallback) {
        try { return Integer.parseInt(attribute(key)); } catch (Exception _) { return fallback; }
    }

    @Override public String toString() { return name + " on " + (serviceName.isBlank() ? "the default service" : serviceName) + " (max " + maxSteps + " steps)"; }
}
