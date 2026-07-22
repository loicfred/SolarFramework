package org.solarframework.db.spring;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.solarframework.db.api.IEntityInfo;
import org.solarframework.db.api.dto.TableStats;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.solarframework.core.util.ClassUtils.getAllFieldsOfClassFamily;
import static org.solarframework.db.spring.DatabaseRegistry.SolarDBManager;

public class EntityInfo extends IEntityInfo {
    @JsonIgnore
    private transient Class<?> entityClass;


    public EntityInfo(Class<?> entityClass, String classLoaderKey) {
        super(entityClass, classLoaderKey);
        this.entityClass = entityClass;
    }

    @Override
    public void Update(Class<?> clazz) {
        this.entityClass = clazz;
        this.className = clazz.getName();
        this.simpleClassName = clazz.getSimpleName();
        this.tableName = DatabaseObject.getTableName(clazz);

        try {
            Constructor<?> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
        } catch (Exception _) {}

        relations.clear();
        fields.clear();
        for (Field f : getAllFieldsOfClassFamily(clazz)) {
            if (!Modifier.isStatic(f.getModifiers())) {
                if (f.getAnnotation(OneToMany.class) != null || f.getAnnotation(ManyToOne.class) != null || f.getAnnotation(OneToOne.class) != null || f.getAnnotation(ManyToMany.class) != null) {
                    relations.add(new Relation(f));
                } else if (f.getAnnotation(Column.class) != null) {
                    fields.add(new FieldInfo(f));
                }
            }
        }
    }

    @Override
    public Class<?> getEntityClass() {
        return entityClass;
    }
    @Override
    public TableStats getTableStats() {
        return SolarDBManager.getTableStats(entityClass);
    }
}
