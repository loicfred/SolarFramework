package org.solarframework.db.test;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.solarframework.db.spring.DatabaseObject;
import org.solarframework.db.test.obj.Order;
import org.solarframework.db.test.obj.User;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.solarframework.db.spring.DatabaseRegistry.SolarDBManager;

/**
 * Lazy blob columns, on the same in-memory H2 database as {@link DatabaseCoherencyTest}.
 *
 * <p>A {@code byte[]} column must not ride along on every read - one list of users would pull every avatar
 * into memory - but it must not be lost either: the read leaves the field on the {@code Lazy.UNLOADED}
 * sentinel, the getter fetches it on demand, and every write skips the columns still holding that sentinel
 * so an unread blob is never overwritten with NULL.
 */
@SpringBootTest(classes = Database_Main.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:solartest;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER",
        "spring.datasource.username=sa",
        "spring.datasource.password=test",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=none"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DatabaseBlobTest {

    private static final byte[] IMG = readImage();
    private static final byte[] OTHER = Arrays.copyOf(IMG, 64);

    private static byte[] readImage() {
        try (InputStream in = DatabaseBlobTest.class.getResourceAsStream("/TestImg.png")) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @BeforeAll
    void createSchema() {
        SolarDBManager.createAllSchemasIfMissing();
        SolarDBManager.verifyEntities();
    }

    @BeforeEach
    void cleanDatabase() {
        SolarDBManager.getServiceByEntity(Order.class).doUpdate(Order.class, "DELETE FROM orders");
        SolarDBManager.getServiceByEntity(User.class).doUpdate(User.class, "DELETE FROM user");
        SolarDBManager.resetAllCaches();
    }

    /** Writes a user carrying the image, then forgets everything about it - the next read starts from the row. */
    private void storeUserWithAvatar(long id) {
        User u = new User(id, "Loic", "loic@gmail.com");
        u.setAvatar(IMG);
        assertEquals(1, u.Upsert());
        SolarDBManager.resetAllCaches();
    }

    private User fetchUser(long id) {
        return SolarDBManager.getById(User.class, id).orElseThrow();
    }

    /** Reads the column behind the framework's back, so an assertion never depends on the lazy path it is testing. */
    private byte[] storedAvatar(long id) {
        return SolarDBManager.getServiceByEntity(User.class).doQueryValueNoCache(byte[].class, "SELECT Avatar FROM user WHERE ID = ?", id).orElse(null);
    }

    // ====== READING ======

    @Test
    void readsDoNotCarryTheBlob() {
        storeUserWithAvatar(1);

        assertFalse(fetchUser(1).isAvatarLoaded(), "getById must leave the avatar behind");
        SolarDBManager.resetAllCaches();
        assertFalse(SolarDBManager.getWhere(User.class, "name = ?", "Loic").orElseThrow().isAvatarLoaded(), "and so must getWhere");
        SolarDBManager.resetAllCaches();
        assertFalse(SolarDBManager.getAll(User.class).getFirst().isAvatarLoaded(), "and getAll, which is the one that would flood memory");
    }

    @Test
    void theGetterFetchesTheBytesOnDemand() {
        storeUserWithAvatar(1);
        User u = fetchUser(1);

        assertArrayEquals(IMG, u.getAvatar(), "asking for the blob must go and get it");
        assertTrue(u.isAvatarLoaded(), "and keep it, so a second call costs nothing");
        assertArrayEquals(IMG, u.getAvatar());
    }

    /** A column that is NULL in the row must come back as loaded-NULL, or every call would query again. */
    @Test
    void anEmptyBlobLoadsOnceAsNull() {
        new User(1L, "Loic", "loic@gmail.com").Upsert();
        SolarDBManager.resetAllCaches();

        User u = fetchUser(1);
        assertNull(u.getAvatar());
        assertTrue(u.isAvatarLoaded(), "fetched-and-NULL is a loaded state, not a reason to query again");
    }

    /** The escape hatch: a caller who explicitly selects every column gets the bytes with the row. */
    @Test
    void anExplicitFullSelectCarriesTheBlob() {
        storeUserWithAvatar(1);

        User u = SolarDBManager.getById("*", User.class, 1L).orElseThrow();
        assertTrue(u.isAvatarLoaded(), "SELECT * means everything, blobs included");
        assertArrayEquals(IMG, u.getAvatar());
    }

    /** Identity map: a blob-less read refreshes the object in place, and must not blank the bytes it already holds. */
    @Test
    void aLoadedBlobSurvivesTheNextRead() {
        storeUserWithAvatar(1);
        User u = fetchUser(1);
        assertArrayEquals(IMG, u.getAvatar());

        assertSame(u, fetchUser(1), "still the same object");
        assertTrue(u.isAvatarLoaded(), "and a read that never touched the column must not unload it");
        assertArrayEquals(IMG, u.getAvatar());
    }

    // ====== WRITING ======

    @Test
    void insertStoresTheBlob() {
        User u = new User(1L, "Loic", "loic@gmail.com");
        u.setAvatar(IMG);
        assertEquals(1, u.Write());

        assertArrayEquals(IMG, storedAvatar(1));
    }

    @Test
    void updateReplacesALoadedBlob() {
        storeUserWithAvatar(1);

        User u = fetchUser(1);
        u.setAvatar(OTHER);
        assertEquals(1, u.Update());
        assertArrayEquals(OTHER, storedAvatar(1));

        u.setAvatar(IMG);
        assertEquals(1, u.UpdateOnly("Avatar"));
        assertArrayEquals(IMG, storedAvatar(1));
    }

    /** The whole point: writing an object that never read its blob must leave the stored bytes alone. */
    @Test
    void updateDoesNotWipeAnUnloadedBlob() {
        storeUserWithAvatar(1);

        User u = fetchUser(1);
        u.setName("Renamed");
        assertEquals(1, u.Update());

        assertEquals("Renamed", fetchUser(1).getName(), "the rest of the row still updates");
        assertArrayEquals(IMG, storedAvatar(1), "and the untouched avatar is still there");
    }

    @Test
    void upsertDoesNotWipeAnUnloadedBlob() {
        storeUserWithAvatar(1);

        User u = fetchUser(1);
        u.setEmail("renamed@example.com");
        u.Upsert();

        assertEquals("renamed@example.com", fetchUser(1).getEmail());
        assertArrayEquals(IMG, storedAvatar(1), "on-conflict must not set the avatar to the NULL it never carried");
    }

    /** A hand-built object never loaded anything either - it must not blank the column it knows nothing about. */
    @Test
    void writingAHandBuiltObjectDoesNotWipeTheBlob() {
        storeUserWithAvatar(1);

        new User(1L, "Rebuilt", "rebuilt@example.com").Upsert();

        assertEquals("Rebuilt", fetchUser(1).getName());
        assertArrayEquals(IMG, storedAvatar(1));
    }

    /** Batches share one column list across rows, so they never carry a blob - and must not null one out either. */
    @Test
    void batchWritesLeaveStoredBlobsAlone() {
        storeUserWithAvatar(1);
        storeUserWithAvatar(2);

        User u1 = fetchUser(1);
        User u2 = fetchUser(2);
        u2.setAvatar(OTHER); // even a loaded one: the batch cannot say "this row only"
        u1.setName("A");
        u2.setName("B");
        DatabaseObject.UpsertAll(List.of(u1, u2));

        assertEquals("A", fetchUser(1).getName());
        assertEquals("B", fetchUser(2).getName());
        assertArrayEquals(IMG, storedAvatar(1));
        assertArrayEquals(IMG, storedAvatar(2), "a batch leaves the column as it was - write it with UpdateOnly");
    }
}
