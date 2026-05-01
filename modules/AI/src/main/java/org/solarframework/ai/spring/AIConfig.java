package org.solarframework.ai.spring;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
public class AIConfig {

    @Bean("aiCacheManager")
    public CacheManager aiCacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        CaffeineCache db1 = new CaffeineCache("Prompt", Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(20_000).build());
        cacheManager.setCaches(List.of(db1));
        return cacheManager;
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
