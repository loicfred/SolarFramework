package org.solarframework.db.exception;

/** Thrown when a table copy fails; identifies the table so it can be reported to the user (NFR-10). */
public class DataMigrationException extends Exception {
    private final String tableName;

    public DataMigrationException(String tableName, Throwable cause) {
        super("Migration failed for table '" + tableName + "'", cause);
        this.tableName = tableName;
    }

    public String getTableName() {
        return tableName;
    }
}