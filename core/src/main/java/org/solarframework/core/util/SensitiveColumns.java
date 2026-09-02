package org.solarframework.core.util;

import java.util.Collection;
import java.util.List;

/**
 * Which database columns must never leave the platform over an API, however they were configured to. This is decided
 * from the column's name rather than from a list of specific tables and columns, because such a list has to be updated
 * every time an entity gains a field and will one day be out of step with the schema it is meant to protect.
 */
public class SensitiveColumns {

    /** A column whose name contains any of these holds a secret. Kept deliberately blunt: refusing to serve a harmless column costs an administrator one inconvenience, serving a password hash costs everybody. */
    public static final List<String> NEVER_SERVED = List.of("password", "passwd", "pwd", "secret", "token", "credential", "apikey", "privatekey", "salt", "hash", "otp", "mfa", "2fa");

    /** Punctuation and case are dropped first, so ApiKey, api_key and API-KEY are one name. */
    public static boolean isSensitive(String columnName) {
        if (columnName == null) return false;
        String plain = columnName.toLowerCase().replaceAll("[^a-z0-9]", "");
        return NEVER_SERVED.stream().anyMatch(plain::contains);
    }
    /** The names a caller may be given, in the order they were asked for. */
    public static List<String> servable(List<String> columnNames) {
        return columnNames == null ? List.of() : columnNames.stream().filter(name -> !isSensitive(name)).toList();
    }
    /**
     * The same, less the columns holding stored files. Every way out of this platform asks for this one list - the
     * data API and the assistant's record tools both - because a rule about what must never be served is worth
     * exactly one copy: the day a second kind of secret is added to {@link #NEVER_SERVED}, every door closes at once.
     */
    public static List<String> servable(List<String> columnNames, Collection<String> binaryColumns) {
        return servable(columnNames).stream().filter(name -> binaryColumns == null || !binaryColumns.contains(name)).toList();
    }
}
