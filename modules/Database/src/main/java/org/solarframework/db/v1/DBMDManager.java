package org.solarframework.db.v1;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

class DBMDManager {
    DatabaseMetaData DMD;
    String tableName;

    public DBMDManager(DatabaseMetaData DMD, String tableName) {
        this.DMD = DMD;
        this.tableName = tableName;
    }

    List<String> uniques = null;

    public List<String> getUniqueColumns() {
        if (uniques == null) {
            uniques = new ArrayList<>();
            try (ResultSet resultSet = DMD.getIndexInfo(null, null, tableName, true, false)) {
                while (resultSet.next()) {
                    String columnName = resultSet.getString("COLUMN_NAME");
                    boolean isUnique = !resultSet.getBoolean("NON_UNIQUE");
                    if (columnName != null && isUnique) {
                        uniques.add(columnName);
                    }
                }
            } catch (SQLException ignored) {
            }
        }
        return uniques;
    }

    List<String> foreign = null;

    public List<String> getForeignKeys() {
        if (foreign == null) {
            foreign = new ArrayList<>();
            try (ResultSet resultSet = DMD.getImportedKeys(null, null, tableName)) {
                while (resultSet.next()) {
                    String foreignTableName = resultSet.getString("PKTABLE_NAME");
                    foreign.add(foreignTableName);
                }
            } catch (SQLException ignored) {
            }
        }
        return foreign;
    }

}
