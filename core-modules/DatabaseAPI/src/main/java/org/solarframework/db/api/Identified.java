package org.solarframework.db.api;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * An entity that carries an ID other rows point at - both {@code ID_OBJ} and {@code ID_RECORD_OBJ} do. Written so
 * that keying a list by its own ID is one method rather than the same one-liner on every entity that needs it.
 */
public interface Identified<IDTYPE> {

    IDTYPE getID();

    /** The rows keyed by their own ID, which is what a page resolves a foreign key against. */
    static <ID, T extends Identified<ID>> Map<ID, T> byId(List<T> rows) {
        return rows.stream().filter(row -> row.getID() != null).collect(Collectors.toMap(Identified::getID, row -> row, (first, second) -> first));
    }
}
