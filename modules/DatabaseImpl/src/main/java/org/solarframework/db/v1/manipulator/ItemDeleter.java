package org.solarframework.db.v1.manipulator;

import org.solarframework.db.v1.DatabaseManager;
import org.solarframework.db.v1.TableRow;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.solarframework.db.v1.manipulator.ItemRetrieval.transferResultToRow;

public class ItemDeleter {
    private final transient DatabaseManager DBM;

    protected String TableName;

    protected String where = "";
    protected List<Object> wheres = new ArrayList<>();

    protected String limit = "";
    protected String offset = "";

    public ItemDeleter(DatabaseManager DBM, String tablename) {
        this.DBM = DBM;
        this.TableName = tablename;
    }
    public ItemDeleter where(String query, Object... o) {
        return where(query, new ArrayList<>(Arrays.asList(o)));
    }
    public ItemDeleter where(String query, List<Object> o) {
        where = "WHERE " + (query.toLowerCase().startsWith("where ") ? query.substring(6) : query);
        wheres = o;
        return this;
    }
    public ItemDeleter limit(int limit) {
        this.limit = "LIMIT " + limit;
        return this;
    }
    public ItemDeleter offset(int offset) {
        this.offset = "OFFSET " + offset;
        return this;
    }


    private String buildQuery() {
        return "DELETE FROM " + TableName + " " + where + " " + offset + " " + limit;
    }


    public int delete() {
        try {
            return process(buildQuery());
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return 0;
        }
    }
    public int deleteFirst() {
        try {
            return process("DELETE FROM " + TableName + " " + where + " " + offset + " LIMIT 1");
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return 0;
        }
    }

    private int process(String query) throws SQLException {
        if (DBM.output()) System.out.println(query + "\n└──> [" + wheres.stream().map(obj -> obj == null ? "null" : obj.toString()).collect(Collectors.joining(",")) + "]");
        try (Connection connection = DBM.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            if (!wheres.isEmpty()) {
                for (int i = 0; i < wheres.size(); i++) {
                    stmt.setObject(i + 1, wheres.get(i));
                }
            }
            return stmt.executeUpdate();
        }
    }
    private List<TableRow> processReturning(String query) throws SQLException {
        if (DBM.output()) System.out.println(query + "\n└──> [" + wheres.stream().map(obj -> obj == null ? "null" : obj.toString()).collect(Collectors.joining(",")) + "]");
        List<TableRow> TR = new ArrayList<>();
        try (Connection connection = DBM.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query + " RETURNING *")) {
            if (!wheres.isEmpty()) {
                for (int i = 0; i < wheres.size(); i++) {
                    stmt.setObject(i + 1, wheres.get(i));
                }
            }
            return transferResultToRow(TR, stmt);
        }
    }
}