package org.solarframework.db.spring;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.solarframework.db.api.IDBObjectService;
import org.solarframework.db.api.IDatabaseService;

import java.lang.annotation.Annotation;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.solarframework.db.spring.DatabaseRegistry.DefaultDBService;
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
        if (SolarDBManager == null && DefaultDBService == null) return 0;
        return getService().Write();
    }

    public Optional<T> WriteThenReturn() {
        onCreate();
        if (SolarDBManager == null && DefaultDBService == null) return Optional.of((T) this);
        return getService().WriteThenReturn();
    }

    public int Upsert() {
        onCreate();
        if (SolarDBManager == null && DefaultDBService == null) return 0;
        return getService().Upsert();
    }

    public Optional<T> UpsertThenReturn() {
        onCreate();
        if (SolarDBManager == null && DefaultDBService == null) return Optional.of((T) this);
        return getService().UpsertThenReturn();
    }

    public int IncrementColumn(String column, int amount) {
        onUpdate();
        if (SolarDBManager == null && DefaultDBService == null) return 0;
        return getService().IncrementColumn(column, amount);
    }

    public int IncrementColumns(Map<String, Number> parameters) {
        onUpdate();
        if (SolarDBManager == null && DefaultDBService == null) return 0;
        return getService().IncrementColumns(parameters);
    }

    public int Update() {
        onUpdate();
        if (SolarDBManager == null && DefaultDBService == null) return 0;
        return getService().Update();
    }

    public int UpdateOnly(String... columns) {
        onUpdate();
        if (SolarDBManager == null && DefaultDBService == null) return 0;
        return getService().UpdateOnly(columns);
    }

    public int Delete() {
        if (SolarDBManager == null && DefaultDBService == null) return 0;
        return getService().Delete();
    }

    public <A> A refetchAttribute(String attributeName, Class<A> attributeType) {
        return getService().refetchAttribute(attributeName, attributeType);
    }

    /**
     * Writes a whole list in one multi-row statement instead of one round trip per object - the batched
     * counterpart of {@link #Upsert()}, for the loops that would otherwise fire N queries mid-operation.
     *
     * <p>The lifecycle hook still runs per item, so {@code CreatedAt}/{@code UpdatedAt} stay correct. Every
     * item must be the same entity as the first one: the statement carries a single table and column list,
     * taken from the head of the list.
     *
     * @return rows affected, or 0 when there is no database configured (same contract as {@link #Upsert()})
     */
    public static int UpsertAll(List<? extends DatabaseObject<?>> items) {
        if (items == null || items.isEmpty()) return 0;
        for (DatabaseObject<?> o : items) o.onCreate();
        if (SolarDBManager == null && DefaultDBService == null) return 0;
        IDatabaseService S = retrieveServiceFor(items.getFirst().getClass());
        return (S == null ? DefaultDBService : S).UpsertBatch(items);
    }

    public String getTableName() {
        return getTableName(getClass());
    }
    public static String getTableName(Class<?> clazz) {
        return TableNames.computeIfAbsent(clazz.getName(), _ -> {
            Table annotation = getAnnotationRecursive(clazz, Table.class);
            if (annotation != null && !annotation.name().isEmpty()) return annotation.name().toLowerCase();
            return clazz.getSimpleName().toLowerCase();
        });
    }

    private static <A extends Annotation> A getAnnotationRecursive(Class<?> clazz, Class<A> annotationClass) {
        if (clazz == null || clazz == Object.class) return null;
        A annotation = clazz.getAnnotation(annotationClass);
        if (annotation != null) return annotation;
        return getAnnotationRecursive(clazz.getSuperclass(), annotationClass);
    }

    @PrePersist
    protected void onCreate() {}
    @PreUpdate
    protected void onUpdate() {}

    /**
     * Soft-deletable: {@link #Delete()} flags the row instead of removing it. getById/getAll/getWhere/
     * Count automatically exclude soft-deleted rows for every subclass (except getById, which still
     * finds them on purpose - see its javadoc). That filtering does NOT reach Hibernate-managed
     * {@code @OneToMany}/{@code @ManyToOne} association loading, since that query is generated by
     * Hibernate itself, not by this framework - and per Hibernate's own behavior, {@code @SQLRestriction}
     * declared on this mapped superclass is not inherited by concrete subclasses for that purpose.
     * Add {@code @SQLRestriction("DeletedAt IS NULL")} directly on a concrete entity if its relations
     * need to hide soft-deleted rows too.
     */
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

        /** Soft delete: flags the row as deleted instead of removing it. Reads filter these out except by ID - see {@code getById}. */
        @Override
        public int Delete() {
            setDeletedAt(Instant.now());
            return UpdateOnly("DeletedAt", "UpdatedAt");
        }

        /** Actually removes the row - subject to whatever FK constraints the schema defines. */
        public int TrueDelete() {
            return super.Delete();
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
