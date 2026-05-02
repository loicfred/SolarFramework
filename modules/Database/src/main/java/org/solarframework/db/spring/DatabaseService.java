package org.solarframework.db.spring;

import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.*;
import java.util.stream.Collectors;

import static org.solarframework.core.util.ClassUtils.*;
import static org.solarframework.db.spring.DatabaseUtils.*;

@Service
@SuppressWarnings("all")
public class DatabaseService {
    private static final Logger log = LoggerFactory.getLogger(DatabaseService.class);

    public static DatabaseService dbService;
    @EventListener(ApplicationReadyEvent.class)
    public void setStaticReference() {
        DatabaseService.dbService = context.getBean(DatabaseService.class);
    }

    protected final ApplicationContext context;
    protected final CacheManager dbCacheManager;
    protected final DataSource dataSource;
    protected final JdbcTemplate jdbcTemplate;

    public DatabaseService(ApplicationContext context, @Qualifier("databaseCacheManager") CacheManager dbCacheManager, JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.context = context;
        this.dbCacheManager = dbCacheManager;
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    public CacheManager getDbCacheManager() {
        return dbCacheManager;
    }

    // ====== SHORT CUTS ======

    public <T> Optional<T> getByIdWithJoins(Class<T> clazz, Object id) {
        return dbService.doQueryJoin(clazz, "MAIN.ID = ?", id);
    }
    public <T> Optional<T> getById(String select, Class<T> clazz, Object id) {
        return dbService.doQuery(clazz, "SELECT " + select + " FROM " + getTableName(clazz) + " WHERE ID = ? LIMIT 1;", id);
    }
    public <T> Optional<T> getById(Class<T> clazz, Object id) {
        return dbService.getById("*", clazz, id);
    }

    public <T> Optional<T> getWhereWithJoins(Class<T> clazz, String whereClause, Object... args) {
        return dbService.doQueryJoin(clazz, whereClause, args); // ADD MAIN. in front of attributes.
    }
    public <T> Optional<T> getWhere(String select, Class<T> clazz, String whereClause, Object... args) {
        return dbService.doQuery(clazz, "SELECT " + select + " FROM " + getTableName(clazz) + " WHERE " + whereClause + " LIMIT 1;", args);
    }
    public <T> Optional<T> getWhere(Class<T> clazz, String whereClause, Object... args) {
        return dbService.getWhere("*", clazz, whereClause, args);
    }

    public <T> List<T> getAll(String select, Class<T> clazz) {
        return dbService.doQueryAll(clazz, "SELECT " + select + " FROM " + getTableName(clazz), null);
    }
    public <T> List<T> getAll(Class<T> clazz) {
        return dbService.getAll("*", clazz);
    }
    public <T> List<T> getAllWhere(String select, Class<T> clazz, String whereClause, Object... args) {
        return dbService.doQueryAll(clazz, "SELECT " + select + " FROM " + getTableName(clazz) + " WHERE " + whereClause, args);
    }
    public <T> List<T> getAllWhere(Class<T> clazz, String whereClause, Object... args) {
        return dbService.getAllWhere("*", clazz, whereClause, args);
    }
    public <T> Set<T> getAllWhereDistinct(String select, Class<T> clazz, String whereClause, Object... args) {
        return dbService.doQueryAllDistinct(clazz, "SELECT " + select + " FROM " + getTableName(clazz) + " WHERE " + whereClause, args);
    }
    public <T> Set<T> getAllWhereDistinct(Class<T> clazz, String whereClause, Object... args) {
        return dbService.getAllWhereDistinct("*", clazz, whereClause, args);
    }

    public <O, T> Optional<O> getSingleColumnOfTableById(String column, Class<O> item, Class<?> table, Object id) {
        return dbService.doQueryValueNoCache(item, "SELECT " + column + " FROM " + getTableName(table) + " WHERE ID = ? LIMIT 1;", id);
    }
    public <O, T> Optional<O> getSingleColumnOfTableWhere(String column, Class<O> item, Class<?> table, String where, Object... args) {
        return dbService.doQueryValueNoCache(item, "SELECT " + column + " FROM " + getTableName(table) + " WHERE " + where + " LIMIT 1;", args);
    }

    public <T> int Count(Class<T> clazz) {
        return dbService.doQueryValue(Integer.class, "SELECT COUNT(*) FROM " + getTableName(clazz)).orElse(0);
    }
    public <T> int Count(Class<T> clazz, String whereClause, Object... args) {
        return dbService.doQueryValue(Integer.class, "SELECT COUNT(*) FROM " + getTableName(clazz) + " WHERE " + whereClause, args).orElse(0);
    }

    public <T> T getRandom(String select, Class<T> clazz) {
        return dbService.getWhere(select, clazz, "ID >= FLOOR(RAND() * (SELECT MAX(ID) FROM " + getTableName(clazz) + "))").orElse(null);
    }
    public <T> T getRandom(Class<T> clazz) {
        return dbService.getRandom("*", clazz);
    }
    public <T> T getRandom(String select, Class<T> clazz, String whereClause, Object... args) {
        return dbService.getWhere(select, clazz, whereClause + " AND ID >= FLOOR(RAND() * (SELECT MAX(ID) FROM " + getTableName(clazz) + "))", args).orElse(null);
    }
    public <T> T getRandom(Class<T> clazz, String whereClause, Object... args) {
        return dbService.getRandom("*", clazz, whereClause, args);
    }

    // ====== GETTERS ======

    @Cacheable(value = "DBObject", key = "T(java.util.Objects).hash(#clazz, #sql, #args != null ? T(java.util.Arrays).deepHashCode(#args) : null)", unless = "#result == null", cacheManager = "databaseCacheManager")
    public <T> Optional<T> doQuery(Class<T> clazz, String sql, Object... args) {
        return doQueryNoCache(clazz, sql, args);
    }
    @Cacheable(value = "DBObject", key = "T(java.util.Objects).hash(#clazz, #sql, T(java.util.Arrays).deepHashCode(#args))", unless = "#result == null", cacheManager = "databaseCacheManager")
    public <T> List<T> doQueryAll(Class<T> clazz, String sql, Object... args) {
        return doQueryAllNoCache(clazz, sql, args);
    }
    @Cacheable(value = "DBObject", key = "'D'+T(java.util.Objects).hash(#clazz, #sql, T(java.util.Arrays).deepHashCode(#args))", unless = "#result == null", cacheManager = "databaseCacheManager")
    public <T> Set<T> doQueryAllDistinct(Class<T> clazz, String sql, Object... args) {
        return doQueryAllDistinctNoCache(clazz, sql, args);
    }

    public <T> Optional<T> doQueryNoCache(Class<T> clazz, String sql, Object... args) {
        try {
            SQLCleaner C = new SQLCleaner(sql, args);
            return Optional.ofNullable(jdbcTemplate.queryForObject(C.newSQL, (rs, rowNum) -> mapResultSetToObject(rs, clazz), C.newParams));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    public <T> List<T> doQueryAllNoCache(Class<T> clazz, String sql, Object... args) {
        SQLCleaner C = new SQLCleaner(sql, args);
        return jdbcTemplate.query(C.newSQL, (rs, rowNum) -> mapResultSetToObject(rs, clazz), C.newParams);
    }
    public <T> Set<T> doQueryAllDistinctNoCache(Class<T> clazz, String sql, Object... args) {
        return new HashSet<>(doQueryAll(clazz, sql, args));
    }


    @Cacheable(value = "DBRow", key = "T(java.util.Objects).hash(#sql, T(java.util.Arrays).deepHashCode(#args))", unless = "#result == null", cacheManager = "databaseCacheManager")
    public Optional<Row> doQuery(String sql, Object... args) {
        return doQueryNoCache(sql, args);
    }
    @Cacheable(value = "DBRow", key = "T(java.util.Objects).hash(#sql, T(java.util.Arrays).deepHashCode(#args))", unless = "#result == null", cacheManager = "databaseCacheManager")
    public List<Row> doQueryAll(String sql, Object... args) {
        return doQueryAllNoCache(sql, args);
    }
    @Cacheable(value = "DBRow", key = "'D'+T(java.util.Objects).hash(#sql, T(java.util.Arrays).deepHashCode(#args))", unless = "#result == null", cacheManager = "databaseCacheManager")
    public Set<Row> doQueryAllDistinct(String sql, Object... args) {
        return doQueryAllDistinctNoCache(sql, args);
    }

    public Optional<Row> doQueryNoCache(String sql, Object... args) {
        try {
            SQLCleaner C = new SQLCleaner(sql, args);
            return Optional.ofNullable(new Row(jdbcTemplate.queryForMap(C.newSQL, C.newParams)));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    public List<Row> doQueryAllNoCache(String sql, Object... args) {
        SQLCleaner C = new SQLCleaner(sql, args);
        return jdbcTemplate.queryForList(C.newSQL, C.newParams).stream().map(Row::new).collect(Collectors.toList());
    }
    public Set<Row> doQueryAllDistinctNoCache(String sql, Object... args) {
        SQLCleaner C = new SQLCleaner(sql, args);
        return jdbcTemplate.queryForList(C.newSQL, C.newParams).stream().map(Row::new).collect(Collectors.toSet());
    }


    @Cacheable(value = "DBRow", key = "T(java.util.Objects).hash(#clazz, #sql, T(java.util.Arrays).deepHashCode(#args))", unless = "#result == null", cacheManager = "databaseCacheManager")
    public <T> Optional<T> doQueryValue(Class<T> clazz, String sql, Object... args) {
        return doQueryValueNoCache(clazz, sql, args);
    }
    public <T> Optional<T> doQueryValueNoCache(Class<T> clazz, String sql, Object... args) {
        SQLCleaner C = new SQLCleaner(sql, args);
        return Optional.ofNullable(jdbcTemplate.queryForObject(C.newSQL, clazz, C.newParams));
    }


    @Cacheable(value = "DBObject", key = "T(java.util.Objects).hash(#clazz, #args != null ? T(java.util.Arrays).deepHashCode(#args) : null)", unless = "#result == null", cacheManager = "databaseCacheManager")
    public <T> Optional<T> doQueryJoin(Class<T> clazz, String whereClause, Object... args) {
        return Join_JSONApproach(clazz, whereClause, args);
    }

    // ====== UPDATE ======

    public int doUpdate(String sql, Object... args) {
        SQLCleaner C = new SQLCleaner(sql, args);
        return jdbcTemplate.update(C.newSQL, C.newParams);
    }
    public int doUpdate(Class<?> clazz, String sql, Object... args) {
        resetCacheForClass(clazz, true, true);
        return doUpdate(sql, args);
    }

    // ====== CACHE RESET ======

    public void resetAllCaches() {
        dbCacheManager.getCacheNames().stream().filter(c -> !c.equals("DBObject")).toList().forEach(c -> dbCacheManager.getCache(c).clear());
    }
    public void resetCache(String cacheName) {
        Cache cache = dbCacheManager.getCache(cacheName);
        if (cache != null) cache.clear();
    }
    public void resetCacheFor(DatabaseObject<?> dbobject) {
        Cache cache = dbCacheManager.getCache("DBObject");
        if (cache == null) return;
        com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache = (com.github.benmanes.caffeine.cache.Cache<Object, Object>) cache.getNativeCache();
        nativeCache.asMap().forEach((key, cacheItem) -> {
            if (cacheItem instanceof DatabaseObject<?> V) {
                if (V.cacheHashes.contains(dbobject.getHashedIdentifier())) {
                    copyObject(V, dbobject);
                }
            } else if (cacheItem instanceof List<?> V2) { // If the item cached is a list
                if (V2.isEmpty()) cache.evict(key);
                if (V2.getFirst() instanceof DatabaseObject<?>) { // Check if the datatype of the cache list is the same as the current item
                    Object found = V2.stream().filter(dbo -> ((DatabaseObject<?>)dbo).cacheHashes.contains(dbobject.getHashedIdentifier())).findFirst().orElseGet(() -> {
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

    // ====== OTHER ======

    @Cacheable(value = "DBData", key = "'DBSTATISTICS'", unless = "#result == null", cacheManager = "databaseCacheManager")
    public DatabaseStats getDatabaseStats() {
        DatabaseStats stats = new DatabaseStats();
        jdbcTemplate.execute((Connection con) -> {
            DatabaseMetaData metaData = con.getMetaData();
            try (ResultSet tables = metaData.getTables(con.getCatalog(), null, "%", new String[]{"TABLE"})) {
                while (tables.next()) {
                    String tableName = tables.getString("TABLE_NAME");
                    stats.totalTables++;
                    stats.tableNames.add(tableName);
                }
            }

            // --- Views ---
            try (ResultSet views = metaData.getTables(con.getCatalog(), null, "%", new String[]{"VIEW"})) {
                while (views.next()) {
                    String viewName = views.getString("TABLE_NAME");
                    stats.totalViews++;
                    stats.viewNames.add(viewName);
                }
            }

            stats.totalRows = jdbcTemplate.queryForObject("""
            SELECT SUM(TABLE_ROWS) AS total_rows
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
            AND TABLE_TYPE = 'BASE TABLE';
            """, Long.class).intValue();

            return null;
        });
        return stats;
    }

    @Cacheable(value = "DBData", key = "#name", unless = "#result == null", cacheManager = "databaseCacheManager")
    public TableStats getTableStats(String name) {
        TableStats stats = new TableStats();
        jdbcTemplate.execute((Connection con) -> {
            stats.tableName = name.toLowerCase();
            DatabaseMetaData metaData = con.getMetaData();
            try (ResultSet columns = metaData.getColumns(con.getCatalog(), null, stats.tableName, null)) {
                while (columns.next()) {
                    stats.columnNames.add(columns.getString("COLUMN_NAME").toLowerCase());
                }
            }
            try (ResultSet count = con.createStatement().executeQuery("SELECT COUNT(*) FROM " + stats.tableName)) {
                if (count.next()) {
                    stats.totalRows = count.getLong(1);
                }
            }
            return null;
        });
        return stats;
    }

    // ====== SCHEMA ======

    @Cacheable(value = "DBData", key = "'SCHEMA'", unless = "#result == null", cacheManager = "databaseCacheManager")
    public String getSchema() {
        return jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
    }

    public <T> boolean createTable(Class<T> clazz) {
        List<Field> f = getSerializableFieldsOfClassFamily(clazz);
        String sql = "CREATE TABLE IF NOT EXISTS " + getTableName(clazz) + " (\n";
        for (Field field : f) {
            sql += "    " + field.getName() + " " + getSQLType(field) + ",\n";
        }
        sql += "\n);";
        return doUpdate(sql) > 0;
    }

    public void createSchema(List<Class<?>> clz) {
        try (SessionFactory sess = getSessionFactory(clz, "create")) {
            log.info("Created tables for classes:\n" + clz.stream().map((Class c ) -> "- " + c.getSimpleName()).collect(Collectors.joining("\n")));
        }
    }
    public void updateSchema(List<Class<?>> clz) {
        try (SessionFactory sess = getSessionFactory(clz, "update")) {
            log.info("Updated tables for classes:\n" + clz.stream().map((Class c ) -> "- " + c.getSimpleName()).collect(Collectors.joining("\n")));
        }
    }
    private SessionFactory getSessionFactory(List<Class<?>> clz, String hb2ddl) {
        List<ClassLoader> loaders = new ArrayList<>();
        loaders.add(Thread.currentThread().getContextClassLoader());

        try {
            PluginManager pluginManager = (PluginManager) context.getBean(Class.forName("org.pf4j.PluginManager"));
            if (pluginManager != null) for (PluginWrapper c : pluginManager.getPlugins()) loaders.add(c.getPluginClassLoader());
        } catch (Exception ignored) {}

        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.datasource", dataSource)
                .applySetting("hibernate.dialect", "org.hibernate.dialect.MariaDBDialect")
                .applySetting("hibernate.hbm2ddl.auto", "update")
                .applySetting("hibernate.classLoaders", loaders).build();
        MetadataSources metadata = new MetadataSources(registry);
        for (Class<?> c : clz) metadata.addAnnotatedClass(c);
        return metadata.buildMetadata().buildSessionFactory();
    }

    public void updateSchemaTest(List<Class<?>> clz) {
        for (Class<?> c : clz) {
            try (SessionFactory sess = getSessionFactoryTest(c, "create")) {
                log.info("Updated tables for class: " + c.getSimpleName());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    private SessionFactory getSessionFactoryTest(Class<?> clz, String hb2ddl) {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(clz.getClassLoader());
            StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                    .applySetting("hibernate.connection.datasource", dataSource)
                    .applySetting("hibernate.dialect", "org.hibernate.dialect.MariaDBDialect")
                    .applySetting("hibernate.hbm2ddl.auto", hb2ddl)
                    .build();
            MetadataSources metadata = new MetadataSources(registry);
            metadata.addAnnotatedClass(clz);
            return metadata.buildMetadata().buildSessionFactory();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }
}