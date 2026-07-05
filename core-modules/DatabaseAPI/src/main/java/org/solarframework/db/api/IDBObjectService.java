package org.solarframework.db.api;

import org.solarframework.db.spring.DatabaseObject;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface IDBObjectService<T> {

    Set<String> getCacheHashes();
    String getHashedIdentifier();

    String toJSON();

    int Write();

    Optional<T> WriteThenReturn();

    int Upsert();
    Optional<T> UpsertThenReturn();
    int Upsert(List<String> conflictCols);
    Optional<T> UpsertThenReturn(List<String> conflictCols);

    int IncrementColumn(String column, int amount);
    int IncrementColumns(Map<String, Double> parameters);
    int Update();
    int UpdateOnly(String... columns);

    int Delete();

    <A> A refetchAttribute(String attributeName, Class<A> attributeType);

    DatabaseObject<T> getDBObject();

}
