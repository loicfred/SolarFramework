package org.solarframework.core.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ClassUtils {

    public static Class<?> getMainClass() {
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            if (element.getMethodName().equals("main")) {
                try {
                    return Class.forName(element.getClassName());
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    public static List<Field> getAllFieldsOfClassFamily(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            fields.addAll(Arrays.stream(c.getDeclaredFields()).peek(f -> f.setAccessible(true)).toList());
        }
        Collections.reverse(fields);
        return fields;
    }
    public static List<Field> getSerializableFieldsOfClassFamily(Class<?> clazz) {
        return getAllFieldsOfClassFamily(clazz).stream().filter(f -> !Modifier.isTransient(f.getModifiers()) && !Modifier.isStatic(f.getModifiers()) && !f.getType().equals(byte[].class) && !f.getType().equals(Byte[].class)).toList();
    }
    public static List<Field> getFieldsWithAnnotation(Class<?> clazz, Class<? extends Annotation> annotationClass) {
        return getAllFieldsOfClassFamily(clazz).stream().filter(f -> f.getAnnotation(annotationClass) != null).toList();
    }
    public static Field findFieldInClassFamily(Class<?> clazz, String fieldName) {
        return getAllFieldsOfClassFamily(clazz).stream().filter(f -> f.getName().equals(fieldName)).findFirst().orElse(null);
    }

    public static boolean isClassRelated(Object obj, Object obj2) {
        return obj != null && obj2 != null && isClassRelated(obj.getClass(), obj2.getClass());
    }
    public static boolean isClassRelated(Object obj, Class<?> clazz) {
        return obj != null && isClassRelated(obj.getClass(), clazz);
    }
    public static boolean isClassRelated(Class<?> clazz1, Class<?> clazz2) {
        return clazz1 != null && clazz2 != null && (clazz1.isAssignableFrom(clazz2) || clazz2.isAssignableFrom(clazz1) || clazz1.equals(clazz2));
    }
    public static Class<?> getGenericOf(Field f, int index) {
        try {
            return (Class<?>) ((ParameterizedType) f.getGenericType()).getActualTypeArguments()[index];
        } catch (Exception ignored) { return null;}
    }

    public static Object getFieldValue(Field f, Object obj) {
        try {
            return f.get(obj);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
    public static void setFieldValue(Field f, Object obj, Object value) {
        try {
            f.set(obj, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static Object copyObject(Object target, Object source) {
        try {
            for (Field field : getAllFieldsOfClassFamily(target.getClass())) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                if (Modifier.isFinal(field.getModifiers())) continue;
                field.set(target, field.get(source));
            }
            return target;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

}
