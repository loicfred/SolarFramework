package org.solarframework.db.spring;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.NoResultException;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import jakarta.persistence.TupleElement;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.solarframework.db.api.*;
import org.solarframework.db.api.dto.DatabaseStats;
import org.solarframework.db.api.dto.Row;
import org.solarframework.db.api.dto.TableStats;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.solarframework.db.api.IEntityInfo.sortByDependency;
import static org.solarframework.db.spring.DBInstanceService.IdFields;
import static org.solarframework.db.spring.DatabaseConfig.defaultConnectionString;
import static org.solarframework.db.spring.DatabaseObject.getTableName;
import static org.solarframework.db.spring.DatabaseRegistry.SolarDBManager;
import static org.solarframework.db.spring.DatabaseUtils.inTransaction;
import static org.solarframework.db.spring.DatabaseUtils.withEm;
import static org.solarframework.db.spring.QueryTranslation.QueryCurrentDatabase;
import static org.solarframework.db.spring.QueryTranslation.QueryDatabaseStats;

@Service
@SuppressWarnings("all")
public class DatabaseService implements IDatabaseService {
    private static Logger log = LoggerFactory.getLogger(DatabaseService.class);

    @JsonIgnore
    private transient CacheManager dbCacheManager;
    @JsonIgnore
    protected transient IDatabaseManager manager;
    @JsonIgnore
    private transient DataSource dataSource;
    @JsonIgnore
    private transient JdbcTemplate jdbcTemplate;
    @JsonIgnore
    private transient TransactionTemplate transactionTemplate;
    @JsonIgnore
    private transient Metadata metadata;
    @JsonIgnore
    private transient volatile SessionFactory sessionFactory;


    private transient Long ping;
    private transient String pingError;
    private String name;
    private DatabaseType databaseType;
    private List<IEntityInfo> entities = new ArrayList<>();

    @Value("${spring.datasource.url:#{null}}") private String connectionString;
    @Value("${spring.datasource.username:#{null}}") private String username;
    @Value("${spring.datasource.password:#{null}}") private String password;
    @Value("${spring.datasource.driver-class-name:#{null}}") private String type;
    @Value("${spring.datasource.hikari.maximum-pool-size:#{null}}") private Integer maxPoolSize;
    @Value("${spring.datasource.hikari.minimum-idle:#{null}}") private Integer minimumIdle;
    @Value("${spring.datasource.hikari.idle-timeout:#{null}}") private Long idleTimeout;
    @Value("${spring.datasource.hikari.max-lifetime:#{null}}") private Long maxLifetime;
    @Value("${spring.datasource.hikari.connection-timeout:#{null}}") private Long connectionTimeout;

    public DatabaseService(@Qualifier("databaseCacheManager") CacheManager dbCacheManager) {
        this.dbCacheManager = dbCacheManager;
        setName("Database (Default)");
        // NOTE: @Value fields are injected AFTER the constructor runs; they are null here.
        // Configuration derived from them happens in init() below.
    }

    public <T> IDBObjectService<T> makeObjectManager(DatabaseObject<T> dbobject) {
        return new DBInstanceService<>(this, dbobject);
    }

    public CacheManager getDbCacheManager() {
        return dbCacheManager;
    }
    public void setDbCacheManager(CacheManager dbCacheManager) {
        this.dbCacheManager = dbCacheManager;
    }

    public IDatabaseManager getManager() {
        return manager;
    }
    public void setManager(IDatabaseManager manager) {
        this.manager = manager;
    }

    public String getName() {
        return name;
    }
    public String getUsername() {
        return username;
    }
    public String getPassword() {
        return password;
    }
    public String getConnectionString() {
        return connectionString;
    }
    public DatabaseType getDatabaseType() {
        return databaseType == null && type != null ? DatabaseType.fromDriver(type) : databaseType;
    }
    public int getMaxPoolSize() {
        return maxPoolSize != null ? maxPoolSize : 10;
    }
    public int getMinimumIdle() {
        return minimumIdle != null ? minimumIdle : 2;
    }
    public long getIdleTimeout() {
        return idleTimeout != null ? idleTimeout : 600_000L;
    }
    public long getMaxLifetime() {
        return maxLifetime != null ? maxLifetime : 1_800_000L;
    }
    public long getConnectionTimeout() {
        return connectionTimeout != null ? connectionTimeout : 30_000L;
    }
    public boolean isDefault() {
        return Objects.equals(getConnectionString(), defaultConnectionString);
    }

    public void setName(String name) {
        this.name = name;
    }
    public void setUsername(String username) {
        this.username = username;
    }
    public void setPassword(String password) {
        this.password = password;
    }
    public void setConnectionString(String connectionString) {
        this.connectionString = connectionString;
    }
    public void setDatabaseType(DatabaseType type) {
        this.databaseType = type;
        this.type = type.getDriverClass();
    }
    public void setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }
    public void setMinimumIdle(int minimumIdle) {
        this.minimumIdle = minimumIdle;
    }
    public void setIdleTimeout(long idleTimeout) {
        this.idleTimeout = idleTimeout;
    }
    public void setMaxLifetime(long maxLifetime) {
        this.maxLifetime = maxLifetime;
    }
    public void setConnectionTimeout(long connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public void setEntities(Collection<IEntityInfo> entities) {
        clearEntities();
        this.entities = sortByDependency(entities);
        reload();
    }
    public void addEntities(IEntityInfo... entities) {
        this.entities.addAll(Arrays.stream(entities).toList());
        this.entities = sortByDependency(this.entities);
        reload();
    }
    public void removeEntities(IEntityInfo... entities) {
        Stream.of(entities).toList().forEach(this.entities::remove);
        this.entities = sortByDependency(this.entities);
        reload();
    }
    public void clearEntities() {
        this.entities.clear();
        reload();
    }

    public List<IEntityInfo> getEntities() {
        return entities;
    }
    public List<Class<?>> getEntitiesClasses() {
        return getEntities().stream().map(IEntityInfo::getEntityClass).collect(Collectors.toList());
    }

    public List<IEntityInfo> getInstalledEntities() {
        return getEntities().stream().filter(e -> getDatabaseStats().getTableNames().contains(e.getTableName())).collect(Collectors.toList());
    }
    public List<Class<?>> getInstalledEntitiesClasses() {
        return getInstalledEntities().stream().map(IEntityInfo::getEntityClass).collect(Collectors.toList());
    }

    public List<IEntityInfo> getMissingEntities() {
        return getEntities().stream().filter(e -> !getDatabaseStats().getTableNames().contains(e.getTableName())).collect(Collectors.toList());
    }
    public List<Class<?>> getMissingEntitiesClasses() {
        return getMissingEntities().stream().map(IEntityInfo::getEntityClass).collect(Collectors.toList());
    }

    public List<IEntityInfo> getUpdatableEntities() {
        return getEntities().stream().filter(e -> {
            TableStats stats = e.getTableStats();
            if (stats.getColumnNames().size() != e.getFields().size()) return true;
            if (stats.getColumnNames().stream().allMatch(cn -> e.getFields().stream().anyMatch(fn -> Objects.equals(cn, fn.getColumnName())))) return true;
            return false;
        }).collect(Collectors.toList());
    }
    public List<Class<?>> getUpdatableEntitiesClasses() {
        return getUpdatableEntities().stream().map(IEntityInfo::getEntityClass).collect(Collectors.toList());
    }

    public Class<?> getClassOfTable(String name) {
        return getEntities().stream().filter(availableEntity -> availableEntity.getTableName().equals(name)).map(IEntityInfo::getEntityClass).findFirst().orElse(null);
    }

    public long getPing() {
        if (ping == null) resetPing();
        return ping;
    }
    public String getPingError() {
        if (pingError == null) resetPing();
        return pingError;
    }
    public void resetPing() {
        long start = System.nanoTime();
        try (Connection conn = getDataSource().getConnection(); PreparedStatement ps = conn.prepareStatement("SELECT 1"); ResultSet rs = ps.executeQuery()) {
            pingError = null;
            ping = (System.nanoTime() - start) / 1_000_000;
        } catch (Exception e) {
            pingError = "Error: " + e.getMessage();
            ping = 0L;
        }
    }

    // ====== PER-INSTANCE JPA INFRASTRUCTURE ======

    public synchronized SessionFactory getSessionFactory() {
        if (sessionFactory == null) {
            BootstrapServiceRegistryBuilder bootstrap = new BootstrapServiceRegistryBuilder();
            getEntitiesClasses().stream().map(Class::getClassLoader).distinct().forEach(bootstrap::applyClassLoader);
            StandardServiceRegistry registry = new StandardServiceRegistryBuilder(bootstrap.build())
                    .applySetting("hibernate.connection.datasource", getDataSource())
                    .applySetting("hibernate.dialect", getDatabaseType().getDialectClass())
                    .applySetting("hibernate.enable_lazy_load_no_trans", "true")
                    .applySetting("hibernate.hbm2ddl.auto", "update")   // additive DDL at build time
                    .build();

            MetadataSources sources = new MetadataSources(registry);
            for (Class<?> c : getEntitiesClasses()) sources.addAnnotatedClass(c);

            sessionFactory = sources.buildMetadata().buildSessionFactory();
        }
        return sessionFactory;
    }
    public synchronized DataSource getDataSource() {
        if (this.dataSource != null) return dataSource;
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(getConnectionString());
        config.setUsername(getUsername());
        config.setPassword(getPassword());
        config.setDriverClassName(getDatabaseType().getDriverClass());

        config.setMaximumPoolSize(getMaxPoolSize());
        config.setMinimumIdle(getMinimumIdle());
        config.setIdleTimeout(getIdleTimeout());
        config.setMaxLifetime(getMaxLifetime());
        config.setConnectionTimeout(getConnectionTimeout());
        config.setPoolName("MyHikariPool-" + getName().replace(" ", "_"));
        return dataSource = new HikariDataSource(config);
    }
    public synchronized void reload() {
        try { sessionFactory.close(); } catch (Exception ignored) { }
        sessionFactory = null;
        metadata = null;
        try { dataSource.unwrap(HikariDataSource.class).close(); } catch (Exception ignored) { }
        dataSource = null;
        jdbcTemplate = null;
        transactionTemplate = null;

        for (IEntityInfo EI : getEntities()) {
            manager.getEntityClassloaders().entrySet().stream().filter(cl -> Objects.equals(cl.getKey(), EI.getClassLoader())).findFirst().ifPresent((cl) -> {
                try {
                    EI.Update(cl.getValue().loadClass(EI.getClassName()));
                } catch (ClassNotFoundException _) {}
            });
        }
    }

    // ====== SHORT CUTS ======

    public <O> Optional<O> getSingleColumnOfTableById(String column, Class<O> item, Class<?> table, Object... id) {
        return doQueryValueNoCache(item, "SELECT " + column + " FROM " + getTableName(table) + " WHERE " + IdFields.get(table.getName()).stream().map(f -> f.getName() + " = ?").collect(Collectors.joining(" AND ")), id);
    }
    public <O> Optional<O> getSingleColumnOfTableWhere(String column, Class<O> item, Class<?> table, String where, Object... args) {
        return doQueryValueNoCache(item, "SELECT " + column + " FROM " + getTableName(table) + " WHERE " + where, args);
    }

    public <T> int InsertBatch(List<T> items) {
        if (items == null || items.isEmpty()) return 0;
        String tableName = null;
        String columns = null;
        List<String> questionMarks = new ArrayList<>();
        List<Object[]> currentValues = new ArrayList<>();
        for (T item : items) {
            if (item instanceof DatabaseObject<?> obj) {
                if (obj.getService() instanceof DBInstanceService<?> service) {
                    DBInstanceService.InsertArgumentManager insertArgMgr = service.makeInsertManager(false, true);
                    questionMarks.add(insertArgMgr.questionMarks());
                    currentValues.add(insertArgMgr.currentValuesList());
                    if (columns == null) columns = insertArgMgr.columns();
                    if (tableName == null) tableName = getTableName(item.getClass());
                }
            }
        }
        String sql = getDatabaseType().UpsertBatch(tableName, columns, questionMarks, null, null);
        try {
            return executeNativeUpdate(sql, currentValues.stream().flatMap(Arrays::stream).toArray());
        } finally {
            SolarDBManager.resetCacheForClass(items.getFirst().getClass(), true, true);
        }
    }
    public <T> int UpsertBatch(List<T> items) {
        return UpsertBatch(items, null);
    }
    public <T> int UpsertBatch(List<T> items, List<String> conflictCols) {
        if (items == null || items.isEmpty()) return 0;
        String tableName = null;
        String columns = null;
        String duplicateKey = null;
        List<String> questionMarks = new ArrayList<>();
        List<Object[]> currentValues = new ArrayList<>();
        for (T item : items) {
            if (item instanceof DatabaseObject<?> obj) {
                if (obj.getService() instanceof DBInstanceService<?> service) {
                    DBInstanceService.InsertArgumentManager insertArgMgr = service.makeInsertManager(true, true);
                    questionMarks.add(insertArgMgr.questionMarks());
                    currentValues.add(insertArgMgr.currentValuesList());
                    if (columns == null) columns = insertArgMgr.columns();
                    if (tableName == null) tableName = getTableName(item.getClass());
                    if (duplicateKey == null) duplicateKey = insertArgMgr.duplicateKeyUpdateClause();
                }
            }
        }
        String sql = getDatabaseType().UpsertBatch(tableName, columns, questionMarks, duplicateKey, conflictCols == null || conflictCols.isEmpty() ? null : conflictCols.stream().collect(Collectors.joining(", ")));
        try {
            return executeNativeUpdate(sql, currentValues.stream().flatMap(Arrays::stream).toArray());
        } finally {
            SolarDBManager.resetCacheForClass(items.getFirst().getClass(), true, true);
        }
    }

    /** Entities extending RECORD_OBJ are hidden from ORM-level reads once soft-deleted. Raw doQuery/doUpdate calls bypass this on purpose. */
    private static boolean isSoftDeletable(Class<?> clazz) { return DatabaseObject.RECORD_OBJ.class.isAssignableFrom(clazz); }

    private static String withActiveFilter(Class<?> clazz, String whereClause) {
        if (!isSoftDeletable(clazz)) return whereClause;
        return whereClause == null || whereClause.isBlank() ? "DeletedAt IS NULL" : "(" + whereClause + ") AND DeletedAt IS NULL";
    }

    /** Bypasses the soft-delete filter on purpose - if the caller already has an ID, they get the row even if it was soft-deleted. */
    public <T> Optional<T> getById(String select, Class<T> clazz, Object... id) {
        String where = IdFields.get(clazz.getName()).stream().map(f -> f.getName() + " = ?").collect(Collectors.joining(" AND "));
        return this.doQuery(clazz, "SELECT " + select + " FROM " + getTableName(clazz) + " WHERE " + where, id);
    }
    public <T> Optional<T> getById(Class<T> clazz, Object... id) {
        return getById("*", clazz, id);
    }

    public <T> Optional<T> getWhere(String select, Class<T> clazz, String whereClause, Object... args) {
        return this.doQuery(clazz, "SELECT " + select + " FROM " + getTableName(clazz) + " WHERE " + withActiveFilter(clazz, whereClause), args);
    }
    public <T> Optional<T> getWhere(Class<T> clazz, String whereClause, Object... args) {
        return getWhere("*", clazz, whereClause, args);
    }

    public <T> List<T> getAll(String select, Class<T> clazz) {
        String where = withActiveFilter(clazz, null);
        return this.doQueryAll(clazz, "SELECT " + select + " FROM " + getTableName(clazz) + (where == null ? "" : " WHERE " + where), null);
    }
    public <T> List<T> getAll(Class<T> clazz) {
        return getAll("*", clazz);
    }

    public <T> List<T> getAllWhere(String select, Class<T> clazz, String whereClause, Object... args) {
        return this.doQueryAll(clazz, "SELECT " + select + " FROM " + getTableName(clazz) + " WHERE " + withActiveFilter(clazz, whereClause), args);
    }
    public <T> List<T> getAllWhere(Class<T> clazz, String whereClause, Object... args) {
        return getAllWhere("*", clazz, whereClause, args);
    }
    public <T> Set<T> getAllWhereDistinct(String select, Class<T> clazz, String whereClause, Object... args) {
        return this.doQueryAllDistinct(clazz, "SELECT " + select + " FROM " + getTableName(clazz) + " WHERE " + withActiveFilter(clazz, whereClause), args);
    }
    public <T> Set<T> getAllWhereDistinct(Class<T> clazz, String whereClause, Object... args) {
        return getAllWhereDistinct("*", clazz, whereClause, args);
    }

    public <T> int Count(Class<T> clazz) {
        String where = withActiveFilter(clazz, null);
        return this.doQueryValue(Integer.class, "SELECT COUNT(*) FROM " + getTableName(clazz) + (where == null ? "" : " WHERE " + where)).orElse(0);
    }
    public <T> int Count(Class<T> clazz, String whereClause, Object... args) {
        return this.doQueryValue(Integer.class, "SELECT COUNT(*) FROM " + getTableName(clazz) + " WHERE " + withActiveFilter(clazz, whereClause), args).orElse(0);
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

    private <T> T getCachedOrCompute(String cacheName, String cacheKey, Supplier<T> supplier) {
        Cache cache = dbCacheManager.getCache(cacheName);
        if (cache != null) {
            Cache.ValueWrapper cached = cache.get(getConnectionString().hashCode() + cacheKey);
            if (cached != null) return (T) cached.get();
        }
        T result = supplier.get();
        if (cache != null && result != null) cache.put(getConnectionString().hashCode() + cacheKey, result);
        return result;
    }

    // Cache keys are prefixed per result shape ("one", "all", ...) because the same SQL+args
    // can be requested as a single value, a List, or a Set — without the prefix those collide.
    public <T> Optional<T> doQuery(Class<T> clazz, String sql, Object... args) {
        String cacheKey = "one" + Objects.hash(clazz, sql, args != null ? Arrays.deepHashCode(args) : null);
        return getCachedOrCompute("DBObject", cacheKey, () -> doQueryNoCache(clazz, sql, args));
    }
    public <T> List<T> doQueryAll(Class<T> clazz, String sql, Object... args) {
        String cacheKey = "all" + Objects.hash(clazz, sql, Arrays.deepHashCode(args));
        return getCachedOrCompute("DBObject", cacheKey, () -> doQueryAllNoCache(clazz, sql, args));
    }
    public <T> Set<T> doQueryAllDistinct(Class<T> clazz, String sql, Object... args) {
        String cacheKey = "set" + Objects.hash(clazz, sql, Arrays.deepHashCode(args));
        return getCachedOrCompute("DBObject", cacheKey, () -> doQueryAllDistinctNoCache(clazz, sql, args));
    }

    public <T> Optional<T> doQueryNoCache(Class<T> clazz, String sql, Object... args) {
        List<T> results = runNativeQuery(clazz, sql, args, 1);
        return results.isEmpty() ? Optional.empty() : Optional.ofNullable(results.get(0));
    }
    public <T> List<T> doQueryAllNoCache(Class<T> clazz, String sql, Object... args) {
        return runNativeQuery(clazz, sql, args, -1);
    }
    public <T> Set<T> doQueryAllDistinctNoCache(Class<T> clazz, String sql, Object... args) {
        return new HashSet<>(doQueryAll(clazz, sql, args));
    }

    public Optional<Row> doQuery(String sql, Object... args) {
        String cacheKey = "rowone" + Objects.hash(sql, args != null ? Arrays.deepHashCode(args) : null);
        return getCachedOrCompute("DBObject", cacheKey, () -> doQueryNoCache(sql, args));
    }
    public List<Row> doQueryAll(String sql, Object... args) {
        String cacheKey = "rowall" + Objects.hash(sql, args != null ? Arrays.deepHashCode(args) : null);
        return getCachedOrCompute("DBObject", cacheKey, () -> doQueryAllNoCache(sql, args));
    }
    public Set<Row> doQueryAllDistinct(String sql, Object... args) {
        String cacheKey = "rowset" + Objects.hash(sql, args != null ? Arrays.deepHashCode(args) : null);
        return getCachedOrCompute("DBObject", cacheKey, () -> doQueryAllDistinctNoCache(sql, args));
    }

    public Optional<Row> doQueryNoCache(String sql, Object... args) {
        List<Row> rows = runNativeRowQuery(sql, args, 1);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
    public List<Row> doQueryAllNoCache(String sql, Object... args) {
        return runNativeRowQuery(sql, args, -1);
    }
    public Set<Row> doQueryAllDistinctNoCache(String sql, Object... args) {
        return new HashSet<>(runNativeRowQuery(sql, args, -1));
    }

    public <T> Optional<T> doQueryValue(Class<T> clazz, String sql, Object... args) {
        String cacheKey = "val" + Objects.hash(clazz, sql, args != null ? Arrays.deepHashCode(args) : null);
        return getCachedOrCompute("DBObject", cacheKey, () -> doQueryValueNoCache(clazz, sql, args));
    }
    public <T> Optional<T> doQueryValueNoCache(Class<T> clazz, String sql, Object... args) {
        return withEm(this, em -> {
            DatabaseUtils.SQLCleaner C = new DatabaseUtils.SQLCleaner(sql, args);
            Query q = bindParams(em.createNativeQuery(toOrdinalParams(C.newSQL)), C.newParams);
            q.setMaxResults(1);
            try {
                Object value = q.getSingleResult();
                return Optional.ofNullable(convertScalar(value, clazz));
            } catch (NoResultException e) {
                return Optional.<T>empty();
            }
        });
    }

    // ====== UPDATE ======

    public int doUpdate(String sql, Object... args) {
        return executeNativeUpdate(sql, args);
    }
    public int doUpdate(Class<?> clazz, String sql, Object... args) {
        try {
            return executeNativeUpdate(sql, args);
        } finally {
            SolarDBManager.resetCacheForClass(clazz, true, true);
        }
    }


    // ====== JPA INTERNALS ======

    /** Executes a native DML statement inside an explicit transaction on this service's data source. */
    private int executeNativeUpdate(String sql, Object... args) {
        return inTransaction(this, em -> {
            DatabaseUtils.SQLCleaner C = new DatabaseUtils.SQLCleaner(sql, args);
            return bindParams(em.createNativeQuery(toOrdinalParams(C.newSQL)), C.newParams).executeUpdate();
        });
    }

    /** Runs a native query mapped to a class. Entities map via JPA; other classes map reflectively by column name. */
    private <T> List<T> runNativeQuery(Class<T> clazz, String sql, Object[] args, int maxResults) {
        return withEm(this, em -> {
            DatabaseUtils.SQLCleaner C = new DatabaseUtils.SQLCleaner(sql, args);
            if (isEntity(clazz) && selectsAllColumns(C.newSQL)) {
                Query q = bindParams(em.createNativeQuery(toOrdinalParams(C.newSQL), clazz), C.newParams);
                if (maxResults > 0) q.setMaxResults(maxResults);
                return (List<T>) q.getResultList();
            }
            Query q = bindParams(em.createNativeQuery(toOrdinalParams(C.newSQL), Tuple.class), C.newParams);
            if (maxResults > 0) q.setMaxResults(maxResults);
            List<Tuple> tuples = q.getResultList();
            return tuples.stream().map(t -> mapTupleToObject(t, clazz)).collect(Collectors.toList());
        });
    }

    /** Runs a native query returning generic Rows (column name -> value). */
    private List<Row> runNativeRowQuery(String sql, Object[] args, int maxResults) {
        return withEm(this, em -> {
            DatabaseUtils.SQLCleaner C = new DatabaseUtils.SQLCleaner(sql, args);
            Query q = bindParams(em.createNativeQuery(toOrdinalParams(C.newSQL), Tuple.class), C.newParams);
            if (maxResults > 0) q.setMaxResults(maxResults);
            List<Tuple> tuples = q.getResultList();
            return tuples.stream().map(t -> {
                Map<String, Object> map = new LinkedHashMap<>();
                for (TupleElement<?> el : t.getElements()) {
                    map.put(el.getAlias(), t.get(el));
                }
                return new Row(map);
            }).collect(Collectors.toList());
        });
    }

    private boolean isEntity(Class<?> clazz) {
        try {
            getSessionFactory().getMetamodel().entity(clazz);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Reflectively maps a Tuple to a non-entity class by matching column aliases to field names (case-insensitive). */
    private <T> T mapTupleToObject(Tuple tuple, Class<T> clazz) {
        // Single-column result mapping straight to the requested type (e.g. SELECT name FROM ...)
        if (tuple.getElements().size() == 1) {
            Object single = tuple.get(0);
            T converted = convertScalar(single, clazz);
            if (converted != null || single == null) return converted;
        }
        try {
            Constructor<T> ctor = clazz.getDeclaredConstructor();
            ctor.setAccessible(true);
            T instance = ctor.newInstance();
            Map<String, Field> fields = new HashMap<>();
            for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) fields.putIfAbsent(f.getName().toLowerCase(), f);
            }
            for (TupleElement<?> el : tuple.getElements()) {
                if (el.getAlias() == null) continue;
                Field f = fields.get(el.getAlias().toLowerCase());
                if (f == null) continue;
                Object value = tuple.get(el);
                if (value == null) continue;
                f.setAccessible(true);
                f.set(instance, convertScalar(value, (Class<Object>) f.getType()));
            }
            return instance;
        } catch (Exception e) {
            throw new RuntimeException("Cannot map result to " + clazz.getName(), e);
        }
    }

    /** Converts JDBC scalar results (BigInteger, BigDecimal, etc.) to the requested Java type. */
    private <T> T convertScalar(Object value, Class<T> type) {
        if (value == null) return null;
        if (type.isInstance(value)) return type.cast(value);
        if (value instanceof byte[] n) return (T) (byte[]) value;
        if (value instanceof Byte[] n) return (T) (Byte[]) value;
        if (value instanceof Number n) {
            if (type == Integer.class || type == int.class) return (T) Integer.valueOf(n.intValue());
            if (type == Long.class || type == long.class) return (T) Long.valueOf(n.longValue());
            if (type == Double.class || type == double.class) return (T) Double.valueOf(n.doubleValue());
            if (type == Float.class || type == float.class) return (T) Float.valueOf(n.floatValue());
            if (type == Short.class || type == short.class) return (T) Short.valueOf(n.shortValue());
            if (type == Byte.class || type == byte.class) return (T) Byte.valueOf(n.byteValue());
            if (type == BigDecimal.class) return (T) new BigDecimal(n.toString());
            if (type == Boolean.class || type == boolean.class) return (T) Boolean.valueOf(n.intValue() != 0);
        }
        if (type == String.class) return (T) value.toString();
        if (type == Boolean.class && value instanceof String s) return (T) Boolean.valueOf(s);
        try {
            return type.cast(value);
        } catch (ClassCastException e) {
            return null;
        }
    }

    /**
     * Converts JDBC-style '?' placeholders to JPA ordinal placeholders '?1', '?2', ...
     * (Hibernate 6 native queries reject bare '?' parameters.)
     * Skips '?' inside single-quoted string literals.
     */
    private String toOrdinalParams(String sql) {
        StringBuilder sb = new StringBuilder(sql.length() + 8);
        int ordinal = 1;
        boolean inLiteral = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\'') inLiteral = !inLiteral;
            if (c == '?' && !inLiteral) {
                sb.append('?').append(ordinal++);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private Query bindParams(Query q, Object[] args) {
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                q.setParameter(i + 1, args[i]);
            }
        }
        return q;
    }

    /** True if the select list is "*" or "alias.*", i.e. all columns are present. */
    private boolean selectsAllColumns(String sql) {
        Matcher m = Pattern
                .compile("(?is)^\\s*select\\s+(distinct\\s+)?(.*?)\\s+from\\s")
                .matcher(sql);
        if (!m.find()) return false;
        String cols = m.group(2).trim();
        return cols.equals("*") || cols.matches("(?i)[\\w`]+\\.\\*");
    }

    // ====== OTHER ======


    public synchronized DatabaseStats getDatabaseStats() {
        return getCachedOrCompute("DBData", "DBSTATISTICS", () -> {
            DatabaseStats stats = new DatabaseStats();
            withEm(this, em -> {
                Session session = em.unwrap(Session.class);
                session.doWork(con -> {
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
                });
                return null;
            });

            try {
                Object total = withEm(this, em -> em.createNativeQuery(QueryDatabaseStats(getDatabaseType())).getSingleResult());
                stats.totalRows = total instanceof Number n ? n.intValue() : 0;
            } catch (Exception e) {
                stats.totalRows = 0;
            }
            return stats;
        });
    }

    public synchronized TableStats getTableStats(String name) {
        return getCachedOrCompute("DBData", "TABLE-" + name, () -> {
            TableStats stats = new TableStats();
            withEm(this, em -> {
                Session session = em.unwrap(Session.class);
                session.doWork(con -> {
                    stats.sourceName = getName();
                    stats.sourceType = getDatabaseType();
                    stats.schemaName = getSchema();
                    stats.tableName = name.toLowerCase();
                    DatabaseMetaData metaData = con.getMetaData();

                    // Get primary keys
                    Set<String> primaryKeys = new HashSet<>();
                    try (ResultSet pk = metaData.getPrimaryKeys(con.getCatalog(), null, stats.tableName)) {
                        while (pk.next()) {
                            primaryKeys.add(pk.getString("COLUMN_NAME").toLowerCase());
                        }
                    }

                    // Get unique constraints
                    Set<String> uniqueColumns = new HashSet<>();
                    try (ResultSet indexes = metaData.getIndexInfo(con.getCatalog(), null, stats.tableName, true, false)) {
                        while (indexes.next()) {
                            uniqueColumns.add(indexes.getString("COLUMN_NAME").toLowerCase());
                        }
                    } catch (Exception e) {
                        // Some databases may not support this
                    }

                    // Get column details
                    try (ResultSet columns = metaData.getColumns(con.getCatalog(), null, stats.tableName, null)) {
                        while (columns.next()) {
                            String columnName = columns.getString("COLUMN_NAME").toLowerCase();
                            TableStats.ColumnDetail detail = new TableStats.ColumnDetail();
                            detail.name = columnName;
                            detail.type = columns.getString("TYPE_NAME");
                            detail.size = columns.getInt("COLUMN_SIZE");
                            detail.decimalDigits = columns.getInt("DECIMAL_DIGITS");
                            detail.nullable = columns.getInt("NULLABLE") == 1;
                            detail.isAutoIncrement = "YES".equals(columns.getString("IS_AUTOINCREMENT"));
                            detail.isPrimaryKey = primaryKeys.contains(columnName);
                            detail.isUnique = uniqueColumns.contains(columnName);
                            detail.defaultValue = columns.getString("COLUMN_DEF");
                            detail.remarks = columns.getString("REMARKS");

                            stats.columnDetails.add(detail);
                        }
                    }

                    try (ResultSet count = con.createStatement().executeQuery("SELECT COUNT(*) FROM " + stats.tableName)) {
                        if (count.next()) {
                            stats.totalRows = count.getLong(1);
                        }
                    }
                });
                return null;
            });
            return stats;
        });
    }

    public boolean doesTableExist(Class<?> clazz) {
        try {
            withEm(this, em -> em.createNativeQuery("SELECT 1 FROM " + getTableName(clazz)).setMaxResults(1).getResultList());
            return true;
        } catch (Exception ignored) {
            ignored.printStackTrace();
            return false;
        }
    }

    public String getSchema() {
        return getCachedOrCompute("DBData", "SCHEMA", () -> {
            Object result = withEm(this, em -> em.createNativeQuery(QueryCurrentDatabase(getDatabaseType())).getSingleResult());
            return result != null ? result.toString() : null;
        });
    }

    // ====== SCHEMA ======


    public void createSchemaIfMissing(Collection<Class<?>> clz) {
        List<Class<?>> missingSchemas = clz.stream().filter(c -> !doesTableExist(c)).collect(Collectors.toList());
        if (!missingSchemas.isEmpty()) {
            reload();
            getSessionFactory();
            log.info("Updated tables for classes:\n" + clz.stream().map(c -> "- " + c.getSimpleName()).collect(Collectors.joining("\n")));
            for (Class<?> c : missingSchemas) SolarDBManager.verifyEntity(this, c);
        }
    }


    public synchronized JdbcTemplate getJdbcTemplate() {
        return jdbcTemplate == null ? jdbcTemplate = new JdbcTemplate(getDataSource()) : jdbcTemplate;
    }
    public synchronized TransactionTemplate getTransactionTemplate() {
        return transactionTemplate == null ? transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(getDataSource())) : transactionTemplate;
    }

    public static class ENTITY<T> implements IDatabaseService.ENTITY<T> {
        private static Logger log = LoggerFactory.getLogger(DatabaseService.class);

        protected IDatabaseService service;
        protected Class<T> clazz;

        public ENTITY(Class<T> clazz, IDatabaseService service) {
            this.service = service;
            this.clazz = clazz;
        }

        @Override
        public IDBObjectService<T> makeObjectManager(DatabaseObject<T> dbobject) {
            return service.makeObjectManager(dbobject);
        }

        @Override
        public Optional<T> getById(Object... id) {
            return service.getById(clazz, id);
        }

        @Override
        public Optional<T> getWhere(String whereClause, Object... args) {
            return service.getWhere(clazz, whereClause, args);
        }

        @Override
        public List<T> getAll() {
            return service.getAll(clazz);
        }

        @Override
        public List<T> getAllWhere(String whereClause, Object... args) {
            return service.getAllWhere(clazz, whereClause, args);
        }

        @Override
        public Set<T> getAllWhereDistinct(String whereClause, Object... args) {
            return service.getAllWhereDistinct(clazz, whereClause, args);
        }

        @Override
        public int Count() {
            return service.Count(clazz);
        }

        @Override
        public int Count(String whereClause, Object... args) {
            return service.Count(clazz, whereClause, args);
        }

        @Override
        public T getRandom() {
            return service.getRandom(clazz);
        }

        @Override
        public T getRandom(String whereClause, Object... args) {
            return service.getRandom(clazz, whereClause, args);
        }

        @Override
        public Optional<T> doQuery(String sql, Object... args) {
            return service.doQuery(clazz, sql, args);
        }

        @Override
        public List<T> doQueryAll(String sql, Object... args) {
            return service.doQueryAll(clazz, sql, args);
        }

        @Override
        public Set<T> doQueryAllDistinct(String sql, Object... args) {
            return service.doQueryAllDistinct(clazz, sql, args);
        }

        @Override
        public Optional<T> doQueryNoCache(String sql, Object... args) {
            return service.doQueryNoCache(clazz, sql, args);
        }

        @Override
        public List<T> doQueryAllNoCache(String sql, Object... args) {
            return service.doQueryAllNoCache(clazz, sql, args);
        }

        @Override
        public Set<T> doQueryAllDistinctNoCache(String sql, Object... args) {
            return service.doQueryAllDistinctNoCache(clazz, sql, args);
        }

        @Override
        public Optional<T> doQueryValue(String sql, Object... args) {
            return service.doQueryValue(clazz, sql, args);
        }

        @Override
        public Optional<T> doQueryValueNoCache(String sql, Object... args) {
            return service.doQueryValueNoCache(clazz, sql, args);
        }

        @Override
        public int doUpdate(String sql, Object... args) {
            return service.doUpdate(clazz, sql, args);
        }

        @Override
        public void createSchemaIfMissing() {
            service.createSchemaIfMissing(Arrays.asList(clazz));
        }

    }
}