package org.solarframework.db.api.dto;

import java.util.ArrayList;
import java.util.List;

public class TableStats {
    public String schemaName;
    public String tableName;
    public long totalRows = 0;
    public List<String> columnNames = new ArrayList<>();

    public String getSchemaName() {
        return schemaName;
    }
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
