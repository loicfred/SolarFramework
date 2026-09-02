package org.solarframework.core.util;

import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClassUtilsTest {

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface Marked {}

    static class Base {
        public static int staticField = 1;
        public transient int transientField = 2;
        public byte[] rawBytes = new byte[0];
        @Marked
        public String markedField = "marked";
    }
    static class Child extends Base {
        public int childField = 3;
    }

    @Test
    void getAllFieldsOfClassFamilyIncludesInheritedFields() {
        List<String> names = ClassUtils.getAllFieldsOfClassFamily(Child.class).stream().map(Field::getName).toList();
        assertTrue(names.contains("childField"));
        assertTrue(names.contains("markedField"));
        assertTrue(names.contains("staticField"));
    }

    @Test
    void getSerializableFieldsOfClassFamilyExcludesStaticTransientAndByteArrayFields() {
        List<String> names = ClassUtils.getSerializableFieldsOfClassFamily(Child.class).stream().map(Field::getName).toList();
        assertTrue(names.contains("childField"));
        assertTrue(names.contains("markedField"));
        assertFalse(names.contains("staticField"));
        assertFalse(names.contains("transientField"));
        assertFalse(names.contains("rawBytes"));
    }

    @Test
    void getFieldsWithAnnotationFindsAnnotatedFieldsOnly() {
        List<Field> fields = ClassUtils.getFieldsWithAnnotation(Child.class, Marked.class);
        assertEquals(1, fields.size());
        assertEquals("markedField", fields.getFirst().getName());
    }

    @Test
    void findFieldInClassFamilyLocatesInheritedField() {
        Field f = ClassUtils.findFieldInClassFamily(Child.class, "markedField");
        assertNotNull(f);
        assertEquals("markedField", f.getName());
    }
    @Test
    void findFieldInClassFamilyReturnsNullWhenMissing() {
        assertNull(ClassUtils.findFieldInClassFamily(Child.class, "doesNotExist"));
    }

    @Test
    void isClassRelatedDetectsSubclassAndSuperclassRelations() {
        assertTrue(ClassUtils.isClassRelated(Child.class, Base.class));
        assertTrue(ClassUtils.isClassRelated(Base.class, Child.class));
        assertTrue(ClassUtils.isClassRelated(Base.class, Base.class));
        assertFalse(ClassUtils.isClassRelated(Child.class, String.class));
    }

    @Test
    void getFieldValueAndSetFieldValueRoundTrip() throws NoSuchFieldException {
        Field f = Child.class.getField("childField");
        Child c = new Child();
        assertEquals(3, ClassUtils.getFieldValue(f, c));
        ClassUtils.setFieldValue(f, c, 99);
        assertEquals(99, ClassUtils.getFieldValue(f, c));
    }

    @Test
    void copyObjectCopiesAllInstanceFields() {
        Child source = new Child();
        source.childField = 42;
        source.markedField = "copied";
        Child target = new Child();

        ClassUtils.copyObject(target, source);

        assertEquals(42, target.childField);
        assertEquals("copied", target.markedField);
    }

    @Test
    void isValidIdentifierFollowsJavaNamingRules() {
        assertTrue(ClassUtils.isValidIdentifier("MauCareers_Sector"));
        assertTrue(ClassUtils.isValidIdentifier("_x9"));
        assertFalse(ClassUtils.isValidIdentifier("9lives"));
        assertFalse(ClassUtils.isValidIdentifier("has space"));
        assertFalse(ClassUtils.isValidIdentifier(null));
    }

    @Test
    void isValidPackageChecksEverySegment() {
        assertTrue(ClassUtils.isValidPackage("org.maucareers.entities"));
        assertFalse(ClassUtils.isValidPackage("org..entities"));
        assertFalse(ClassUtils.isValidPackage(".org"));
        assertFalse(ClassUtils.isValidPackage(""));
        assertFalse(ClassUtils.isValidPackage(null));
    }

    @Test
    void packageExistsFindsLoadedPackagesAndTheirParents() {
        assertTrue(ClassUtils.packageExists("org.solarframework.core.util"));
        assertTrue(ClassUtils.packageExists("org.solarframework"));
        assertFalse(ClassUtils.packageExists("com.nowhere.at.all"));
    }
}
