package org.solarframework.db.test.transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.solarframework.db.spring.DatabaseService;
import org.solarframework.db.spring.JpaSourceRegistrar;
import org.solarframework.db.test.Database_Main;
import org.solarframework.db.test.SolarH2Test;
import org.solarframework.db.test.obj.Order;
import org.solarframework.db.test.obj.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.solarframework.db.spring.DatabaseRegistry.SolarDBManager;

@SolarH2Test
class PublicApiTransactionalTest {

    @Autowired
    ConfigurableApplicationContext context;
    DatabaseService source;
    TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        SolarDBManager.createAllSchemasIfMissing();
        source = (DatabaseService) SolarDBManager.getDefaultService();
        if (source.getJpaBeans() == null) JpaSourceRegistrar.register(source, context);
        tx = new TransactionTemplate(source.getJpaBeans().transactionManager());
        SolarDBManager.getServiceByEntity(Order.class).doUpdate(Order.class, "DELETE FROM orders"); // child table first: every H2 class shares one schema, so rows another class left behind still hold the FK
        SolarDBManager.getServiceByEntity(User.class).doUpdate(User.class, "DELETE FROM user");

        User u = new User(701L, "Api", "api@example.com");
        u.setCreatedAt(Instant.now());
        u.setUpdatedAt(Instant.now());
        u.Upsert();
    }

    /**
     * Reads the name column directly, bypassing EntityIdentity and any JPA first-level cache entirely, so
     * this proves the DB row itself changed - not just that some in-memory object reflects its own mutation.
     */
    private String nameInDbNoCache() {
        return SolarDBManager.getServiceByEntity(User.class).doQueryValueNoCache(String.class, "SELECT name FROM user WHERE ID = ?", 701L).orElseThrow();
    }

    @Test
    void getByIdThroughSolarDBManagerDirtyChecksInsideATransaction() {
        tx.execute(status -> {
            User u = SolarDBManager.getById(User.class, 701L).orElseThrow();
            u.setName("Renamed via SolarDBManager");
            return null; // no explicit write call - this only proves anything if getById returned a managed entity
        });

        assertEquals("Renamed via SolarDBManager", nameInDbNoCache(), "the DB row itself must have changed - dirty-checking through the public getById entry point, not just an in-memory EntityIdentity mutation");
    }

    @Test
    void explicitUpdateInsideATransactionStaysConsistentWithDirtyChecking() {
        tx.execute(status -> {
            User u = SolarDBManager.getById(User.class, 701L).orElseThrow();
            u.setName("Renamed via explicit Update");
            u.Update();
            u.setEmail("changed@example.com"); // mutated AFTER the explicit call, with no further explicit call
            return null;
        });

        // both the explicitly-written column and the dirty-checked-only one must have persisted - if
        // .Update() had bypassed the JPA persistence context (e.g. by falling through to the old native-SQL
        // path even though a transaction was bound), the entity would no longer be the one Hibernate is
        // tracking, and the second, unflushed mutation would silently be lost.
        assertEquals("Renamed via explicit Update", nameInDbNoCache());
        String emailInDb = SolarDBManager.getServiceByEntity(User.class).doQueryValueNoCache(String.class, "SELECT email FROM user WHERE ID = ?", 701L).orElseThrow();
        assertEquals("changed@example.com", emailInDb);
    }

    @Test
    void staticUsageOutsideAnyTransactionIsUnchanged() {
        User u = SolarDBManager.getById(User.class, 701L).orElseThrow();
        u.setName("Static rename");
        // no explicit call, no transaction: must NOT persist, matching today's behavior
        assertEquals("Static rename", SolarDBManager.getById(User.class, 701L).orElseThrow().getName(), "static reads share the EntityIdentity instance, so the in-memory mutation is visible in-process");

        SolarDBManager.resetAllCaches();
        assertEquals("Api", SolarDBManager.getById(User.class, 701L).orElseThrow().getName(), "but nothing was ever written to the DB without an explicit call");
    }
}
