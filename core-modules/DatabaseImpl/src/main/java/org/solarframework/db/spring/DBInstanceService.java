package org.solarframework.db.spring;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Transient;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.LazyInitializer;
import org.solarframework.db.api.DatabaseType;
import org.solarframework.db.api.IDBObjectService;
import org.solarframework.db.api.IDatabaseService;
import org.solarframework.db.api.Lazy;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.solarframework.core.util.ClassUtils.*;
import static org.solarframework.db.spring.DatabaseObject.columnOf;
import static org.solarframework.db.spring.DatabaseRegistry.SolarDBManager;
import static org.solarframework.json.JSONItem.SimpleGSON;

@SuppressWarnings("all")
public class DBInstanceService<T> implements IDBObjectService<T> {
    // ConcurrentHashMap, not HashMap: one of these is populated for every entity ever constructed, and consumers
    // routinely read on several threads at once - a concurrent computeIfAbsent during a resize can lose an entry
    // or spin a thread inside HashMap. None of the mapping functions below re-enter their own map, which is the
    // one thing CHM#computeIfAbsent will not tolerate.
    protected static final Map<String, Set<Field>> IdFields = new ConcurrentHashMap<>();
    protected static final Map<String, Set<Field>> CachedFields = new ConcurrentHashMap<>();
    protected static final Map<String, List<Field>> LazyFields = new ConcurrentHashMap<>();
    protected static final Map<String, List<Field>> ToOneFields = new ConcurrentHashMap<>();
    protected static final Map<String, List<Field>> OneToManyFields = new ConcurrentHashMap<>();

    protected transient DatabaseObject<T> dbObject;
    protected transient IDatabaseService dbService;
    protected transient Class<T> entityClass;
    protected transient String tableName;
    // No initialisers on these three: one of these services is built for every entity ever read, and the first
    // two are only ever the shared static sets - the initialisers were two throwaway HashMaps per row.
    // cacheHashes is built on demand for the same reason.
    protected transient Set<Field> cachedFields;
    protected transient Set<Field> idFields;
    protected transient Set<String> cacheHashes;

    public Set<String> getCacheHashes() {
        return cacheHashes == null ? cacheHashes = new HashSet<>() : cacheHashes;
    }

    public DBInstanceService(IDatabaseService service, DatabaseObject<T> obj) {
        this.dbService = service;
        this.dbObject = obj;
        this.entityClass = (Class<T>) dbObject.getClass();
        this.tableName = obj.getTableName();
    }

    /**
     * Resolved on first use, not in the constructor: it reads the live table columns, so computing it eagerly
     * meant that merely calling {@code new SomeEntity()} opened the pool and bootstrapped a SessionFactory.
     * DatabaseManager#reload instantiates every discovered entity (EntityInfo#Update) <i>before</i> assigning
     * the entity list, so that bootstrap mapped zero classes and was thrown away by the setEntities reload
     * moments later - a whole wasted Hibernate startup, pool included, on every boot.
     */
    protected Set<Field> cachedFields() {
        return cachedFields != null ? cachedFields : (cachedFields = CachedFields.computeIfAbsent(entityClass.getName(), c -> getAllFieldsOfClassFamily(entityClass).stream().filter(DBInstanceService::isPersisted).filter(f -> dbService.getTableStats(tableName).getColumnNames().contains(columnOf(f).toLowerCase())).collect(Collectors.toSet())));
    }

    /**
     * A field the database is allowed to own. A field a class keeps for itself can be named after a real column
     * and would be written as if it were one - which is how a {@code @Transient List} once reached a native
     * query as a parameter and failed with "No JDBC mapping could be inferred". Declared transient in either
     * sense, it is the object's own business and never a column.
     */
    private static boolean isPersisted(Field f) {
        return !f.isAnnotationPresent(Transient.class) && !Modifier.isTransient(f.getModifiers()) && !Modifier.isStatic(f.getModifiers());
    }
    protected Set<Field> idFields() {
        if (idFields == null) idFields = IdFields.computeIfAbsent(entityClass.getName(), c -> cachedFields().stream().filter(f -> f.isAnnotationPresent(Id.class)).collect(Collectors.toSet()));
        return idFields;
    }

    /** Makes this object the canonical instance of its row, so every later read hands it back instead of a copy. */
    private void remember() {
        if (dbService instanceof DatabaseService s) EntityIdentity.register(s, dbObject);
    }

    /** IdFields is only filled when an instance of a class exists; abstract @Entity roots (Tournament, Match...) never get an entry, so reads keyed on them must fall back to reflection. Not cached, to avoid seeding the map with an unfiltered set the constructor would then keep. */
    static Set<Field> idFieldsOf(Class<?> clazz) {
        Set<Field> known = IdFields.get(clazz.getName());
        return known != null ? known : getSerializableFieldsOfClassFamily(clazz).stream().filter(f -> f.isAnnotationPresent(Id.class)).collect(Collectors.toSet());
    }

    /**
     * The blob columns reads leave behind. Only {@code byte[]} qualifies: {@link Lazy#UNLOADED} is a
     * {@code byte[]} sentinel, so no other type can tell "never fetched" from "fetched and NULL" and any
     * other type must keep being read and written whole.
     */
    static List<Field> lazyFieldsOf(Class<?> clazz) {
        return LazyFields.computeIfAbsent(clazz.getName(), _ -> getAllFieldsOfClassFamily(clazz).stream().filter(DBInstanceService::isLazy).toList());
    }
    static boolean isLazy(Field f) {
        return f.getType() == byte[].class && !Modifier.isStatic(f.getModifiers()) && !Modifier.isTransient(f.getModifiers());
    }
    /** True when this object never loaded that blob - writing it would replace the stored bytes with NULL. */
    static boolean unloaded(Field f, Object obj) {
        return isLazy(f) && Lazy.unloaded((byte[]) getFieldValue(f, obj));
    }
    /** Drops the blobs out of an entity a read carried without them, so the getters fetch instead of reporting NULL. */
    static void markUnloaded(Object obj) {
        for (Field f : lazyFieldsOf(obj.getClass())) setFieldValue(f, obj, Lazy.UNLOADED);
    }

    static List<Field> toOneFieldsOf(Class<?> clazz) {
        return ToOneFields.computeIfAbsent(clazz.getName(), _ -> getAllFieldsOfClassFamily(clazz).stream().filter(f -> f.isAnnotationPresent(ManyToOne.class) || f.isAnnotationPresent(OneToOne.class)).toList());
    }

    /**
     * Redirects every to-one association on a freshly Hibernate-loaded entity onto the framework's own
     * canonical instance for that row - called from a {@code PostLoadEventListener}, after Hibernate is done
     * with the entity, so it is a plain field mutation with no Hibernate session involvement. A
     * {@code getEntity}/{@code lock} based Interceptor hook was tried first and rejected: it asks Hibernate
     * to adopt an object still attached to a different, already-closed EntityManager, which this framework
     * has one of per call, and Hibernate answers that with {@code DetachedObjectException}.
     *
     * <p>An already-initialized association (eager, or a lazy one some earlier load already touched) is
     * folded into the canonical instance directly. An untouched lazy proxy is swapped for the canonical
     * instance only when one is already registered - its id is available without initializing it - so a row
     * nothing has read yet stays a deferred proxy, exactly as {@code FetchType.LAZY} asked for; a row read
     * anywhere else already comes back as that same object instead of a second, disconnected one.
     */
    static void canonicalizeToOneAssociations(DatabaseService service, Object entity) {
        for (Field f : toOneFieldsOf(entity.getClass())) {
            Object value = getFieldValue(f, entity);
            if (value == null) continue;
            LazyInitializer lazy = HibernateProxy.extractLazyInitializer(value);
            if (lazy == null) setFieldValue(f, entity, EntityIdentity.canonical(service, value));
            else if (lazy.isUninitialized()) {
                Object known = EntityIdentity.known(service, lazy.getPersistentClass(), lazy.getIdentifier());
                if (known != null) setFieldValue(f, entity, known);
            } else setFieldValue(f, entity, EntityIdentity.canonical(service, lazy.getImplementation()));
        }
    }

    static List<Field> oneToManyFieldsOf(Class<?> clazz) {
        return OneToManyFields.computeIfAbsent(clazz.getName(), _ -> getAllFieldsOfClassFamily(clazz).stream().filter(f -> {
            OneToMany o2m = f.getAnnotation(OneToMany.class);
            return o2m != null && !o2m.mappedBy().isBlank();
        }).toList());
    }

    /**
     * Replaces every @OneToMany(mappedBy=...) collection on a freshly loaded entity with a lazy collection
     * of the field's own declared type (Set, List, Collection, ...) backed by the framework's own query
     * path, wiring each loaded child's back-reference directly to this entity -
     * the "for (Order o : orders) o.user = this" a getter would otherwise have to write by hand.
     *
     * <p>Called from PostLoad, which fires reliably whether or not a transaction is bound - unlike
     * Hibernate's own collection self-faulting, which runs through a StatelessSession out-of-transaction and
     * skips the entire event system (confirmed empirically - neither PostLoad nor INIT_COLLECTION reach that
     * path). Replacing the field outright, rather than trying to hook Hibernate's own bag, is exactly why
     * this reaches the case the earlier InitializeCollectionEventListener attempt could not.
     *
     * <p>This deliberately reverses the collections half of the framework's own prior decision (see
     * docs/superpowers/specs/2026-08-03-lazy-associations-design.md) to trust Hibernate's own bag
     * self-faulting rather than a custom mechanism - that decision was made without transaction-aware
     * identity in place yet, and did not need to solve back-reference wiring. The field is no longer a
     * Hibernate-tracked PersistentCollection afterward, so JPA-level collection cascade (persist/remove
     * propagating from a saved parent to elements added to this list) no longer applies - this framework's
     * own explicit per-object .Upsert()/.Update()/.Delete() calls are unaffected and remain how writes here
     * actually happen.
     */
    static void replaceInverseCollections(DatabaseService source, Object entity) {
        for (Field f : oneToManyFieldsOf(entity.getClass())) {
            OneToMany o2m = f.getAnnotation(OneToMany.class);
            Class<?> elementType = getGenericOf(f, 0);
            if (elementType == null) continue;
            Field backField = findFieldInClassFamily(elementType, o2m.mappedBy());
            if (backField == null) continue;
            JoinColumn joinColumn = backField.getAnnotation(JoinColumn.class);
            if (joinColumn == null) continue;

            Set<Field> ownerIdFields = idFieldsOf(entity.getClass());
            if (ownerIdFields.isEmpty()) continue;
            Object ownerId = getFieldValue(ownerIdFields.iterator().next(), entity);
            if (ownerId == null) continue;

            setFieldValue(f, entity, LazyMappedCollection.of(f.getType(), () -> {
                List<?> loaded = source.getAllWhere(elementType, joinColumn.name() + " = ?", ownerId);
                for (Object child : loaded) setFieldValue(backField, child, entity);
                return loaded;
            }));
        }
    }

    private Set<Field> getIdAndUniqueFields() {
        Set<Field> fs = cachedFields().stream().filter(f -> f.isAnnotationPresent(Id.class)).collect(Collectors.toSet());
        fs.addAll(getUniqueFields());
        return fs;
    }
    private Set<Field> getUniqueFields() {
        return cachedFields().stream().filter(f -> f.isAnnotationPresent(Column.class) && f.getAnnotation(Column.class).unique()).collect(Collectors.toSet());
    }

    public String getHashedIdentifier() {
        List<Object> ids = idFields().stream().map(f -> getFieldValue(f, dbObject)).toList();
        return entityClass.getName() + String.valueOf(ids.stream().map(Object::toString).collect(Collectors.joining("/")).hashCode());
    }

    public DatabaseType getType() {
        return dbService.getDatabaseType();
    }

    @Override
    public String toJSON() {
        return SimpleGSON.toJson(dbObject);
    }

    @Override
    public int Write() {
        try {
            InsertArgumentManager insertArgMgr = makeInsertManager(false, false);
            String sql = getType().Upsert(tableName, insertArgMgr.columns(), insertArgMgr.questionMarks(), null, null);
            return dbService.doUpdate(entityClass, sql, insertArgMgr.currentValuesList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to write object", e);
        }
    }
    @Override
    public Optional<T> WriteThenReturn() {
        try {
            InsertArgumentManager insertArgMgr = makeInsertManager(false, false);
            String sql = getType().Upsert(tableName, insertArgMgr.columns(), insertArgMgr.questionMarks(), null, null) + " RETURNING *";
            return dbService.doQueryNoCache(entityClass, sql, insertArgMgr.currentValuesList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to insert object", e);
        } finally {
            SolarDBManager.resetCacheForClass(entityClass, true, true);
        }
    }

    @Override
    public int Upsert() {
        return Upsert(null);
    }
    @Override
    public Optional<T> UpsertThenReturn() {
        return UpsertThenReturn(null);
    }
    @Override
    public int Upsert(List<String> conflictCols) {
        if (dbService instanceof DatabaseService ds && TransactionResolver.isBound(ds)) {
            TransactionalAccess.write(ds, dbObject, false);
            return 1;
        }
        try {
            InsertArgumentManager insertArgMgr = makeInsertManager(true, false);
            String sql = getType().Upsert(tableName, insertArgMgr.columns(), insertArgMgr.questionMarks(), insertArgMgr.duplicateKeyUpdateClause(), conflictCols == null || conflictCols.isEmpty() ? null : conflictCols.stream().collect(Collectors.joining(", ")));
            return dbService.doUpdate(entityClass, sql, insertArgMgr.currentValuesList());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to upsert object", e);
        }
    }
    @Override
    public Optional<T> UpsertThenReturn(List<String> conflictCols) {
        try {
            InsertArgumentManager insertArgMgr = makeInsertManager(true, false);
            String sql = getType().Upsert(tableName, insertArgMgr.columns(), insertArgMgr.questionMarks(), insertArgMgr.duplicateKeyUpdateClause(), conflictCols == null || conflictCols.isEmpty() ? null : conflictCols.stream().collect(Collectors.joining(", "))) + " RETURNING *";
            return dbService.doQueryNoCache(entityClass, sql, insertArgMgr.currentValuesList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to upsert object", e);
        } finally {
            SolarDBManager.resetCacheForClass(entityClass, true, true);
        }
    }


    @Override
    public int IncrementColumn(String column, int amount) {
        try {
            remember();
            for (Field f : cachedFields()) {
                if (columnOf(f).equalsIgnoreCase(column)) {
                    f.set(dbObject, (int) getFieldValue(f, dbObject) + amount);
                    break;
                }
            }

            String setClause = column + " = " + column + " + ?";
            List<Object> setValues = List.of(amount);

            String whereClause = idFields().stream().map(f -> columnOf(f) + " = ?").collect(Collectors.joining(" AND "));
            List<Object> whereValues = cleanParameterList(idFields().stream().map(ID -> getFieldValue(ID, dbObject)).collect(Collectors.toList()));

            List<Object> finalValues = new ArrayList<>();
            finalValues.addAll(setValues);
            finalValues.addAll(whereValues);

            String sql = "UPDATE " + tableName + " SET " + setClause + " WHERE " + whereClause;
            return dbService.doUpdate(entityClass, sql, finalValues.toArray());
        } catch (Exception e) {
            throw new RuntimeException("No ID field found in " + tableName + ".");
        }
    }
    @Override
    public int IncrementColumns(Map<String, Number> parameters) {
        try {
            remember();
            String setClause = parameters.entrySet().stream().map(f -> f.getKey() + " = " + f.getKey() + " + ?").collect(Collectors.joining(", "));
            List<Object> setValues = parameters.entrySet().stream().map(f -> f.getValue()).collect(Collectors.toList());

            String whereClause = idFields().stream().map(f -> columnOf(f) + " = ?").collect(Collectors.joining(" AND "));
            List<Object> whereValues = cleanParameterList(idFields().stream().map(ID -> getFieldValue(ID, dbObject)).collect(Collectors.toList()));

            List<Object> finalValues = new ArrayList<>();
            finalValues.addAll(setValues);
            finalValues.addAll(whereValues);

            String sql = "UPDATE " + tableName + " SET " + setClause + " WHERE " + whereClause;
            return dbService.doUpdate(entityClass, sql, finalValues.toArray());
        } catch (Exception e) {
            throw new RuntimeException("No ID field found in " + tableName + ".");
        }
    }


    @Override
    public int Update() {
        if (dbService instanceof DatabaseService ds && TransactionResolver.isBound(ds)) {
            TransactionalAccess.write(ds, dbObject, false);
            return 1;
        }
        try {
            remember();
            Set<Field> writable = cachedFields().stream().filter(f -> !unloaded(f, dbObject)).collect(Collectors.toSet());
            String setClause = writable.stream().map(f -> columnOf(f) + " = ?").collect(Collectors.joining(", "));
            List<Object> setValues = cleanParameterList(writable.stream().map(f -> getFieldValue(f, dbObject)).collect(Collectors.toList()));

            String whereClause = idFields().stream().map(f -> columnOf(f) + " = ?").collect(Collectors.joining(" AND "));
            List<Object> whereValues = cleanParameterList(idFields().stream().map(ID -> getFieldValue(ID, dbObject)).collect(Collectors.toList()));

            List<Object> finalValues = new ArrayList<>();
            finalValues.addAll(setValues);
            finalValues.addAll(whereValues);

            String sql = "UPDATE " + tableName + " SET " + setClause + " WHERE " + whereClause;
            return dbService.doUpdate(entityClass, sql, finalValues.toArray());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("No ID field found in " + tableName + ".");
        }
    }
    @Override
    public int UpdateOnly(String... columns) {
        try {
            List<String> cols = new ArrayList<>(Arrays.asList(columns));
            if (dbObject instanceof DatabaseObject.RECORD_OBJ<T> && !cols.contains("UpdatedAt")) cols.add("UpdatedAt");

            Set<Field> fieldsList = cachedFields().stream().filter(f -> !unloaded(f, dbObject)).filter(f -> cols.stream().anyMatch(c -> columnOf(f).equalsIgnoreCase(c))).collect(Collectors.toSet());
            if (fieldsList.isEmpty()) return 0;
            remember();

            String setClause = fieldsList.stream().map(f -> columnOf(f) + " = ?").collect(Collectors.joining(", "));
            List<Object> setValues = cleanParameterList(fieldsList.stream().map(f -> getFieldValue(f, dbObject)).collect(Collectors.toList()));

            String whereClause = idFields().stream().map(f -> columnOf(f) + " = ?").collect(Collectors.joining(" AND "));
            List<Object> whereValues = cleanParameterList(idFields().stream().map(ID -> getFieldValue(ID, dbObject)).collect(Collectors.toList()));

            List<Object> finalValues = new ArrayList<>();
            finalValues.addAll(setValues);
            finalValues.addAll(whereValues);

            String sql = "UPDATE " + tableName + " SET " + setClause + " WHERE " + whereClause;
            return dbService.doUpdate(entityClass, sql, finalValues.toArray());
        } catch (Exception e) {
            throw new RuntimeException("No ID field found in " + tableName + ".");
        }
    }


    @Override
    public int Delete() {
        if (dbService instanceof DatabaseService ds && TransactionResolver.isBound(ds)) {
            TransactionalAccess.write(ds, dbObject, true);
            return 1;
        }
        try {
            if (dbService instanceof DatabaseService s) EntityIdentity.forget(s, dbObject); // hard delete: no instance should outlive the row
            List<Object> whereValues = cleanParameterList(idFields().stream().map(ID -> getFieldValue(ID, dbObject)).collect(Collectors.toList()));

            String sql = "DELETE FROM " + tableName + " WHERE " + idFields().stream().map(f -> columnOf(f) + " = ?").collect(Collectors.joining(" AND "));
            return dbService.doUpdate(entityClass, sql, whereValues.toArray());
        } catch (Exception e) {
            throw new RuntimeException("No ID field found in " + tableName + ".");
        }
    }


    @Override
    public <A> A refetchAttribute(String attributeName, Class<A> attributeType) {
        Field attribute = getAllFieldsOfClassFamily(entityClass).stream().filter(f -> f.getName().equalsIgnoreCase(attributeName)).findFirst().orElse(null);
        if (attribute == null) return null;
        String whereClause = idFields().stream().map(f -> columnOf(f) + " = ?").collect(Collectors.joining(" AND "));
        List<Object> whereValues = cleanParameterList(idFields().stream().map(ID -> getFieldValue(ID, dbObject)).collect(Collectors.toList()));
        A val = dbService.getSingleColumnOfTableWhere(columnOf(attribute), attributeType, entityClass, whereClause, whereValues.toArray()).orElse(null);
        setFieldValue(attribute,dbObject, val);
        return val;
    }

    @Override
    public DatabaseObject<T> getDBObject() {
        return dbObject;
    }


    protected record InsertArgumentManager(String columns, String questionMarks, String duplicateKeyUpdateClause, Object[] currentValuesList) {}
    protected InsertArgumentManager makeInsertManager(boolean update, boolean includeNullFields) {
        remember(); // the one point every insert/upsert path goes through, batches included - and before the read of a RETURNING *, so it refreshes this object instead of building a second one
        // A batch shares one column list across rows, which cannot say "leave this row's blob alone" - so it never carries one. Single writes carry the blobs this object actually loaded.
        Set<Field> writable = cachedFields().stream().filter(f -> includeNullFields ? !isLazy(f) : !unloaded(f, dbObject)).collect(Collectors.toSet());
        Set<Field> nonNullFields = writable.stream().filter(f -> includeNullFields || getFieldValue(f, dbObject) != null).collect(Collectors.toSet());

        String columnsSeparatedByComma = nonNullFields.stream().map(DatabaseObject::columnOf).collect(Collectors.joining(", "));
        String questionMarksSeparatedByComma = nonNullFields.stream().map(p -> "?").collect(Collectors.joining(", "));

        List<Object> currentValuesList = cleanParameterList(nonNullFields.stream().map(f -> getFieldValue(f, dbObject)).collect(Collectors.toList()));

        if (!update) return new InsertArgumentManager(columnsSeparatedByComma, questionMarksSeparatedByComma, null, currentValuesList.toArray());
        // Excluding the unwritten blobs here too: "col = excluded.col" on a column the insert never listed sets it to NULL.
        String duplicateKeyUpdateClause = writable.stream().map(f -> getType().UpsertExcludedReference(columnOf(f))).collect(Collectors.joining(", "));
        return new InsertArgumentManager(columnsSeparatedByComma, questionMarksSeparatedByComma, duplicateKeyUpdateClause, currentValuesList.toArray());
    }

    protected static List<Object> cleanParameterList(List<Object> currentValuesList) {
        for (Object c : currentValuesList.stream().filter(c -> c != null && c.getClass().isEnum()).toList()) currentValuesList.set(currentValuesList.indexOf(c), ((Enum<?>) c).name());
        return currentValuesList;
    }
}
