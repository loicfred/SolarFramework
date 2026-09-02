package org.solarframework.ai.spring;

import org.springframework.ai.model.tool.ToolCallingManager;

/** Builds a configured service the way {@link AIManager#makeNewService} does, without a Spring context. */
class TestAI {
    static AIService service(String name, String baseUrl, String model) {
        AIService s = new AIService(ToolCallingManager.builder().build());
        s.setName(name); s.setBaseUrl(baseUrl); s.setApiKey("N/A"); s.setModel(model);
        return s;
    }
}
