package org.solarframework.db.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.solarframework.db.api.DatabaseObject;
import org.solarframework.db.api.IDBObjectService;
import org.solarframework.db.api.IDatabaseService;
import org.solarframework.db.api.dto.DatabaseStats;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.solarframework.core.util.ClassUtils.copyObject;
import static org.solarframework.core.util.ClassUtils.isClassRelated;
import static org.solarframework.db.spring.DatabaseUtils.getTableName;
import static org.solarframework.db.spring.DatabaseRegistry.*;

@Service
public class DatabaseManager {
    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);
    private final List<AvailableDataSource> dataSources = new java.util.ArrayList<>();

    protected ApplicationContext context;
    protected CacheManager dbCacheManager;

    public DatabaseManager(ApplicationContext context, @Qualifier("databaseCacheManager") CacheManager dbCacheManager) {
        this.context = context;
        this.dbCacheManager = dbCacheManager;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void setStaticReference() {
        DefaultDBService = context.getBean(DatabaseService.class);
        SolarDBManager = context.getBean(DatabaseManager.class);
    }


    public void addSource(AvailableDataSource ds) {
        if (ds.isDefault()) for (AvailableDataSource d : dataSources) d.setDefault(false);
        dataSources.add(ds);
    }


    public List<AvailableDataSource> getSources() {
        if (dataSources.stream().noneMatch(AvailableDataSource::isDefault) && !dataSources.isEmpty()) dataSources.getFirst().setDefault(true);
        return dataSources;
    }

    public IDatabaseService getService(String name) {
        return getAvailableSource(name).getService();
    }
    public IDatabaseService getService(Class<?> entity) {
        return getAvailableSource(entity).getService();
    }

    public AvailableDataSource getAvailableSource(String name) {
        return getSources().stream().filter(ds -> ds.getEntities().contains(name) || ds.getName().equalsIgnoreCase(name)).findFirst().orElseThrow();
    }
    public AvailableDataSource getAvailableSource(Class<?> entity) {
        return getSources().stream().filter(ds -> ds.getEntities().contains(entity.getName())).findFirst().orElseThrow();
    }

    public AvailableDataSource getDefaultAvailableSource() {
        return getSources().stream().filter(AvailableDataSource::isDefault).findFirst().orElse(null);
    }
    public IDatabaseService getDefaultService() {
        return getDefaultAvailableSource().getService();
    }



    // ====== SHORT CUTS ======

    public <T> Optional<T> getByIdWithJoins(Class<T> clazz, Object id) {
        return getService(clazz).getByIdWithJoins(clazz, id);
    }
    public <T> Optional<T> getById(String select, Class<T> clazz, Object id) {
        return getService(clazz).getById(select, clazz, id);
    }
    public <T> Optional<T> getById(Class<T> clazz, Object id) {
        return getService(clazz).getById(clazz, id);
    }

    public <T> Optional<T> getWhereWithJoins(Class<T> clazz, String whereClause, Object... args) {
        return getService(clazz).getWhereWithJoins(clazz, whereClause, args);
    }
    public <T> Optional<T> getWhere(String select, Class<T> clazz, String whereClause, Object... args) {
        return getService(clazz).getWhere(select, clazz, whereClause, args);
    }
    public <T> Optional<T> getWhere(Class<T> clazz, String whereClause, Object... args) {
        return getService(clazz).getWhere(clazz, whereClause, args);
    }

    public <T> List<T> getAll(String select, Class<T> clazz) {
        return getService(clazz).getAll(select, clazz);
    }
    public <T> List<T> getAll(Class<T> clazz) {
        return getService(clazz).getAll(clazz);
    }
    public <T> List<T> getAllWhere(String select, Class<T> clazz, String whereClause, Object... args) {
        return getService(clazz).getAllWhere(select, clazz, whereClause, args);
    }
    public <T> List<T> getAllWhere(Class<T> clazz, String whereClause, Object... args) {
        return getService(clazz).getAllWhere(clazz, whereClause, args);
    }
    public <T> Set<T> getAllWhereDistinct(String select, Class<T> clazz, String whereClause, Object... args) {
        return getService(clazz).getAllWhereDistinct(select, clazz, whereClause, args);
    }
    public <T> Set<T> getAllWhereDistinct(Class<T> clazz, String whereClause, Object... args) {
        return getService(clazz).getAllWhereDistinct(clazz, whereClause, args);
    }


    public <T> int Count(Class<T> clazz) {
        return getService(clazz).Count(clazz);
    }
    public <T> int Count(Class<T> clazz, String whereClause, Object... args) {
        return getService(clazz).Count(clazz, whereClause, args);
    }

    public <T> T getRandom(String select, Class<T> clazz) {
        return getService(clazz).getRandom(select, clazz);
    }
    public <T> T getRandom(Class<T> clazz) {
        return getService(clazz).getRandom(clazz);
    }
    public <T> T getRandom(String select, Class<T> clazz, String whereClause, Object... args) {
        return getService(clazz).getRandom(select, clazz, whereClause, args);
    }
    public <T> T getRandom(Class<T> clazz, String whereClause, Object... args) {
        return getService(clazz).getRandom(clazz, whereClause, args);
    }

    public void resetAllCaches() {
        dbCacheManager.getCacheNames().stream().filter(c -> !c.equals("DBObject")).toList().forEach(c -> dbCacheManager.getCache(c).clear());
    }
    public void resetCache(String cacheName) {
        Cache cache = dbCacheManager.getCache(cacheName);
        if (cache != null) cache.clear();
    }

    public void resetCacheFor(IDBObjectService<?> dbobject) {
        resetCacheFor(dbobject.getDBObject());
    }
    public void resetCacheFor(DatabaseObject<?> dbobject) {
        Cache cache = dbCacheManager.getCache("DBObject");
        if (cache == null) return;
        com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache = (com.github.benmanes.caffeine.cache.Cache<Object, Object>) cache.getNativeCache();
        nativeCache.asMap().forEach((key, cacheItem) -> {
            if (cacheItem instanceof DatabaseObject<?> V) {
                if (V.getService().getCacheHashes().contains(dbobject.getHashedIdentifier())) {
                    copyObject(V, dbobject);
                }
            } else if (cacheItem instanceof List<?> V2) { // If the item cached is a list
                if (V2.isEmpty()) cache.evict(key);
                if (V2.getFirst() instanceof DatabaseObject<?>) { // Check if the datatype of the cache list is the same as the current item
                    Object found = V2.stream().filter(dbo -> ((DatabaseObject<?>)dbo).getService().getCacheHashes().contains(dbobject.getHashedIdentifier())).findFirst().orElseGet(() -> {
                        cache.evict(key);
                        return null;
                    });
                    if (found != null) copyObject(found, dbobject);
                }
            }
        });
    }
    public void resetCacheForClass(Class<?> dbclazz, boolean items, boolean lists) {
        Cache cache = dbCacheManager.getCache("DBObject");
        if (cache == null || (!items && !lists)) return;
        com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache = (com.github.benmanes.caffeine.cache.Cache<Object, Object>) cache.getNativeCache();
        nativeCache.asMap().forEach((key, cacheItem) -> {
            if (items && cacheItem instanceof DatabaseObject<?> V && isClassRelated(V, dbclazz)) {
                cache.evict(key);
            } else if (lists && cacheItem instanceof List<?> V2) { // If the item cached is a list
                if (!V2.isEmpty() && V2.getFirst() instanceof DatabaseObject<?> V3 && isClassRelated(V3, dbclazz)) { // Check if the datatype of the cache list is the same as the current item
                    cache.evict(key);
                }
            }
        });
    }

    @Cacheable(value = "DBData", key = "'ALLDBSTATISTICS'", unless = "#result == null", cacheManager = "databaseCacheManager")
    public DatabaseStats getAllDatabaseStats() {
        DatabaseStats full = new DatabaseStats();
        for (AvailableDataSource ds : getSources()) {
            try {
                DatabaseStats stats = ds.getService().getDatabaseStats();
                full.totalRows += stats.totalRows;
                full.totalTables += stats.totalTables;
                full.tableNames.addAll(stats.tableNames);
                full.totalViews += stats.totalViews;
                full.viewNames.addAll(stats.viewNames);
                return stats;
            } catch (Exception ignored) {
                log.warn("Unable to get databse stats for {}", ds.getName());
            }
        }
        return full;
    }
}
