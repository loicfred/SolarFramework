package org.solarframework.db.spring;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Set;

import static org.solarframework.core.util.ClassUtils.*;

/**
 * One Java object per (connection, entity, id) - the first-level cache the framework has no persistence
 * context to provide. Reads run through an EntityManager closed with the query, so they used to hand back
 * a brand new graph every time: the entity you built and the one {@code getById} returned held the same
 * row without ever being {@code ==}. Writes register their object here and full-row reads go through
 * {@link #canonical}, so both sides end up on the one instance.
 *
 * <p>Two consequences: <b>last writer wins</b> - writing claims the row, and an instance read earlier
 * keeps its state but stops being handed out (an object with no complete id is never canonical); and
 * <b>reads refresh in place</b> - the freshly read state is copied onto the canonical object, overwriting
 * unsaved edits, associations included. Those come back as an unloaded Hibernate collection/proxy - it
 * self-faults on first touch under {@code enable_lazy_load_no_trans} regardless of whether the
 * EntityManager that read it is still open, which is the refresh a caller used to get from being handed a
 * new object. Values are held weakly, so an entry dies with the last reference to it.
 */
final class EntityIdentity {
    private static final Cache<String, Object> Instances = Caffeine.newBuilder().weakValues().build();

    private EntityIdentity() {}

    /** Null when the object carries no complete id - an unkeyed row cannot be canonical. */
    private static String keyOf(DatabaseService service, Object o) {
        Set<Field> ids = DBInstanceService.idFieldsOf(o.getClass());
        if (ids.isEmpty()) return null;
        StringBuilder key = new StringBuilder(o.getClass().getName()).append('@').append(service.getConnectionString().hashCode());
        for (Field f : ids) {
            Object v = getFieldValue(f, o);
            if (v == null) return null;
            key.append('/').append(v);
        }
        return key.toString();
    }

    /** Makes {@code o} the instance every later read of its row hands back. */
    static void register(DatabaseService service, Object o) {
        String key = o == null ? null : keyOf(service, o);
        if (key != null) Instances.put(key, o);
    }

    /** Drops the row - for hard deletes, where no instance should outlive it. */
    static void forget(DatabaseService service, Object o) {
        String key = o == null ? null : keyOf(service, o);
        if (key != null) Instances.invalidate(key);
    }

    static void clear() { Instances.invalidateAll(); }

    /** The instance already registered for this row, keyed the same way {@link #keyOf} would for a live object - or null if this service has not seen it yet. */
    static Object known(DatabaseService service, Class<?> clazz, Object id) {
        return id == null ? null : Instances.getIfPresent(clazz.getName() + '@' + service.getConnectionString().hashCode() + '/' + id);
    }

    /** Returns the instance callers already hold for this row, refreshed from {@code fresh}, or adopts {@code fresh} as that instance. */
    @SuppressWarnings("unchecked")
    static <T> T canonical(DatabaseService service, T fresh) {
        String key = fresh == null ? null : keyOf(service, fresh);
        if (key == null) return fresh;
        Object known = Instances.getIfPresent(key);
        if (known == null || known == fresh || known.getClass() != fresh.getClass()) {
            Instances.put(key, fresh);
            return fresh;
        }
        for (Field f : getSerializableFieldsOfClassFamily(fresh.getClass())) if (!Modifier.isFinal(f.getModifiers())) setFieldValue(f, known, getFieldValue(f, fresh));
        // byte[] stays out of the copy above on purpose - a blob-less read must not blank the bytes the canonical object already holds - but a read that did fetch them still has to carry them over.
        for (Field f : DBInstanceService.lazyFieldsOf(fresh.getClass())) if (!DBInstanceService.unloaded(f, fresh)) setFieldValue(f, known, getFieldValue(f, fresh));
        return (T) known;
    }
}
