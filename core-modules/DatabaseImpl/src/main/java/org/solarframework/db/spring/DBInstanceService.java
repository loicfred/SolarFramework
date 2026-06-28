package org.solarframework.db.spring;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import org.jspecify.annotations.NonNull;
import org.solarframework.db.api.DatabaseType;
import org.solarframework.db.api.IDBObjectService;
import org.solarframework.db.api.IDatabaseService;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

import static org.solarframework.core.util.ClassUtils.*;
import static org.solarframework.core.util.ClassUtils.getAllFieldsOfClassFamily;
import static org.solarframework.core.util.ClassUtils.setFieldValue;
import static org.solarframework.db.spring.DatabaseRegistry.SolarDBManager;
import static org.solarframework.db.spring.DatabaseUtils.*;
import static org.solarframework.json.JSONItem.GSON;

@SuppressWarnings("all")
public class DBInstanceService<T> implements IDBObjectService<T> {
    protected static final Map<Class<?>, String> TableNames = new HashMap<>();
    protected static final Map<Class<?>, Set<Field>> IdFields = new HashMap<>();
    protected static final Map<Class<?>, Set<Field>> CachedFields = new HashMap<>();

    protected transient DatabaseObject<T> dbObject;
    protected transient IDatabaseService dbService;
    protected transient Class<T> entityClass;
    protected transient String tableName;
    protected transient Set<Field> cachedFields = new HashSet<>();
    protected transient Set<Field> idFields = new HashSet<>();
    protected transient Set<String> cacheHashes = new HashSet<>();

    public Set<String> getCacheHashes() {
        return cacheHashes;
    }

    public DBInstanceService(IDatabaseService service, DatabaseObject<T> obj) {
        this.dbService = service;
        this.dbObject = obj;
        this.entityClass = (Class<T>) dbObject.getClass();

        this.tableName = TableNames.computeIfAbsent(entityClass, c -> obj.getTableName());
        this.cachedFields = CachedFields.computeIfAbsent(entityClass, c -> getSerializableFieldsOfClassFamily(entityClass).stream().filter(f -> dbService.getTableStats(tableName).getColumnNames().contains(f.getName().toLowerCase())).collect(Collectors.toSet()));
        this.idFields = IdFields.computeIfAbsent(entityClass, c -> CachedFields.get(entityClass).stream().filter(f -> f.isAnnotationPresent(Id.class)).collect(Collectors.toSet()));
    }

    private Set<Field> getIdAndUniqueFields() {
        Set<Field> fs = CachedFields.get(entityClass).stream().filter(f -> f.isAnnotationPresent(Id.class)).collect(Collectors.toSet());
        fs.addAll(CachedFields.get(entityClass).stream().filter(f -> f.isAnnotationPresent(Column.class) && f.getAnnotation(Column.class).unique()).collect(Collectors.toSet()));
        return fs;
    }
    private Set<Field> getUniqueFields() {
        return CachedFields.get(entityClass).stream().filter(f -> f.isAnnotationPresent(Column.class) && f.getAnnotation(Column.class).unique()).collect(Collectors.toSet());
    }

    public String getHashedIdentifier() {
        List<Object> ids = cachedFields.stream().filter(f -> idFields.contains(f.getName().toLowerCase())).map(f -> getFieldValue(f, dbObject)).toList();
        return entityClass.getName() + String.valueOf(ids.stream().map(Object::toString).collect(Collectors.joining("/")).hashCode());
    }

    public DatabaseType getDatabaseType() {
        return dbService.getDatabaseType();
    }

    @Override
    public String toJSON() {
        return GSON.toJson(dbObject);
    }

    @Override
    public int Write() {
        try {
            InsertArgumentManager insertArgMgr = makeInsertManager(false);
            String sql = getDatabaseType().Upsert(tableName, insertArgMgr.columns(), insertArgMgr.questionMarks(), null, getUniqueFields().stream().map(f -> f.getName()).collect(Collectors.joining(", ")));
            return dbService.doUpdate(sql, insertArgMgr.currentValuesList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to write object", e);
        } finally {
            SolarDBManager.resetCacheFor(dbObject);
        }
    }
    @Override
    public Optional<T> WriteThenReturn() {
        try {
            InsertArgumentManager insertArgMgr = makeInsertManager(false);
            String sql = getDatabaseType().Upsert(tableName, insertArgMgr.columns(), insertArgMgr.questionMarks(), null, getUniqueFields().stream().map(f -> f.getName()).collect(Collectors.joining(", "))) + " RETURNING *";
            return dbService.doQueryNoCache(entityClass, sql, insertArgMgr.currentValuesList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to insert object", e);
        } finally {
            SolarDBManager.resetCacheFor(dbObject);
        }
    }

    @Override
    public int Upsert() {
        return Upsert(null);
    }
    @Override
    public Optional<T> UpsertThenReturn() {
        return UpsertThenReturn(null);
    }
    @Override
    public int Upsert(List<String> conflictCols) {
        try {
            InsertArgumentManager insertArgMgr = makeInsertManager(true);
            String sql = getDatabaseType().Upsert(tableName, insertArgMgr.columns(), insertArgMgr.questionMarks(), insertArgMgr.duplicateKeyUpdateClause(), conflictCols == null || conflictCols.isEmpty() ? null : conflictCols.stream().collect(Collectors.joining(", ")));
            return dbService.doUpdate(sql, insertArgMgr.currentValuesList());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to upsert object", e);
        } finally {
            SolarDBManager.resetCacheFor(dbObject);
        }
    }
    @Override
    public Optional<T> UpsertThenReturn(List<String> conflictCols) {
        try {
            InsertArgumentManager insertArgMgr = makeInsertManager(true);
            String sql = getDatabaseType().Upsert(tableName, insertArgMgr.columns(), insertArgMgr.questionMarks(), insertArgMgr.duplicateKeyUpdateClause(), conflictCols == null || conflictCols.isEmpty() ? null : conflictCols.stream().collect(Collectors.joining(", "))) + " RETURNING *";
            return dbService.doQueryNoCache(entityClass, sql, insertArgMgr.currentValuesList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to upsert object", e);
        } finally {
            SolarDBManager.resetCacheFor(dbObject);
        }
    }


    @Override
    public int IncrementColumn(String column, int amount) {
        try {
            for (Field f : cachedFields) {
                if (f.getName().equalsIgnoreCase(column)) {
                    f.set(dbObject, (int) getFieldValue(f, dbObject) + amount);
                    break;
                }
            }

            String setClause = column + " = " + column + " + ?";
            List<Object> setValues = List.of(amount);

            String whereClause = idFields.stream().map(f -> f.getName() + " = ?").collect(Collectors.joining(" AND "));
            List<Object> whereValues = cleanParameterList(idFields.stream().map(ID -> getFieldValue(ID, dbObject)).collect(Collectors.toList()));

            List<Object> finalValues = new ArrayList<>();
            finalValues.addAll(setValues);
            finalValues.addAll(whereValues);

            String sql = "UPDATE " + tableName + " SET " + setClause + " WHERE " + whereClause;
            return dbService.doUpdate(sql, finalValues.toArray());
        } catch (Exception e) {
            throw new RuntimeException("No ID field found in " + tableName + ".");
        } finally {
            SolarDBManager.resetCacheFor(dbObject);
        }
    }
    @Override
    public int IncrementColumns(Map<String, Double> parameters) {
        try {
            String setClause = parameters.entrySet().stream().map(f -> f.getKey() + " = " + f.getKey() + " + ?").collect(Collectors.joining(", "));
            List<Object> setValues = parameters.entrySet().stream().map(f -> f.getValue()).collect(Collectors.toList());

            String whereClause = idFields.stream().map(f -> f.getName() + " = ?").collect(Collectors.joining(" AND "));
            List<Object> whereValues = cleanParameterList(idFields.stream().map(ID -> getFieldValue(ID, dbObject)).collect(Collectors.toList()));

            List<Object> finalValues = new ArrayList<>();
            finalValues.addAll(setValues);
            finalValues.addAll(whereValues);

            String sql = "UPDATE " + tableName + " SET " + setClause + " WHERE " + whereClause;
            return dbService.doUpdate(sql, finalValues.toArray());
        } catch (Exception e) {
            throw new RuntimeException("No ID field found in " + tableName + ".");
        } finally {
            SolarDBManager.resetCacheFor(dbObject);
        }
    }


    @Override
    public int Update() {
        try {
            String setClause = cachedFields.stream().map(f -> f.getName() + " = ?").collect(Collectors.joining(", "));
            List<Object> setValues = cleanParameterList(cachedFields.stream().map(f -> getFieldValue(f, dbObject)).collect(Collectors.toList()));

            String whereClause = idFields.stream().map(f -> f.getName() + " = ?").collect(Collectors.joining(" AND "));
            List<Object> whereValues = cleanParameterList(idFields.stream().map(ID -> getFieldValue(ID, dbObject)).collect(Collectors.toList()));

            List<Object> finalValues = new ArrayList<>();
            finalValues.addAll(setValues);
            finalValues.addAll(whereValues);

            String sql = "UPDATE " + tableName + " SET " + setClause + " WHERE " + whereClause;
            return dbService.doUpdate(sql, finalValues.toArray());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("No ID field found in " + tableName + ".");
        } finally {
            SolarDBManager.resetCacheFor(dbObject);
        }
    }
    @Override
    public int UpdateOnly(String... columns) {
        try {
            List<Field> fieldsList = cachedFields.stream().filter(f -> Arrays.stream(columns).anyMatch(c -> f.getName().equalsIgnoreCase(c.toLowerCase()))).toList();
            if (fieldsList.isEmpty()) return 0;

            String setClause = fieldsList.stream().map(f -> f.getName() + " = ?").collect(Collectors.joining(", "));
            List<Object> setValues = cleanParameterList(fieldsList.stream().map(f -> getFieldValue(f, dbObject)).collect(Collectors.toList()));

            String whereClause = idFields.stream().map(f -> f.getName() + " = ?").collect(Collectors.joining(" AND "));
            List<Object> whereValues = cleanParameterList(idFields.stream().map(ID -> getFieldValue(ID, dbObject)).collect(Collectors.toList()));

            List<Object> finalValues = new ArrayList<>();
            finalValues.addAll(setValues);
            finalValues.addAll(whereValues);

            String sql = "UPDATE " + tableName + " SET " + setClause + " WHERE " + whereClause;
            return dbService.doUpdate(sql, finalValues.toArray());
        } catch (Exception e) {
            throw new RuntimeException("No ID field found in " + tableName + ".");
        } finally {
            SolarDBManager.resetCacheFor(dbObject);
        }
    }


    @Override
    public int Delete() {
        try {
            List<Object> whereValues = cleanParameterList(idFields.stream().map(ID -> getFieldValue(ID, dbObject)).collect(Collectors.toList()));

            String sql = "DELETE FROM " + tableName + " WHERE " + idFields.stream().map(f -> f.getName() + " = ?").collect(Collectors.joining(" AND "));
            return dbService.doUpdate(sql, whereValues.toArray());
        } catch (Exception e) {
            throw new RuntimeException("No ID field found in " + tableName + ".");
        } finally {
            SolarDBManager.resetCacheFor(dbObject);
        }
    }


    @Override
    public <A> A refetchAttribute(String attributeName, Class<A> attributeType) {
        Field attribute = getAllFieldsOfClassFamily(entityClass).stream().filter(f -> f.getName().equalsIgnoreCase(attributeName)).findFirst().orElse(null);
        if (attribute == null) return null;
        String whereClause = idFields.stream().map(f -> f.getName() + " = ?").collect(Collectors.joining(" AND "));
        List<Object> whereValues = cleanParameterList(idFields.stream().map(ID -> getFieldValue(ID, dbObject)).collect(Collectors.toList()));
        A val = dbService.getSingleColumnOfTableWhere(attribute.getName(), attributeType, entityClass, whereClause, whereValues.toArray()).orElse(null);
        setFieldValue(attribute,dbObject, val);
        return val;
    }

    @Override
    public DatabaseObject<T> getDBObject() {
        return dbObject;
    }


    protected record InsertArgumentManager(String columns, String questionMarks, String duplicateKeyUpdateClause, Object[] currentValuesList) {}
    private InsertArgumentManager makeInsertManager(boolean update) {
        Set<Field> nonNullFields = cachedFields.stream().filter(f -> getFieldValue(f, dbObject) != null).collect(Collectors.toSet());

        String columnsSeparatedByComma = nonNullFields.stream().map(Field::getName).collect(Collectors.joining(", "));
        String questionMarksSeparatedByComma = nonNullFields.stream().map(p -> "?").collect(Collectors.joining(", "));

        List<Object> currentValuesList = cleanParameterList(nonNullFields.stream().map(f -> getFieldValue(f, dbObject)).collect(Collectors.toList()));

        if (!update) return new InsertArgumentManager(columnsSeparatedByComma, questionMarksSeparatedByComma, null, currentValuesList.toArray());
        String duplicateKeyUpdateClause = cachedFields.stream().map(f -> getDatabaseType().UpsertExcludedReference(f.getName())).collect(Collectors.joining(", "));
        return new InsertArgumentManager(columnsSeparatedByComma, questionMarksSeparatedByComma, duplicateKeyUpdateClause, currentValuesList.toArray());
    }

}
