package org.solarframework.db.api;

import org.solarframework.db.api.dto.DatabaseStats;
import org.solarframework.db.api.dto.Row;
import org.solarframework.db.api.dto.TableStats;
import org.solarframework.db.spring.DatabaseObject;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface IDatabaseService {

    DatabaseType getDatabaseType();
    <T> IDBObjectService<T> makeObjectManager(DatabaseObject<T> dbobject);


    <O> Optional<O> getSingleColumnOfTableById(String column, Class<O> item, Class<?> table, Object... id);
    <O> Optional<O> getSingleColumnOfTableWhere(String column, Class<O> item, Class<?> table, String where, Object... args);
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


    DatabaseStats getDatabaseStats();

    TableStats getTableStats(String name);

    String getSchema();

    void createSchema(Collection<Class<?>> clz);

    void updateSchema(Collection<Class<?>> clz);

    interface ENTITY<T> {

        IDBObjectService<T> makeObjectManager(DatabaseObject<T> dbobject);

        Optional<T> getByIdWithJoins(Object... id);
        Optional<T> getById(Object... id);
        Optional<T> getWhereWithJoins(String whereClause, Object... args);
        Optional<T> getWhere(String whereClause, Object... args);
        List<T> getAll();
        List<T> getAllWhere(String whereClause, Object... args);
        Set<T> getAllWhereDistinct(String whereClause, Object... args);
        int Count();
        int Count(String whereClause, Object... args);
        T getRandom();
        T getRandom(String whereClause, Object... args);


        Optional<T> doQuery(String sql, Object... args);

        List<T> doQueryAll(String sql, Object... args);

        Set<T> doQueryAllDistinct(String sql, Object... args);

        Optional<T> doQueryNoCache(String sql, Object... args);

        List<T> doQueryAllNoCache(String sql, Object... args);

        Set<T> doQueryAllDistinctNoCache(String sql, Object... args);


        Optional<T> doQueryValue(String sql, Object... args);

        Optional<T> doQueryValueNoCache(String sql, Object... args);

        Optional<T> doQueryJoin(String whereClause, Object... args);

        int doUpdate(String sql, Object... args);


        void createSchema();
        void updateSchema();

    }

}
