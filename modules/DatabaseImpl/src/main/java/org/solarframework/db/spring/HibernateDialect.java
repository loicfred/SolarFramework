package org.solarframework.db.spring;

public enum HibernateDialect {

    // H2
    H2("org.hibernate.dialect.H2Dialect"),

    // PostgreSQL
    POSTGRESQL("org.hibernate.dialect.PostgreSQLDialect"),

    // MySQL
    MYSQL("org.hibernate.dialect.MySQLDialect"),

    // MariaDB
    MARIADB("org.hibernate.dialect.MariaDBDialect"),

    // SQLite (Hibernate 6 community dialect)
    SQLITE("org.hibernate.community.dialect.SQLiteDialect"),

    // Oracle
    ORACLE("org.hibernate.dialect.OracleDialect"),

    // SQL Server
    SQL_SERVER("org.hibernate.dialect.SQLServerDialect"),

    // DB2
    DB2("org.hibernate.dialect.DB2Dialect"),

    // Sybase
    SYBASE("org.hibernate.dialect.SybaseDialect"),

    // Derby
    DERBY("org.hibernate.dialect.DerbyDialect"),

    // Informix
    INFORMIX("org.hibernate.dialect.InformixDialect"),

    // Firebird
    FIREBIRD("org.hibernate.community.dialect.FirebirdDialect"),

    // CockroachDB
    COCKROACHDB("org.hibernate.dialect.CockroachDialect"),

    // TimescaleDB (PostgreSQL compatible)
    TIMESCALEDB("org.hibernate.dialect.PostgreSQLDialect"),

    // YugabyteDB (PostgreSQL compatible)
    YUGABYTEDB("org.hibernate.dialect.PostgreSQLDialect");


    private final String className;
    HibernateDialect(String className) {
        this.className = className;
    }
    public String getClassName() {
        return className;
    }
}