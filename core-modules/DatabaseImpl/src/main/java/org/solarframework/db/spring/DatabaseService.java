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
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PostLoadEventListener;
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
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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

/**
 * One data source: one connection string, one Hikari pool, one SessionFactory, and the entities registered against it.
 * <p>Not a Spring bean. Under {@link DatabaseConfig} the default source gets its {@code @Value} fields injected from
 * {@code spring.datasource.*}; every other source - and every source of an application with no context at all - is
 * configured through the setters instead, which is what {@code DatabaseManager#makeNewSource} and {@code DataSourceFile}
 * already do.
 */
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
    @JsonIgnore
    transient JpaSourceRegistrar.JpaSourceBeans jpaBeans;

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
    @Value("${spring.datasource.hikari.keepalive-time:#{null}}") private Long keepaliveTime;
    @Value("${spring.datasource.hikari.leak-detection-threshold:#{null}}") private Long leakDetectionThreshold;

    /** The caches are left for whichever manager takes this source to hand over - {@code DatabaseManager}'s constructor sets them on the default source, {@code makeNewSource} and {@code LoadFromFile} on every other. */
    public DatabaseService() {
        setName("Database (Default)");
        // NOTE: @Value fields are injected AFTER the constructor runs; they are null here.
        // Configuration derived from them happens in init() below.
    }
    public DatabaseService(CacheManager dbCacheManager) {
        this();
        this.dbCacheManager = dbCacheManager;
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
    /** The Spring bean name this source's JPA infrastructure (EMF, tx manager) is registered under. */
    public String jpaBeanName() {
        return "solarJpa_" + name.replaceAll("\\W+", "_");
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
    public long getKeepaliveTime() {
        return keepaliveTime != null ? keepaliveTime : 0L;
    }
    public long getLeakDetectionThreshold() {
        return leakDetectionThreshold != null ? leakDetectionThreshold : 0L;
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
    public void setKeepaliveTime(long keepaliveTime) {
        this.keepaliveTime = keepaliveTime;
    }
    public void setLeakDetectionThreshold(long leakDetectionThreshold) {
        this.leakDetectionThreshold = leakDetectionThreshold;
    }

    public void setEntities(Collection<IEntityInfo> entities) {
        this.entities = sortByDependency(entities); // no clearEntities() first: the list is replaced outright, and its reload() only made this tear the source down twice
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
            // Collections/proxies faulted out-of-transaction (enable_lazy_load_no_trans) load through a
            // StatelessSession (AbstractPersistentCollection#openTemporarySessionForLoading), which skips
            // the event system entirely - no PostLoad, no INIT_COLLECTION - so this can only reach entities
            // Hibernate loads through one of our own EntityManagers: getById/getAllWhere/getWhere results,
            // and any to-one association resolved while that EntityManager is still open (eager, or a lazy
            // one this same call touches). A collection's own elements, and a proxy nothing touches until
            // after the call returns, are outside what any Interceptor/event hook can reach.
            ((SessionFactoryImplementor) sessionFactory).getServiceRegistry().getService(EventListenerRegistry.class)
                    .appendListeners(EventType.POST_LOAD, (PostLoadEventListener) event -> {
                        DBInstanceService.canonicalizeToOneAssociations(this, event.getEntity());
                        DBInstanceService.replaceInverseCollections(this, event.getEntity());
                    });
        }
        return sessionFactory;
    }

    /**
     * Lazily builds this source's JPA EntityManagerFactory/JpaTransactionManager on first access, so adding
     * many sources via DatabaseManager#addSource/LoadFromFile stays cheap - only a source someone actually
     * opens a transaction against ever pays the Hibernate bootstrap cost. TransactionResolver deliberately
     * reads the raw {@code jpaBeans} field instead of calling this getter, so merely checking "is a
     * transaction bound right now" never itself triggers the build.
     *
     * <p>Stays null with no application context - the beans it would build are registered singletons, so there is
     * nowhere to put them. Nothing breaks: TransactionResolver then always answers "not bound", and every read and
     * write takes the SessionFactory path instead.
     */
    public synchronized JpaSourceRegistrar.JpaSourceBeans getJpaBeans() {
        if (jpaBeans == null && manager instanceof DatabaseManager dm && dm.getContext() != null) JpaSourceRegistrar.register(this, dm.getContext());
        return jpaBeans;
    }
    public void setJpaBeans(JpaSourceRegistrar.JpaSourceBeans jpaBeans) {
        this.jpaBeans = jpaBeans;
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
        config.setKeepaliveTime(getKeepaliveTime());
        config.setLeakDetectionThreshold(getLeakDetectionThreshold());
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
        jpaBeans = null; // built around the dataSource just closed above - stale until something re-registers it

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
        return doQueryValueNoCache(item, "SELECT " + column + " FROM " + getTableName(table) + " WHERE " + DBInstanceService.idFieldsOf(table).stream().map(f -> DatabaseObject.columnOf(f) + " = ?").collect(Collectors.joining(" AND ")), id);
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

    private static final Pattern ORDER_BY = Pattern.compile("\\sORDER\\s+BY\\s", Pattern.CASE_INSENSITIVE);

    /** Callers routinely fold "ORDER BY ..." into whereClause; splitting it off keeps it outside the parenthesised predicate below. */
    private static String withActiveFilter(Class<?> clazz, String whereClause) {
        if (!isSoftDeletable(clazz)) return whereClause;
        if (whereClause == null || whereClause.isBlank()) return "DeletedAt IS NULL";
        Matcher m = ORDER_BY.matcher(whereClause);
        if (!m.find()) return "(" + whereClause + ") AND DeletedAt IS NULL";
        return "(" + whereClause.substring(0, m.start()) + ") AND DeletedAt IS NULL " + whereClause.substring(m.start() + 1);
    }

    private static final Map<String, String> LazySelects = new ConcurrentHashMap<>();

    /**
     * The select list every read uses by default: every column of the table, with the blob ones replaced by a
     * NULL literal. Leaving them out of the list is not an option - a native query mapped to an entity needs
     * all of its columns in the result set - but a NULL literal carries no payload, and
     * {@link DBInstanceService#markUnloaded} turns it back into "never fetched" right after hydration, so the
     * getters fetch on demand instead of reporting NULL.
     *
     * <p>Plain {@code "*"} for the entities that declare no blob, which leaves every other read untouched.
     */
    String selectOf(Class<?> clazz) {
        if (DBInstanceService.lazyFieldsOf(clazz).isEmpty()) return "*";
        String known = LazySelects.get(clazz.getName());
        if (known != null) return known;
        List<Field> lazy = DBInstanceService.lazyFieldsOf(clazz);
        List<String> columns = getTableStats(getTableName(clazz)).getColumnNames();
        if (columns.isEmpty()) return "*"; // table not created yet - nothing to enumerate, and not cached either
        String select = Stream.concat(columns.stream().filter(c -> lazy.stream().noneMatch(f -> DatabaseObject.columnOf(f).equalsIgnoreCase(c))), lazy.stream().map(f -> "NULL AS " + DatabaseObject.columnOf(f))).collect(Collectors.joining(", "));
        LazySelects.put(clazz.getName(), select);
        return select;
    }
    static void clearLazySelects() { LazySelects.clear(); }

    /** Bypasses the soft-delete filter on purpose - if the caller already has an ID, they get the row even if it was soft-deleted. */
    public <T> Optional<T> getById(String select, Class<T> clazz, Object... id) {
        String where = DBInstanceService.idFieldsOf(clazz).stream().map(f -> DatabaseObject.columnOf(f) + " = ?").collect(Collectors.joining(" AND "));
        return this.doQuery(clazz, "SELECT " + select + " FROM " + getTableName(clazz) + " WHERE " + where, id);
    }
    public <T> Optional<T> getById(Class<T> clazz, Object... id) {
        if (TransactionResolver.isBound(this)) return TransactionalAccess.getById(this, clazz, id.length == 1 ? id[0] : id);
        return getById(selectOf(clazz), clazz, id);
    }

    public <T> Optional<T> getWhere(String select, Class<T> clazz, String whereClause, Object... args) {
        return this.doQuery(clazz, "SELECT " + select + " FROM " + getTableName(clazz) + " WHERE " + withActiveFilter(clazz, whereClause), args);
    }
    public <T> Optional<T> getWhere(Class<T> clazz, String whereClause, Object... args) {
        if (TransactionResolver.isBound(this)) return TransactionalAccess.getWhere(this, clazz, whereClause, args);
        return getWhere(selectOf(clazz), clazz, whereClause, args);
    }

    public <T> List<T> getAll(String select, Class<T> clazz) {
        String where = withActiveFilter(clazz, null);
        return this.doQueryAll(clazz, "SELECT " + select + " FROM " + getTableName(clazz) + (where == null ? "" : " WHERE " + where), null);
    }
    public <T> List<T> getAll(Class<T> clazz) {
        if (TransactionResolver.isBound(this)) return TransactionalAccess.getAll(this, clazz);
        return getAll(selectOf(clazz), clazz);
    }

    public <T> List<T> getAllWhere(String select, Class<T> clazz, String whereClause, Object... args) {
        return this.doQueryAll(clazz, "SELECT " + select + " FROM " + getTableName(clazz) + " WHERE " + withActiveFilter(clazz, whereClause), args);
    }
    public <T> List<T> getAllWhere(Class<T> clazz, String whereClause, Object... args) {
        if (TransactionResolver.isBound(this)) return TransactionalAccess.getAllWhere(this, clazz, whereClause, args);
        return getAllWhere(selectOf(clazz), clazz, whereClause, args);
    }
    public <T> Set<T> getAllWhereDistinct(String select, Class<T> clazz, String whereClause, Object... args) {
        return this.doQueryAllDistinct(clazz, "SELECT " + select + " FROM " + getTableName(clazz) + " WHERE " + withActiveFilter(clazz, whereClause), args);
    }
    public <T> Set<T> getAllWhereDistinct(Class<T> clazz, String whereClause, Object... args) {
        return getAllWhereDistinct(selectOf(clazz), clazz, whereClause, args);
    }

    public <T> int Count(Class<T> clazz) {
        if (TransactionResolver.isBound(this)) return (int) TransactionalAccess.count(this, clazz);
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
        return this.getRandom(selectOf(clazz), clazz);
    }
    public <T> T getRandom(String select, Class<T> clazz, String whereClause, Object... args) {
        return this.getWhere(select, clazz, whereClause + " AND ID >= FLOOR(RAND() * (SELECT MAX(ID) FROM " + getTableName(clazz) + "))", args).orElse(null);
    }
    public <T> T getRandom(Class<T> clazz, String whereClause, Object... args) {
        return this.getRandom(selectOf(clazz), clazz, whereClause, args);
    }

    // ====== GETTERS ======

    /**
     * A result past this many rows is served but never stored. The big ones are the list/leaderboard queries
     * nobody re-requests with identical arguments, and a single one of them outweighs every other entry in the
     * cache combined - it used to be held for a full hour and counted as 1 of the 10 000 permitted entries.
     */
    private static final int MAX_CACHED_ROWS = 1_000;

    /**
     * {@code <connection>|<owner>|<shape>|<type>|<sql>|<args>}. Three things ride on this shape.
     *
     * <p>The <b>owner</b> segment names the entity class an entry's rows belong to, and is empty for a scalar,
     * a Row, a non-entity DTO, or - see {@link #staysOnOneTable} - a statement whose rows depend on a table
     * other than its own. That is what lets {@link DatabaseManager#resetCacheForClass} decide what a write
     * invalidates from the key alone, instead of reflecting over every element of every cached list on every
     * write. Empty means "attributable to no table", so it is dropped on any write.
     * It is deliberately <i>not</i> the only place the type appears: the <b>type</b> segment repeats it
     * unconditionally, because two different requested types over the same SQL are two different results and
     * blanking the owner for both would have them share an entry and hand one back as the other.
     *
     * <p>The <b>shape</b> segment separates "one"/"all"/"set" because the same SQL can be asked for as a single
     * value, a List or a Set. And the sql and args go in verbatim rather than through {@code Objects.hash}: an
     * int hash over ten thousand live entries collides with a probability around one percent, and a collision
     * here does not miss - it serves another query's rows as if they were yours.
     */
    private String cacheKey(Class<?> type, String shape, String sql, Object[] args) {
        String name = type == null ? "" : type.getName();
        return getConnectionString().hashCode() + "|" + (type != null && DatabaseObject.class.isAssignableFrom(type) && staysOnOneTable(sql) ? name : "") + "|" + shape + "|" + name + "|" + sql + "|" + (args == null ? "" : Arrays.deepToString(args));
    }

    /** No whitespace before the paren on purpose: {@code AND (a OR b)} is a grouped predicate, not a call, and allowing it would strip attribution from most ordinary where clauses. */
    private static final Pattern CALLS_A_FUNCTION = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*\\(");

    /**
     * Whether this statement's rows can only have been changed by a write to its own table - which is what
     * makes attributing the entry to one entity class sound.
     *
     * <p>Attribution by class is the cheap invalidation signal, but it is only as good as the assumption that
     * a query reads the table it selects from. {@code SELECT * FROM item WHERE price > 40} keeps that promise;
     * {@code ... JOIN clanmember}, {@code ... WHERE ID IN (SELECT ...)} and {@code ... WHERE
     * GetClanMemberCount(ID) > 0} do not, and the last one is the reason this is decided on the SQL rather than
     * on the entity graph: a stored function's body is invisible to any association check, so a mapped
     * relation between the two entities is the only thing that would have caught it, and that is luck rather
     * than a rule. A statement that reaches further gets no owner and is dropped on any write.
     *
     * <p>Deliberately conservative and deliberately not memoised. Every test is a linear scan of a string the
     * caller just built by concatenation, next to a database round trip - and a wrong answer in the safe
     * direction only costs an eviction, while memoising it would key a permanent map on raw SQL, which callers
     * are free to build with values interpolated into it.
     */
    static boolean staysOnOneTable(String sql) {
        String u = sql.toUpperCase();
        if (u.contains(" JOIN ") || u.indexOf("SELECT", u.indexOf("SELECT") + 1) > 0) return false;
        return !CALLS_A_FUNCTION.matcher(u).find();
    }

    private <T> T getCachedOrCompute(String cacheName, String cacheKey, Supplier<T> supplier) {
        Cache cache = dbCacheManager.getCache(cacheName);
        String key = cacheKey + "-" + getConnectionString().hashCode();
        if (cache != null) {
            Cache.ValueWrapper cached = cache.get(key);
            if (cached != null) return (T) cached.get();
        }
        T result = supplier.get();
        if (cache != null && result != null && !(result instanceof Collection<?> c && c.size() > MAX_CACHED_ROWS)) cache.put(key, result);
        return result;
    }

    public <T> Optional<T> doQuery(Class<T> clazz, String sql, Object... args) {
        return getCachedOrCompute("DBObject", cacheKey(clazz, "one", sql, args), () -> doQueryNoCache(clazz, sql, args));
    }
    public <T> List<T> doQueryAll(Class<T> clazz, String sql, Object... args) {
        return getCachedOrCompute("DBObject", cacheKey(clazz, "all", sql, args), () -> doQueryAllNoCache(clazz, sql, args));
    }
    public <T> Set<T> doQueryAllDistinct(Class<T> clazz, String sql, Object... args) {
        return getCachedOrCompute("DBObject", cacheKey(clazz, "set", sql, args), () -> doQueryAllDistinctNoCache(clazz, sql, args));
    }

    public <T> Optional<T> doQueryNoCache(Class<T> clazz, String sql, Object... args) {
        List<T> results = runNativeQuery(clazz, sql, args, 1);
        return results.isEmpty() ? Optional.empty() : Optional.ofNullable(results.getFirst());
    }
    public <T> List<T> doQueryAllNoCache(Class<T> clazz, String sql, Object... args) {
        return runNativeQuery(clazz, sql, args, -1);
    }
    public <T> Set<T> doQueryAllDistinctNoCache(Class<T> clazz, String sql, Object... args) {
        return new HashSet<>(doQueryAllNoCache(clazz, sql, args)); // ...NoCache must not populate the cache through the cached overload
    }

    /**
     * Rows live in their own cache, on a much shorter TTL. A {@link Row} is a LinkedHashMap per row - the most
     * expensive shape anything here stores, roughly 40 bytes per column on top of the boxed values - and these
     * are the list/report queries, so they were both the heaviest entries and the least likely to be re-asked
     * identically. Their invalidation is unchanged: attributable to no table, so any write drops all of them,
     * which {@link DatabaseManager#resetCacheForClass} now does by clearing this cache outright.
     */
    public Optional<Row> doQuery(String sql, Object... args) {
        return getCachedOrCompute("DBRow", cacheKey(null, "rowone", sql, args), () -> doQueryNoCache(sql, args));
    }
    public List<Row> doQueryAll(String sql, Object... args) {
        return getCachedOrCompute("DBRow", cacheKey(null, "rowall", sql, args), () -> doQueryAllNoCache(sql, args));
    }
    public Set<Row> doQueryAllDistinct(String sql, Object... args) {
        return getCachedOrCompute("DBRow", cacheKey(null, "rowset", sql, args), () -> doQueryAllDistinctNoCache(sql, args));
    }

    public Optional<Row> doQueryNoCache(String sql, Object... args) {
        List<Row> rows = runNativeRowQuery(sql, args, 1);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }
    public List<Row> doQueryAllNoCache(String sql, Object... args) {
        return runNativeRowQuery(sql, args, -1);
    }
    public Set<Row> doQueryAllDistinctNoCache(String sql, Object... args) {
        return new HashSet<>(runNativeRowQuery(sql, args, -1));
    }

    /** {@code clazz} here is the scalar's own type (Integer for a COUNT), never a DatabaseObject - so the key comes out with no owner and any write drops it. */
    public <T> Optional<T> doQueryValue(Class<T> clazz, String sql, Object... args) {
        return getCachedOrCompute("DBObject", cacheKey(clazz, "val", sql, args), () -> doQueryValueNoCache(clazz, sql, args));
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

    private static final Map<Class<?>, Class<?>> ConcreteEntities = new ConcurrentHashMap<>();

    /**
     * Abstract @Entity roots (Tournament, Match...) cannot be the target of a native query: Hibernate skips the
     * discriminator fetch only for leaf types, so on a root it looks for the @DiscriminatorFormula's label ('0') in
     * a result set that a native SELECT * never carries. Mapping to the single concrete subclass sidesteps it, and
     * the reflective Tuple path cannot instantiate an abstract class either.
     */
    private <T> Class<T> concreteEntity(Class<T> clazz) {
        if (!Modifier.isAbstract(clazz.getModifiers())) return clazz;
        Class<?> known = ConcreteEntities.get(clazz);
        if (known == null) {
            known = clazz;
            for (var e : getSessionFactory().getMetamodel().getEntities()) {
                Class<?> j = e.getJavaType();
                if (j != clazz && clazz.isAssignableFrom(j) && !Modifier.isAbstract(j.getModifiers())) { known = j; break; }
            }
            ConcreteEntities.put(clazz, known);
        }
        return (Class<T>) known;
    }

    /** setMaxResults becomes a LIMIT clause, which no database accepts on the INSERT ... RETURNING * of a WriteThenReturn. */
    private static boolean takesLimit(String sql) {
        return sql.stripLeading().regionMatches(true, 0, "SELECT", 0, 6);
    }

    /** Runs a native query mapped to a class. Entities map via JPA; other classes map reflectively by column name. */
    private <T> List<T> runNativeQuery(Class<T> clazz, String sql, Object[] args, int maxResults) {
        return withEm(this, em -> {
            DatabaseUtils.SQLCleaner C = new DatabaseUtils.SQLCleaner(sql, args);
            // A blob-less read is a full row too - it carries every column, the lazy ones as NULL literals - so it maps as an entity and stays canonical.
            boolean lazyRow = isEntity(clazz) && !"*".equals(selectOf(clazz)) && C.newSQL.contains(selectOf(clazz));
            if (isEntity(clazz) && (selectsAllColumns(C.newSQL) || lazyRow)) {
                Query q = bindParams(em.createNativeQuery(toOrdinalParams(C.newSQL), concreteEntity(clazz)), C.newParams);
                if (maxResults > 0 && takesLimit(C.newSQL)) q.setMaxResults(maxResults);
                // Full rows only - the Tuple path below can carry a subset of the columns, which would blank out the rest of the canonical object.
                return ((List<T>) q.getResultList()).stream().map(o -> { if (lazyRow) DBInstanceService.markUnloaded(o); return EntityIdentity.canonical(this, o); }).collect(Collectors.toList());
            }
            Query q = bindParams(em.createNativeQuery(toOrdinalParams(C.newSQL), Tuple.class), C.newParams);
            if (maxResults > 0 && takesLimit(C.newSQL)) q.setMaxResults(maxResults);
            List<Tuple> tuples = q.getResultList();
            return tuples.stream().map(t -> mapTupleToObject(t, concreteEntity(clazz))).collect(Collectors.toList());
        });
    }

    /** Runs a native query returning generic Rows (column name -> value). */
    private List<Row> runNativeRowQuery(String sql, Object[] args, int maxResults) {
        return withEm(this, em -> {
            DatabaseUtils.SQLCleaner C = new DatabaseUtils.SQLCleaner(sql, args);
            Query q = bindParams(em.createNativeQuery(toOrdinalParams(C.newSQL), Tuple.class), C.newParams);
            if (maxResults > 0 && takesLimit(C.newSQL)) q.setMaxResults(maxResults);
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
                // keyed by the column each field owns, not by the field's own name: a result alias is a column name
                for (Field f : c.getDeclaredFields()) fields.putIfAbsent(DatabaseObject.columnOf(f).toLowerCase(), f);
            }
            for (TupleElement<?> el : tuple.getElements()) {
                if (el.getAlias() == null) continue;
                Field f = fields.get(el.getAlias().toLowerCase());
                if (f == null) continue;
                Object value = tuple.get(el);
                if (value == null) continue;
                f.setAccessible(true);
                Object converted = convertScalar(value, (Class<Object>) f.getType());
                if (converted == null && f.getType().isPrimitive()) {
                    // Driver returned a type we couldn't coerce; leave the field at its default
                    // rather than crashing on Field.set(primitive, null).
                    continue;
                }
                f.set(instance, converted);
            }
            return instance;
        } catch (Exception e) {
            throw new RuntimeException("Cannot map result to " + clazz.getName(), e);
        }
    }

    /** Converts JDBC scalar results (BigInteger, BigDecimal, etc.) to the requested Java type. */
    private <T> T convertScalar(Object value, Class<T> type) {
        if (value == null) return null;
        Class<?> targetType = wrap(type); // normalize primitive -> wrapper before any isInstance/cast check
        if (targetType.isInstance(value)) return (T) targetType.cast(value);
        if (value instanceof byte[]) return (T) value;
        if (value instanceof Byte[]) return (T) value;
        // A BLOB column read outside the entity mapping (refetchAttribute, doQueryValue) comes back as a driver Blob handle on H2 - materialize it, the caller asked for bytes.
        if (value instanceof java.sql.Blob b) try { return (T) b.getBinaryStream().readAllBytes(); } catch (Exception e) { return null; }
        if (value instanceof Boolean b) {
            if (targetType == Boolean.class) return (T) b;
            if (Number.class.isAssignableFrom(targetType)) {
                return convertScalar(b ? 1 : 0, type);
            }
        }
        if (value instanceof Number n) {
            if (targetType == Integer.class) return (T) Integer.valueOf(n.intValue());
            if (targetType == Long.class) return (T) Long.valueOf(n.longValue());
            if (targetType == Double.class) return (T) Double.valueOf(n.doubleValue());
            if (targetType == Float.class) return (T) Float.valueOf(n.floatValue());
            if (targetType == Short.class) return (T) Short.valueOf(n.shortValue());
            if (targetType == Byte.class) return (T) Byte.valueOf(n.byteValue());
            if (targetType == BigDecimal.class) return (T) new BigDecimal(n.toString());
            if (targetType == Boolean.class) return (T) Boolean.valueOf(n.intValue() != 0);
        }
        if (targetType == String.class) return (T) value.toString();
        if (targetType == Boolean.class && value instanceof String s) return (T) Boolean.valueOf(s);
        try {
            return (T) targetType.cast(value);
        } catch (ClassCastException e) {
            return null;
        }
    }

    /** Boxes a primitive Class to its wrapper type; returns non-primitive types unchanged. */
    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == boolean.class) return Boolean.class;
        if (type == int.class)     return Integer.class;
        if (type == long.class)    return Long.class;
        if (type == double.class)  return Double.class;
        if (type == float.class)   return Float.class;
        if (type == short.class)   return Short.class;
        if (type == byte.class)    return Byte.class;
        if (type == char.class)    return Character.class;
        return type;
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

    private static final Pattern RETURNING_ALL = Pattern.compile("(?is)\\breturning\\s+\\*\\s*;?\\s*$");

    /** True if the statement yields every column: a "*" / "alias.*" select list, or the RETURNING * of a WriteThenReturn. */
    private boolean selectsAllColumns(String sql) {
        if (RETURNING_ALL.matcher(sql).find()) return true;
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

    public int createOrReplaceView(String viewName, String sql) {
        return doUpdate("CREATE OR REPLACE VIEW " + viewName + " AS " + sql);
    }
    public int createOrReplaceFunction(String sql) {
        return doUpdate("CREATE OR REPLACE FUNCTION " + sql);
    }

    public int createOrReplaceProcedure(String sql) {
        return doUpdate("CREATE OR REPLACE PROCEDURE " + sql);
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