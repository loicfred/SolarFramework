package org.solarframework.db.test;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;
import org.solarframework.db.test.obj.User;
import org.solarframework.db.spring.SolarRegistryListener;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.solarframework.db.spring.DatabaseRegistry.SolarDBManager;

/**
 * The identity guarantees {@link DatabaseIdentityTest} cannot check on H2: {@code Write/UpsertThenReturn}
 * read the row back with {@code RETURNING *}, which H2 has no syntax for.
 *
 * <p>Runs on the MariaDB of {@code src/test/resources/application.properties} (no property overrides here,
 * unlike the H2 tests) and disables itself when that server is unreachable, so the build never depends on it.
 * It only ever touches its own ids.
 *
 * <p>The one class in this module on a second context, hence the only reason {@link SolarRegistryListener}
 * has to exist: the static {@link org.solarframework.db.spring.DatabaseRegistry} would otherwise still point
 * here for every {@link SolarH2Test} class that runs afterwards in the same JVM.
 */
@SpringBootTest(classes = Database_Main.class)
@TestExecutionListeners(value = SolarRegistryListener.class, mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIf("serverIsUp")
class DatabaseIdentityRemoteTest {

    private static final long ID = 9001L;

    static boolean serverIsUp() {
        try (InputStream in = DatabaseIdentityRemoteTest.class.getResourceAsStream("/application.properties")) {
            Properties p = new Properties();
            p.load(in);
            DriverManager.setLoginTimeout(3);
            try (Connection c = DriverManager.getConnection(p.getProperty("spring.datasource.url"), p.getProperty("spring.datasource.username"), p.getProperty("spring.datasource.password"))) {
                return c.isValid(3);
            }
        } catch (Exception e) {
            return false;
        }
    }

    @BeforeAll
    void createSchema() {
        SolarDBManager.createAllSchemasIfMissing();
        SolarDBManager.verifyEntities();
    }

    @BeforeEach
    @AfterAll
    void cleanOwnRows() {
        SolarDBManager.getServiceByEntity(User.class).doUpdate(User.class, "DELETE FROM user WHERE ID = ?", ID);
        SolarDBManager.resetAllCaches();
    }

    @Test
    void writeThenReturnRefreshesTheObjectItWrote() {
        User u = new User(ID, "Loic", "loic@gmail.com");
        assertSame(u, u.WriteThenReturn().orElseThrow(), "RETURNING * must refresh this object, not build a second one");
        assertNotNull(u.getCreatedAt(), "and the columns the database filled in must land on it");
    }

    @Test
    void upsertThenReturnRefreshesTheObjectItWrote() {
        new User(ID, "Loic", "loic@gmail.com").Upsert();

        User second = new User(ID, "Renamed", "renamed@example.com");
        assertSame(second, second.UpsertThenReturn().orElseThrow(), "the upserting object must be the one handed back");
        assertEquals("Renamed", SolarDBManager.getById(User.class, ID).orElseThrow().getName());
        assertSame(second, SolarDBManager.getById(User.class, ID).orElseThrow(), "and it must stay canonical afterwards");
    }
}
