package org.solarframework.core.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ClassUtils {

    /**
     * Makes a ClassLoader scannable by ClassGraph inside a Spring Boot executable jar.
     * <p>Boot 3.2+ hands out its classpath as {@code jar:nested:/app.jar/!BOOT-INF/lib/x.jar!/} URLs; ClassGraph has no
     * handler for the {@code nested:} scheme, so {@code overrideClassLoaders(launchedClassLoader)} scans zero classes and
     * every annotated class silently disappears - while the exact same code works from the IDE. Rewriting the URLs to the
     * classic {@code jar:file:/app.jar!/BOOT-INF/lib/x.jar!/} form fixes it. The real loader stays as parent, so
     * {@code ClassInfo.loadClass()} still returns the application's own Class objects.
     */
    public static ClassLoader scannable(ClassLoader cl) {
        if (!(cl instanceof URLClassLoader u)) return cl;
        URL[] urls = u.getURLs();
        if (Arrays.stream(urls).noneMatch(x -> x.toString().contains("nested:"))) return cl;
        return new URLClassLoader(Arrays.stream(urls).map(ClassUtils::unnest).toArray(URL[]::new), cl);
    }
    public static ClassLoader[] scannable(java.util.Collection<ClassLoader> cls) {
        return cls.stream().map(ClassUtils::scannable).toArray(ClassLoader[]::new);
    }
    private static URL unnest(URL u) {
        String s = u.toString();
        if (!s.contains("nested:")) return u;
        s = s.replace("nested:", "file:");
        int i = s.indexOf("/!");
        if (i >= 0) s = s.substring(0, i) + "!/" + s.substring(i + 2);
        try { return URI.create(s).toURL(); } catch (Exception e) { return u; }
    }

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
    public static <A extends Annotation> A getAnnotationRecursive(Class<?> clazz, Class<A> annotationClass) {
        if (clazz == null || clazz == Object.class) return null;
        A annotation = clazz.getAnnotation(annotationClass);
        if (annotation != null) return annotation;
        return getAnnotationRecursive(clazz.getSuperclass(), annotationClass);
    }
}
