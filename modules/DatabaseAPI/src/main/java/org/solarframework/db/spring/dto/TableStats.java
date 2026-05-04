package org.solarframework.db.spring.dto;

import java.util.ArrayList;
import java.util.List;

public class TableStats {
    public String tableName;
    public long totalRows = 0;
    public List<String> columnNames = new ArrayList<>();

    public String getTableName() {
        return tableName;
    }
    public long getTotalRows() {
        return totalRows;
    }
    public List<String> getColumnNames() {
        return columnNames;
    }
}
