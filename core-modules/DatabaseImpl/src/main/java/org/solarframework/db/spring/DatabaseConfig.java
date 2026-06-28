package org.solarframework.db.spring;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.solarframework.db.spring.DatabaseRegistry.DefaultDBService;
import static org.solarframework.db.spring.DatabaseRegistry.SolarDBManager;

@Configuration
@EnableCaching
public class DatabaseConfig {

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

    @Value("${spring.datasource.url:#{null}}")
    private String connectionString;
    @Value("${spring.datasource.username:#{null}}")
    public String username;
    @Value("${spring.datasource.password:#{null}}")
    public String password;
    @Value("${spring.datasource.driver-class-name:#{null}}")
    public String type;
    @Value("${spring.datasource.hikari.pool-name:#{null}}")
    public String name;
    @Value("${spring.datasource.hikari.maximum-pool-size:#{null}}")
    public int maxPoolSize;
    @Value("${spring.datasource.hikari.minimum-idle:#{null}}")
    public int minimumIdle;
    @Value("${spring.datasource.hikari.idle-timeout:#{null}}")
    public long idleTimeout;
    @Value("${spring.datasource.hikari.max-lifetime:#{null}}")
    public long maxLifetime;
    @Value("${spring.datasource.hikari.connection-timeout:#{null}}")
    public long connectionTimeout;

}
