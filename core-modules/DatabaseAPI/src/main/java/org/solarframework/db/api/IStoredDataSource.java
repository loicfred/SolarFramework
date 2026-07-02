package org.solarframework.db.api;

import javax.sql.DataSource;
import java.util.Set;

public interface IStoredDataSource {

    void setEntities(Set<IEntityInfo> entities);

    void addEntities(IEntityInfo... entities);

    void removeEntities(IEntityInfo... entities);

    void clearEntities();

    String getName();

    String getUsername();

    String getPassword();

    String getConnectionString();

    DatabaseType getType();

    boolean isDefault();

    int getMaxPoolSize();

    int getMinimumIdle();

    long getIdleTimeout();

    long getMaxLifetime();

    long getConnectionTimeout();

    void setName(String name);

    void setUsername(String username);

    void setPassword(String password);

    void setConnectionString(String connectionString);

    void setType(DatabaseType type);

    void setMaxPoolSize(int maxPoolSize);

    void setMinimumIdle(int minimumIdle);

    void setIdleTimeout(long idleTimeout);

    void setMaxLifetime(long maxLifetime);

    void setConnectionTimeout(long connectionTimeout);

    long getPing();

    String getPingError();

    void resetPing();

    IDatabaseService getService();

    DataSource getDataSource();

    Set<IEntityInfo> getEntities();

    Set<Class<?>> getEntitiesClasses();

    Set<IEntityInfo> getInstalledEntities();

    Set<Class<?>> getInstalledEntitiesClasses();

    Set<IEntityInfo> getMissingEntities();

    Set<Class<?>> getMissingEntitiesClasses();

    Set<IEntityInfo> getUpdatableEntities();

    Set<Class<?>> getUpdatableEntitiesClasses();

    Class<?> getClassOfTable(String name);

    void setManager(IDatabaseManager manager);
}