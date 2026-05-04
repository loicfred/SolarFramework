package org.solarframework.db.spring;

import jakarta.persistence.Id;
import org.springframework.jdbc.core.RowMapper;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;

import static org.solarframework.core.util.ClassUtils.*;
import static org.solarframework.core.util.ClassUtils.getAllFieldsOfClassFamily;
import static org.solarframework.core.util.ClassUtils.setFieldValue;
import static org.solarframework.db.spring.DatabaseUtils.getTableName;
import static org.solarframework.db.spring.DatabaseUtils.mapResultSetToObject;
import static org.solarframework.db.spring.Provider.dbService;
import static org.solarframework.json.JSONItem.GSON;

@SuppressWarnings("all")
public class DBObjectService<T> implements IDBObjectService<T> {
    protected transient Class<T> entityClass;
    protected transient List<Field> cachedFields = new ArrayList<>();
    protected transient String tableName;
    protected transient List<String> idFields = new ArrayList<>();
    protected transient List<String> cacheHashes = new ArrayList<>();
    protected transient RowMapper<T> rowMapper;
    protected DatabaseObject<T> dbObject;


    public List<String> getCacheHashes() {
        return cacheHashes;
    }


    public DBObjectService(DatabaseObject<T> obj) {
        dbObject = obj;
        this.entityClass = (Class<T>) dbObject.getClass();
        this.tableName = getTableName(entityClass);

        this.cachedFields.addAll(getSerializableFieldsOfClassFamily(entityClass).stream().collect(Collectors.toList()));
        this.cachedFields.stream().filter(f -> f.isAnnotationPresent(Id.class)).forEach(f -> this.idFields.add(f.getName().toLowerCase()));

        this.cachedFields.removeIf(f -> !dbService.getTableStats(tableName).columnNames.contains(f.getName().toLowerCase()));
        this.rowMapper = (rs, rowNum) -> mapResultSetToObject(rs, entityClass);
    }

    public String getHashedIdentifier() {
        List<Object> ids = cachedFields.stream().filter(f -> idFields.contains(f.getName().toLowerCase())).map(f -> getFieldValue(f, this)).toList();
        return entityClass.getName() + String.valueOf(ids.stream().map(Object::toString).collect(Collectors.joining("/")).hashCode());
    }

    @Override
    public String toJSON() {
        return GSON.toJson(this);
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
            dbService.resetCacheFor(dbObject);
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
            dbService.resetCacheFor(this);
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
            dbService.resetCacheFor(this);
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
            dbService.resetCacheFor(this);
        }
    }


    @Override
    public int IncrementColumn(String column, int amount) {
        try {
            for (Field f : cachedFields) {
                if (f.getName().equalsIgnoreCase(column)) {
                    f.set(this, (int) getFieldValue(f, this) + amount);
                    break;
                }
            }

            String setClause = column + " = " + column + " + ?";
            List<Object> setValues = List.of(amount);

            String whereClause = idFields.stream().map(ID -> ID + " = ?").collect(Collectors.joining(" AND "));
            List<Object> whereValues = idFields.stream().map(ID -> getFieldValue(cachedFields.stream().filter(f -> f.getName().equalsIgnoreCase(ID)).findFirst().orElseThrow(), this)).collect(Collectors.toList());

            List<Object> finalValues = new ArrayList<>();
            finalValues.addAll(setValues);
            finalValues.addAll(whereValues);

            String sql = "UPDATE " + tableName + " SET " + setClause + " WHERE " + whereClause;
            return dbService.doUpdate(sql, finalValues.toArray());
        } catch (Exception e) {
            throw new RuntimeException("No ID field found in " + tableName + ".");
        } finally {
            dbService.resetCacheFor(this);
        }
    }
    @Override
    public int IncrementColumns(Map<String, Object> parameters) {
        try {
            String setClause = parameters.entrySet().stream().map(f -> f.getKey() + " = " + f.getKey() + " + ?").collect(Collectors.joining(", "));
            List<Object> setValues = parameters.entrySet().stream().map(f -> f.getValue()).collect(Collectors.toList());

            String whereClause = idFields.stream().map(ID -> ID + " = ?").collect(Collectors.joining(" AND "));
            List<Object> whereValues = idFields.stream().map(ID -> getFieldValue(cachedFields.stream().filter(f -> f.getName().equalsIgnoreCase(ID)).findFirst().orElseThrow(), this)).collect(Collectors.toList());

            List<Object> finalValues = new ArrayList<>();
            finalValues.addAll(setValues);
            finalValues.addAll(whereValues);

            String sql = "UPDATE " + tableName + " SET " + setClause + " WHERE " + whereClause;
            return dbService.doUpdate(sql, finalValues.toArray());
        } catch (Exception e) {
            throw new RuntimeException("No ID field found in " + tableName + ".");
        } finally {
            dbService.resetCacheFor(this);
        }
    }


    @Override
    public int Update() {
        try {
            String setClause = cachedFields.stream().map(f -> f.getName() + " = ?").collect(Collectors.joining(", "));
            List<Object> setValues = cachedFields.stream().map(f -> getFieldValue(f, this)).collect(Collectors.toList());

            String whereClause = idFields.stream().map(ID -> ID + " = ?").collect(Collectors.joining(" AND "));
            List<Object> whereValues = idFields.stream().map(ID -> getFieldValue(cachedFields.stream().filter(f -> f.getName().equalsIgnoreCase(ID)).findFirst().orElseThrow(), this)).collect(Collectors.toList());

            List<Object> finalValues = new ArrayList<>();
            finalValues.addAll(setValues);
            finalValues.addAll(whereValues);

            String sql = "UPDATE " + tableName + " SET " + setClause + " WHERE " + whereClause;
            return dbService.doUpdate(sql, finalValues.toArray());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("No ID field found in " + tableName + ".");
        } finally {
            dbService.resetCacheFor(this);
        }
    }
    @Override
    public int UpdateOnly(String... columns) {
        try {
            List<Field> fieldsList = Arrays.stream(columns).noneMatch(c -> cachedFields.stream().anyMatch(f -> Objects.equals(c, f.getName()))) ?
                    getAllFieldsOfClassFamily(entityClass).stream().filter(ff -> !Modifier.isTransient(ff.getModifiers()) && !Modifier.isStatic(ff.getModifiers())).toList() : cachedFields;

            String setClause = fieldsList.stream().filter(f -> Arrays.stream(columns).anyMatch(c -> Objects.equals(c, f.getName()))).map(f -> f.getName() + " = ?").collect(Collectors.joining(", "));
            List<Object> setValues = fieldsList.stream().filter(f -> Arrays.stream(columns).anyMatch(c -> Objects.equals(c, f.getName()))).map(f -> getFieldValue(f, this)).collect(Collectors.toList());

            String whereClause = idFields.stream().map(ID -> ID + " = ?").collect(Collectors.joining(" AND "));
            List<Object> whereValues = idFields.stream().map(ID -> getFieldValue(cachedFields.stream().filter(f -> f.getName().equalsIgnoreCase(ID)).findFirst().orElseThrow(), this)).collect(Collectors.toList());

            List<Object> finalValues = new ArrayList<>();
            finalValues.addAll(setValues);
            finalValues.addAll(whereValues);

            String sql = "UPDATE " + tableName + " SET " + setClause + " WHERE " + whereClause;
            return dbService.doUpdate(sql, finalValues.toArray());
        } catch (Exception e) {
            throw new RuntimeException("No ID field found in " + tableName + ".");
        } finally {
            dbService.resetCacheFor(this);
        }
    }


    @Override
    public int Delete() {
        try {
            List<Object> whereValues = idFields.stream().map(ID -> getFieldValue(cachedFields.stream().filter(f -> f.getName().equalsIgnoreCase(ID)).findFirst().orElseThrow(), this)).collect(Collectors.toList());

            String sql = "DELETE FROM " + tableName + " WHERE " + idFields.stream().map(ID -> ID + " = ?").collect(Collectors.joining(" AND "));
            return dbService.doUpdate(sql, whereValues.toArray());
        } catch (Exception e) {
            throw new RuntimeException("No ID field found in " + tableName + ".");
        } finally {
            dbService.resetCacheFor(this);
        }
    }


    @Override
    public <A> A refetchAttribute(String attributeName, Class<A> attributeType) {
        Field attribute = getAllFieldsOfClassFamily(this.getClass()).stream().filter(f -> f.getName().equalsIgnoreCase(attributeName)).findFirst().orElse(null);
        if (attribute == null) return null;
        String whereClause = idFields.stream().map(ID -> ID + " = ?").collect(Collectors.joining(" AND "));
        List<Object> whereValues = idFields.stream().map(ID -> getFieldValue(cachedFields.stream().filter(f -> f.getName().equalsIgnoreCase(ID)).findFirst().orElseThrow(), this)).collect(Collectors.toList());
        A val = dbService.getSingleColumnOfTableWhere(attribute.getName(), attributeType, entityClass, whereClause, whereValues.toArray()).orElse(null);
        setFieldValue(attribute,this, val);
        return val;
    }

    @Override
    public DatabaseObject<T> getDBObject() {
        return dbObject;
    }


    private record ParameterManager(String columnsSeparatedByComma, String questionMarksSeparatedByComma, Object[] currentValuesList, String duplicateKeyUpdateClause) {}
    private ParameterManager getResult(boolean update) {
        Set<Field> nonNullFields = cachedFields.stream().filter(f -> getFieldValue(f, this) != null).collect(Collectors.toSet());

        String columnsSeparatedByComma = nonNullFields.stream().map(Field::getName).collect(Collectors.joining(", "));
        String questionMarksSeparatedByComma = nonNullFields.stream().map(p -> "?").collect(Collectors.joining(", "));

        List<Object> currentValuesList = nonNullFields.stream().map(f -> getFieldValue(f, this)).toList();

        if (!update) return new ParameterManager(columnsSeparatedByComma, questionMarksSeparatedByComma, currentValuesList.toArray(), null);
        String duplicateKeyUpdateClause = cachedFields.stream().map(f -> f.getName() + " = VALUES(" + f.getName() + ")").collect(Collectors.joining(", "));
        return new ParameterManager(columnsSeparatedByComma, questionMarksSeparatedByComma, currentValuesList.toArray(), duplicateKeyUpdateClause);
    }
}
