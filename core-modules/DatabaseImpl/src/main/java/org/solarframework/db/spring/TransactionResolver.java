package org.solarframework.db.spring;

import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The one branch point every read/write entry point checks: is a transaction already bound, on this thread,
 * to this specific source's EntityManagerFactory? JpaTransactionManager binds its EntityManagerHolder keyed
 * by the EntityManagerFactory instance itself, so checking hasResource(emf) answers "is THIS source's
 * transaction active right now" - not "is any transaction active anywhere", which matters once more than one
 * source is registered.
 *
 * <p>Reads the raw {@code jpaBeans} field rather than calling {@code DatabaseService#getJpaBeans()}, which
 * lazily builds it - a source nothing has ever opened a transaction against has no beans yet, and simply
 * checking "is one bound" must not be what triggers building them.
 */
public final class TransactionResolver {
    private TransactionResolver() {}

    public static boolean isBound(DatabaseService source) {
        JpaSourceRegistrar.JpaSourceBeans beans = source.jpaBeans;
        return beans != null && TransactionSynchronizationManager.hasResource(beans.entityManagerFactory());
    }
}
