package org.solarframework.db.spring;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Scheduler;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
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
        CaffeineCache db1 = new CaffeineCache("DBObject", Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).maximumWeight(50_000).weigher(DatabaseConfig::rowsOf).softValues().scheduler(Scheduler.systemScheduler()).build());
        CaffeineCache db2 = new CaffeineCache("DBRow", Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumWeight(20_000).weigher(DatabaseConfig::rowsOf).softValues().scheduler(Scheduler.systemScheduler()).build());
        CaffeineCache db3 = new CaffeineCache("DBData", Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).maximumSize(500).build());
        cacheManager.setCaches(List.of(db1, db2, db3));
        return cacheManager;
    }

    /**
     * A cached entry is a whole result list, so counting <i>entries</i> put no bound at all on the heap: one
     * list query is twenty thousand rows and still counted as 1 of 10 000. Weight counts rows instead, so the
     * budget means "at most this many cached rows across every query" whatever shape they arrive in.
     *
     * <p>Paired with {@code softValues()}: even inside the budget these are pure caches, and letting the
     * collector take them under pressure is strictly better than dying with them resident. It costs value
     * identity comparison ({@code ==}), which nothing here relies on - eviction is by key throughout.
     */
    private static int rowsOf(Object key, Object value) {
        return switch (value) {
            case Collection<?> c -> Math.max(1, c.size());
            case Optional<?> o when o.orElse(null) instanceof Collection<?> c -> Math.max(1, c.size());
            default -> 1;
        };
    }

    @Bean
    @Primary
    public CacheManager defaultCacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder().expireAfterWrite(30, TimeUnit.MINUTES).maximumSize(50_000));
        return manager;
    }

}
