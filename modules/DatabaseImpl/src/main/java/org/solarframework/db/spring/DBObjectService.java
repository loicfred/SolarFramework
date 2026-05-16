package org.solarframework.db.spring;

import jakarta.persistence.Id;
import org.solarframework.db.api.IDBObjectService;
import org.solarframework.db.api.IDatabaseService;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;

import static org.solarframework.core.util.ClassUtils.*;
import static org.solarframework.core.util.ClassUtils.getAllFieldsOfClassFamily;
import static org.solarframework.core.util.ClassUtils.setFieldValue;
import static org.solarframework.db.spring.DatabaseRegistry.SolarDBManager;
import static org.solarframework.db.spring.DatabaseUtils.*;
import static org.solarframework.json.JSONItem.GSON;

@SuppressWarnings("all")
public class DBObjectService<T> implements IDBObjectService<T> {
    protected static final Map<Class<?>, String> TableNames = new HashMap<>();
    protected static final Map<Class<?>, List<Field>> IdFields = new HashMap<>();
    protected static final Map<Class<?>, List<Field>> CachedFields = new HashMap<>();

    protected transient DatabaseObject<T> dbObject;
    protected transient IDatabaseService dbService;
    protected transient Class<T> entityClass;
    protected transient String tableName;
    protected transient List<Field> cachedFields = new ArrayList<>();
    protected transient List<Field> idFields = new ArrayList<>();
    protected transient List<String> cacheHashes = new ArrayList<>();

    public List<String> getCacheHashes() {
        return cacheHashes;
    }

    public DBObjectService(IDatabaseService service, DatabaseObject<T> obj) {
        this.dbService = service;
        this.dbObject = obj;
        this.entityClass = (Class<T>) dbObject.getClass();

        this.tableName = TableNames.computeIfAbsent(entityClass, c -> getTableName(entityClass));
        this.cachedFields = CachedFields.computeIfAbsent(entityClass, c -> getSerializableFieldsOfClassFamily(entityClass).stream().filter(f -> dbService.getTableStats(tableName).getColumnNames().contains(f.getName().toLowerCase())).collect(Collectors.toList()));
        this.idFields = IdFields.computeIfAbsent(entityClass, c -> CachedFields.get(entityClass).stream().filter(f -> f.isAnnotationPresent(Id.class)).collect(Collectors.toList()));
    }


    public String getHashedIdentifier() {
        List<Object> ids = cachedFields.stream().filter(f -> idFields.contains(f.getName().toLowerCase())).map(f -> getFieldValue(f, dbObject)).toList();
        return entityClass.getName() + String.valueOf(ids.stream().map(Object::toString).collect(Collectors.joining("/")).hashCode());
    }

    @Override
    public String toJSON() {
        return GSON.toJson(dbObject);
    }

    @Override
    public int Write() {
        try {
            ParameterManager parameterManager = getResult(false);
            String sql = "INSERT INTO " + tableName + " (" + parameterManager.columnsSeparatedByComma() + ") VALUES (" + parameterManager.questionMarksSeparatedByComma() + ")";
            return dbService.doUpdate(sql, parameterManager.currentValuesList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to write object", e);
        } finally {
            SolarDBManager.resetCacheFor(dbObject);
        }
    }
    @Override
    public Optional<T> WriteThenReturn() {
        try {
            ParameterManager parameterManager = getResult(false);
            String sql = "INSERT INTO " + tableName + " (" + parameterManager.columnsSeparatedByComma() + ") VALUES (" + parameterManager.questionMarksSeparatedByComma() + ") RETURNING *";
            return dbService.doQueryNoCache(entityClass, sql, parameterManager.currentValuesList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to insert object", e);
        } finally {
            SolarDBManager.resetCacheFor(dbObject);
        }
    }

    @Override
    public int Upsert() {
        try {
            ParameterManager parameterManager = getResult(true);
            String sql = "INSERT INTO " + tableName + " (" + parameterManager.columnsSeparatedByComma() + ") VALUES (" + parameterManager.questionMarksSeparatedByComma() + ") ON DUPLICATE KEY UPDATE " + parameterManager.duplicateKeyUpdateClause();
            return dbService.doUpdate(sql, parameterManager.currentValuesList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to upsert object", e);
        } finally {
            SolarDBManager.resetCacheFor(dbObject);
        }
    }
    @Override
    public Optional<T> UpsertThenReturn() {
        try {
            ParameterManager parameterManager = getResult(true);
            String sql = "INSERT INTO " + tableName + " (" + parameterManager.columnsSeparatedByComma() + ") VALUES (" + parameterManager.questionMarksSeparatedByComma() + ") ON DUPLICATE KEY UPDATE " + parameterManager.duplicateKeyUpdateClause() + " RETURNING *";
            return dbService.doQueryNoCache(entityClass, sql, parameterManager.currentValuesList());
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
            List<Field> fieldsList = Arrays.stream(columns).noneMatch(c -> cachedFields.stream().anyMatch(f -> Objects.equals(c, f.getName()))) ?
                    getAllFieldsOfClassFamily(entityClass).stream().filter(ff -> !Modifier.isTransient(ff.getModifiers()) && !Modifier.isStatic(ff.getModifiers())).toList() : cachedFields;

            String setClause = fieldsList.stream().filter(f -> Arrays.stream(columns).anyMatch(c -> Objects.equals(c, f.getName()))).map(f -> f.getName() + " = ?").collect(Collectors.joining(", "));
            List<Object> setValues = cleanParameterList(fieldsList.stream().filter(f -> Arrays.stream(columns).anyMatch(c -> Objects.equals(c, f.getName()))).map(f -> getFieldValue(f, dbObject)).collect(Collectors.toList()));

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


    private record ParameterManager(String columnsSeparatedByComma, String questionMarksSeparatedByComma, Object[] currentValuesList, String duplicateKeyUpdateClause) {}
    private ParameterManager getResult(boolean update) {
        Set<Field> nonNullFields = cachedFields.stream().filter(f -> getFieldValue(f, dbObject) != null).collect(Collectors.toSet());

        String columnsSeparatedByComma = nonNullFields.stream().map(Field::getName).collect(Collectors.joining(", "));
        String questionMarksSeparatedByComma = nonNullFields.stream().map(p -> "?").collect(Collectors.joining(", "));

        List<Object> currentValuesList = cleanParameterList(nonNullFields.stream().map(f -> getFieldValue(f, dbObject)).collect(Collectors.toList()));

        if (!update) return new ParameterManager(columnsSeparatedByComma, questionMarksSeparatedByComma, currentValuesList.toArray(), null);
        String duplicateKeyUpdateClause = cachedFields.stream().map(f -> f.getName() + " = VALUES(" + f.getName() + ")").collect(Collectors.joining(", "));
        return new ParameterManager(columnsSeparatedByComma, questionMarksSeparatedByComma, currentValuesList.toArray(), duplicateKeyUpdateClause);
    }

}
