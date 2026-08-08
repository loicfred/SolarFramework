package org.solarframework.db.spring;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.orm.jpa.SharedEntityManagerCreator;

import java.util.List;
import java.util.Optional;

/**
 * Read/write access to one source's entities via its transaction-scoped shared EntityManager. Only meaningful
 * to call once TransactionResolver.isBound(source) is true - the shared EntityManager proxy resolves to
 * whatever real EntityManager is bound to the current thread for that source right now, so calling this with
 * no bound transaction throws rather than silently doing something unexpected.
 */
public final class TransactionalAccess {
    private TransactionalAccess() {}

    public static <T> Optional<T> getById(DatabaseService source, Class<T> clazz, Object id) {
        return Optional.ofNullable(sharedEm(source).find(clazz, id));
    }

    public static <T> Optional<T> getWhere(DatabaseService source, Class<T> clazz, String whereClause, Object... args) {
        List<T> results = getAllWhere(source, clazz, whereClause, args);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public static <T> List<T> getAll(DatabaseService source, Class<T> clazz) {
        return sharedEm(source).createQuery("SELECT e FROM " + clazz.getSimpleName() + " e", clazz).getResultList();
    }

    public static <T> List<T> getAllWhere(DatabaseService source, Class<T> clazz, String whereClause, Object... args) {
        TypedQuery<T> query = sharedEm(source).createQuery("SELECT e FROM " + clazz.getSimpleName() + " e WHERE " + toOrdinalParams(whereClause), clazz);
        for (int i = 0; i < args.length; i++) query.setParameter(i + 1, args[i]);
        return query.getResultList();
    }

    public static long count(DatabaseService source, Class<?> clazz) {
        return sharedEm(source).createQuery("SELECT COUNT(e) FROM " + clazz.getSimpleName() + " e", Long.class).getSingleResult();
    }

    public static void flush(DatabaseService source) {
        sharedEm(source).flush();
    }

    public static void write(DatabaseService source, Object entity, boolean remove) {
        EntityManager em = sharedEm(source);
        if (remove) {
            em.remove(em.contains(entity) ? entity : em.merge(entity));
        } else {
            if (!em.contains(entity)) em.merge(entity);
        }
        em.flush();
    }

    private static String toOrdinalParams(String jpql) {
        StringBuilder sb = new StringBuilder();
        int ordinal = 1;
        for (char c : jpql.toCharArray()) sb.append(c == '?' ? "?" + ordinal++ : c);
        return sb.toString();
    }

    private static EntityManager sharedEm(DatabaseService source) {
        return SharedEntityManagerCreator.createSharedEntityManager(source.getJpaBeans().entityManagerFactory());
    }
}
