package org.solarframework.db.api;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

public class DatabaseObject<T> {
    private static final Logger log = LoggerFactory.getLogger(DatabaseObject.class);
    private transient IDBObjectService<T> service;

    public IDBObjectService<T> getService() {
        return service;
    }

    public static IDatabaseService retrieveServiceFor(Class<?> clazz) {
        try {
            Class<?> databaseRegistry = Class.forName("org.solarframework.db.spring.DatabaseRegistry");
            Object SolarDBManager = databaseRegistry.getField("SolarDBManager").get(null);
            return ((IDatabaseService) SolarDBManager.getClass().getMethod("getService", Class.class).invoke(SolarDBManager, clazz));
        } catch (Exception ignored) {
            return null;
        }
    }

    public DatabaseObject() {
        try {
            service = retrieveServiceFor(this.getClass()).makeObjectManager(this);
        } catch (NullPointerException ignored) {
            log.warn("The entity {} doesn't have any linked data source. Operations will not be available.", this.getClass().getSimpleName());
        }
    }

    public String getHashedIdentifier() {
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

    public int IncrementColumns(Map<String, Double> parameters) {
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
