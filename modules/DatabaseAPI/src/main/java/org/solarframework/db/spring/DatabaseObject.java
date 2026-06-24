package org.solarframework.db.spring;

import jakarta.persistence.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.solarframework.db.api.IDBObjectService;
import org.solarframework.db.api.IDatabaseService;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public class DatabaseObject<T> {
    protected static final Map<Class<?>, IDatabaseService> serviceCache = new HashMap<>();
    protected static final Map<Class<?>, IDatabaseService.ENTITY<?>> entityServiceCache = new HashMap<>();
    private static final Logger log = LoggerFactory.getLogger(DatabaseObject.class);
    private transient IDBObjectService<T> service;

    public IDBObjectService<T> getService() {
        return service;
    }

    public static IDatabaseService retrieveServiceFor(Class<?> clazz) {
        return serviceCache.computeIfAbsent(clazz, _ -> {
            try {
                Class<?> databaseRegistry = Class.forName("org.solarframework.db.spring.DatabaseRegistry");
                Object SolarDBManager = databaseRegistry.getField("SolarDBManager").get(null);
                return ((IDatabaseService) SolarDBManager.getClass().getMethod("getService", Class.class).invoke(SolarDBManager, clazz));
            } catch (Exception ignored) {
                return null;
            }
        });
    }
    @SuppressWarnings("unchecked")
    public static <T> IDatabaseService.ENTITY<T> retrieveEntityServiceFor(Class<T> clazz) {
        IDatabaseService.ENTITY<?> C = entityServiceCache.computeIfAbsent(clazz, _ -> {
            try {
                Class<?> databaseRegistry = Class.forName("org.solarframework.db.spring.DatabaseRegistry");
                Object SolarDBManager = databaseRegistry.getField("SolarDBManager").get(null);
                return ((IDatabaseService.ENTITY<?>) SolarDBManager.getClass().getMethod("getEntityService", Class.class).invoke(SolarDBManager, clazz));
            } catch (Exception ignored) {
                return null;
            }
        });
        return (IDatabaseService.ENTITY<T>) C;
    }

    public DatabaseObject() {
        try {
            service = retrieveServiceFor(this.getClass()).makeObjectManager(this);
        } catch (NullPointerException ignored) {}
    }

    public String getHashedIdentifier() {
        return service.getHashedIdentifier();
    }

    public String toJSON() {
       return service.toJSON();
    }

    public int Write() {
        onCreate();
        return service.Write();
    }

    public Optional<T> WriteThenReturn() {
        onCreate();
        return service.WriteThenReturn();
    }

    public int Upsert() {
        onCreate();
        return service.Upsert();
    }

    public Optional<T> UpsertThenReturn() {
        onCreate();
        return service.UpsertThenReturn();
    }

    public int IncrementColumn(String column, int amount) {
        onUpdate();
        return service.IncrementColumn(column, amount);
    }

    public int IncrementColumns(Map<String, Double> parameters) {
        onUpdate();
        return service.IncrementColumns(parameters);
    }

    public int Update() {
        onUpdate();
        return service.Update();
    }

    public int UpdateOnly(String... columns) {
        onUpdate();
        return service.UpdateOnly(columns);
    }

    public int Delete() {
        return service.Delete();
    }

    public <A> A refetchAttribute(String attributeName, Class<A> attributeType) {
        return service.refetchAttribute(attributeName, attributeType);
    }

    @PrePersist
    protected void onCreate() {}
    @PreUpdate
    protected void onUpdate() {}

    @MappedSuperclass
    public static class ID_RECORD<T> extends DatabaseObject<T> {
        @Column(name = "CreatedAt", nullable = false)
        private Instant createdAt;
        @Column(name = "UpdatedAt", nullable = false)
        private Instant updatedAt;
        @Column(name = "DeletedAt")
        private Instant deletedAt;

        public Instant getCreatedAt() {
            return createdAt;
        }
        public Instant getUpdatedAt() {
            return updatedAt;
        }
        public Instant getDeletedAt() {
            return deletedAt;
        }

        public void setCreatedAt(Instant createdAt) {
            this.createdAt = createdAt;
        }
        public void setUpdatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
        }
        public void setDeletedAt(Instant deletedAt) {
            this.deletedAt = deletedAt;
        }

        @Override
        protected void onCreate() {
            updatedAt = Instant.now();
            if (createdAt == null) createdAt = updatedAt;
            super.onCreate();
        }
        @Override
        protected void onUpdate() {
            updatedAt = Instant.now();
            super.onUpdate();
        }

        protected ID_RECORD() {}
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
    @MappedSuperclass
    public static class ID_OBJ_RECORD<IDTYPE, T> extends ID_RECORD<T> {
        @Id
        @Column(name = "ID")
        public IDTYPE ID;

        public IDTYPE getID() {
            return ID;
        }
        public void setID(IDTYPE ID) {
            this.ID = ID;
        }

        protected ID_OBJ_RECORD() {}
    }
}
