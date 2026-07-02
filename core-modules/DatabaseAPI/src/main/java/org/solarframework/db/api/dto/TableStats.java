package org.solarframework.db.api.dto;

import org.solarframework.db.api.DatabaseType;

import java.util.ArrayList;
import java.util.List;

public class TableStats {
    public String sourceName;
    public DatabaseType sourceType;
    public String schemaName;
    public String tableName;
    public long totalRows = 0;
    public List<ColumnDetail> columnDetails = new ArrayList<>();

    public String getSourceName() {
        return sourceName;
    }
    public DatabaseType getSourceType() {
        return sourceType;
    }
    public String getSchemaName() {
        return schemaName;
    }
    public String getTableName() {
        return tableName;
    }
    public long getTotalRows() {
        return totalRows;
    }
    public List<ColumnDetail> getColumnDetails() {
        return columnDetails;
    }
    public List<String> getColumnNames() {
        return columnDetails.stream().map(ColumnDetail::getName).toList();
    }


    public static class ColumnDetail {
        public String name;
        public String type;
        public int size;
        public int decimalDigits;
        public boolean nullable;
        public boolean isAutoIncrement;
        public boolean isPrimaryKey;
        public boolean isUnique;
        public String defaultValue;
        public String remarks;

        public boolean isAutoIncrement() {
            return isAutoIncrement;
        }
        public boolean isNullable() {
            return nullable;
        }
        public boolean isPrimaryKey() {
            return isPrimaryKey;
        }
        public boolean isUnique() {
            return isUnique;
        }
        public int getDecimalDigits() {
            return decimalDigits;
        }
        public int getSize() {
            return size;
        }
        public String getType() {
            return type;
        }
        public String getName() {
            return name;
        }
        public String getDefaultValue() {
            return defaultValue;
        }
        public String getRemarks() {
            return remarks;
        }
    }
}
