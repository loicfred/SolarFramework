package org.solarframework.db.spring;

import org.solarframework.db.api.DatabaseType;

public class QueryTranslation {

    public static String QueryDatabaseStats(DatabaseType type) {
        return switch (type) {
            case MYSQL, MARIADB ->
                    """
                    SELECT SUM(TABLE_ROWS) AS total_rows
                    FROM information_schema.tables
                    WHERE table_schema = DATABASE()
                    AND TABLE_TYPE = 'BASE TABLE'
                    """;
            case POSTGRESQL ->
                    """
                    SELECT SUM(reltuples)::BIGINT AS total_rows
                    FROM pg_class
                    WHERE relkind = 'r'
                    """;
            case ORACLE ->
                    """
                    SELECT SUM(NUM_ROWS) AS total_rows
                    FROM USER_TABLES
                    """;
            case SQLSERVER ->
                    """
                    SELECT SUM(p.rows) AS total_rows
                    FROM sys.tables t
                    INNER JOIN sys.partitions p
                        ON t.object_id = p.object_id
                    WHERE p.index_id IN (0,1)
                    """;
            case SQLITE ->
                    """
                    SELECT SUM(
                        (SELECT COUNT(*) FROM sqlite_master m2)
                    ) AS total_rows
                    FROM sqlite_master m
                    WHERE type = 'table'
                    """;
            case H2 ->
                    """
                    SELECT SUM(ROW_COUNT_ESTIMATE)
                    FROM INFORMATION_SCHEMA.TABLES
                    WHERE TABLE_TYPE = 'TABLE'
                    """;
            case HSQLDB ->
                    """
                    SELECT SUM(TABLE_ROWS)
                    FROM INFORMATION_SCHEMA.SYSTEM_TABLES
                    WHERE TABLE_TYPE='TABLE'
                    """;
            case DERBY ->
                    """
                    SELECT SUM(CARDINALITY)
                    FROM SYS.SYSTABLESTATISTICS
                    """;
            case DB2 ->
                    """
                    SELECT SUM(CARD)
                    FROM SYSCAT.TABLES
                    WHERE TYPE = 'T'
                    """;
            case FIREBIRD ->
                    """
                    SELECT SUM(RDB$RECORD_COUNT)
                    FROM RDB$RELATIONS
                    WHERE RDB$VIEW_BLR IS NULL
                    """;
            case INFORMIX ->
                    """
                    SELECT SUM(nrows)
                    FROM systables
                    WHERE tabtype = 'T'
                    """;
            case COCKROACHDB ->
                    """
                    SELECT SUM(row_count)
                    FROM crdb_internal.table_row_statistics
                    """;
        };
    }


    public static String QueryCurrentDatabase(DatabaseType type) {
        return switch (type) {
            case POSTGRESQL, COCKROACHDB -> "SELECT current_schema()";
            case MYSQL, MARIADB -> "SELECT DATABASE()";
            case SQLSERVER -> "SELECT SCHEMA_NAME(SCHEMA_ID())";
            case ORACLE -> "SELECT SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') FROM DUAL";
            case SQLITE -> "SELECT 'main'";
            case H2 -> "SELECT SCHEMA()";
            case HSQLDB -> "CALL CURRENT_SCHEMA";
            case DERBY, DB2 -> "VALUES CURRENT SCHEMA";
            case FIREBIRD -> "SELECT CURRENT_USER FROM RDB$DATABASE";
            case INFORMIX -> "SELECT OWNER FROM SYSTABLES WHERE TABID = 1 LIMIT 1";
        };
    }
}
