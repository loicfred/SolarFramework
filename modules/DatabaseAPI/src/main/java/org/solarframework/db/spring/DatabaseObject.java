package org.solarframework.db.spring;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.solarframework.db.spring.Provider.dbService;

public class DatabaseObject<T> {
    private transient final IDBObjectService<T> service;

    public IDBObjectService<T> getService() {
        return service;
    }

    public DatabaseObject() {
        service = dbService.makeObjectManager(this);
    }

    protected String getHashedIdentifier() {
        return service.getHashedIdentifier();
    }

    public String toJSON() {
       return service.toJSON();
    }

    public int Write() {
        return service.Write();
    }

    public Optional<T> WriteThenReturn() {
        return service.WriteThenReturn();
    }

    public int Upsert() {
        return service.Upsert();
    }

    public Optional<T> UpsertThenReturn() {
        return service.UpsertThenReturn();
    }

    public int IncrementColumn(String column, int amount) {
        return service.IncrementColumn(column, amount);
    }

    public int IncrementColumns(Map<String, Object> parameters) {
        return service.IncrementColumns(parameters);
    }

    public int Update() {
        return service.Update();
    }

    public int UpdateOnly(String... columns) {
        return service.UpdateOnly(columns);
    }

    public int Delete() {
        return service.Delete();
    }

    public <A> A refetchAttribute(String attributeName, Class<A> attributeType) {
        return service.refetchAttribute(attributeName, attributeType);
    }

    @MappedSuperclass
    public static class ID_OBJ<IDTYPE, T> extends DatabaseObject<T> {
        @Id
        @Column(name = "ID")
        public IDTYPE ID;

        public IDTYPE getID() {
            return ID;
        }
        public void setID(IDTYPE ID) {
            this.ID = ID;
        }

        protected ID_OBJ() {}
    }
}
