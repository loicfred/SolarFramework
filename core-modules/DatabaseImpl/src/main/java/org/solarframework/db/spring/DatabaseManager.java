package org.solarframework.db.spring;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.solarframework.db.api.*;
import org.solarframework.db.api.dto.DatabaseStats;
import org.solarframework.db.api.dto.TableStats;
import org.solarframework.db.api.IEntityInfo;
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
import java.util.stream.Collectors;

import static org.solarframework.core.util.ClassUtils.copyObject;
import static org.solarframework.core.util.ClassUtils.isClassRelated;
import static org.solarframework.db.spring.DBInstanceService.*;
import static org.solarframework.db.spring.DatabaseObject.*;
import static org.solarframework.db.spring.DatabaseRegistry.DefaultDBService;
import static org.solarframework.db.spring.DatabaseRegistry.SolarDBManager;
import static org.solarframework.db.spring.DatabaseConfig.defaultConnectionString;
import static org.solarframework.json.JSONItem.GSON;

@Service
public class DatabaseManager implements IDatabaseManager {
    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);

    @JsonIgnore
    private transient final Map<String, ClassLoader> entityClassloaders = new HashMap<>(Map.of("app", Thread.currentThread().getContextClassLoader()));
    @JsonIgnore
    private transient final Set<IStoredDataSource> storedDataSources = new HashSet<>();
    @JsonIgnore
    private transient final CacheManager dbCacheManager;
    @JsonIgnore
    private transient List<DatabaseObject<?>> bundleObjects;
    @JsonIgnore
    private transient Instant Cooldown = Instant.now().minusSeconds(5);

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
        IStoredDataSource s = makeNewSource(new HashSet<>());
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

            if (IsSingleSource()) {
                if (entityClassloaders.isEmpty()) entityClassloaders.put("app", Thread.currentThread().getContextClassLoader());
                DatabaseUtils.scanEntitiesOfLoaders(new HashSet<>(entityClassloaders.values()), classes -> {
                    getDefaultAvailableSource().setEntities(classes.stream().map((c) -> new EntityInfo(c, getEntityClassloaderKey(c.getClassLoader()))).collect(Collectors.toSet()));
                });
            } else {
                for (IStoredDataSource ds : getSources()) {
                    for (IEntityInfo EI : ds.getEntities()) {
                        entityClassloaders.entrySet().stream().filter(cl -> Objects.equals(cl.getKey(), EI.getClassLoader())).findFirst().ifPresent((cl) -> {
                            try {
                                EI.Update(cl.getValue().loadClass(EI.getClassName()));
                            } catch (ClassNotFoundException _) {}
                        });
                    }
                }
            }
            scanBundleContainers();
        }
    }

    public boolean IsSingleSource() {
        return getSources().size() == 1;
    }

    public void verifyEntities() {
        for (IStoredDataSource ds : storedDataSources) {
            DatabaseStats stats = ds.getService().getDatabaseStats();
            log.info("Verifying entities for [{}] - {} Tables - {} Views - {} Rows", ds.getName(), stats.totalTables, stats.totalViews, stats.totalRows);
            for (Class<?> C : ds.getEntitiesClasses()) verifyEntity(ds, C);
        }
    }
    public void verifyEntity(IStoredDataSource ds, Class<?> C) {
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
                            i.Upsert();
                }
            } else {
                log.error("Entity class [{}] is NOT CHILD of DatabaseObject<>.", C.getName());
            }
        } catch (Exception ignored) {
            log.error("{} does not have a default constructor.", C.getName());
        }
    }

    public IStoredDataSource makeNewSource(Set<IEntityInfo> entities) {
        IStoredDataSource es = new StoredDataSource(this);
        es.setEntities(entities);
        return es;
    }

    public void createAllSchemas() {
        for (IStoredDataSource ds : storedDataSources) {
            ds.getService().createSchema(ds.getEntitiesClasses());
        }
    }
    public void updateAllSchemas() {
        for (IStoredDataSource ds : storedDataSources) {
            ds.getService().updateSchema(ds.getEntitiesClasses());
        }
    }

    public boolean addSource(IStoredDataSource ds) {
        if (ds.getConnectionString().isEmpty() || ds.getName().isEmpty() || ds.getPassword().isEmpty() || ds.getUsername().isEmpty() || getSources().stream().anyMatch(d -> d.getConnectionString().equalsIgnoreCase(ds.getConnectionString()) || d.getName().equals(ds.getName()))) return false;
        if (getDefaultAvailableSource() != null && ds.isDefault()) return false;
        storedDataSources.add(ds);
        return true;
    }
    public boolean removeSource(String connectionString) {
        if (getSources().stream().anyMatch(ds -> ds.getConnectionString().equals(connectionString) && ds.isDefault())) return false;
        getSources().removeIf(ds -> ds.getConnectionString().equals(connectionString));
        return true;
    }
    public Set<IStoredDataSource> getSources() {
        return storedDataSources;
    }

    public IDatabaseService getService(String name) {
        return IsSingleSource() ? getDefaultService() : getSourceByName(name).getService();
    }
    public IDatabaseService getService(Class<?> entity) {
        return IsSingleSource() ? getDefaultService() : getSourceByEntity(entity).getService();
    }

    public <T> IDatabaseService.ENTITY<T> getEntityService(Class<T> entity) {
        return new DatabaseService.ENTITY<>(entity, IsSingleSource() ? getDefaultService() : getSourceByEntity(entity).getService());
    }

    public IStoredDataSource getSourceByName(String name) {
        if (IsSingleSource()) return getDefaultAvailableSource();
        return getSources().stream().filter(ds -> ds.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
    }
    public IStoredDataSource getSourceByEntity(String className) {
        if (IsSingleSource()) return getDefaultAvailableSource();
        return getSources().stream().filter(ds -> ds.getEntities().stream().anyMatch(e -> e.getClassName().equals(className))).findFirst().orElse(null);
    }
    public IStoredDataSource getSourceByEntity(Class<?> entity) {
        return getSourceByEntity(entity.getName());
    }

    public IStoredDataSource getDefaultAvailableSource() {
        return getSources().stream().filter(IStoredDataSource::isDefault).findFirst().orElse(null);
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
    public void resetCacheForClass(Class<?> dbClazz, boolean items, boolean lists) {
        Cache cache = dbCacheManager.getCache("DBObject");
        if (cache == null || (!items && !lists)) return;
        com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache = (com.github.benmanes.caffeine.cache.Cache<Object, Object>) cache.getNativeCache();
        nativeCache.asMap().forEach((key, cacheItem) -> {
            if (items && cacheItem instanceof DatabaseObject<?> V && isClassRelated(V, dbClazz)) {
                cache.evict(key);
            } else if (lists && cacheItem instanceof List<?> V2) { // If the item cached is a list
                if (!V2.isEmpty() && V2.getFirst() instanceof DatabaseObject<?> V3 && isClassRelated(V3, dbClazz)) { // Check if the datatype of the cache list is the same as the current item
                    cache.evict(key);
                }
            }
        });
    }

    @Cacheable(value = "DBData", key = "'ALLDBSTATISTICS'", unless = "#result == null", cacheManager = "databaseCacheManager")
    public DatabaseStats getAllDatabaseStats() {
        DatabaseStats full = new DatabaseStats();
        for (IStoredDataSource ds : getSources()) {
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

    public void setEntityClassLoaders(Map<String, ClassLoader> entityClassloaders) {
        this.entityClassloaders.clear();
        this.entityClassloaders.putAll(entityClassloaders);
    }
    public void addEntityClassLoader(String name, ClassLoader entityClassloaders) {
        this.entityClassloaders.put(name, entityClassloaders);
    }
    public Set<ClassLoader> getEntityClassloaders() {
        return new HashSet<>(entityClassloaders.values());
    }
    public ClassLoader getEntityClassloader(String key) {
        return entityClassloaders.get(key);
    }
    public String getEntityClassloaderKey(ClassLoader key) {
        return entityClassloaders.entrySet().stream().filter(e -> Objects.equals(e.getValue(), key)).findFirst().map(Map.Entry::getKey).orElse(null);
    }

    private void scanBundleContainers() {
        bundleObjects = new ArrayList<>();
        if (entityClassloaders.isEmpty()) entityClassloaders.put("app", Thread.currentThread().getContextClassLoader());
        try (ScanResult scanResult = new ClassGraph().enableClassInfo().enableAnnotationInfo().overrideClassLoaders(entityClassloaders.values().toArray(new ClassLoader[]{})).scan()) {
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
        DataSourceFile db = DataSourceFile.ReadFrom(path);
        List<StoredDataSource> readSources = db.getSources();
        if (readSources.isEmpty()) {
            readSources = new DataSourceFile(this).WriteTo(path).getSources();
            log.warn("No database sources found, re-creating source file using default sources.");
        }
        if (readSources.stream().filter(IStoredDataSource::isDefault).count() != 1) {
            readSources = new DataSourceFile(this).WriteTo(path).getSources();
            log.warn("None or multiple default database sources found, re-creating source file using default sources.");
        }

        StoredDataSource defaultSource = readSources.stream().filter(IStoredDataSource::isDefault).findFirst().orElseThrow();
        List<StoredDataSource> otherSources = readSources.stream().filter((IStoredDataSource s) -> !s.isDefault()).toList();

        getDefaultAvailableSource().setEntities(defaultSource.getEntities());
        getSources().removeIf(ds -> !ds.isDefault());
        for (StoredDataSource ds : otherSources) {
            ds.setManager(this);
            addSource(ds);
        }

        Cooldown = Instant.now().minusSeconds(1);
        reload();
    }
    public void SaveAsFile(String path) {
        if (getSources().isEmpty()) {
            log.error("Failed to write data source file, no database sources found.");
        } else if (getSources().stream().filter(IStoredDataSource::isDefault).count() != 1) {
            log.warn("Failed to write data source file, none or multiple default database sources found.");
        } else {
            new DataSourceFile(this).WriteTo(path);
        }
    }


    protected static class DataSourceFile extends JSONItem<DataSourceFile> {
        private final List<StoredDataSource> sources = new ArrayList<>();
        public DataSourceFile(IDatabaseManager manager) {
            for (IStoredDataSource ds : manager.getSources()) {
                sources.add((StoredDataSource) ds);
            }
        }

        public List<StoredDataSource> getSources() {
            return sources;
        }
        public DataSourceFile WriteTo(String path) {
            try {
                return WriteJSON(path);
            } catch (Exception _) {
                return null;
            }
        }

        public static DataSourceFile ReadFrom(String path) {
            DataSourceFile db;
            try {
                db = ReadJSON(path, DataSourceFile.class);
                if (db == null) throw new Exception();
            } catch (Exception e) {
                db = new DataSourceFile(SolarDBManager).WriteTo(path) instanceof DataSourceFile dbf ? dbf : new DataSourceFile(SolarDBManager);
            }
            return db;
        }
    }
}
