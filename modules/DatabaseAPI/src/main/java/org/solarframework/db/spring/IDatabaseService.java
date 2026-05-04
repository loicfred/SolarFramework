package org.solarframework.db.spring;

import org.solarframework.db.spring.dto.DatabaseStats;
import org.solarframework.db.spring.dto.Row;
import org.solarframework.db.spring.dto.TableStats;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface IDatabaseService {

    <T> IDBObjectService<T> makeObjectManager(DatabaseObject<T> dbobject);

    <T> Optional<T> getByIdWithJoins(Class<T> clazz, Object id);

    <T> Optional<T> getById(String select, Class<T> clazz, Object id);

    <T> Optional<T> getById(Class<T> clazz, Object id);

    <T> Optional<T> getWhereWithJoins(Class<T> clazz, String whereClause, Object... args);

    <T> Optional<T> getWhere(String select, Class<T> clazz, String whereClause, Object... args);

    <T> Optional<T> getWhere(Class<T> clazz, String whereClause, Object... args);

    <T> List<T> getAll(String select, Class<T> clazz);

    <T> List<T> getAll(Class<T> clazz);

    <T> List<T> getAllWhere(String select, Class<T> clazz, String whereClause, Object... args);

    <T> List<T> getAllWhere(Class<T> clazz, String whereClause, Object... args);

    <T> Set<T> getAllWhereDistinct(String select, Class<T> clazz, String whereClause, Object... args);

    <T> Set<T> getAllWhereDistinct(Class<T> clazz, String whereClause, Object... args);

    <O, T> Optional<O> getSingleColumnOfTableById(String column, Class<O> item, Class<?> table, Object id);

    <O, T> Optional<O> getSingleColumnOfTableWhere(String column, Class<O> item, Class<?> table, String where, Object... args);

    <T> int Count(Class<T> clazz);

    <T> int Count(Class<T> clazz, String whereClause, Object... args);

    <T> T getRandom(String select, Class<T> clazz);

    <T> T getRandom(Class<T> clazz);

    <T> T getRandom(String select, Class<T> clazz, String whereClause, Object... args);

    <T> T getRandom(Class<T> clazz, String whereClause, Object... args);

    <T> Optional<T> doQuery(Class<T> clazz, String sql, Object... args);

    <T> List<T> doQueryAll(Class<T> clazz, String sql, Object... args);

    <T> Set<T> doQueryAllDistinct(Class<T> clazz, String sql, Object... args);

    <T> Optional<T> doQueryNoCache(Class<T> clazz, String sql, Object... args);

    <T> List<T> doQueryAllNoCache(Class<T> clazz, String sql, Object... args);

    <T> Set<T> doQueryAllDistinctNoCache(Class<T> clazz, String sql, Object... args);

    Optional<Row> doQuery(String sql, Object... args);

    List<Row> doQueryAll(String sql, Object... args);

    Set<Row> doQueryAllDistinct(String sql, Object... args);

    Optional<Row> doQueryNoCache(String sql, Object... args);

    List<Row> doQueryAllNoCache(String sql, Object... args);

    Set<Row> doQueryAllDistinctNoCache(String sql, Object... args);

    <T> Optional<T> doQueryValue(Class<T> clazz, String sql, Object... args);

    <T> Optional<T> doQueryValueNoCache(Class<T> clazz, String sql, Object... args);

    <T> Optional<T> doQueryJoin(Class<T> clazz, String whereClause, Object... args);

    int doUpdate(String sql, Object... args);

    int doUpdate(Class<?> clazz, String sql, Object... args);

    void resetAllCaches();

    void resetCache(String cacheName);

    void resetCacheFor(DatabaseObject<?> dbobject);
    void resetCacheFor(IDBObjectService<?> dbobject);

    void resetCacheForClass(Class<?> dbclazz, boolean items, boolean lists);

    DatabaseStats getDatabaseStats();

    TableStats getTableStats(String name);

    String getSchema();

    <T> boolean createTable(Class<T> clazz);

    void createSchema(List<Class<?>> clz);

    void updateSchema(List<Class<?>> clz);

}
