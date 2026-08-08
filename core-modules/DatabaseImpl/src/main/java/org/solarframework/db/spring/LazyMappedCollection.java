package org.solarframework.db.spring;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.Supplier;

/**
 * A List that computes itself, exactly once, on first real access via the given loader - used to replace a
 * Hibernate-managed @OneToMany field outright at PostLoad time, rather than trying to hook Hibernate's own
 * collection self-faulting. That self-faulting is unreachable out-of-transaction (it runs through a
 * StatelessSession, which skips the entire event system - see DBInstanceService#replaceInverseCollections
 * for the full reasoning), so this sidesteps it entirely instead of trying to catch it.
 */
final class LazyMappedCollection {
    private LazyMappedCollection() {}

    @SuppressWarnings("unchecked")
    static List<?> of(Supplier<List<?>> loader) {
        Object[] backing = new Object[1];
        return (List<?>) Proxy.newProxyInstance(List.class.getClassLoader(), new Class<?>[]{List.class}, (proxy, method, args) -> {
            if (backing[0] == null) backing[0] = loader.get();
            try {
                return method.invoke(backing[0], args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        });
    }
}
