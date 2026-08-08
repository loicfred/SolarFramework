package org.solarframework.db.spring;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.solarframework.db.api.*;
import org.solarframework.db.api.dto.DatabaseStats;
import org.solarframework.db.api.dto.TableStats;
import org.solarframework.db.exception.DataMigrationException;
import org.solarframework.json.JSONItem;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.solarframework.core.util.ClassUtils.*;
import static org.solarframework.db.spring.DBInstanceService.CachedFields;
import static org.solarframework.db.spring.DBInstanceService.LazyFields;
import static org.solarframework.db.spring.DBInstanceService.IdFields;
import static org.solarframework.db.spring.DatabaseConfig.defaultConnectionString;
import static org.solarframework.db.spring.DatabaseObject.*;
import static org.solarframework.db.spring.DatabaseRegistry.DefaultDBService;
import static org.solarframework.db.spring.DatabaseRegistry.SolarDBManager;

@Service
public class DatabaseManager implements IDatabaseManager {
    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);

    @JsonIgnore
    private transient final Map<String, ClassLoader> entityClassloaders = new HashMap<>(Map.of("app", Thread.currentThread().getContextClassLoader()));
    @JsonIgnore
    private transient final Set<IDatabaseService> storedDataSources = new HashSet<>();
    @JsonIgnore
    private transient final CacheManager dbCacheManager;
    @JsonIgnore
    private transient List<DatabaseObject<?>> bundleObjects;
    @JsonIgnore
    private transient Instant Cooldown = Instant.now().minusSeconds(5);
    @JsonIgnore
    private transient final ConfigurableApplicationContext context;

    protected DatabaseManager(@Qualifier("databaseCacheManager") CacheManager dbCacheManager, DatabaseService defaultService, ConfigurableApplicationContext context) {
        defaultConnectionString = defaultService.getConnectionString();
        this.dbCacheManager = dbCacheManager;
        this.context = context;
        defaultService.setManager(this);
        addSource(defaultService);
        DefaultDBService = defaultService;
        SolarDBManager = this;
        reload();
    }

    /** Used by DatabaseService#getJpaBeans to lazily build its own JPA beans on first real use. */
    ConfigurableApplicationContext getContext() {
        return context;
    }

    public CacheManager getDbCacheManager() {
        return dbCacheManager;
    }

    public void reload() {
        if (Instant.now().isAfter(Cooldown)) {
            Cooldown = Instant.now().plusSeconds(5);
            IdFields.clear();
            TableNames.clear();
            CachedFields.clear();
            LazyFields.clear();
            DatabaseService.clearLazySelects();
            serviceCache.clear();
            resetAllCaches();

            if (IsSingleSource()) {
                if (entityClassloaders.isEmpty()) entityClassloaders.put("app", Thread.currentThread().getContextClassLoader());
                DatabaseUtils.scanEntitiesOfLoaders(new HashSet<>(entityClassloaders.values()), classes -> {
                    getDefaultService().setEntities(classes.stream().map((c) -> new EntityInfo(c, getEntityClassloaderKey(c.getClassLoader()))).collect(Collectors.toSet()));
                });
            } else {
                for (IDatabaseService ds : getSources()) {
                    ds.reload();
                }
            }
            scanBundleContainers();
        }
    }

    public boolean IsSingleSource() {
        return getSources().size() == 1;
    }

    public void verifyEntities() {
        for (IDatabaseService ds : storedDataSources) {
            DatabaseStats stats = ds.getDatabaseStats();
            log.info("Verifying entities for [{}] - {} Tables - {} Views - {} Rows", ds.getName(), stats.totalTables, stats.totalViews, stats.totalRows);
            for (Class<?> C : ds.getEntitiesClasses()) verifyEntity(ds, C);
        }
    }
    public void verifyEntity(IDatabaseService ds, Class<?> C) {
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
                    RegisterBundleObjects(C);
                }
                warnAboutCrossSourceRelations(ds, C);
            } else {
                log.error("Entity class [{}] is NOT CHILD of DatabaseObject<>.", C.getName());
            }
        } catch (Exception e) {
            e.printStackTrace();
            log.error("{} does not have a default constructor.", C.getName());
        }
    }

    /**
     * A OneToMany/ManyToOne/OneToOne/ManyToMany pointing at an entity registered for a different data source
     * can never be resolved by a real SQL join - they are different connections, possibly different database
     * engines entirely - and a write spanning both is not covered by either source's own transaction. Single
     * source setups can never trigger this (getServiceByEntity always resolves to the one source there), so
     * it only fires once a second source is actually registered.
     */
    private void warnAboutCrossSourceRelations(IDatabaseService ds, Class<?> C) {
        for (Class<?> c = C; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (!(f.isAnnotationPresent(OneToMany.class) || f.isAnnotationPresent(ManyToOne.class)
                        || f.isAnnotationPresent(OneToOne.class) || f.isAnnotationPresent(ManyToMany.class))) continue;
                Class<?> target = Collection.class.isAssignableFrom(f.getType()) ? getGenericOf(f, 0) : f.getType();
                if (target == null) continue;
                IDatabaseService targetService = getServiceByEntity(target);
                if (targetService != null && targetService != ds) {
                    log.warn("Entity [{}] field [{}] relates to [{}], which is registered for a different data source ([{}] vs [{}]) - cross-source relations cannot be resolved by a real SQL join and are not covered by either source's transactions.",
                            C.getName(), f.getName(), target.getName(), ds.getName(), targetService.getName());
                }
            }
        }
    }

    public void RegisterBundleObjects(Class<?> C) {
        for (Object item : bundleObjects.stream().filter(bo -> bo.getClass() == C).toList())
            if (item instanceof DatabaseObject<?> i)
                i.Upsert();
    }

    public IDatabaseService makeNewSource(Set<IEntityInfo> entities) {
        DatabaseService ds = new DatabaseService(dbCacheManager);
        ds.setManager(this);
        ds.setEntities(entities);
        return ds;
    }

    public void createAllSchemasIfMissing() {
        for (IDatabaseService ds : storedDataSources) {
            ds.createSchemaIfMissing(ds.getEntitiesClasses());
        }
    }

    public boolean addSource(IDatabaseService ds) {
        if (ds.getConnectionString().isEmpty() || ds.getName().isEmpty() || ds.getPassword().isEmpty() || ds.getUsername().isEmpty() || getSources().stream().anyMatch(d -> d.getConnectionString().equalsIgnoreCase(ds.getConnectionString()) || d.getName().equals(ds.getName()))) return false;
        if (getDefaultService() != null && ds.isDefault()) return false;
        storedDataSources.add(ds);
        // No JPA registration here on purpose - DatabaseService#getJpaBeans builds it lazily on first real
        // use, so adding many sources (plugin-driven consumers can add several) stays cheap for the common
        // case of a source nothing ever opens a transaction against.
        return true;
    }
    public boolean removeNonDefaultSources() {
        return getSources().removeIf(ds -> {
            if (!ds.isDefault()) {
                ds.reload();
                if (ds instanceof DatabaseService concrete) JpaSourceRegistrar.unregister(concrete, context);
            }
            return !ds.isDefault();
        });
    }
    public Set<IDatabaseService> getSources() {
        return storedDataSources;
    }


    public <T> IDatabaseService.ENTITY<T> getEntityService(Class<T> entity) {
        return new DatabaseService.ENTITY<>(entity, IsSingleSource() ? getDefaultService() : getServiceByEntity(entity));
    }

    public IDatabaseService getService(String name) {
        if (IsSingleSource()) return getDefaultService();
        return getSources().stream().filter(ds -> ds.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
    }
    public IDatabaseService getServiceByEntity(String className) {
        if (IsSingleSource()) return getDefaultService();
        return getSources().stream().filter(ds -> ds.getEntities().stream().anyMatch(e -> e.getClassName().equals(className))).findFirst().orElse(null);
    }
    public IDatabaseService getServiceByEntity(Class<?> entity) {
        return getServiceByEntity(entity.getName());
    }

    public IDatabaseService getDefaultService() {
        return getSources().stream().filter(IDatabaseService::isDefault).findFirst().orElse(null);
    }



    // ====== SHORT CUTS ======

    public <T> Optional<T> getById(String select, Class<T> clazz, Object... id) {
        return getServiceByEntity(clazz).getById(select, clazz, id);
    }
    public <T> Optional<T> getById(Class<T> clazz, Object... id) {
        return getServiceByEntity(clazz).getById(clazz, id);
    }

    public <T> Optional<T> getWhere(String select, Class<T> clazz, String whereClause, Object... args) {
        return getServiceByEntity(clazz).getWhere(select, clazz, whereClause, args);
    }
    public <T> Optional<T> getWhere(Class<T> clazz, String whereClause, Object... args) {
        return getServiceByEntity(clazz).getWhere(clazz, whereClause, args);
    }

    public <T> List<T> getAll(String select, Class<T> clazz) {
        return getServiceByEntity(clazz).getAll(select, clazz);
    }
    public <T> List<T> getAll(Class<T> clazz) {
        return getServiceByEntity(clazz).getAll(clazz);
    }
    public <T> List<T> getAllWhere(String select, Class<T> clazz, String whereClause, Object... args) {
        return getServiceByEntity(clazz).getAllWhere(select, clazz, whereClause, args);
    }
    public <T> List<T> getAllWhere(Class<T> clazz, String whereClause, Object... args) {
        return getServiceByEntity(clazz).getAllWhere(clazz, whereClause, args);
    }
    public <T> Set<T> getAllWhereDistinct(String select, Class<T> clazz, String whereClause, Object... args) {
        return getServiceByEntity(clazz).getAllWhereDistinct(select, clazz, whereClause, args);
    }
    public <T> Set<T> getAllWhereDistinct(Class<T> clazz, String whereClause, Object... args) {
        return getServiceByEntity(clazz).getAllWhereDistinct(clazz, whereClause, args);
    }

    public <T> int Count(Class<T> clazz) {
        return getServiceByEntity(clazz).Count(clazz);
    }
    public <T> int Count(Class<T> clazz, String whereClause, Object... args) {
        return getServiceByEntity(clazz).Count(clazz, whereClause, args);
    }

    public <T> T getRandom(String select, Class<T> clazz) {
        return getServiceByEntity(clazz).getRandom(select, clazz);
    }
    public <T> T getRandom(Class<T> clazz) {
        return getServiceByEntity(clazz).getRandom(clazz);
    }
    public <T> T getRandom(String select, Class<T> clazz, String whereClause, Object... args) {
        return getServiceByEntity(clazz).getRandom(select, clazz, whereClause, args);
    }
    public <T> T getRandom(Class<T> clazz, String whereClause, Object... args) {
        return getServiceByEntity(clazz).getRandom(clazz, whereClause, args);
    }

    /** Drops instance identity too: after this, a read of a row you already hold builds a new object instead of refreshing yours. */
    public void resetAllCaches() {
        dbCacheManager.getCacheNames().forEach(c -> dbCacheManager.getCache(c).clear());
        EntityIdentity.clear();
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
            if (cacheItem instanceof Optional<?> optV && optV.isPresent() && optV.get() instanceof DatabaseObject<?> inCache) {
                if (inCache.getHashedIdentifier().contains(dbobject.getHashedIdentifier())) {
                    copyObject(inCache, dbobject);
                }
            } else if (cacheItem instanceof Collection<?> itemList) {
                if (itemList.isEmpty()) cache.evict(key);
                for (Object element : itemList) {
                    if (element instanceof DatabaseObject<?> inCache) {
                        if (inCache.getHashedIdentifier().contains(dbobject.getHashedIdentifier())) {
                            copyObject(inCache, dbobject);
                        }
                    }
                }
            }
        });
    }
    public void resetCacheForClass(Class<?> dbClazz, boolean items, boolean lists) {
        Cache cache = dbCacheManager.getCache("DBObject");
        if (cache == null || (!items && !lists)) return;
        com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache = (com.github.benmanes.caffeine.cache.Cache<Object, Object>) cache.getNativeCache();
        nativeCache.asMap().forEach((key, cacheItem) -> {
            // Single results are cached wrapped in Optional (see DatabaseService.doQuery); unwrap before matching.
            Object value = cacheItem instanceof Optional<?> opt ? opt.orElse(null) : cacheItem;
            if (value == null) { // cached empty result: the write may have created a row that now matches
                cache.evict(key);
            } else if (value instanceof Collection<?> col) {
                if (lists && (col.isEmpty() || col.stream().anyMatch(e -> isAffectedByChangeOf(e, dbClazz)))) {
                    cache.evict(key);
                }
            } else if (items && isAffectedByChangeOf(value, dbClazz)) {
                cache.evict(key);
            }
        });
    }

    /**
     * A cached object is stale after a write to {@code changed} when it is of that class itself,
     * or when either side declares a mapped association to the other — loaded relations
     * (e.g. User.orders) embed the other entity's rows inside the cached object.
     * Non-entity values (scalars like COUNT results, Rows) cannot be attributed to a table,
     * so they are evicted on any write.
     */
    private boolean isAffectedByChangeOf(Object cached, Class<?> changed) {
        if (!(cached instanceof DatabaseObject<?>)) return true;
        Class<?> cachedClass = cached.getClass();
        return isClassRelated(cachedClass, changed) || hasRelationTo(cachedClass, changed) || hasRelationTo(changed, cachedClass);
    }

    private boolean hasRelationTo(Class<?> from, Class<?> to) {
        for (Class<?> c = from; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (!(f.isAnnotationPresent(OneToMany.class) || f.isAnnotationPresent(ManyToOne.class)
                        || f.isAnnotationPresent(OneToOne.class) || f.isAnnotationPresent(ManyToMany.class))) continue;
                Class<?> target = Collection.class.isAssignableFrom(f.getType()) ? getGenericOf(f, 0) : f.getType();
                if (isClassRelated(target, to)) return true;
            }
        }
        return false;
    }

    @Cacheable(value = "DBData", key = "'ALLDBSTATISTICS'", unless = "#result == null", cacheManager = "databaseCacheManager")
    public DatabaseStats getAllDatabaseStats() {
        DatabaseStats full = new DatabaseStats();
        for (IDatabaseService ds : getSources()) {
            try {
                DatabaseStats stats = ds.getDatabaseStats();
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
        return getServiceByEntity(clazz).getTableStats(getTableName(clazz));
    }

    public void setEntityClassLoaders(Map<String, ClassLoader> entityClassloaders) {
        this.entityClassloaders.clear();
        this.entityClassloaders.putAll(entityClassloaders);
    }
    public void addEntityClassLoader(String name, ClassLoader entityClassloaders) {
        this.entityClassloaders.put(name, entityClassloaders);
    }
    public Map<String, ClassLoader> getEntityClassloaders() {
        return entityClassloaders;
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
        try (ScanResult scanResult = DatabaseUtils.scannerOf(entityClassloaders.values()).scan()) {
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
        List<DatabaseService> readSources = db.getSources();
        if (readSources.isEmpty()) {
            readSources = new DataSourceFile(this).WriteTo(path).getSources();
            log.warn("No database sources found, re-creating source file using default sources.");
        }
        if (readSources.stream().filter(IDatabaseService::isDefault).count() != 1) {
            readSources = new DataSourceFile(this).WriteTo(path).getSources();
            log.warn("None or multiple default database sources found, re-creating source file using default sources.");
        }

        DatabaseService defaultSource = readSources.stream().filter(IDatabaseService::isDefault).findFirst().orElseThrow();
        List<DatabaseService> otherSources = readSources.stream().filter((IDatabaseService s) -> !s.isDefault()).toList();

        getDefaultService().setEntities(defaultSource.getEntities());
        removeNonDefaultSources();
        for (DatabaseService ds : otherSources) {
            ds.setManager(this);
            ds.setDbCacheManager(dbCacheManager);
            addSource(ds);
        }

        Cooldown = Instant.now().minusSeconds(1);
        reload();
    }
    public void SaveAsFile(String path) {
        if (getSources().isEmpty()) {
            log.error("Failed to write data source file, no database sources found.");
        } else if (getSources().stream().filter(IDatabaseService::isDefault).count() != 1) {
            log.warn("Failed to write data source file, none or multiple default database sources found.");
        } else {
            new DataSourceFile(this).WriteTo(path);
        }
    }


    protected static class DataSourceFile extends JSONItem<DataSourceFile> {
        private final List<DatabaseService> sources = new ArrayList<>();
        public DataSourceFile(IDatabaseManager manager) {
            for (IDatabaseService ds : manager.getSources()) {
                sources.add((DatabaseService) ds);
            }
        }

        public List<DatabaseService> getSources() {
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




    private static final int BATCH = 500;

    /**
     * Migrates a single entity's table from the old source to the new one.
     *
     * @return number of rows copied
     * @throws DataMigrationException if the copy fails; target changes for this table are rolled back
     */
    public long migrate(IDatabaseService ioldSource, IDatabaseService inewSource, IEntityInfo entity) throws DataMigrationException {
        DatabaseService oldSource = (DatabaseService) ioldSource;
        DatabaseService newSource = (DatabaseService) inewSource;
        var stats = entity.getTableStats();
        String table = stats.getTableName();
        try {
            List<String> cols = stats.getColumnNames();
            List<String> pks = stats.getColumnDetails().stream().filter(TableStats.ColumnDetail::isPrimaryKey).map(TableStats.ColumnDetail::getName).toList();
            String orderBy = String.join(", ", pks.isEmpty() ? cols : pks);
            String colList = String.join(", ", cols);
            String select = "SELECT " + colList + " FROM " + table + " ORDER BY " + orderBy + " LIMIT ? OFFSET ?";
            String insert = "INSERT INTO " + table + " (" + colList + ") VALUES ("
                    + cols.stream().map(c -> "?").collect(Collectors.joining(", ")) + ")";

            JdbcTemplate from = oldSource.getJdbcTemplate();
            JdbcTemplate to = newSource.getJdbcTemplate();

            log.info("Migrating table '{}' ({} -> {})", table, oldSource.getName(), newSource.getName());
            Long copied = newSource.getTransactionTemplate().execute(status -> {
                long offset = 0;
                List<Map<String, Object>> rows;
                do {
                    rows = from.queryForList(select, BATCH, offset);
                    if (!rows.isEmpty()) {
                        Set<String> temporal = entity.getTableStats().getTimeColumns();
                        to.batchUpdate(insert, rows, BATCH, (ps, row) -> {
                            int i = 1;
                            for (String c : cols) {
                                Object v = row.get(c);
                                ps.setObject(i++, temporal.contains(c.toLowerCase()) ? normalize(v) : v);
                            }
                        });
                    }

                    offset += rows.size();
                } while (rows.size() == BATCH);
                return offset;
            });
            log.info("Migrated {} rows of '{}'", copied, table);
            return copied != null ? copied : 0;
        } catch (Exception ex) {
            log.error("Migration failed for table '{}', target rolled back", table, ex);
            throw new DataMigrationException(table, ex);
        } finally {
            oldSource.reload();
            newSource.reload();
        }
    }

    /**
     * Migrates several entities, parents first so foreign key constraints hold.
     * Stops at the first failure; already-migrated tables remain on the target.
     *
     * @return classNames of successfully migrated entities
     */
    public List<String> migrate(IDatabaseService oldSource, IDatabaseService newSource, Collection<IEntityInfo> entities) throws DataMigrationException {
        List<String> migrated = new ArrayList<>();
        for (IEntityInfo entity : insertOrder(entities)) {
            migrate(oldSource, newSource, entity);
            migrated.add(entity.getClassName());
        }
        return migrated;
    }

    /** Orders entities so ManyToOne/OneToOne targets are inserted before their dependents. */
    private List<IEntityInfo> insertOrder(Collection<IEntityInfo> entities) {
        Map<String, IEntityInfo> byName = new HashMap<>();
        for (IEntityInfo e : entities) byName.put(e.getClassName(), e);
        List<IEntityInfo> ordered = new ArrayList<>();
        Set<String> done = new HashSet<>();
        for (IEntityInfo e : entities) visit(e, byName, done, ordered, new HashSet<>());
        return ordered;
    }

    private void visit(IEntityInfo e, Map<String, IEntityInfo> byName, Set<String> done, List<IEntityInfo> out, Set<String> path) {
        if (done.contains(e.getClassName()) || !path.add(e.getClassName())) return; // path guard breaks cycles
        for (IEntityInfo.Relation r : e.getRelations()) {
            IEntityInfo dep = byName.get(r.getForeignClassName());
            if (dep != null && ("ManyToOne".equals(r.getRelationType()) || "OneToOne".equals(r.getRelationType())))
                visit(dep, byName, done, out, path);
        }
        done.add(e.getClassName());
        out.add(e);
    }


    private static Object normalize(Object v) {
        return switch (v) {
            case null -> null;
            case java.time.Instant i -> java.sql.Timestamp.from(i);
            case java.time.OffsetDateTime o -> java.sql.Timestamp.from(o.toInstant());
            case java.time.ZonedDateTime z -> java.sql.Timestamp.from(z.toInstant());
            case java.time.LocalDateTime l -> java.sql.Timestamp.valueOf(l);
            case java.time.LocalDate d -> java.sql.Date.valueOf(d);          // DATE columns
            case java.time.LocalTime t -> java.sql.Time.valueOf(t);          // TIME columns
            case Long epoch -> new java.sql.Timestamp(epoch);                // SQLite INTEGER storage
            case String s -> parseTemporal(s);
            default -> v;
        };
    }
    private static Object parseTemporal(String s) {
        try { return java.sql.Timestamp.from(java.time.Instant.parse(s)); }                    // ...Z
        catch (Exception e1) { /* fall through */ }
        try { return java.sql.Timestamp.valueOf(java.time.LocalDateTime.parse(s)); }           // ISO, no zone
        catch (Exception e2) { /* fall through */ }
        try { return java.sql.Timestamp.valueOf(s); }                                          // "2026-07-04 00:22:08"
        catch (Exception e3) { return s; }                                                     // let the driver decide
    }
}
