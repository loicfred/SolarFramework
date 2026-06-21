package org.solarframework.db.spring;

import jakarta.persistence.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.solarframework.db.api.IDBObjectService;
import org.solarframework.db.api.IDatabaseService;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class DatabaseObject<T> {
    protected static final Map<Class<?>, IDatabaseService> serviceCache = new HashMap<>();
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
        private LocalDateTime createdAt;
        @Column(name = "UpdatedAt", nullable = false)
        private LocalDateTime updatedAt;
        @Column(name = "DeletedAt")
        private LocalDateTime deletedAt;

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }
        public LocalDateTime getUpdatedAt() {
            return updatedAt;
        }
        public LocalDateTime getDeletedAt() {
            return deletedAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }
        public void setUpdatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
        }
        public void setDeletedAt(LocalDateTime deletedAt) {
            this.deletedAt = deletedAt;
        }

        @Override
        protected void onCreate() {
            updatedAt = LocalDateTime.now();
            if (createdAt == null) createdAt = updatedAt;
            super.onCreate();
        }
        @Override
        protected void onUpdate() {
            updatedAt = LocalDateTime.now();
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
