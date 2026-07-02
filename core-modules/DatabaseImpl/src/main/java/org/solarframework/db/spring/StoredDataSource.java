package org.solarframework.db.spring;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.solarframework.db.api.*;
import org.solarframework.db.api.dto.TableStats;
import org.solarframework.db.api.IEntityInfo;
import org.solarframework.json.JSONItem;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.solarframework.core.util.ClassUtils.copyObject;
import static org.solarframework.db.spring.DatabaseConfig.defaultConnectionString;
import static org.solarframework.db.spring.DatabaseRegistry.DefaultDBService;

public class StoredDataSource extends JSONItem<StoredDataSource> implements IStoredDataSource {
    @JsonIgnore
    protected transient IDatabaseManager manager;
    @JsonIgnore
    private transient DatabaseService service;
    @JsonIgnore
    private transient DataSource dataSource;

    private transient Long ping;
    private transient String pingError;

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

    private Set<IEntityInfo> entities = new HashSet<>();

    protected StoredDataSource(IDatabaseManager manager) {
        this.manager = manager;
    }

    public void setEntities(Set<IEntityInfo> entities) {
        clearEntities();
        this.entities = entities;
    }
    public void addEntities(IEntityInfo... entities) {
        this.entities.addAll(Arrays.stream(entities).toList());
    }
    public void removeEntities(IEntityInfo... entities) {
        Stream.of(entities).toList().forEach(this.entities::remove);
    }
    public void clearEntities() {
        this.entities.clear();
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
        return Objects.equals(getConnectionString(), defaultConnectionString);
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

    public Set<IEntityInfo> getEntities() {
        return entities;
    }
    public Set<Class<?>> getEntitiesClasses() {
        return getEntities().stream().map(IEntityInfo::getEntityClass).collect(Collectors.toSet());
    }

    public Set<IEntityInfo> getInstalledEntities() {
        return getEntities().stream().filter(e -> getService().getDatabaseStats().getTableNames().contains(e.getTableName())).collect(Collectors.toSet());
    }
    public Set<Class<?>> getInstalledEntitiesClasses() {
        return getInstalledEntities().stream().map(IEntityInfo::getEntityClass).collect(Collectors.toSet());
    }

    public Set<IEntityInfo> getMissingEntities() {
        return getEntities().stream().filter(e -> !getService().getDatabaseStats().getTableNames().contains(e.getTableName())).collect(Collectors.toSet());
    }
    public Set<Class<?>> getMissingEntitiesClasses() {
        return getMissingEntities().stream().map(IEntityInfo::getEntityClass).collect(Collectors.toSet());
    }

    public Set<IEntityInfo> getUpdatableEntities() {
        return getEntities().stream().filter(e -> {
            TableStats stats = e.getTableStats();
            if (stats.getColumnNames().size() != e.getFields().size()) return true;
            if (stats.getColumnNames().stream().allMatch(cn -> e.getFields().stream().anyMatch(fn -> Objects.equals(cn, fn.getColumnName())))) return true;
            return false;
        }).collect(Collectors.toSet());
    }
    public Set<Class<?>> getUpdatableEntitiesClasses() {
        return getUpdatableEntities().stream().map(IEntityInfo::getEntityClass).collect(Collectors.toSet());
    }

    public Class<?> getClassOfTable(String name) {
        return getEntities().stream().filter(availableEntity -> availableEntity.getTableName().equals(name)).map(IEntityInfo::getEntityClass).findFirst().orElse(null);
    }

    @Override
    public void setManager(IDatabaseManager manager) {
        this.manager = manager;
    }
}