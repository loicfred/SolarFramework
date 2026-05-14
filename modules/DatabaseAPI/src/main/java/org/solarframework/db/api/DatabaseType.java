package org.solarframework.db.api;

public enum DatabaseType {

    MariaDB("org.mariadb.jdbc.Driver"),
    Oracle("oracle.jdbc.driver.OracleDriver"),
    PostgresSQL("org.postgresql.Driver"),
    MySQL("com.mysql.cj.jdbc.Driver"),
    SQLServer("com.microsoft.sqlserver.jdbc.SQLServerDriver"),
    SQLite("org.sqlite.JDBC");

    private final String driverClass;

    DatabaseType(String driverClass) {
        this.driverClass = driverClass;
    }

    public String getDriverClass() {
        return driverClass;
    }
    public boolean supportsReturning() {
        return this == MariaDB || this == Oracle || this == PostgresSQL;
    }
}
