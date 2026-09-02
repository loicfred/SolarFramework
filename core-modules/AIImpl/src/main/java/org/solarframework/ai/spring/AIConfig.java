package org.solarframework.ai.spring;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.solarframework.ai.IAIManager;
import org.solarframework.ai.IAIService;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The Spring way in, for a host that wants one.
 * <p>{@link AIManager} and {@link AIService} are deliberately plain objects with no stereotype of their own, because
 * the stack also has to run where there is no application context at all - inside a plugin, which is how SolarERP
 * carries it. This class is the other half: a host that component-scans this package gets the manager as a bean
 * without doing anything else.
 * <p>A service is seeded here <b>only</b> when {@code spring.ai.openai.base-url} is actually set. An application that
 * says nothing about AI gets an empty manager rather than one pointing at a machine nobody promised was there.
 */
@Configuration
public class AIConfig {

    @Bean
    public IAIManager solarAIManager(ObjectProvider<ToolCallingManager> toolCalling, @Value("${spring.ai.openai.base-url:#{null}}") String baseUrl, @Value("${spring.ai.openai.api-key:N/A}") String apiKey, @Value("${spring.ai.openai.chat.options.model:#{null}}") String model, @Value("${solar.ai.timeout-seconds:300}") int timeoutSeconds) {
        AIManager manager = new AIManager(toolCalling.getIfAvailable(() -> ToolCallingManager.builder().build()));
        if (baseUrl != null) manager.makeNewService(IAIService.DEFAULT, baseUrl, apiKey, model).setTimeoutSeconds(timeoutSeconds);
        return manager;
    }

    /** Caches for the AI module. Only a Spring host has anywhere to put these, which is why they live here with the beans rather than on the service. */
    @Bean("aiCacheManager")
    public CacheManager aiCacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(List.of(new CaffeineCache("Prompt", Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).maximumSize(20_000).build())));
        return cacheManager;
    }
}
