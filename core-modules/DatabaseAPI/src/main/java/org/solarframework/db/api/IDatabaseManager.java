package org.solarframework.db.api;

import org.solarframework.db.api.dto.DatabaseStats;
import org.solarframework.db.api.dto.TableStats;
import org.solarframework.db.spring.DatabaseObject;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface IDatabaseManager {

    void reload();

    boolean IsSingleSource();

    void verifyEntities();
    void verifyEntity(IStoredDataSource ds, Class<?> C);


    void createAllSchemas();

    void updateAllSchemas();

    IStoredDataSource makeNewSource(Set<IEntityInfo> entities);

    boolean addSource(IStoredDataSource ds);

    boolean removeSource(String connectionString);

    Set<IStoredDataSource> getSources();

    IDatabaseService getService(String name);

    IDatabaseService getService(Class<?> entity);

    <T> IDatabaseService.ENTITY<T> getEntityService(Class<T> entity);

    IStoredDataSource getSourceByName(String name);

    IStoredDataSource getSourceByEntity(String className);

    IStoredDataSource getSourceByEntity(Class<?> entity);

    IStoredDataSource getDefaultAvailableSource();

    IDatabaseService getDefaultService();

    <T> Optional<T> getByIdWithJoins(Class<T> clazz, Object... id);

    <T> Optional<T> getById(String select, Class<T> clazz, Object... id);

    <T> Optional<T> getById(Class<T> clazz, Object... id);

    <T> Optional<T> getWhereWithJoins(Class<T> clazz, String whereClause, Object... args);

    <T> Optional<T> getWhere(String select, Class<T> clazz, String whereClause, Object... args);

    <T> Optional<T> getWhere(Class<T> clazz, String whereClause, Object... args);

    <T> List<T> getAll(String select, Class<T> clazz);

    <T> List<T> getAll(Class<T> clazz);

    <T> List<T> getAllWhere(String select, Class<T> clazz, String whereClause, Object... args);

    <T> List<T> getAllWhere(Class<T> clazz, String whereClause, Object... args);

    <T> Set<T> getAllWhereDistinct(String select, Class<T> clazz, String whereClause, Object... args);

    <T> Set<T> getAllWhereDistinct(Class<T> clazz, String whereClause, Object... args);

    <T> int Count(Class<T> clazz);

    <T> int Count(Class<T> clazz, String whereClause, Object... args);

    <T> T getRandom(String select, Class<T> clazz);

    <T> T getRandom(Class<T> clazz);

    <T> T getRandom(String select, Class<T> clazz, String whereClause, Object... args);

    <T> T getRandom(Class<T> clazz, String whereClause, Object... args);

    void resetAllCaches();

    void resetCache(String cacheName);

    void resetCacheFor(IDBObjectService<?> dbobject);

    void resetCacheFor(DatabaseObject<?> dbobject);

    void resetCacheForClass(Class<?> dbClazz, boolean items, boolean lists);

    DatabaseStats getAllDatabaseStats();

    <T> TableStats getTableStats(Class<T> clazz);

    void setEntityClassLoaders(Map<String, ClassLoader> entityClassloaders);

    void addEntityClassLoader(String name, ClassLoader entityClassloaders);

    Set<ClassLoader> getEntityClassloaders();

    ClassLoader getEntityClassloader(String key);

    String getEntityClassloaderKey(ClassLoader key);

    void LoadFromFile(String path);

    void SaveAsFile(String path);
}