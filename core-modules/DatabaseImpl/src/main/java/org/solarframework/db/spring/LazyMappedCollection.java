package org.solarframework.db.spring;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * A collection that computes itself, exactly once, on first real access via the given loader - used to
 * replace a Hibernate-managed @OneToMany field outright at PostLoad time, rather than trying to hook
 * Hibernate's own collection self-faulting. That self-faulting is unreachable out-of-transaction (it runs
 * through a StatelessSession, which skips the entire event system - see
 * DBInstanceService#replaceInverseCollections for the full reasoning), so this sidesteps it entirely
 * instead of trying to catch it.
 *
 * <p>The proxy is declared over the field's own type rather than a hardcoded List, so Set, SortedSet,
 * List, Collection and Iterable fields all work - the same declared-type dispatch Hibernate itself does
 * when it picks PersistentSet/PersistentBag/PersistentSortedSet. Without this, a Set field failed at the
 * reflective Field#set with "Can not set java.util.Set field ... to jdk.proxy1.$ProxyN". The loader
 * always returns a List, since that is what the query path produces, and {@link #convert} adapts it.
 */
final class LazyMappedCollection {
    private LazyMappedCollection() {}

    /**
     * A field declared as a concrete class (ArrayList, HashSet, ...) cannot be proxied - Proxy only
     * implements interfaces - so it is loaded eagerly rather than left as Hibernate's own bag, which is
     * the thing that cannot fault out-of-transaction.
     */
    static Object of(Class<?> declaredType, Supplier<List<?>> loader) {
        if (!declaredType.isInterface()) return convert(declaredType, loader.get());
        Object[] backing = new Object[1];
        return Proxy.newProxyInstance(declaredType.getClassLoader(), new Class<?>[]{declaredType}, (proxy, method, args) -> {
            if (backing[0] == null) backing[0] = convert(declaredType, loader.get());
            try {
                return method.invoke(backing[0], args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        });
    }

    /**
     * SortedSet gets a TreeSet, which requires Comparable elements exactly as Hibernate's own
     * PersistentSortedSet does without an explicit comparator. Set gets a LinkedHashSet so the query's row
     * order survives de-duplication. List/Collection/Iterable are already satisfied by the loaded list.
     */
    private static Object convert(Class<?> type, List<?> loaded) {
        if (SortedSet.class.isAssignableFrom(type)) return new TreeSet<>(loaded);
        if (Set.class.isAssignableFrom(type)) return new LinkedHashSet<>(loaded);
        return type.isInstance(loaded) ? loaded : new ArrayList<>(loaded);
    }
}
