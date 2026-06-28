package org.solarframework.db.api.dto;

import java.util.ArrayList;
import java.util.List;

public class DatabaseStats {
    public int totalTables = 0;
    public int totalViews = 0;
    public long totalRows = 0;
    public List<String> tableNames = new ArrayList<>();
    public List<String> viewNames = new ArrayList<>();

    public int getTotalTables() {
        return totalTables;
    }
    public int getTotalViews() {
        return totalViews;
    }
    public long getTotalRows() {
        return totalRows;
    }
    public List<String> getTableNames() {
        return tableNames;
    }
    public List<String> getViewNames() {
        return viewNames;
    }
}