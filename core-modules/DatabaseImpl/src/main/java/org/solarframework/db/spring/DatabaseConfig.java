package org.solarframework.db.spring;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * {@code @EnableTransactionManagement} is spelled out explicitly rather than left to Spring Boot's own
 * TransactionAutoConfiguration: Boot only builds that infrastructure when a TransactionManager bean already
 * exists at context-refresh time ({@code @ConditionalOnBean(TransactionManager.class)}), but every
 * TransactionManager this framework registers is built lazily, on first use, by JpaSourceRegistrar - long
 * after refresh. Declaring it here builds the AOP infrastructure unconditionally; TransactionInterceptor
 * still resolves the actual TransactionManager bean lazily per invocation, so this works whether or not one
 * has been registered yet when the context starts.
 */
@Configuration
@EnableCaching
@EnableTransactionManagement
public class DatabaseConfig {
    protected static String defaultConnectionString;

    @Bean("databaseCacheManager")
    public CacheManager databaseCacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        CaffeineCache db1 = new CaffeineCache("DBObject", Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).maximumSize(10_000).build());
        CaffeineCache db2 = new CaffeineCache("DBRow", Caffeine.newBuilder().expireAfterWrite(15, TimeUnit.MINUTES).maximumSize(5_000).build());
        CaffeineCache db3 = new CaffeineCache("DBData", Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).maximumSize(500).build());
        cacheManager.setCaches(List.of(db1, db2, db3));
        return cacheManager;
    }

    @Bean
    @Primary
    public CacheManager defaultCacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder().expireAfterWrite(30, TimeUnit.MINUTES).maximumSize(50_000));
        return manager;
    }

}
