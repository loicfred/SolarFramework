package org.solarframework.db.spring;

import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.solarframework.db.api.DatabaseObject;
import org.solarframework.db.api.*;
import org.solarframework.db.api.dto.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.*;
import java.util.stream.Collectors;

import static org.solarframework.db.spring.DatabaseRegistry.SolarDBManager;
import static org.solarframework.db.spring.DatabaseUtils.*;

@Service
@SuppressWarnings("all")
public class DatabaseService implements IDatabaseService {
    private static Logger log = LoggerFactory.getLogger(DatabaseService.class);

    protected ApplicationContext context;
    protected CacheManager dbCacheManager;
    protected DataSource dataSource;
    protected JdbcTemplate jdbcTemplate;

    public DatabaseService(ApplicationContext context, @Qualifier("databaseCacheManager") CacheManager dbCacheManager, DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.context = context;
        this.dbCacheManager = dbCacheManager;
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    public CacheManager getDbCacheManager() {
        return dbCacheManager;
    }

    public DataSource getDataSource() {
        return this.dataSource;
    }
    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public <T> IDBObjectService<T> makeObjectManager(DatabaseObject<T> dbobject) {
        return new DBObjectService<>(this, dbobject);
    }

    public <O, T> Optional<O> getSingleColumnOfTableById(String column, Class<O> item, Class<?> table, Object id) {
        return doQueryValueNoCache(item, "SELECT " + column + " FROM " + getTableName(table) + " WHERE ID = ? LIMIT 1;", id);
    }
    public <O, T> Optional<O> getSingleColumnOfTableWhere(String column, Class<O> item, Class<?> table, String where, Object... args) {
        return doQueryValueNoCache(item, "SELECT " + column + " FROM " + getTableName(table) + " WHERE " + where + " LIMIT 1;", args);
    }


    // ====== SHORT CUTS ======

    public <T> Optional<T> getByIdWithJoins(Class<T> clazz, Object id) {
        return this.doQueryJoin(clazz, "MAIN.ID = ?", id);
    }
    public <T> Optional<T> getById(String select, Class<T> clazz, Object id) {
        return this.doQuery(clazz, "SELECT " + select + " FROM " + getTableName(clazz) + " WHERE ID = ? LIMIT 1;", id);
    }
    public <T> Optional<T> getById(Class<T> clazz, Object id) {
        return getById("*", clazz, id);
    }

    public <T> Optional<T> getWhereWithJoins(Class<T> clazz, String whereClause, Object... args) {
        return this.doQueryJoin(clazz, whereClause, args); // ADD MAIN. in front of attributes.
    }
    public <T> Optional<T> getWhere(String select, Class<T> clazz, String whereClause, Object... args) {
        return this.doQuery(clazz, "SELECT " + select + " FROM " + getTableName(clazz) + " WHERE " + whereClause + " LIMIT 1;", args);
    }
    public <T> Optional<T> getWhere(Class<T> clazz, String whereClause, Object... args) {
        return getWhere("*", clazz, whereClause, args);
    }

    public <T> List<T> getAll(String select, Class<T> clazz) {
        return this.doQueryAll(clazz, "SELECT " + select + " FROM " + getTableName(clazz), null);
    }
    public <T> List<T> getAll(Class<T> clazz) {
        return getAll("*", clazz);
    }
    public <T> List<T> getAllWhere(String select, Class<T> clazz, String whereClause, Object... args) {
        return this.doQueryAll(clazz, "SELECT " + select + " FROM " + getTableName(clazz) + " WHERE " + whereClause, args);
    }
    public <T> List<T> getAllWhere(Class<T> clazz, String whereClause, Object... args) {
        return getAllWhere("*", clazz, whereClause, args);
    }
    public <T> Set<T> getAllWhereDistinct(String select, Class<T> clazz, String whereClause, Object... args) {
        return this.doQueryAllDistinct(clazz, "SELECT " + select + " FROM " + getTableName(clazz) + " WHERE " + whereClause, args);
    }
    public <T> Set<T> getAllWhereDistinct(Class<T> clazz, String whereClause, Object... args) {
        return getAllWhereDistinct("*", clazz, whereClause, args);
    }

    public <T> int Count(Class<T> clazz) {
        return this.doQueryValue(Integer.class, "SELECT COUNT(*) FROM " + getTableName(clazz)).orElse(0);
    }
    public <T> int Count(Class<T> clazz, String whereClause, Object... args) {
        return this.doQueryValue(Integer.class, "SELECT COUNT(*) FROM " + getTableName(clazz) + " WHERE " + whereClause, args).orElse(0);
    }

    public <T> T getRandom(String select, Class<T> clazz) {
        return this.getWhere(select, clazz, "ID >= FLOOR(RAND() * (SELECT MAX(ID) FROM " + getTableName(clazz) + "))").orElse(null);
    }
    public <T> T getRandom(Class<T> clazz) {
        return this.getRandom("*", clazz);
    }
    public <T> T getRandom(String select, Class<T> clazz, String whereClause, Object... args) {
        return this.getWhere(select, clazz, whereClause + " AND ID >= FLOOR(RAND() * (SELECT MAX(ID) FROM " + getTableName(clazz) + "))", args).orElse(null);
    }
    public <T> T getRandom(Class<T> clazz, String whereClause, Object... args) {
        return this.getRandom("*", clazz, whereClause, args);
    }

    // ====== GETTERS ======

    private <T> T getCachedOrCompute(String cacheName, String cacheKey, java.util.function.Supplier<T> supplier) {
        Cache cache = dbCacheManager.getCache(cacheName);
        if (cache != null) {
            Cache.ValueWrapper cached = cache.get(cacheKey);
            if (cached != null) return (T) cached.get();
        }
        T result = supplier.get();
        if (cache != null && result != null) cache.put(cacheKey, result);
        return result;
    }


    public <T> Optional<T> doQuery(Class<T> clazz, String sql, Object... args) {
        String cacheKey = String.valueOf(Objects.hash(clazz, sql, args != null ? Arrays.deepHashCode(args) : null));
        return getCachedOrCompute("DBObject", cacheKey, () -> doQueryNoCache(clazz, sql, args));
    }
    public <T> List<T> doQueryAll(Class<T> clazz, String sql, Object... args) {
        String cacheKey = String.valueOf(Objects.hash(clazz, sql, Arrays.deepHashCode(args)));
        return getCachedOrCompute("DBObject", cacheKey, () -> doQueryAllNoCache(clazz, sql, args));
    }
    public <T> Set<T> doQueryAllDistinct(Class<T> clazz, String sql, Object... args) {
        String cacheKey = "D" + Objects.hash(clazz, sql, Arrays.deepHashCode(args));
        return getCachedOrCompute("DBObject", cacheKey, () -> doQueryAllDistinctNoCache(clazz, sql, args));
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


    public Optional<Row> doQuery(String sql, Object... args) {
        String cacheKey = String.valueOf(Objects.hash(sql, args != null ? Arrays.deepHashCode(args) : null));
        return getCachedOrCompute("DBObject", cacheKey, () -> doQueryNoCache(sql, args));
    }
    public List<Row> doQueryAll(String sql, Object... args) {
        String cacheKey = String.valueOf(Objects.hash(sql, args != null ? Arrays.deepHashCode(args) : null));
        return getCachedOrCompute("DBObject", cacheKey, () -> doQueryAllNoCache(sql, args));
    }
    public Set<Row> doQueryAllDistinct(String sql, Object... args) {
        String cacheKey = String.valueOf(Objects.hash(sql, args != null ? Arrays.deepHashCode(args) : null));
        return getCachedOrCompute("DBObject", cacheKey, () -> doQueryAllDistinctNoCache(sql, args));
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


    public <T> Optional<T> doQueryValue(Class<T> clazz, String sql, Object... args) {
        String cacheKey = String.valueOf(Objects.hash(clazz, sql, args != null ? Arrays.deepHashCode(args) : null));
        return getCachedOrCompute("DBObject", cacheKey, () -> doQueryValueNoCache(clazz, sql, args));
    }
    public <T> Optional<T> doQueryValueNoCache(Class<T> clazz, String sql, Object... args) {
        SQLCleaner C = new SQLCleaner(sql, args);
        return Optional.ofNullable(jdbcTemplate.queryForObject(C.newSQL, clazz, C.newParams));
    }


    public <T> Optional<T> doQueryJoin(Class<T> clazz, String whereClause, Object... args) {
        String cacheKey = String.valueOf(Objects.hash(clazz, whereClause, args != null ? Arrays.deepHashCode(args) : null));
        return getCachedOrCompute("DBObject", cacheKey, () -> Join_JSONApproach(this, clazz, whereClause, args));
    }

    // ====== UPDATE ======

    public int doUpdate(String sql, Object... args) {
        SQLCleaner C = new SQLCleaner(sql, args);
        return jdbcTemplate.update(C.newSQL, C.newParams);
    }
    public int doUpdate(Class<?> clazz, String sql, Object... args) {
        SolarDBManager.resetCacheForClass(clazz, true, true);
        return doUpdate(sql, args);
    }


    // ====== OTHER ======

    public DatabaseStats getDatabaseStats() {
        return getCachedOrCompute("DBData", "DBSTATISTICS", () -> {
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

                try {
                    stats.totalRows = jdbcTemplate.queryForObject("""
                    SELECT SUM(TABLE_ROWS) AS total_rows
                    FROM information_schema.tables
                    WHERE table_schema = DATABASE()
                    AND TABLE_TYPE = 'BASE TABLE';
                    """, Long.class).intValue();
                } catch (Exception e) {
                    stats.totalRows = 0;
                }
                return stats;
            });
            return stats;
        });
    }
    public TableStats getTableStats(String name) {
        return getCachedOrCompute("DBData", "TABLE-" + name, () -> {
            TableStats stats = new TableStats();
            jdbcTemplate.execute((Connection con) -> {
                stats.schemaName = getSchema();
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
        });
    }

    // ====== SCHEMA ======

    public String getSchema() {
        return getCachedOrCompute("DBData", "SCHEMA", () -> {
            return jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        });
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

    public void createSchemaTest(List<Class<?>> clz) {
        for (Class<?> c : clz) {
            try (SessionFactory sess = getSessionFactoryTest(c, "create")) {
                log.info("Updated tables for class: " + c.getSimpleName());
                System.err.println("Updated tables for class: " + c.getSimpleName() + " with " + c.getClassLoader());
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


    public void createSchema(List<Class<?>> clz) {
        appendSchema(clz, "create");
    }
    public void updateSchema(List<Class<?>> clz) {
        appendSchema(clz, "update");
    }

    private void appendSchema(List<Class<?>> clz, String action) {
        List<ClassLoader> loaders = clz.stream().map(Class::getClassLoader).distinct().collect(Collectors.toList());
        for (ClassLoader cl : loaders) {
            List<Class<?>> clz2 = clz.stream().filter(c -> c.getClassLoader() == cl).collect(Collectors.toList());
            try (SessionFactory sess = getSessionFactory(cl, clz2, action)) {
                log.info("Updated tables for class: \n" + clz.stream().map((Class c ) -> "- " + c.getSimpleName()).collect(Collectors.joining("\n")));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    private SessionFactory getSessionFactory(ClassLoader loader, List<Class<?>> clz, String hb2ddl) {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(loader);
            StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                    .applySetting("hibernate.connection.datasource", dataSource)
                    .applySetting("hibernate.dialect", "org.hibernate.dialect.MariaDBDialect")
                    .applySetting("hibernate.hbm2ddl.auto", hb2ddl)
                    .build();
            MetadataSources metadata = new MetadataSources(registry);
            for (Class<?> c : clz) metadata.addAnnotatedClass(c);
            return metadata.buildMetadata().buildSessionFactory();
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

}