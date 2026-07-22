package org.solarframework.db.spring;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.solarframework.db.api.IDBObjectService;
import org.solarframework.db.api.IDatabaseService;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.solarframework.db.spring.DatabaseRegistry.SolarDBManager;

public class DatabaseObject<T> {
    protected static final Map<String, String> TableNames = new HashMap<>();
    protected static final Map<String, IDatabaseService> serviceCache = new HashMap<>();
    protected static final Map<String, IDatabaseService.ENTITY<?>> entityServiceCache = new HashMap<>();

    @JsonIgnore
    private transient IDBObjectService<T> service;

    public IDBObjectService<T> getService() {
        return service == null ? service = retrieveServiceFor(this.getClass()).makeObjectManager(this) : service;
    }

    public static IDatabaseService retrieveServiceFor(Class<?> clazz) {
        return serviceCache.computeIfAbsent(clazz.getName(), _ -> {
            try {
                return SolarDBManager.getServiceByEntity(clazz);
            } catch (Exception ignored) {
                return null;
            }
        });
    }
    @SuppressWarnings("unchecked")
    public static <T> IDatabaseService.ENTITY<T> retrieveEntityServiceFor(Class<T> clazz) {
        IDatabaseService.ENTITY<?> C = entityServiceCache.computeIfAbsent(clazz.getName(), _ -> {
            try {
                return SolarDBManager.getEntityService(clazz);
            } catch (Exception ignored) {
                return null;
            }
        });
        return (IDatabaseService.ENTITY<T>) C;
    }

    public DatabaseObject() {
        try {
            getService();
        } catch (NullPointerException ignored) {}
    }

    public String getHashedIdentifier() {
        return getService().getHashedIdentifier();
    }

    public String toJSON() {
       return getService().toJSON();
    }

    public int Write() {
        onCreate();
        return getService().Write();
    }

    public Optional<T> WriteThenReturn() {
        onCreate();
        return getService().WriteThenReturn();
    }

    public int Upsert() {
        onCreate();
        return getService().Upsert();
    }

    public Optional<T> UpsertThenReturn() {
        onCreate();
        return getService().UpsertThenReturn();
    }

    public int IncrementColumn(String column, int amount) {
        onUpdate();
        return getService().IncrementColumn(column, amount);
    }

    public int IncrementColumns(Map<String, Number> parameters) {
        onUpdate();
        return getService().IncrementColumns(parameters);
    }

    public int Update() {
        onUpdate();
        return getService().Update();
    }

    public int UpdateOnly(String... columns) {
        onUpdate();
        return getService().UpdateOnly(columns);
    }

    public int Delete() {
        return getService().Delete();
    }

    public <A> A refetchAttribute(String attributeName, Class<A> attributeType) {
        return getService().refetchAttribute(attributeName, attributeType);
    }

    public String getTableName() {
        return getTableName(getClass());
    }
    public static String getTableName(Class<?> clazz) {
        return TableNames.computeIfAbsent(clazz.getName(), _ -> {
            Table annotation = clazz.getAnnotation(Table.class);
            if (annotation != null && !annotation.name().isEmpty()) return annotation.name().toLowerCase();
            return clazz.getSimpleName().toLowerCase();
        });
    }

    @PrePersist
    protected void onCreate() {}
    @PreUpdate
    protected void onUpdate() {}

    @MappedSuperclass
    public static class RECORD_OBJ<T> extends DatabaseObject<T> {
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

        protected RECORD_OBJ() {}
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
    public static class ID_RECORD_OBJ<IDTYPE, T> extends RECORD_OBJ<T> {
        @Id
        @Column(name = "ID")
        public IDTYPE ID;

        public IDTYPE getID() {
            return ID;
        }
        public void setID(IDTYPE ID) {
            this.ID = ID;
        }

        protected ID_RECORD_OBJ() {}
    }
}
