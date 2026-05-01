package org.solarframework.db.v1.manipulator;

import org.solarframework.db.v1.DBUpdate;
import org.solarframework.db.v1.DatabaseManager;
import org.solarframework.db.v1.QueryParameter;
import org.solarframework.db.v1.TableRow;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.solarframework.db.v1.manipulator.ItemRetrieval.transferResultToRow;

public class ItemUpdater {
    private final transient DatabaseManager DBM;

    protected String TableName;

    protected String set = "";
    protected List<Object> sets = new ArrayList<>();

    protected String where = "";
    protected List<Object> wheres = new ArrayList<>();

    protected String limit = "";
    protected String offset = "";

    public ItemUpdater(DatabaseManager DBM, String tablename) {
        this.TableName = tablename;
        this.DBM = DBM;
    }
    public ItemUpdater set(List<QueryParameter> s) {
        if (s != null) {
            set = "SET " + s.stream().map(M -> (M.type.equals(DBUpdate.INCREMENT) ? M.fieldName + " = " + M.fieldName + " + ?" : M.fieldName + " = ?")).collect(Collectors.joining(", "));
            sets = s.stream().map(QueryParameter::getValue).toList();
        }
        return this;
    }
    public ItemUpdater set(QueryParameter... s) {
        if (s == null) return this;
        List<QueryParameter> l = new ArrayList<>();
        Collections.addAll(l, s);
        return set(l);
    }
    public ItemUpdater where(String query, Object... o) {
        return where(query, new ArrayList<>(Arrays.asList(o)));
    }
    public ItemUpdater where(String query, List<Object> o) {
        where = "WHERE " + (query.toLowerCase().startsWith("where ") ? query.substring(6) : query);
        wheres = o;
        return this;
    }
    public ItemUpdater limit(int limit) {
        this.limit = "LIMIT " + limit;
        return this;
    }
    public ItemUpdater offset(int offset) {
        this.offset = "OFFSET " + offset;
        return this;
    }


    private String buildQuery() {
        return "UPDATE " + TableName + " " + set + " " + where + " " + offset + " " + limit;
    }


    public int update() {
        try {
            return process(buildQuery());
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return 0;
        }
    }
    public List<TableRow> returning() {
        try {
            return processReturning(buildQuery());
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return null;
        }
    }
    public int updateFirst() {
        try {
            return process("UPDATE " + TableName + " " + set + " " + where + " " + offset + " LIMIT 1");
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return 0;
        }
    }

    private int process(String query) throws SQLException {
        OutputLog(query);
        try (Connection connection = DBM.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            setValues(stmt);
            return stmt.executeUpdate();
        }
    }
    private List<TableRow> processReturning(String query) throws SQLException {
        OutputLog(query);
        List<TableRow> TR = new ArrayList<>();
        try (Connection connection = DBM.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query + " RETURNING *")) {
            setValues(stmt);
            return transferResultToRow(TR, stmt);
        }
    }

    private void setValues(PreparedStatement stmt) throws SQLException {
        if (!sets.isEmpty()) {
            for (int i = 0; i < sets.size(); i++) {
                if (sets.get(i) instanceof LocalDate LD) {
                    stmt.setObject(i + 1, java.sql.Date.valueOf(LD));
                } else {
                    stmt.setObject(i + 1, sets.get(i));
                }
            }
        }
        if (!wheres.isEmpty()) {
            for (int i = 0; i < wheres.size(); i++) {
                if (wheres.get(i) instanceof LocalDate LD) {
                    stmt.setObject(sets.size() + i + 1, java.sql.Date.valueOf(LD));
                } else {
                    stmt.setObject(sets.size() + i + 1, wheres.get(i));
                }
            }
        }
    }
    private void OutputLog(String query) {
        if (DBM.output()) System.out.println(query + "\n└──> [" + sets.stream().map(obj -> obj == null ? "null" : obj.toString()).collect(Collectors.joining(",")) + "]" +
                "\n└──> [" + wheres.stream().map(obj -> obj == null ? "null" : obj.toString()).collect(Collectors.joining(",")) + "]");
    }
}