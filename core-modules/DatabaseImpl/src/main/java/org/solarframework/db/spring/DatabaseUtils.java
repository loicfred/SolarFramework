package org.solarframework.db.spring;

import com.google.gson.*;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import jakarta.persistence.*;
import org.solarframework.db.api.IDatabaseService;
import org.solarframework.db.api.dto.Row;
import org.springframework.stereotype.Component;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.solarframework.db.spring.DatabaseObject.getTableName;
import static org.solarframework.json.JSONItem.GSON;
import static org.solarframework.core.util.ClassUtils.*;

@Component
public class DatabaseUtils {

    protected static <T> T mapResultSetToObject(ResultSet rs, Class<T> clazz) {
        List<Field> fs = getSerializableFieldsOfClassFamily(clazz);
        Map<String, Object> map = new HashMap<>();
        for (Field f : fs) {
            String columnName = f.getAnnotation(Column.class) != null && !f.getAnnotation(Column.class).name().isBlank() ? f.getAnnotation(Column.class).name() : f.getName();
            Object value;
            try {value = rs.getObject(columnName);
            } catch (Exception ignored) {value = null;}
            map.put(f.getName(), value);
        }
        return mapResultSetToObject(clazz, map, fs);
    }
    protected static <T> T mapResultSetToObject(Row rs, Class<T> clazz) {
        List<Field> fs = getSerializableFieldsOfClassFamily(clazz);
        Map<String, Object> map = new HashMap<>();
        for (Field f : fs) {
            String columnName = f.getAnnotation(Column.class) != null && !f.getAnnotation(Column.class).name().isBlank() ? f.getAnnotation(Column.class).name() : f.getName();
            map.put(f.getName(), rs.get(columnName));
        }
        return mapResultSetToObject(clazz, map, fs);
    }
    private static <T> T mapResultSetToObject(Class<T> clazz, Map<String, Object> rs, List<Field> fs) {
        try {
            Constructor<T> ctor = clazz.getDeclaredConstructor();
            ctor.setAccessible(true);
            T item = ctor.newInstance();
            for (Field f : fs) {
                Object value = rs.get(f.getName());
                setValueWhileConvertingDBToObject(item, value, f);
            }
            if (item instanceof DatabaseObject<?> i) i.getService().getCacheHashes().add(i.getHashedIdentifier());
            return item;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to map ResultSet to " + clazz.getSimpleName(), e);
        }
    }


    protected static String cleanJson(String json, List<Field> fields) {
        if (json == null) return null;
        JsonElement root = JsonParser.parseString(json);
        Map<String, Field> fieldMap = new HashMap<>();
        for (Field f : fields) fieldMap.put(f.getName().toLowerCase(), f);
        return GSON.toJson(cleanElement(root, fieldMap));
    }
    private static JsonElement cleanElement(JsonElement element, Map<String, Field> fieldMap) {
        if (element.isJsonObject()) {
            JsonObject input = element.getAsJsonObject();
            JsonObject output = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : input.entrySet()) {
                String key = entry.getKey();
                JsonElement value = entry.getValue();
                Field field = fieldMap.get(key.toLowerCase());
                String newKey = key;
                if (field != null) newKey = field.getName();
                JsonElement cleanedValue;
                if (field != null && isBooleanField(field)) {
                    cleanedValue = normalizeBoolean(value);
                } else {
                    cleanedValue = cleanElement(value, fieldMap);
                }
                output.add(newKey, cleanedValue);
            }
            return output;
        }
        if (element.isJsonArray()) {
            JsonArray arr = new JsonArray();
            for (JsonElement item : element.getAsJsonArray()) arr.add(cleanElement(item, fieldMap));
            return arr;
        }
        return element;
    }
    private static boolean isBooleanField(Field field) {
        Class<?> type = field.getType();
        return type == boolean.class || type == Boolean.class;
    }
    private static JsonElement normalizeBoolean(JsonElement value) {
        if (value.isJsonPrimitive()) {
            JsonPrimitive p = value.getAsJsonPrimitive();
            if (p.isNumber()) return new JsonPrimitive(p.getAsInt() == 1);
            if (p.isString()) {
                String s = p.getAsString();
                if ("1".equals(s)) return new JsonPrimitive(true);
                if ("0".equals(s)) return new JsonPrimitive(false);
            }
            if (p.isBoolean()) return p;
        }
        return value;
    }
    private static List<Class<?>> getClassesWhoJoinsWithThis(Class<?> clazz) {
        List<Class<?>> classes = new ArrayList<>();
        for (Field f : clazz.getDeclaredFields()) {
            if (f.isAnnotationPresent(JoinColumn.class)) {
                if (f.getType().equals(List.class) || f.getType().equals(Set.class)) {
                    classes.add((Class<?>) ((ParameterizedType) f.getGenericType()).getActualTypeArguments()[0]);
                } else {
                    classes.add(f.getType());
                }
            }
        }
        return classes;
    }


    protected static List<Object> cleanParameterList(List<Object> currentValuesList) {
        for (Object c : currentValuesList.stream().filter(c -> c.getClass().isEnum()).toList()) currentValuesList.set(currentValuesList.indexOf(c), ((Enum<?>) c).name());
        return currentValuesList;
    }

    protected static <T> Optional<T> Join_BasicApproach(IDatabaseService dbService, Class<T> clazz, String whereClause, Object[] args) {
        String tableName = getTableName(clazz);

        List<Field> mainObjectFields = getSerializableFieldsOfClassFamily(clazz);
        List<Field> mainFieldsUsedForJoin = new ArrayList<>();

        String mainObjectSelect = mainObjectFields.stream().map(f -> "MAIN." + f.getName().toLowerCase() + " AS 'MAIN." + f.getName().toLowerCase() + "'").collect(Collectors.joining(", "));
        List<String> selectOfOtherItems = new ArrayList<>();
        List<String> joinOfOtherItems = new ArrayList<>();
        getAllFieldsOfClassFamily(clazz).forEach(mainField -> {
            if (mainField.isAnnotationPresent(JoinColumn.class)) {
                JoinColumn jc = mainField.getAnnotation(JoinColumn.class);
                String mainColumnName = jc.name().toLowerCase();
                String joinColumnName = jc.referencedColumnName().isBlank() ? "ID" : jc.referencedColumnName().toLowerCase();

                if (mainField.isAnnotationPresent(OneToOne.class) || mainField.isAnnotationPresent(ManyToOne.class)) {
                    Class<?> itemClass = mainField.getType();
                    List<Field> itemFields = getSerializableFieldsOfClassFamily(itemClass);
                    selectOfOtherItems.add(itemFields.stream().map(ff ->  mainField.getName() + "." + ff.getName().toLowerCase() + " AS '" + mainField.getName() + "." + ff.getName().toLowerCase() + "'").collect(Collectors.joining(", ")));
                    joinOfOtherItems.add("LEFT JOIN " + getTableName(itemClass) + " " + mainField.getName() + " ON " + mainField.getName() + "." + joinColumnName + " = MAIN." + mainColumnName);
                } else if (mainField.isAnnotationPresent(OneToMany.class)) {
                    Class<?> itemClass = getGenericOf(mainField, 0);
                    List<Field> itemFields = getSerializableFieldsOfClassFamily(itemClass);
                    selectOfOtherItems.add(itemFields.stream().map(ff ->  mainField.getName() + "." + ff.getName().toLowerCase() + " AS '" + mainField.getName() + "." + ff.getName().toLowerCase() + "'").collect(Collectors.joining(", ")));
                    joinOfOtherItems.add("LEFT JOIN " + getTableName(itemClass) + " " + mainField.getName() + " ON " + mainField.getName() + "." + mainColumnName + " = MAIN." + joinColumnName);
                }
                mainFieldsUsedForJoin.add(mainField);
            } else if (mainField.isAnnotationPresent(JoinTable.class) || mainField.isAnnotationPresent(ManyToMany.class)) {
                JoinTable joinTable = mainField.getAnnotation(JoinTable.class);
                String junctionTable = joinTable.name().toLowerCase();

                Class<?> itemClass = getGenericOf(mainField, 0);
                List<Field> itemFields = getSerializableFieldsOfClassFamily(itemClass);
                selectOfOtherItems.add(itemFields.stream().map(ff ->  mainField.getName() + "." + ff.getName().toLowerCase() + " AS '" + mainField.getName() + "." + ff.getName().toLowerCase() + "'").collect(Collectors.joining(", ")));

                joinOfOtherItems.add("LEFT JOIN " + junctionTable + " " + mainField.getName() + "JUNCTION ON "
                        + Arrays.stream(joinTable.joinColumns()).map(mainLink -> mainField.getName() + "JUNCTION." + mainLink.name().toLowerCase() + " = MAIN." + (mainLink.referencedColumnName().isBlank() ? "ID" : mainLink.referencedColumnName().toLowerCase())).collect(Collectors.joining(" AND ")));
                joinOfOtherItems.add("LEFT JOIN " + getTableName(itemClass) + " " + mainField.getName() + " ON "
                        + Arrays.stream(joinTable.inverseJoinColumns()).map(itemLink -> mainField.getName() + "JUNCTION." + itemLink.name().toLowerCase() + " = " + mainField.getName() + "." + (itemLink.referencedColumnName().isBlank() ? "ID" : itemLink.referencedColumnName().toLowerCase())).collect(Collectors.joining(" AND ")));
                mainFieldsUsedForJoin.add(mainField);
            }
        });

        String sql = "SELECT " + mainObjectSelect + (selectOfOtherItems.isEmpty() ? "" : ", " + selectOfOtherItems.stream().map(String::toString).collect(Collectors.joining(", ")) ) +
                " FROM " + tableName + " MAIN " + joinOfOtherItems.stream().map(String::toString).collect(Collectors.joining(" ")) + " WHERE " + whereClause;

        try {
            List<Row> resultAll = dbService.doQueryAll(sql, args);
            if (resultAll.isEmpty()) return Optional.empty();
            Row singleRow = resultAll.getFirst();
            Constructor<T> ctor = clazz.getDeclaredConstructor();
            ctor.setAccessible(true);
            T mainItem = ctor.newInstance();
            for (Field mainObjectField : mainObjectFields) {
                setValueWhileConvertingDBToObject(mainItem, singleRow.get("MAIN." + mainObjectField.getName().toLowerCase()).orElse(null), mainObjectField);
            }
            if (mainItem instanceof DatabaseObject<?> mI) mI.getService().getCacheHashes().add(mI.getHashedIdentifier());
            for (Field mainJoinField : mainFieldsUsedForJoin) {
                Object JoinItem;
                if (isClassRelated(mainJoinField.getType(), Collection.class)) {
                    Collection<Object> list = new HashSet<>();
                    Constructor<?> Jctor = getGenericOf(mainJoinField, 0).getDeclaredConstructor();
                    Jctor.setAccessible(true);
                    for (Row row : resultAll) {
                        Object o = Jctor.newInstance();
                        for (Field joinObjectField : getSerializableFieldsOfClassFamily(o.getClass())) {
                            setValueWhileConvertingDBToObject(o, row.get(mainJoinField.getName() + "." + joinObjectField.getName().toLowerCase()).orElse(null), joinObjectField);
                        }
                        list.add(o);
                    }
                    JoinItem = mainJoinField.getType() == List.class ? new ArrayList<>(list) : mainJoinField.getType() == Set.class ? new HashSet<>(list) : list;
                } else {
                    Constructor<?> Jctor = mainJoinField.getType().getDeclaredConstructor();
                    Jctor.setAccessible(true);
                    JoinItem = Jctor.newInstance();
                    for (Field joinObjectField : getSerializableFieldsOfClassFamily(JoinItem.getClass())) {
                        setValueWhileConvertingDBToObject(JoinItem, singleRow.get(mainJoinField.getName() + "." + joinObjectField.getName().toLowerCase()).orElse(null), joinObjectField);
                    }
                }
                if (JoinItem instanceof Collection<?> joinItemList) {
                    for (Object joinListItem : joinItemList) {
                        assignMainToJoinItem(mainJoinField, joinListItem, mainItem);
                    }
                } else {
                    assignMainToJoinItem(mainJoinField, JoinItem, mainItem);
                }
                if (JoinItem instanceof DatabaseObject<?> dbItem) {
                    ((DatabaseService) dbService).getDbCacheManager().getCache("DBObject").put(dbItem.getHashedIdentifier(), dbItem);
                }
                mainJoinField.set(mainItem, JoinItem);
            }
            return Optional.of(mainItem);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }
    protected static <T> Optional<T> Join_JSONApproach(IDatabaseService dbService, Class<T> clazz, String whereClause, Object[] args) {
        String tableName = getTableName(clazz);

        List<Field> mainObjectFields = getSerializableFieldsOfClassFamily(clazz);
        String jsonOfMainObject = "JSON_OBJECT(" + mainObjectFields.stream().map(f -> "'" + f.getName().toLowerCase() + "', MAIN." + f.getName().toLowerCase()).collect(Collectors.joining(",")) + ") AS " + clazz.getSimpleName();

        List<Field> mainFieldsUsedForJoin = new ArrayList<>();
        List<String> jsonOfOtherItems = new ArrayList<>();
        getAllFieldsOfClassFamily(clazz).forEach(mainField -> {
            if (mainField.isAnnotationPresent(JoinColumn.class)) {
                JoinColumn jc = mainField.getAnnotation(JoinColumn.class);
                String mainColumnName = jc.name().toLowerCase();
                String joinColumnName = jc.referencedColumnName().isBlank() ? "ID" : jc.referencedColumnName().toLowerCase();

                if (mainField.isAnnotationPresent(ManyToOne.class) || mainField.isAnnotationPresent(OneToOne.class)) {
                    Class<?> itemClass = mainField.getType();
                    List<Field> itemFields = getSerializableFieldsOfClassFamily(itemClass);
                    jsonOfOtherItems.add("(SELECT COALESCE(JSON_OBJECT(" + itemFields.stream().map(ff -> "'" + ff.getName().toLowerCase() + "', " + mainField.getName() + "." + ff.getName().toLowerCase()).collect(Collectors.joining(",")) + "), JSON_OBJECT()) FROM " + getTableName(itemClass) + " " + mainField.getName() + " WHERE " + mainField.getName() + "." + joinColumnName + " = MAIN." + mainColumnName + " LIMIT 1) AS " + mainField.getName());
                } else if (mainField.isAnnotationPresent(OneToMany.class)) {
                    Class<?> itemClass = getGenericOf(mainField, 0);
                    List<Field> itemFields = getSerializableFieldsOfClassFamily(itemClass);
                    jsonOfOtherItems.add("(SELECT COALESCE(JSON_ARRAYAGG(JSON_OBJECT(" + itemFields.stream().map(ff -> "'" + ff.getName().toLowerCase() + "', " + mainField.getName() + "." + ff.getName().toLowerCase()).collect(Collectors.joining(",")) + ")), JSON_ARRAY()) FROM " + getTableName(itemClass) + " " + mainField.getName() + " WHERE " + mainField.getName() + "." + mainColumnName + " = MAIN." + joinColumnName + ") AS " + mainField.getName());
                }
                mainFieldsUsedForJoin.add(mainField);
            } else if (mainField.isAnnotationPresent(JoinTable.class) || mainField.isAnnotationPresent(ManyToMany.class)) {
                JoinTable joinTable = mainField.getAnnotation(JoinTable.class);
                String junctionTable = joinTable.name().toLowerCase();
                String juncJoin = "";
                for (JoinColumn mainLink : joinTable.joinColumns()) {
                    juncJoin += " AND " + mainField.getName() + "JUNCTION." + mainLink.name().toLowerCase() + " = MAIN." + (mainLink.referencedColumnName().isBlank() ? "ID" : mainLink.referencedColumnName().toLowerCase());
                }
                for (JoinColumn itemLink : joinTable.inverseJoinColumns()) {
                    juncJoin += " AND " + mainField.getName() + "JUNCTION." + itemLink.name().toLowerCase() + " = " + mainField.getName() + "." + (itemLink.referencedColumnName().isBlank() ? "ID" : itemLink.referencedColumnName().toLowerCase());
                }
                Class<?> itemClass = getGenericOf(mainField, 0);
                List<Field> itemFields = getSerializableFieldsOfClassFamily(itemClass);
                jsonOfOtherItems.add("(SELECT COALESCE(JSON_ARRAYAGG(JSON_OBJECT(" + itemFields.stream().map(ff -> "'" + ff.getName().toLowerCase() + "', " + mainField.getName() + "." + ff.getName().toLowerCase()).collect(Collectors.joining(",")) + ")), JSON_ARRAY()) FROM " + junctionTable + " " + mainField.getName() + "JUNCTION JOIN " + getTableName(itemClass) + " " + mainField.getName() + " ON " + juncJoin.replaceFirst(" AND ", "") + ") AS " + mainField.getName());
                mainFieldsUsedForJoin.add(mainField);
            }
        });

        String sql = "SELECT " + jsonOfMainObject + (jsonOfOtherItems.isEmpty() ? "" : ", " + jsonOfOtherItems.stream().map(String::toString).collect(Collectors.joining(",")) ) + " FROM " + tableName + " MAIN WHERE " + whereClause + " LIMIT 1;";

        Row result = dbService.doQueryNoCache(sql, args).orElse(null);
        if (result == null) return Optional.empty();
        T mainItem = GSON.fromJson(cleanJson(result.getAsString(clazz.getSimpleName()), mainObjectFields), clazz);
        if (mainItem == null) return Optional.empty();
        if (mainItem instanceof DatabaseObject<?> mI) mI.getService().getCacheHashes().add(mI.getHashedIdentifier());
        for (Field mainJoinField : mainFieldsUsedForJoin) {
            try {
                Object JoinItem;
                if (isClassRelated(mainJoinField.getType(), Collection.class)) {
                    String json = cleanJson(result.getAsString(mainJoinField.getName()), getSerializableFieldsOfClassFamily(getGenericOf(mainJoinField, 0)));
                    JoinItem = json != null ? GSON.fromJson(json, mainJoinField.getGenericType()) : null;
                } else {
                    String json = cleanJson(result.getAsString(mainJoinField.getName()), getSerializableFieldsOfClassFamily(mainJoinField.getType()));
                    JoinItem = json != null ? GSON.fromJson(json, mainJoinField.getType()) : null;
                }
                if (JoinItem == null) continue;
                if (JoinItem instanceof Collection<?> joinItemList) {
                    for (Object joinListItem : joinItemList) {
                        assignMainToJoinItem(mainJoinField, joinListItem, mainItem);
                    }
                } else {
                    assignMainToJoinItem(mainJoinField, JoinItem, mainItem);
                }
                if (JoinItem instanceof DatabaseObject<?> dbItem) {
                    ((DatabaseService) dbService).getDbCacheManager().getCache("DBObject").put(dbItem.getHashedIdentifier(), dbItem);
                }
                mainJoinField.set(mainItem, JoinItem);
            } catch (IllegalAccessException ignored) {}
        }
        return Optional.of(mainItem);
    }

    protected static <T> void assignMainToJoinItem(Field mainJoinField, Object joinListItem, T mainItem) throws IllegalAccessException {
        if (joinListItem instanceof DatabaseObject<?> joinItem) {
            joinItem.getService().getCacheHashes().add(joinItem.getHashedIdentifier());
            if (mainItem instanceof DatabaseObject<?> mI) {
                joinItem.getService().getCacheHashes().add(mI.getHashedIdentifier());
                mI.getService().getCacheHashes().add(joinItem.getHashedIdentifier());
            }
            for (Field joinItemField : getAllFieldsOfClassFamily(joinItem.getClass())) {
                if (mainJoinField.isAnnotationPresent(JoinColumn.class) && joinItemField.isAnnotationPresent(JoinColumn.class) && joinItemField.getAnnotation(JoinColumn.class).name().equals(mainJoinField.getAnnotation(JoinColumn.class).name())) {
                    joinItemField.set(joinItem, mainItem);
                    break;
                }
            }
        }
    }

    protected static void setValueWhileConvertingDBToObject(Object item, Object dbValue, Field f) throws IllegalAccessException {
        Class<?> targetType = resolveTargetType(item, f);
        if (targetType != null && targetType.isEnum()) {
            Object enu = dbValue == null ? null : Arrays.stream(targetType.getEnumConstants()).filter(o -> o.toString().equals(dbValue.toString())).findFirst().orElse(null);
            f.set(item, enu);
            return;
        }

        switch (dbValue) {
            case null -> f.set(item, null);
            case Number n -> setNumeric(item, f, targetType, n);
            case java.util.Date d -> setTemporal(item, f, targetType, d);
            case String s -> setFromString(item, f, targetType, s);
            default -> f.set(item, dbValue);
        }
    }

    private static Class<?> resolveTargetType(Object item, Field f) {
        if (f.getType() != Object.class) return f.getType();
        Class<?> resolved = resolveGenericIdType(item.getClass());
        return resolved != null ? resolved : Object.class;
    }

    private static void setNumeric(Object item, Field f, Class<?> targetType, Number n) throws IllegalAccessException {
        Object value;
        if (targetType == long.class || targetType == Long.class) {
            value = n.longValue();
        } else if (targetType == int.class || targetType == Integer.class) {
            value = n.intValue();
        } else if (targetType == short.class || targetType == Short.class) {
            value = n.shortValue();
        } else if (targetType == byte.class || targetType == Byte.class) {
            value = n.byteValue();
        } else if (targetType == double.class || targetType == Double.class) {
            value = n.doubleValue();
        } else if (targetType == float.class || targetType == Float.class) {
            value = n.floatValue();
        } else if (targetType == BigDecimal.class) {
            value = (n instanceof BigDecimal bd) ? bd : new BigDecimal(n.toString());
        } else if (targetType == String.class) {
            value = n.toString();
        } else if (targetType == Boolean.class || targetType == boolean.class) {
            value = n.longValue() != 0;
        } else {
            value = n; // Number/Object/unresolved — store as-is, no narrowing
        }
        f.set(item, value);
    }

    private static void setTemporal(Object item, Field f, Class<?> targetType, java.util.Date d) throws IllegalAccessException {
        Object value;
        if (targetType == LocalDate.class) {
            value = (d instanceof java.sql.Date sd) ? sd.toLocalDate() : d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        } else if (targetType == LocalDateTime.class) {
            value = (d instanceof Timestamp ts) ? ts.toLocalDateTime() : d.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        } else if (targetType == LocalTime.class) {
            value = (d instanceof java.sql.Time st) ? st.toLocalTime() : d.toInstant().atZone(ZoneId.systemDefault()).toLocalTime();
        } else if (targetType == Instant.class) {
            value = d.toInstant();
        } else if (targetType == Long.class || targetType == long.class) {
            value = d.getTime();
        } else if (targetType == String.class) {
            value = d.toString();
        } else {
            value = d;
        }
        f.set(item, value);
    }

    private static void setFromString(Object item, Field f, Class<?> targetType, String s) throws IllegalAccessException {
        Object value;
        if (targetType == LocalDateTime.class) {
            value = LocalDateTime.parse(s);
        } else if (targetType == LocalDate.class) {
            value = LocalDate.parse(s);
        } else if (targetType == LocalTime.class) {
            value = LocalTime.parse(s);
        } else if (targetType == Instant.class) {
            value = Instant.parse(s);
        } else if (targetType == Boolean.class || targetType == boolean.class) {
            value = "1".equals(s) || "true".equalsIgnoreCase(s);
        } else if (targetType == Long.class || targetType == long.class) {
            value = Long.parseLong(s);
        } else if (targetType == Integer.class || targetType == int.class) {
            value = Integer.parseInt(s);
        } else if (targetType == BigDecimal.class) {
            value = new BigDecimal(s);
        } else {
            value = s;
        }
        f.set(item, value);
    }
    private static Class<?> resolveGenericIdType(Class<?> entityClass) {
        Type superclass = entityClass.getGenericSuperclass();
        if (superclass instanceof ParameterizedType pt) {
            Type actual = pt.getActualTypeArguments()[0]; // assumes T is the first param
            if (actual instanceof Class<?> cls) {
                return cls;
            }
        }
        return null; // couldn't resolve, fall back to raw value
    }

    protected static class SQLCleaner {
        public String newSQL;
        public Object[] newParams;

        public SQLCleaner(String sql, List<Object> params) {
            fixNullParams(sql, params);
        }
        public SQLCleaner(String sql, Object[] params) {
            if (params == null) {
                newSQL = sql;
                return;
            }
            fixNullParams(sql, Arrays.asList(params));
        }

        public void fixNullParams(String sql, List<Object> params) {
            sql = sql.replaceAll("\\s*=\\s*\\?", "=?");

            StringBuilder newSql = new StringBuilder();
            List<Object> newParams = new ArrayList<>();

            int paramIndex = 0;
            int pos = 0;

            int wherePos = -1;
            {
                String upperSql = sql.toUpperCase();
                wherePos = upperSql.indexOf("WHERE ");
                if (wherePos == -1)
                    wherePos = upperSql.indexOf("WHERE\n");
                if (wherePos == -1)
                    wherePos = upperSql.indexOf("WHERE\t");
                if (wherePos == -1)
                    wherePos = upperSql.indexOf("WHERE"); // fallback
            }

            while (pos < sql.length()) {
                int qIndex = sql.indexOf("?", pos);
                if (qIndex == -1 || paramIndex >= params.size()) {
                    newSql.append(sql.substring(pos));
                    break;
                }

                newSql.append(sql, pos, qIndex);
                Object value = params.get(paramIndex);
                boolean isEqualsParam = false;

                // Only check for "=" before the "?" and if we are after WHERE
                int check = qIndex - 1;
                while (check >= 0 && Character.isWhitespace(sql.charAt(check))) check--;
                if (check >= 0 && sql.charAt(check) == '=') {
                    isEqualsParam = (qIndex > wherePos && wherePos != -1);
                }

                if (isEqualsParam && value == null) {
                    // Replace "= ?" with "IS NULL"
                    int lastEq = newSql.lastIndexOf("=");
                    if (lastEq != -1) newSql.deleteCharAt(lastEq);
                    newSql.append(" IS NULL");
                } else {
                    newSql.append("?");
                    newParams.add(value);
                }

                pos = qIndex + 1;
                paramIndex++;
            }

            this.newSQL = newSql.toString();
            this.newParams = newParams.toArray();
        }
    }

    public static void scanEntitiesOfLoaders(Collection<ClassLoader> entityClassloaders, Consumer<List<Class<?>>> consumer) {
        try (ScanResult scanResult = new ClassGraph().enableClassInfo().enableAnnotationInfo().overrideClassLoaders(entityClassloaders.toArray(new ClassLoader[]{})).scan()) {
            List<Class<?>> L = new ArrayList<>();
            for (ClassInfo classInfo : scanResult.getClassesWithAnnotation(Entity.class).stream().toList()) {
                if (classInfo.extendsSuperclass(DatabaseObject.class)) {
                    L.add(classInfo.loadClass());
                }
            }
            consumer.accept(L);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}