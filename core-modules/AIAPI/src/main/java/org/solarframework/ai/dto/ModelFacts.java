package org.solarframework.ai.dto;

import java.util.List;

/**
 * What a service can say about the model it runs, for a screen that has to show it. The prose form of
 * the same thing is {@code IAIService.describe()}; this is the form a page can render field by field.
 *
 * @param toolsSupported null when the server could not be asked — which is not the same as a no, and a
 *                       screen that treats it as one warns about a model it never reached.
 */
public record ModelFacts(List<String> models, String model, Boolean toolsSupported) {

    public ModelFacts { models = models == null ? List.of() : List.copyOf(models); }

    @Override public String toString() { return model + " (" + models.size() + " offered, tools " + (toolsSupported == null ? "unknown" : toolsSupported) + ")"; }
}
