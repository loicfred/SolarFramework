package org.solarframework.db.spring;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import jakarta.persistence.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.solarframework.db.api.IDBObjectService;
import org.solarframework.db.api.IDatabaseService;
import org.solarframework.db.api.dto.DatabaseStats;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;

import static org.solarframework.core.util.ClassUtils.copyObject;
import static org.solarframework.core.util.ClassUtils.isClassRelated;
import static org.solarframework.db.spring.DBObjectService.*;
import static org.solarframework.db.spring.DatabaseObject.serviceCache;

@Service
public class DatabaseManager {
    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);
    private final List<AvailableDataSource> dataSources = new java.util.ArrayList<>();
    private List<ClassLoader> entityClassloaders = new java.util.ArrayList<>();

    protected CacheManager dbCacheManager;

    private boolean IsSingleSource = true;

    protected DatabaseManager(@Qualifier("databaseCacheManager") CacheManager dbCacheManager) {
        this.dbCacheManager = dbCacheManager;
    }

    public void reload() {
        IdFields.clear();
        TableNames.clear();
        CachedFields.clear();
        serviceCache.clear();
        resetAllCaches();
        for (AvailableDataSource ds : dataSources) {
            DatabaseStats stats = ds.getService().getDatabaseStats();
            log.info("Verifying entities for [{}] - {} Tables - {} Views - {} Rows", ds.getName(), stats.totalTables, stats.totalViews, stats.totalRows);
            List<Class<?>> availableEntities = (IsSingleSource ? findEntities() : ds.getEntitiesClasses());
            for (Class<?> C : availableEntities)
                loadEntity(ds, C);
            for (String E : ds.getInstalledEntities().stream().filter(e -> availableEntities.stream().noneMatch(c -> Objects.equals(TableNames.get(c), e))).toList())
                log.warn("Entity class [{}] is missing for [{}].", E, ds.getName());
        }
    }

    protected static void loadEntity(AvailableDataSource ds, Class<?> C) {
        try {
            if (C.getDeclaredConstructor().newInstance() instanceof DatabaseObject<?>) {
                if (ds.getMissingEntities().contains(C.getSimpleName())) {
                    log.error("Entity [{}] is registered for [{}] but is missing from the database.", C.getName(), ds.getName());
                } else if (ds.getUpdatableEntities().contains(C.getSimpleName())) {
                    log.warn("Entity [{}] is registered for [{}] but needs to be updated.", C.getName(), ds.getName());
                } else {
                    log.info("Entity [{}] is registered for [{}].", C.getName(), ds.getName());
                }
            } else {
                log.error("Entity class [{}] is NOT CHILD of DatabaseObject<>.", C.getName());
            }
        } catch (Exception ignored) {
            log.error("{} does not have a default constructor.", C.getName());
        }
    }


    public void createAllSchemas() {
        for (AvailableDataSource ds : dataSources) {
            ds.getService().createSchema(ds.getEntitiesClasses());
        }
    }
    public void updateAllSchemas() {
        for (AvailableDataSource ds : dataSources) {
            ds.getService().updateSchema(ds.getEntitiesClasses());
        }
    }

    public void addSource(AvailableDataSource ds) {
        if (ds.getConnectionString().isEmpty() || ds.getName().isEmpty() || ds.getPassword().isEmpty() || ds.getUsername().isEmpty()
                || getSources().stream().anyMatch(d -> d.getConnectionString().equalsIgnoreCase(ds.getConnectionString()) || d.getName().equals(ds.getName()))) return;
        dataSources.add(ds);
        IsSingleSource = dataSources.size() == 1;
    }
    public boolean removeSource(String name) {
        if (getSources().stream().anyMatch(ds -> ds.getName().equals(name) && ds.isDefault())) return false;
        return getSources().removeIf(ds -> ds.getName().equals(name));
    }
    public List<AvailableDataSource> getSources() {
        return dataSources;
    }

    public IDatabaseService getService(String name) {
        if (IsSingleSource) return getDefaultService();
        return getAvailableSource(name).getService();
    }
    public IDatabaseService getService(Class<?> entity) {
        if (IsSingleSource) return getDefaultService();
        return getAvailableSource(entity).getService();
    }

    public AvailableDataSource getAvailableSource(String name) {
        if (IsSingleSource) return getDefaultAvailableSource();
        return getSources().stream().filter(ds -> ds.getEntities().contains(name) || ds.getName().equalsIgnoreCase(name)).findFirst().orElseThrow();
    }
    public AvailableDataSource getAvailableSource(Class<?> entity) {
        if (IsSingleSource) return getDefaultAvailableSource();
        return getSources().stream().filter(ds -> ds.getEntities().contains(entity.getName())).findFirst().orElseThrow();
    }

    public AvailableDataSource getDefaultAvailableSource() {
        return getSources().stream().filter(AvailableDataSource::isDefault).findFirst().orElse(null);
    }
    public IDatabaseService getDefaultService() {
        return getDefaultAvailableSource().getService();
    }



    // ====== SHORT CUTS ======

    public <T> Optional<T> getByIdWithJoins(Class<T> clazz, Object... id) {
        return getService(clazz).getByIdWithJoins(clazz, id);
    }
    public <T> Optional<T> getById(String select, Class<T> clazz, Object... id) {
        return getService(clazz).getById(select, clazz, id);
    }
    public <T> Optional<T> getById(Class<T> clazz, Object... id) {
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
        dbCacheManager.getCacheNames().forEach(c -> dbCacheManager.getCache(c).clear());
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
                full.totalViews += stats.totalViews;
                full.tableNames.addAll(stats.tableNames);
                full.viewNames.addAll(stats.viewNames);
                return stats;
            } catch (Exception ignored) {
                log.warn("Unable to get databse stats for {}", ds.getName());
            }
        }
        return full;
    }

    public void setEntityClassLoaders(List<ClassLoader> entityClassloaders) {
        this.entityClassloaders = entityClassloaders;
    }


    private List<Class<?>> findEntities() {
        List<Class<?>> L = new ArrayList<>();
        if (entityClassloaders.isEmpty()) entityClassloaders.add(Thread.currentThread().getContextClassLoader());
        try (ScanResult scanResult = new ClassGraph().enableClassInfo().enableAnnotationInfo().overrideClassLoaders(entityClassloaders.toArray(new ClassLoader[]{})).scan()) {
            for (ClassInfo classInfo : scanResult.getClassesWithAnnotation(Entity.class).stream().toList()) {
                L.add(classInfo.loadClass());
            }
        }
        return L;
    }
}
