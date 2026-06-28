package org.solarframework.db.spring;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import jakarta.persistence.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.solarframework.db.api.BundleEntities;
import org.solarframework.db.api.DatabaseType;
import org.solarframework.db.api.IDBObjectService;
import org.solarframework.db.api.IDatabaseService;
import org.solarframework.db.api.dto.DatabaseStats;
import org.solarframework.db.api.dto.TableStats;
import org.solarframework.json.JSONItem;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.*;

import static org.solarframework.core.util.ClassUtils.copyObject;
import static org.solarframework.core.util.ClassUtils.isClassRelated;
import static org.solarframework.db.spring.DBInstanceService.*;
import static org.solarframework.db.spring.DatabaseObject.getTableName;
import static org.solarframework.db.spring.DatabaseObject.serviceCache;
import static org.solarframework.db.spring.DatabaseRegistry.DefaultDBService;
import static org.solarframework.db.spring.DatabaseRegistry.SolarDBManager;
import static org.solarframework.json.JSONItem.ReadJSON;

@Service
public class DatabaseManager {
    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);
    private transient List<ClassLoader> entityClassloaders = new java.util.ArrayList<>(List.of(Thread.currentThread().getContextClassLoader()));
    private transient final CacheManager dbCacheManager;
    private final List<AvailableDataSource> dataSources = new java.util.ArrayList<>();
    private transient List<DatabaseObject<?>> bundleObjects;

    private transient boolean IsSingleSource = true;
    private transient Instant Cooldown = Instant.now().minusSeconds(5);
    protected static String defaultConnectionString;

    protected DatabaseManager(@Qualifier("databaseCacheManager") CacheManager dbCacheManager,
                              @Value("${spring.datasource.url:#{null}}") String connectionString,
                              @Value("${spring.datasource.username:#{null}}") String username,
                              @Value("${spring.datasource.password:#{null}}") String password,
                              @Value("${spring.datasource.driver-class-name:#{null}}") String type,
                              @Value("${spring.datasource.hikari.pool-name:#{null}}") String name,
                              @Value("${spring.datasource.hikari.maximum-pool-size:#{null}}") Integer maxPoolSize,
                              @Value("${spring.datasource.hikari.minimum-idle:#{null}}") Integer minimumIdle,
                              @Value("${spring.datasource.hikari.idle-timeout:#{null}}") Long idleTimeout,
                              @Value("${spring.datasource.hikari.max-lifetime:#{null}}") Long maxLifetime,
                              @Value("${spring.datasource.hikari.connection-timeout:#{null}}") Long connectionTimeout,
                              DatabaseService defaultService) {
        defaultConnectionString = connectionString;
        this.dbCacheManager = dbCacheManager;
        AvailableDataSource s = new AvailableDataSource(this);
        s.setName("Database (Default)");
        s.setConnectionString(connectionString);
        s.setUsername(username);
        s.setPassword(password);
        s.setType(DatabaseType.fromDriver(type));
        s.setMaxPoolSize(maxPoolSize);
        s.setMinimumIdle(minimumIdle);
        s.setIdleTimeout(idleTimeout);
        s.setMaxLifetime(maxLifetime);
        s.setConnectionTimeout(connectionTimeout);
        addSource(s);
        DefaultDBService = defaultService;
        SolarDBManager = this;
        reload();
    }

    public void reload() {
        if (Instant.now().isAfter(Cooldown)) {
            Cooldown = Instant.now().plusSeconds(5);
            IdFields.clear();
            TableNames.clear();
            CachedFields.clear();
            serviceCache.clear();
            resetAllCaches();
            List<Class<?>> entities = scanEntities();
            if (IsSingleSource) {
                getDefaultAvailableSource().clearEntities();
                getDefaultAvailableSource().addEntities(entities.toArray(new Class<?>[0]));
            }
            for (Class<?> e : entities) {
                try {
                    Constructor<?> constructor = e.getDeclaredConstructor();
                    constructor.setAccessible(true);
                    constructor.newInstance();
                } catch (Exception ignored) {}
                if (getSourceByEntity(e) == null)
                    getDefaultAvailableSource().addEntities(e);
            }
            scanBundleContainers();
        }
    }

    public void verifyEntities() {
        for (AvailableDataSource ds : dataSources) {
            DatabaseStats stats = ds.getService().getDatabaseStats();
            log.info("Verifying entities for [{}] - {} Tables - {} Views - {} Rows", ds.getName(), stats.totalTables, stats.totalViews, stats.totalRows);
            for (Class<?> C : ds.getEntitiesClasses()) verifyEntity(ds, C);
        }
    }

    protected void verifyEntity(AvailableDataSource ds, Class<?> C) {
        try {
            Constructor<?> constructor = C.getDeclaredConstructor();
            constructor.setAccessible(true);
            if (ds.getMissingEntitiesClasses().contains(C)) {
                log.error("Entity [{}] is registered for [{}] but is missing from the database.", C.getName(), ds.getName());
            } else if (constructor.newInstance() instanceof DatabaseObject<?>) {
                if (ds.getUpdatableEntitiesClasses().contains(C)) {
                    log.warn("Entity [{}] is registered for [{}] but needs to be updated.", C.getName(), ds.getName());
                } else {
                    log.info("Entity [{}] is registered for [{}].", C.getName(), ds.getName());

                    for (Object item : bundleObjects.stream().filter(bo -> bo.getClass() == C).toList())
                        if (item instanceof DatabaseObject<?> i)
                            i.Write();
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

    public boolean addSource(AvailableDataSource ds) {
        if (ds.getConnectionString().isEmpty() || ds.getName().isEmpty() || ds.getPassword().isEmpty() || ds.getUsername().isEmpty() || getSources().stream().anyMatch(d -> d.getConnectionString().equalsIgnoreCase(ds.getConnectionString()) || d.getName().equals(ds.getName()))) return false;
        if (getDefaultAvailableSource() != null && ds.isDefault()) return false;
        dataSources.add(ds);
        IsSingleSource = getSources().size() == 1;
        return true;
    }
    public boolean removeSource(String connectionString) {
        if (getSources().stream().anyMatch(ds -> ds.getConnectionString().equals(connectionString) && ds.isDefault())) return false;
        getSources().removeIf(ds -> ds.getConnectionString().equals(connectionString));
        IsSingleSource = getSources().size() == 1;
        return true;
    }
    public boolean clearSources() {
        getSources().removeIf(ds -> !ds.isDefault());
        IsSingleSource = getSources().size() == 1;
        return true;
    }
    public List<AvailableDataSource> getSources() {
        return dataSources;
    }

    public IDatabaseService getService(String name) {
        return IsSingleSource ? getDefaultService() : getSourceByName(name).getService();
    }

    public IDatabaseService getService(Class<?> entity) {
        return IsSingleSource ? getDefaultService() : getSourceByEntity(entity).getService();
    }
    public <T> IDatabaseService.ENTITY<T> getEntityService(Class<T> entity) {
        return new DatabaseService.ENTITY<>(entity, IsSingleSource ? getDefaultService() : getSourceByEntity(entity).getService());
    }

    public AvailableDataSource getSourceByName(String name) {
        if (IsSingleSource) return getDefaultAvailableSource();
        return getSources().stream().filter(ds -> ds.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
    }
    public AvailableDataSource getSourceByEntity(String name) {
        if (IsSingleSource) return getDefaultAvailableSource();
        return getSources().stream().filter(ds -> ds.getEntities().contains(name)).findFirst().orElse(null);
    }
    public AvailableDataSource getSourceByEntity(Class<?> entity) {
        return getSourceByEntity(entity.getName());
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
                else if (V2.getFirst() instanceof DatabaseObject<?>) { // Check if the datatype of the cache list is the same as the current item
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

    public <T> TableStats getTableStats(Class<T> clazz) {
        return getService(clazz).getTableStats(getTableName(clazz));
    }

    public void addEntityClassLoaders(ClassLoader entityClassloaders) {
        this.entityClassloaders.add(entityClassloaders);
    }
    public void setEntityClassLoaders(List<ClassLoader> entityClassloaders) {
        this.entityClassloaders = entityClassloaders;
    }
    public List<ClassLoader> getEntityClassloaders() {
        return entityClassloaders;
    }

    public List<Class<?>> scanEntities() {
        if (entityClassloaders.isEmpty()) entityClassloaders.add(Thread.currentThread().getContextClassLoader());
        return scanEntities(entityClassloaders);
    }
    public List<Class<?>> scanEntities(List<ClassLoader> entityClassloaders) {
        List<Class<?>> L = new ArrayList<>();
        try (ScanResult scanResult = new ClassGraph().enableClassInfo().enableAnnotationInfo().overrideClassLoaders(entityClassloaders.toArray(new ClassLoader[]{})).scan()) {
            for (ClassInfo classInfo : scanResult.getClassesWithAnnotation(Entity.class).stream().toList()) {
                L.add(classInfo.loadClass());
            }
        }
        return L;
    }
    private void scanBundleContainers() {
        bundleObjects = new ArrayList<>();
        if (entityClassloaders.isEmpty()) entityClassloaders.add(Thread.currentThread().getContextClassLoader());
        try (ScanResult scanResult = new ClassGraph().enableClassInfo().enableAnnotationInfo().overrideClassLoaders(entityClassloaders.toArray(new ClassLoader[]{})).scan()) {
            for (ClassInfo classInfo : scanResult.getClassesImplementing(BundleEntities.class).stream().toList()) {
                Constructor<?> cons = classInfo.loadClass().getDeclaredConstructor();
                cons.setAccessible(true);
                if (cons.newInstance() instanceof BundleEntities BE && BE.bundleEntities() != null && BE.bundleEntities().get() != null) {
                    bundleObjects.addAll(BE.bundleEntities().get());
                }
            }
        } catch (Exception _) {}
    }



    public void LoadFromFile(String path) {
        DataSourceFile db;
        try {
            db = ReadJSON(path, DataSourceFile.class);
            if (db == null) throw new Exception();
        } catch (Exception e) {
            db = new DataSourceFile(this).WriteJSON(path) instanceof DataSourceFile dbf ? dbf : new DataSourceFile(this);
        }
        List<AvailableDataSource> readSources = db.getSources();
        if (readSources.isEmpty()) {
            readSources = new DataSourceFile(this).Write(path).getSources();
            log.warn("No database sources found, re-creating source file using default sources.");
        }
        if (readSources.stream().filter(AvailableDataSource::isDefault).count() != 1) {
            readSources = new DataSourceFile(this).Write(path).getSources();
            log.warn("None or multiple default database sources found, re-creating source file using default sources.");
        }

        for (AvailableDataSource ds : readSources) ds.manager = this;
        AvailableDataSource defaultSource = readSources.stream().filter(AvailableDataSource::isDefault).findFirst().orElseThrow();
        List<AvailableDataSource> otherSources = readSources.stream().filter((AvailableDataSource s) -> !s.isDefault()).toList();

        getDefaultAvailableSource().setEntities(defaultSource.getEntitiesClasses());
        for (AvailableDataSource ds : otherSources) addSource(ds);
        Cooldown = Instant.now().minusSeconds(5);
        reload();
    }

    public void SaveAsFile(String path) {
        if (getSources().isEmpty()) {
            log.error("Failed to write data source file, no database sources found.");
        } else if (getSources().stream().filter(AvailableDataSource::isDefault).count() != 1) {
            log.warn("Failed to write data source file, none or multiple default database sources found.");
        } else {
            new DataSourceFile(this).Write(path);
        }
    }


    protected static class DataSourceFile extends JSONItem<DataSourceFile> {
        private final List<AvailableDataSource> sources;
        public DataSourceFile(DatabaseManager manager) {
            sources = new ArrayList<>(manager.getSources());
        }

        public List<AvailableDataSource> getSources() {
            return sources;
        }
        public DataSourceFile Write(String path) {
            try {
                return WriteJSON(path);
            } catch (Exception _) {
                return null;
            }
        }
    }
}
