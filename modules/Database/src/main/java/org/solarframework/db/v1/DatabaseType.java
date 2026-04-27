package org.solarframework.db.v1;

public enum DatabaseType {

    MySQL, SQLServer, SQLite, Oracle, PostgreSQL, H2, MariaDB;

    public boolean supportsReturning() {
        return this == MariaDB || this == Oracle || this == PostgreSQL;
    }

}
