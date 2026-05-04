package org.solarframework.db.v1.manipulator;

import org.solarframework.db.v1.DatabaseManager;
import org.solarframework.db.v1.QueryParameter;
import org.solarframework.db.v1.TableRow;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.solarframework.db.v1.DatabaseManager.getFields;
import static org.solarframework.db.v1.manipulator.ItemRetrieval.transferResultToRow;

public class ItemCreator {
    private final transient DatabaseManager DBM;

    protected String TableName;
    protected boolean UpdateOnDuplicate;
    protected List<QueryParameter> updates;

    public ItemCreator(DatabaseManager DBM, String tablename) {
        this.DBM = DBM;
        this.TableName = tablename;
    }

    public ItemCreator updateIfExist() {
        UpdateOnDuplicate = true;
        return this;
    }

    public int create(QueryParameter... F) {
        List<QueryParameter> L = new ArrayList<>();
        Collections.addAll(L, F);
        return create(L);
    }
    public int create(List<QueryParameter> F) {
        try {
           String query = "INSERT INTO " + TableName + " (" + F.stream().map(QueryParameter::getFieldName).collect(Collectors.joining(", ")) + ") VALUES (" + F.stream().map(f -> "?").collect(Collectors.joining(", ")) + ")";
            if (DBM.output()) System.out.println(query + "\n└──> [" + F.stream().map((QueryParameter V) -> V.getValue().toString()).collect(Collectors.joining(" | ")) + "]");
            try (Connection connection = DBM.getConnection();
                 PreparedStatement stmt = connection.prepareStatement(query)) {
                for (int i = 0; i < F.size(); i++) {
                    stmt.setObject(i + 1, F.get(i).getValue());
                }
                return stmt.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return 0;
        }
    }

    public int create(Object obj) {
        try {
            List<Field> F = getFields(obj, false);
            String sql = writeSql(F);
            output(F, sql, obj);
            try (Connection connection = DBM.getConnection(); PreparedStatement stmt = connection.prepareStatement(sql)) {
                try {
                    for (int i = 0; i < F.size(); i++)
                        stmt.setObject(i + 1, F.get(i).get(obj));
                } catch (Exception ignored) {}
                return stmt.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return 0;
        }
    }
    public TableRow returning(Object obj) {
        try {
            List<Field> F = getFields(obj, false);
            String sql = writeSql(F) + " RETURNING *";
            output(F, sql, obj);
            try (Connection connection = DBM.getConnection(); PreparedStatement stmt = connection.prepareStatement(sql)) {
                try {
                    for (int i = 0; i < F.size(); i++)
                        stmt.setObject(i + 1, F.get(i).get(obj));
                } catch (Exception ignored) {}
                return transferResultToRow(new ArrayList<>(), stmt).get(0);
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return null;
        }
    }
    public TableRow returning(Object obj, List<QueryParameter> parameters) {
        try {
            updates = parameters;
            return returning(obj);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return null;
        }
    }

    private String writeSql(List<Field> F) {
        String columns = F.stream().map(Field::getName).collect(Collectors.joining(", "));
        String placeholders = F.stream().map(f -> "?").collect(Collectors.joining(", "));
        String query = "INSERT INTO " + TableName + " (" + columns + ") VALUES (" + placeholders + ")";
        if (UpdateOnDuplicate) {
            String updatesStr;
            if (updates != null) {
                updatesStr = updates.stream().map(f -> f.getFieldName() + " = VALUES(" + f.getValue() + ")").collect(Collectors.joining(", "));
            } else {
                updatesStr = F.stream().map(f -> f.getName() + " = VALUES(" + f.getName() + ")").collect(Collectors.joining(", "));
            }
            if (!updatesStr.isEmpty()) query += " ON DUPLICATE KEY UPDATE " + updatesStr;
        }
        return query;
    }

    private void output(List<Field> fields, String query, Object obj) {
        if (DBM.output()) System.out.println(query + "\n└──> [" + fields.stream().map(f -> {
            try {
                return String.valueOf(f.get(obj));
            } catch (IllegalAccessException e) {
                return "?";
            }
        }).collect(Collectors.joining(" | ")) + "]");
    }
}