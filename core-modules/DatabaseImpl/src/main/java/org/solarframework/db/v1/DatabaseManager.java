package org.solarframework.db.v1;

import org.solarframework.db.api.DatabaseType;
import org.solarframework.db.v1.manipulator.ItemCreator;
import org.solarframework.db.v1.manipulator.ItemDeleter;
import org.solarframework.db.v1.manipulator.ItemRetrieval;
import org.solarframework.db.v1.manipulator.ItemUpdater;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.solarframework.core.util.ClassUtils.getFieldValue;
import static org.solarframework.db.v1.manipulator.ItemRetrieval.transferResultToRow;

public record DatabaseManager(DatabaseType type, String url, String user, String password, boolean output)  {

    public DatabaseManager {
        try {
            System.out.println("Starting spring connectivity...");
            if (url == null || url.isBlank()) {
                throw new IllegalArgumentException("URL cannot be empty");
            }
            try (Connection C = DriverManager.getConnection(url, user, password)) {
                System.out.println("Connected to spring successfully!");
            } catch (SQLException e) {
                System.err.println("Connection to spring failed: " + e.getMessage());
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }
    public Connection getConnection() throws SQLException {
        if (user == null && password == null) return DriverManager.getConnection(url);
        return DriverManager.getConnection(url, user, password);
    }

    public long countRows(String tablename) {
        try {
            return Long.parseLong(processQuery("SELECT COUNT(*) AS total_rows FROM " + tablename.toLowerCase()).get(0).QP.get(0).getValue().toString());
        } catch (Exception ignored) {
            return 0;
        }
    }
    public long countRows(String tablename, String whereQuery, Object... o) {
        try {
            whereQuery = "WHERE " + (whereQuery.toLowerCase().startsWith("where ") ? whereQuery.substring(6) : whereQuery);
            return Long.parseLong(processQuery("SELECT COUNT(*) AS total_rows FROM " + tablename.toLowerCase() + " " + whereQuery, o).get(0).QP.get(0).getValue().toString());
        } catch (Exception ignored) {
            return 0;
        }
    }
    public <T> long countRows(Class<T> tablename) {
        try {
            return (long) processQuery("SELECT COUNT(*) AS total_rows FROM " + tablename.getSimpleName().toLowerCase()).get(0).QP.get(0).getValue();
        } catch (Exception ignored) {
            return 0;
        }
    }
    public <T> long countRows(Class<T> tablename, String whereQuery, Object... o) {
        try {
            whereQuery = "WHERE " + (whereQuery.toLowerCase().startsWith("where ") ? whereQuery.substring(6) : whereQuery);
            return (long) processQuery("SELECT COUNT(*) AS total_rows FROM " + tablename.getSimpleName().toLowerCase() + " " + whereQuery, o).get(0).QP.get(0).getValue();
        } catch (Exception ignored) {
            return 0;
        }
    }

    public List<TableRow> processQuery(String query, List<Object> O) throws SQLException {
        if (output) System.out.println(query + "\n└──> [" + O.stream().map(obj -> obj == null ? "null" : obj.toString()).collect(Collectors.joining(",")) + "]");
        try (Connection connection = getConnection(); PreparedStatement stmt = connection.prepareStatement(query)) {
            for (int i = 0; i < O.size(); i++) {stmt.setObject(i + 1, O.get(i));}
            List<TableRow> TR = new ArrayList<>();
            return transferResultToRow(TR, stmt);
        }
    }
    public List<TableRow> processQuery(String query, Object... o) throws SQLException {
        return processQuery(query, new ArrayList<>(Arrays.asList(o)));
    }
    public void processUpdate(String query, List<Object> O) {
        if (output) System.out.println(query + "\n└──> [" + O.stream().map(obj -> obj == null ? "null" : obj.toString()).collect(Collectors.joining(",")) + "]");
        try (Connection connection = getConnection(); PreparedStatement stmt = connection.prepareStatement(query)) {
            for (int i = 0; i < O.size(); i++) {stmt.setObject(i + 1, O.get(i));}
            stmt.executeUpdate();
        } catch (Exception ignored) {}
    }
    public void processUpdate(String query, Object... o) {
        List<Object> O = new ArrayList<>();
        Collections.addAll(O, o);
        processUpdate(query, O);
    }



    public ItemCreator createItem(String tableName) {
        return new ItemCreator(this, tableName.toLowerCase());
    }
    public <T> ItemCreator createItem(Class<T> clazz) {
        return new ItemCreator(this, clazz.getSimpleName().toLowerCase());
    }

    public ItemRetrieval retrieveItems(String tableName) {
        return new ItemRetrieval(this, tableName.toLowerCase());
    }
    public <T> ItemRetrieval retrieveItems(Class<T> clazz) {
        return new ItemRetrieval(this, clazz.getSimpleName().toLowerCase());
    }

    public ItemUpdater updateItems(String tableName) {
        return new ItemUpdater(this, tableName.toLowerCase());
    }
    public <T> ItemUpdater updateItems(Class<T> clazz) {
        return new ItemUpdater(this, clazz.getSimpleName().toLowerCase());
    }

    public ItemDeleter deleteItems(String tableName) {
        return new ItemDeleter(this, tableName.toLowerCase());
    }
    public <T> ItemDeleter deleteItems(Class<T> clazz) {
        return new ItemDeleter(this, clazz.getSimpleName().toLowerCase());
    }






    public static Field getFieldIgnoreCase(List<Field> fields, String fieldName) throws NoSuchFieldException {
        for (Field F : fields) {
            if (F.getName().equalsIgnoreCase(fieldName)) {
                fields.remove(F);
                return F;
            }
        }
        throw new NoSuchFieldException("No field found with name " + fieldName);
    }

    public static List<Field> getFields(Object obj, boolean acceptNull) {
        List<Field> fields = new ArrayList<>();
        Class<?> clazz = obj.getClass();
        while (clazz != null) {
            for (Field field : clazz.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    if (acceptNull || field.get(obj) != null) {
                        fields.add(field);
                    }
                } catch (IllegalAccessException ignored) {
                }
            }
            clazz = clazz.getSuperclass();
        }
        return new ArrayList<>(fields.stream().filter(F -> !Modifier.isTransient(F.getModifiers())).toList());
    }

    public <T> List<String> getUniqueKeys(Class<T> clazz) {
        try (Connection connection = getConnection()) {
            DBMDManager DBMD = new DBMDManager(connection.getMetaData(), clazz.getSimpleName().replace("DB", "").toLowerCase());
            return DBMD.getUniqueColumns();
        } catch (SQLException ignored) {
            return new ArrayList<>();
        }
    }

    public <T> List<String> getForeignKeys(Class<T> clazz) {
        try (Connection connection = getConnection()) {
            DBMDManager DBMD = new DBMDManager(connection.getMetaData(), clazz.getSimpleName().replace("DB", "").toLowerCase());
            return DBMD.getForeignKeys();
        } catch (SQLException ignored) {
            return new ArrayList<>();
        }
    }

    public <T> List<QueryParameter> getAllColumnValues(T item) {
        List<QueryParameter> QP = new ArrayList<>();
        for (Field f : getFields(item, false)) {
            QP.add(new QueryParameter(f.getName(), getFieldValue(f, item)));
        }
        return QP;
    }

    public <T> List<QueryParameter> getUniqueColumnValues(T item) {
        List<String> uniquecolumns = getUniqueKeys(item.getClass()).stream().map(String::toLowerCase).toList();
        List<QueryParameter> QP = new ArrayList<>();
        for (Field f : getFields(item, false).stream().filter(f -> !uniquecolumns.contains(f.getName().toLowerCase())).toList()) {
            if (getFieldValue(f, item).getClass() != QueryParameter.class) QP.add(new QueryParameter(f.getName(), getFieldValue(f, item)));
        }
        return QP;
    }


    public static <T> T ConstructItem(Class<T> clazz) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        Constructor<T> C = clazz.getDeclaredConstructor();
        C.setAccessible(true);
        return C.newInstance();
    }


}