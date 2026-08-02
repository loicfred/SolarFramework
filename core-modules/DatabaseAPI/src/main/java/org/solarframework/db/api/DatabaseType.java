package org.solarframework.db.api;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum DatabaseType {

    POSTGRESQL(
            "PostgreSQL",
            "org.postgresql.Driver",
            "jdbc:postgresql://",
            5432,
            "org.hibernate.dialect.PostgreSQLDialect",
            true,
            true,
            true,
            true,
            true
    ),

    MYSQL(
            "MySQL",
            "com.mysql.cj.jdbc.Driver",
            "jdbc:mysql://",
            3306,
            "org.hibernate.dialect.MySQLDialect",
            false,
            false,
            true,
            false,
            true
    ),

    MARIADB(
            "MariaDB",
            "org.mariadb.jdbc.Driver",
            "jdbc:mariadb://",
            3306,
            "org.hibernate.dialect.MariaDBDialect",
            true,
            false,
            true,
            false,
            true
    ),

    ORACLE(
            "Oracle",
            "oracle.jdbc.OracleDriver",
            "jdbc:oracle:thin:@",
            1521,
            "org.hibernate.dialect.OracleDialect",
            true,
            true,
            true,
            true,
            true
    ),

    SQLSERVER(
            "Microsoft SQL Server",
            "com.microsoft.sqlserver.jdbc.SQLServerDriver",
            "jdbc:sqlserver://",
            1433,
            "org.hibernate.dialect.SQLServerDialect",
            false,
            false,
            true,
            true,
            true
    ),

    SQLITE(
            "SQLite",
            "org.sqlite.JDBC",
            "jdbc:sqlite:",
            null,
            "org.hibernate.community.dialect.SQLiteDialect",
            false,
            false,
            true,
            false,
            true
    ),

    H2(
            "H2 Database",
            "org.h2.Driver",
            "jdbc:h2:",
            9092,
            "org.hibernate.dialect.H2Dialect",
            true,
            true,
            true,
            true,
            true
    ),

    HSQLDB(
            "HSQLDB",
            "org.hsqldb.jdbc.JDBCDriver",
            "jdbc:hsqldb:",
            9001,
            "org.hibernate.dialect.HSQLDialect",
            true,
            true,
            true,
            true,
            true
    ),

    DERBY(
            "Apache Derby",
            "org.apache.derby.jdbc.EmbeddedDriver",
            "jdbc:derby:",
            1527,
            "org.hibernate.dialect.DerbyDialect",
            false,
            true,
            true,
            true,
            true
    ),

    DB2(
            "IBM DB2",
            "com.ibm.db2.jcc.DB2Driver",
            "jdbc:db2://",
            50000,
            "org.hibernate.dialect.DB2Dialect",
            false,
            true,
            true,
            true,
            true
    ),

    COCKROACHDB(
            "CockroachDB",
            "org.postgresql.Driver",
            "jdbc:postgresql://",
            26257,
            "org.hibernate.dialect.CockroachDialect",
            true,
            true,
            true,
            true,
            true
    ),

    FIREBIRD(
            "Firebird",
            "org.firebirdsql.jdbc.FBDriver",
            "jdbc:firebirdsql:",
            3050,
            "org.hibernate.community.dialect.FirebirdDialect",
            false,
            true,
            true,
            true,
            true
    ),

    INFORMIX(
            "Informix",
            "com.informix.jdbc.IfxDriver",
            "jdbc:informix-sqli://",
            9088,
            "org.hibernate.dialect.InformixDialect",
            false,
            true,
            true,
            true,
            true
    );


    private final String displayName;
    private final String driverClass;
    private final String jdbcPrefix;
    private final Integer defaultPort;
    private final String dialectClass;

    private final boolean supportsReturning;
    private final boolean supportsSequences;
    private final boolean supportsIdentity;
    private final boolean supportsSchemas;
    private final boolean supportsTransactions;


    DatabaseType(
            String displayName,
            String driverClass,
            String jdbcPrefix,
            Integer defaultPort,
            String dialectClass,
            boolean supportsReturning,
            boolean supportsSequences,
            boolean supportsIdentity,
            boolean supportsSchemas,
            boolean supportsTransactions) {
        this.displayName = displayName;
        this.driverClass = driverClass;
        this.jdbcPrefix = jdbcPrefix;
        this.defaultPort = defaultPort;
        this.dialectClass = dialectClass;
        this.supportsReturning = supportsReturning;
        this.supportsSequences = supportsSequences;
        this.supportsIdentity = supportsIdentity;
        this.supportsSchemas = supportsSchemas;
        this.supportsTransactions = supportsTransactions;
    }


    public String getDisplayName() {
        return displayName;
    }

    public String getDriverClass() {
        return driverClass;
    }

    public String getJdbcPrefix() {
        return jdbcPrefix;
    }

    public Integer getDefaultPort() {
        return defaultPort;
    }

    public String getDialectClass() {
        return dialectClass;
    }

    public boolean supportsReturning() {
        return supportsReturning;
    }

    public boolean supportsSequences() {
        return supportsSequences;
    }

    public boolean supportsIdentity() {
        return supportsIdentity;
    }

    public boolean supportsSchemas() {
        return supportsSchemas;
    }

    public boolean supportsTransactions() {
        return supportsTransactions;
    }

    public static DatabaseType fromDriver(String driver) {
        return Arrays.stream(values()).filter(type -> type.getDriverClass().equalsIgnoreCase(driver)).findFirst().orElse(null);
    }


    public boolean supportsUpsert() { return this == POSTGRESQL || this == MYSQL || this == MARIADB || this == SQLITE || this == ORACLE || this == SQLSERVER || this == H2; }

    public String Upsert(String table, String columns, String questionMarks, String duplicateKeyUpdateClause, String conflictColumn) {
        return switch (this) {
            case MYSQL, MARIADB -> "INSERT INTO " + table + " (" + columns + ") VALUES (" + questionMarks + ")" + (duplicateKeyUpdateClause != null ? " ON DUPLICATE KEY UPDATE " + duplicateKeyUpdateClause : "");
            case POSTGRESQL, SQLITE, COCKROACHDB  -> "INSERT INTO " + table + " (" + columns + ") VALUES (" + questionMarks + ")" + (duplicateKeyUpdateClause != null ? " ON CONFLICT" + (conflictColumn != null ? "(" + conflictColumn + ")" : "") + " DO UPDATE SET " + duplicateKeyUpdateClause : "");
            // H2 has no ON CONFLICT / ON DUPLICATE KEY in Regular mode; its upsert is MERGE (primary key implied when KEY() is omitted).
            case H2 -> duplicateKeyUpdateClause == null
                    ? "INSERT INTO " + table + " (" + columns + ") VALUES (" + questionMarks + ")"
                    : "MERGE INTO " + table + " (" + columns + ")" + (conflictColumn != null ? " KEY(" + conflictColumn + ")" : "") + " VALUES (" + questionMarks + ")";
            default -> throw new UnsupportedOperationException(name() + " does not support UPSERT");
        };
    }
    public String UpsertBatch(String table, String columns, List<String> questionMarks, String duplicateKeyUpdateClause, String conflictColumn) {
        return switch (this) {
            case MYSQL, MARIADB -> "INSERT INTO " + table + " (" + columns + ") VALUES " + questionMarks.stream().map(s -> "(" + s + ")").collect(Collectors.joining(",")) + (duplicateKeyUpdateClause != null ? " ON DUPLICATE KEY UPDATE " + duplicateKeyUpdateClause : "");
            // one placeholder group per row, not the List's toString - "VALUES ([a, b])" is not SQL
            case POSTGRESQL, SQLITE, COCKROACHDB  -> "INSERT INTO " + table + " (" + columns + ") VALUES " + questionMarks.stream().map(s -> "(" + s + ")").collect(Collectors.joining(",")) + (duplicateKeyUpdateClause != null ? " ON CONFLICT" + (conflictColumn != null ? "(" + conflictColumn + ")" : "") + " DO UPDATE SET " + duplicateKeyUpdateClause : "");
            case H2 -> (duplicateKeyUpdateClause == null ? "INSERT" : "MERGE") + " INTO " + table + " (" + columns + ")"
                    + (duplicateKeyUpdateClause != null && conflictColumn != null ? " KEY(" + conflictColumn + ")" : "")
                    + " VALUES " + questionMarks.stream().map(s -> "(" + s + ")").collect(Collectors.joining(","));
            default -> throw new UnsupportedOperationException(name() + " does not support UPSERT");
        };
    }

    public String UpsertExcludedReference(String column) {
        return switch (this) {
            case MYSQL, MARIADB -> column + " = VALUES(" + column + ")";
            case POSTGRESQL, SQLITE, H2, COCKROACHDB  -> column + " = excluded." + column;
            default -> throw new UnsupportedOperationException(name() + " does not support UPSERT");
        };
    }

}

