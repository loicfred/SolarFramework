package org.solarframework.db.api;

import jakarta.persistence.*;
import org.solarframework.db.api.dto.TableStats;
import org.solarframework.db.spring.DatabaseObject;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.*;

public class IEntityInfo {
    protected String className;
    protected String simpleClassName;
    protected String tableName;
    protected final String classLoader;
    protected final Set<FieldInfo> fields = new java.util.HashSet<>();
    protected final Set<Relation> relations = new java.util.HashSet<>();


    protected IEntityInfo(Class<?> clazz, String classLoaderKey) {
        this.classLoader = classLoaderKey;
        Update(clazz);
    }

    public void Update(Class<?> clazz) {}

    public String getClassName() {
        return className;
    }
    public String getSimpleClassName() {
        return simpleClassName;
    }
    public String getTableName() {
        return tableName;
    }
    public String getClassLoader() {
        return classLoader;
    }
    public Set<FieldInfo> getFields() {
        return fields;
    }
    /** Reads leave blobs behind by default, but a caller naming its own select list steps around that - so it needs these. */
    public Set<String> getBinaryColumns() {
        return fields.stream().filter(FieldInfo::isBinary).map(FieldInfo::getColumnName).collect(java.util.stream.Collectors.toSet());
    }
    public Set<Relation> getRelations() {
        return relations;
    }
    public Class<?> getEntityClass() { return null;}
    public TableStats getTableStats() { return null;}

    public static class FieldInfo {
        private final String variableName;
        private final String columnName;
        private final String type;
        private final boolean primaryKey;
        private final boolean unique;
        private final boolean nullable;

        public FieldInfo(Field f) {
            variableName = f.getName();
            type = f.getType().getName();
            primaryKey = f.getAnnotation(Id.class) != null;
            unique = f.getAnnotation(Column.class) != null && f.getAnnotation(Column.class).unique();
            nullable = f.getAnnotation(Column.class) != null && f.getAnnotation(Column.class).nullable();
            // asked rather than repeated: @Column(nullable = false) with no name of its own reads as "" here, and as
            // the field's name everywhere that builds SQL - one rule, so a dictionary entry cannot disagree with a query
            columnName = DatabaseObject.columnOf(f);
        }

        public String getVariableName() {
            return variableName;
        }
        public String getColumnName() {
            return columnName;
        }
        public String getType() {
            return type;
        }
        /** A byte[] column, which is how every file this platform stores is held. {@code [B} is what the JVM calls that type. */
        public boolean isBinary() {
            return "[B".equals(type);
        }
        public boolean isPrimaryKey() {
            return primaryKey;
        }
        public boolean isUnique() {
            return unique;
        }
        public boolean isNullable() {
            return nullable;
        }
    }
    public static class Relation {
        private final String variableName;
        private final String foreignClassName;
        private final String relationType;

        public Relation(Field f) {
            variableName = f.getName();
            relationType = f.getAnnotation(ManyToOne.class) != null ? "ManyToOne" : f.getAnnotation(OneToMany.class) != null ? "OneToMany" : f.getAnnotation(OneToOne.class) != null ? "OneToOne" : "ManyToMany";
            if (Collection.class.isAssignableFrom(f.getType()) && f.getGenericType() instanceof ParameterizedType pt) {
                this.foreignClassName = pt.getActualTypeArguments()[0].getTypeName();
            } else {
                this.foreignClassName = f.getType().getName();
            }
        }

        public String getVariableName() {
            return variableName;
        }
        public String getForeignClassName() {
            return foreignClassName;
        }
        public String getRelationType() {
            return relationType;
        }
    }


    public static List<IEntityInfo> sortByDependency(Collection<IEntityInfo> entities) {
        Map<String, IEntityInfo> byName = new HashMap<>();
        entities.forEach(e -> byName.put(e.getClassName(), e));

        Map<String, Set<String>> deps = new HashMap<>();
        Map<String, Set<String>> dependents = new HashMap<>();
        for (IEntityInfo e : entities) {
            Set<String> d = new HashSet<>();
            for (IEntityInfo.Relation r : e.getRelations())
                if (("ManyToOne".equals(r.getRelationType()) || "OneToOne".equals(r.getRelationType()))
                        && byName.containsKey(r.getForeignClassName())
                        && !r.getForeignClassName().equals(e.getClassName())) // ignore self-reference
                    d.add(r.getForeignClassName());
            deps.put(e.getClassName(), d);
            d.forEach(t -> dependents.computeIfAbsent(t, k -> new HashSet<>()).add(e.getClassName()));
        }

        PriorityQueue<String> ready = new PriorityQueue<>();
        deps.forEach((name, d) -> { if (d.isEmpty()) ready.add(name); });

        List<IEntityInfo> sorted = new ArrayList<>(entities.size());
        while (!ready.isEmpty()) {
            String name = ready.poll();
            sorted.add(byName.get(name));
            for (String dep : dependents.getOrDefault(name, Set.of())) {
                Set<String> remaining = deps.get(dep);
                remaining.remove(name);
                if (remaining.isEmpty()) ready.add(dep);
            }
        }

        if (sorted.size() < entities.size()) entities.stream().filter(e -> !sorted.contains(e)).forEach(sorted::add);

        return sorted;
    }
}
