package org.solarframework.db.spring;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.solarframework.db.api.IDatabaseService;

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

public class AvailableDataSource {
    private transient IDatabaseService service;
    private transient DataSource dataSource;


    private transient Long ping;
    private transient String pingError;

    private final String id = UUID.randomUUID().toString();
    private String name;
    private String username;
    private String password;
    private String connectionString;
    private String type;
    private boolean isDefault = false;

    private int maxPoolSize = 50;
    private int minimumIdle = 5;
    private long idleTimeout = 30000;
    private long maxLifetime = 1800000;
    private long connectionTimeout = 20000;

    private List<String> entities = new ArrayList<>();

    public AvailableDataSource() {}

    public void addEntity(Class<?>... entities) {
        this.entities.addAll(Stream.of(entities).map(Class::getName).toList());
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
    public String getType() {
        return type;
    }
    public boolean isDefault() {
        return isDefault;
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
    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
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
    public void setType(String type) {
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

    public long getPing() {
        if (ping == null) calculatePing();
        return ping;
    }
    public String getPingError() {
        if (pingError == null) calculatePing();
        return pingError;
    }
    private void calculatePing() {
        long start = System.nanoTime();
        try (Connection conn = getDataSource().getConnection(); PreparedStatement ps = conn.prepareStatement("SELECT 1"); ResultSet rs = ps.executeQuery()) {
            pingError = "";
            ping = (System.nanoTime() - start) / 1_000_000;
        } catch (Exception e) {
            pingError = "Error: " + e.getMessage();
            ping = 0L;
        }
    }


    public IDatabaseService getService() {
        if (service != null) return service;
        if (DefaultDBService != null) this.service = (DatabaseService) copyObject(new DatabaseService(null, null, null, null), DefaultDBService);
        if (this.service != null) this.service.setDataSource(getDataSource());
        return service;
    }
    public DataSource getDataSource() {
        if (this.dataSource != null) return dataSource;
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(getConnectionString());
        config.setUsername(getUsername());
        config.setPassword(getPassword());
        config.setDriverClassName(getType());

        config.setMaximumPoolSize(getMaxPoolSize());
        config.setMinimumIdle(getMinimumIdle());
        config.setIdleTimeout(getIdleTimeout());
        config.setMaxLifetime(getMaxLifetime());
        config.setConnectionTimeout(getConnectionTimeout());
        config.setPoolName("MyHikariPool-" + getName().replace(" ", "_"));
        return dataSource = new HikariDataSource(config);
    }



    public List<String> getEntities() {
        return entities;
    }
    public List<Class<?>> getEntitiesClasses() {
        return entities.stream().map(entityName -> {
            try {
                return Class.forName(entityName);
            } catch (ClassNotFoundException e) {
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public List<String> getUpdatableEntities() {
        return getUpdatableEntitiesClasses().stream().map(Class::getSimpleName).toList();
    }
    public List<Class<?>> getUpdatableEntitiesClasses() {
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
            for (Field field : fields)
                if (columns.stream().noneMatch(column -> column.equalsIgnoreCase(field.getName())))
                    entities.add(entity);
        }
        return entities;
    }

    public List<String> getInstalledEntities() {
        return getService().getDatabaseStats().getTableNames();
    }
    public List<Class<?>> getInstalledEntitiesClasses() {
        return getInstalledEntities().stream().map(this::getClassOfTable).collect(Collectors.toList());
    }

    public List<String> getMissingEntities() {
        List<String> installedEntities = getInstalledEntities();
        return getEntities().stream().filter(availableEntity -> installedEntities.stream().noneMatch(availableEntity::equalsIgnoreCase)).toList();
    }
    public List<Class<?>> getMissingEntitiesClasses() {
        return getMissingEntities().stream().map(entityName -> {
            try {
                return Class.forName(entityName);
            } catch (ClassNotFoundException e) {
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    private Class<?> getClassOfTable(String name) {
        return getEntitiesClasses().stream().filter(availableEntity -> DatabaseUtils.getTableName(availableEntity).equalsIgnoreCase(name)).findFirst().orElse(null);
    }
}