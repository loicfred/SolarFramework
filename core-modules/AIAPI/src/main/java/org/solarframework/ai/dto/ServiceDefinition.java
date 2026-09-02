package org.solarframework.ai.dto;

import org.solarframework.ai.IAIService;

/** One {@code IAIService}'s settings, flattened for a config file. */
public record ServiceDefinition(String name, String baseUrl, String apiKey, String model, int timeoutSeconds, int requestsPerMinute) {

    public ServiceDefinition { timeoutSeconds = timeoutSeconds <= 0 ? 300 : timeoutSeconds; }

    public boolean isDefault() { return IAIService.DEFAULT.equals(name); }

    @Override public String toString() { return name + " -> " + baseUrl + (model == null ? "" : " (" + model + ")"); }
}
