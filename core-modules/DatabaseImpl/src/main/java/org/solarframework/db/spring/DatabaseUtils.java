package org.solarframework.db.spring;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import org.hibernate.annotations.View;
import org.springframework.stereotype.Component;

import org.solarframework.core.util.ClassUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class DatabaseUtils {

    /** Runs read-only work with a short-lived EntityManager bound to THIS service's data source. */
    protected static <R> R withEm(DatabaseService session, Function<EntityManager, R> work) {
        try (EntityManager em = session.getSessionFactory().createEntityManager()) {
            return work.apply(em);
        }
    }

    /** Runs write work inside an explicit transaction on THIS service's data source. */
    protected static <R> R inTransaction(DatabaseService session, Function<EntityManager, R> work) {
        try (EntityManager em = session.getSessionFactory().createEntityManager()) {
            em.getTransaction().begin();
            try {
                R result = work.apply(em);
                em.getTransaction().commit();
                return result;
            } catch (RuntimeException e) {
                if (em.getTransaction().isActive()) em.getTransaction().rollback();
                throw e;
            }
        }
    }




    protected static class SQLCleaner {
        public String newSQL;
        public Object[] newParams;

        public SQLCleaner(String sql, List<Object> params) {
            fixNullParams(sql, params);
        }
        public SQLCleaner(String sql, Object[] params) {
            if (params == null) {
                newSQL = sql;
                return;
            }
            fixNullParams(sql, Arrays.asList(params));
        }

        public void fixNullParams(String sql, List<Object> params) {
            sql = sql.replaceAll("\\s*=\\s*\\?", "=?");

            StringBuilder newSql = new StringBuilder();
            List<Object> newParams = new ArrayList<>();

            int paramIndex = 0;
            int pos = 0;

            int wherePos = -1;
            {
                String upperSql = sql.toUpperCase();
                wherePos = upperSql.indexOf("WHERE ");
                if (wherePos == -1)
                    wherePos = upperSql.indexOf("WHERE\n");
                if (wherePos == -1)
                    wherePos = upperSql.indexOf("WHERE\t");
                if (wherePos == -1)
                    wherePos = upperSql.indexOf("WHERE"); // fallback
            }

            while (pos < sql.length()) {
                int qIndex = sql.indexOf("?", pos);
                if (qIndex == -1 || paramIndex >= params.size()) {
                    newSql.append(sql.substring(pos));
                    break;
                }

                newSql.append(sql, pos, qIndex);
                Object value = params.get(paramIndex);
                boolean isEqualsParam = false;

                // Only check for "=" before the "?" and if we are after WHERE
                int check = qIndex - 1;
                while (check >= 0 && Character.isWhitespace(sql.charAt(check))) check--;
                if (check >= 0 && sql.charAt(check) == '=') {
                    isEqualsParam = (qIndex > wherePos && wherePos != -1);
                }

                if (isEqualsParam && value == null) {
                    // Replace "= ?" with "IS NULL"
                    int lastEq = newSql.lastIndexOf("=");
                    if (lastEq != -1) newSql.deleteCharAt(lastEq);
                    newSql.append(" IS NULL");
                } else {
                    newSql.append("?");
                    newParams.add(value);
                }

                pos = qIndex + 1;
                paramIndex++;
            }

            this.newSQL = newSql.toString();
            this.newParams = newParams.toArray();
        }
    }

    /** ClassGraph configured to scan exactly the given loaders. {@link ClassUtils#scannable} keeps overrideClassLoaders() working inside a Spring Boot fat jar. */
    public static ClassGraph scannerOf(Collection<ClassLoader> entityClassloaders) {
        return new ClassGraph().enableClassInfo().enableAnnotationInfo().overrideClassLoaders(ClassUtils.scannable(entityClassloaders));
    }

    public static void scanEntitiesOfLoaders(Collection<ClassLoader> entityClassloaders, Consumer<List<Class<?>>> consumer) {
        try (ScanResult scanResult = scannerOf(entityClassloaders).scan()) {
            List<Class<?>> L = new ArrayList<>();
            for (ClassInfo classInfo : scanResult.getClassesWithAnnotation(Entity.class).stream().filter(c -> !c.isAbstract()).toList()) {
                if (classInfo.extendsSuperclass(DatabaseObject.class)) {
                    L.add(classInfo.loadClass());
                }
            }
            consumer.accept(L);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void scanViewsOfLoaders(Collection<ClassLoader> entityClassloaders, Consumer<List<Class<?>>> consumer) {
        try (ScanResult scanResult = scannerOf(entityClassloaders).scan()) {
            List<Class<?>> L = new ArrayList<>();
            for (ClassInfo classInfo : scanResult.getClassesWithAnnotation(View.class).stream().filter(c -> !c.isAbstract()).toList()) {
                if (classInfo.extendsSuperclass(DatabaseObject.class)) {
                    L.add(classInfo.loadClass());
                }
            }
            consumer.accept(L);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}