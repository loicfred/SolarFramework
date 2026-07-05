package org.solarframework.db.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.solarframework.db.api.DatabaseType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TableStats {
    @JsonIgnore
    private transient Set<String> timeColumns;

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

    private static final Set<String> TEMPORAL_TYPES = Set.of("DATETIME", "TIMESTAMP", "DATE", "TIME");

    public Set<String> getTimeColumns() {
        return timeColumns == null ? timeColumns = getColumnDetails().stream().filter(c -> TEMPORAL_TYPES.contains(c.getType().toUpperCase())).map(c -> c.getName().toLowerCase()).collect(Collectors.toSet()) : timeColumns;
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
