package org.solarframework.db.spring;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.Column;
import org.solarframework.db.api.DatabaseType;
import org.solarframework.db.api.IDatabaseService;
import org.solarframework.json.JSONItem;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.solarframework.core.util.ClassUtils.copyObject;
import static org.solarframework.core.util.ClassUtils.getAllFieldsOfClassFamily;
import static org.solarframework.db.spring.DatabaseRegistry.DefaultDBService;

public class AvailableDataSource extends JSONItem<AvailableDataSource> {
    protected transient DatabaseManager manager;
    private transient DatabaseService service;
    private transient DataSource dataSource;


    private transient Long ping;
    private transient String pingError;

    private boolean Default = false;
    private final String id = UUID.randomUUID().toString();
    private String name;
    private String username;
    private String password;
    private String connectionString;
    private DatabaseType type;

    private int maxPoolSize = 50;
    private int minimumIdle = 5;
    private long idleTimeout = 30000;
    private long maxLifetime = 1800000;
    private long connectionTimeout = 20000;

    private List<String> entities = new ArrayList<>();

    public AvailableDataSource(DatabaseManager manager) {
        this.manager = manager;
    }

    public void setEntities(List<Class<?>> entities) {
        this.entities = entities.stream().map(Class::getName).collect(Collectors.toList());
        clearEntities();
    }
    public void addEntities(Class<?>... entities) {
        this.entities.addAll(Stream.of(entities).map(Class::getName).toList());
        clearEntitiesClasses();
    }
    public void removeEntities(Class<?>... entities) {
        this.entities.removeAll(Stream.of(entities).map(Class::getName).toList());
        clearEntitiesClasses();
    }
    public void clearEntities() {
        this.entities.clear();
        clearEntitiesClasses();
    }
    private void clearEntitiesClasses() {
        entitiesClasses = null;
        missingClasses = null;
        updatableClasses = null;
        installedClasses = null;
    }

    public String getId() {
        return id;
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
    public DatabaseType getType() {
        return type;
    }
    public boolean isDefault() {
        return Default;
    }
    public int getMaxPoolSize() {
        return maxPoolSize;
    }
    public int getMinimumIdle() {
        return minimumIdle;
    }
    public long getIdleTimeout() {
        return idleTimeout;
    }
    public long getMaxLifetime() {
        return maxLifetime;
    }
    public long getConnectionTimeout() {
        return connectionTimeout;
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
    public void setType(DatabaseType type) {
        this.type = type;
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
    protected void asDefault() {
        this.Default = true;
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


    public IDatabaseService getService() {
        if (service == null && manager != null) {
            this.service = (DatabaseService) copyObject(new DatabaseService(null, null, null), DefaultDBService);
            if (this.service != null) {
                this.service.availableSource = this;
                this.service.jdbcTemplate = new JdbcTemplate(getDataSource());
            }
        }
        return service;
    }
    public DataSource getDataSource() {
        if (this.dataSource != null) return dataSource;
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(getConnectionString());
        config.setUsername(getUsername());
        config.setPassword(getPassword());
        config.setDriverClassName(getType().getDriverClass());

        config.setMaximumPoolSize(getMaxPoolSize());
        config.setMinimumIdle(getMinimumIdle());
        config.setIdleTimeout(getIdleTimeout());
        config.setMaxLifetime(getMaxLifetime());
        config.setConnectionTimeout(getConnectionTimeout());
        config.setPoolName("MyHikariPool-" + getName().replace(" ", "_"));
        return dataSource = new HikariDataSource(config);
    }


    protected transient List<Class<?>> entitiesClasses;
    protected transient List<Class<?>> updatableClasses;
    protected transient List<Class<?>> installedClasses;
    protected transient List<Class<?>> missingClasses;


    public List<String> getEntities() {
        return entities;
    }
    public List<Class<?>> getEntitiesClasses() {
        return entitiesClasses == null ? entitiesClasses = getEntities().stream().map(entityName -> {
            for (ClassLoader cl : manager.getEntityClassloaders()) {
                try {
                    return cl.loadClass(entityName);
                } catch (ClassNotFoundException _) {}
            }
            return null;
        }).filter(Objects::nonNull).collect(Collectors.toList()) : entitiesClasses;
    }

    public List<String> getInstalledEntities() {
        return getInstalledEntitiesClasses().stream().map(Class::getName).toList();
    }
    public List<Class<?>> getInstalledEntitiesClasses() {
        return installedClasses == null ? installedClasses = getService().getDatabaseStats().getTableNames().stream().map(this::getClassOfTable).filter(Objects::nonNull).collect(Collectors.toList()) : installedClasses;
    }


    public List<String> getMissingEntities() {
        return getMissingEntitiesClasses().stream().map(Class::getName).toList();
    }
    public List<Class<?>> getMissingEntitiesClasses() {
        return missingClasses == null ? missingClasses = getEntitiesClasses().stream().filter(e -> !getInstalledEntitiesClasses().contains(e)).collect(Collectors.toList()) : missingClasses;
    }

    public List<String> getUpdatableEntities() {
        return getUpdatableEntitiesClasses().stream().map(Class::getName).toList();
    }
    public List<Class<?>> getUpdatableEntitiesClasses() {
        if (updatableClasses != null) return updatableClasses;
        List<Class<?>> entities = new ArrayList<>();
        for (String table : getService().getDatabaseStats().getTableNames()) {
            Class<?> entity = getClassOfTable(table);
            if (entity == null) continue;
            List<String> columns = getService().getTableStats(table).getColumnNames();
            List<Field> fields = getAllFieldsOfClassFamily(entity).stream().filter(f -> !Modifier.isTransient(f.getModifiers()) && !Modifier.isStatic(f.getModifiers())).toList();
            if (columns.size() != fields.size()) {
                entities.add(entity);
                continue;
            }
            for (Field field : fields) {
                String fieldName = field.getAnnotation(Column.class) != null && !field.getAnnotation(Column.class).name().isEmpty() ? field.getAnnotation(Column.class).name() : field.getName();
                if (columns.stream().noneMatch(column -> column.equalsIgnoreCase(fieldName))) {
                    entities.add(entity);
                    break;
                }
            }
        }
        return updatableClasses = entities;
    }

    public Class<?> getClassOfTable(String name) {
        return getEntitiesClasses().stream().filter(availableEntity -> DatabaseUtils.getTableName(availableEntity).equalsIgnoreCase(name)).findFirst().orElse(null);
    }
}