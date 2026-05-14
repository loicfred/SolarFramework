package org.solarframework.db.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface IDBObjectService<T> {

    List<String> getCacheHashes();
    String getHashedIdentifier();

    String toJSON();

    int Write();

    Optional<T> WriteThenReturn();

    int Upsert();

    Optional<T> UpsertThenReturn();

    int IncrementColumn(String column, int amount);
    int IncrementColumns(Map<String, Double> parameters);
    int Update();
    int UpdateOnly(String... columns);

    int Delete();

    <A> A refetchAttribute(String attributeName, Class<A> attributeType);

    DatabaseObject<T> getDBObject();
}
