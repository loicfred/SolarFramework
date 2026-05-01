package org.solarframework.db.v1.manipulator;

import org.solarframework.db.v1.DBOrder;
import org.solarframework.db.v1.DatabaseManager;
import org.solarframework.db.v1.QueryParameter;
import org.solarframework.db.v1.TableRow;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.solarframework.core.util.NumberUtils.GenerateRandomNumber;

public class ItemRetrieval {
    private final transient DatabaseManager DBM;

    protected String TableName;

    protected String select = "SELECT *";

    protected String where = "";
    protected List<Object> wheres = new ArrayList<>();

    protected String order = "";
    protected String limit = "";
    protected String offset = "";

    public ItemRetrieval(DatabaseManager DBM, String tablename) {
        this.DBM = DBM;
        this.TableName = tablename;
    }

    public ItemRetrieval select(String query) {
        select = "SELECT " + (query.toLowerCase().startsWith("select ") ? query.substring(7) : query);
        if (select.length() == 7) select = select + "*";
        return this;
    }
    public ItemRetrieval select(String... query) {
        select = "SELECT " + String.join(",", query);
        if (select.length() == 7) select = select + "*";
        return this;
    }
    public ItemRetrieval where(String query, Object... o) {
        return where(query, new ArrayList<>(Arrays.asList(o)));
    }
    public ItemRetrieval where(String query, List<Object> o) {
        where = "WHERE " + (query.toLowerCase().startsWith("where ") ? query.substring(6) : query);
        wheres = o;
        return this;
    }
    public ItemRetrieval order(String column, DBOrder ASC_DESC) {
        this.order = "ORDER BY " + column + " " + ASC_DESC;
        return this;
    }
    public ItemRetrieval limit(int limit) {
        this.limit = "LIMIT " + limit;
        return this;
    }
    public ItemRetrieval offset(int offset) {
        this.offset = "OFFSET " + offset;
        return this;
    }


    private String buildQuery() {
        return select + " FROM " + TableName + " " + where + " " + order + " " + offset + " " + limit;
    }


    public List<TableRow> get() {
        try {
            return process(buildQuery());
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return new ArrayList<>();
        }
    }
    public TableRow getRandom() {
        try {
            List<TableRow> t = process(buildQuery());
            return t.get(GenerateRandomNumber(0, t.size()-1));
        } catch (Exception e) {
            if (!e.getMessage().contains("Index 0 out of bounds")) System.err.println(e.getMessage());
            return null;
        }
    }
    public TableRow getFirst() {
        try {
            return process(select + " FROM " + TableName + " " + where + " " + order + " " + offset + " LIMIT 1").get(0);
        } catch (Exception e) {
            if (!e.getMessage().contains("Index 0 out of bounds")) System.err.println(e.getMessage());
            return null;
        }
    }

    public <T> T mapFirstTo(Class<T> clazz) {
        TableRow F = getFirst();
        return F != null ? F.mapTo(clazz) : null;
    }
    public <T> List<T> mapAllTo(Class<T> clazz) {
        List<T> Items = new ArrayList<>();
        for (TableRow TR : get()) {
            Items.add(TR.mapTo(clazz));
        }
        return Items;
    }

    private List<TableRow> process(String query) throws SQLException {
        if (DBM.output()) System.out.println(query + "\n└──> [" + wheres.stream().map(obj -> obj == null ? "null" : obj.toString()).collect(Collectors.joining(",")) + "]");
        List<TableRow> TR = new ArrayList<>();
        try (Connection connection = DBM.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            if (!wheres.isEmpty()) {
                for (int i = 0; i < wheres.size(); i++) {
                    stmt.setObject(i + 1, wheres.get(i));
                }
            }
            return transferResultToRow(TR, stmt);
        }
    }

    public static List<TableRow> transferResultToRow(List<TableRow> TR, PreparedStatement stmt) throws SQLException {
        try (ResultSet RowReturned = stmt.executeQuery()) {
            while (RowReturned.next()) {
                List<QueryParameter> QS = new ArrayList<>();
                ResultSetMetaData MD = RowReturned.getMetaData();
                for (int i = 1; i <= MD.getColumnCount(); i++) {
                    QS.add(new QueryParameter(MD.getColumnLabel(i), RowReturned.getObject(i)));
                }
                TR.add(new TableRow(QS));
            }
            return TR;
        }
    }

}
