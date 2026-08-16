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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.solarframework.db.spring.DatabaseRegistry.SolarDBManager;

/**
 * DIAGNOSTIC ONLY - not part of the delivered feature set. Every other transactional test in this package
 * uses a manually-built TransactionTemplate wired directly to the registered JpaTransactionManager. This is
 * the first one that actually uses the plain @Transactional annotation on a Spring bean method, which
 * depends on Spring's OWN default PlatformTransactionManager bean resolution finding the dynamically
 * registerSingleton'd one - genuinely untested until now.
 */
@SolarH2Test
class AnnotationTransactionalTest {

    @Service
    static class RenameService {
        @Transactional
        public void renameWithNoExplicitCall(long id, String newName) {
            User u = SolarDBManager.getById(User.class, id).orElseThrow();
            u.setName(newName);
        }
    }

    @Autowired
    ConfigurableApplicationContext context;
    @Autowired
    RenameService renameService;

    @BeforeEach
    void setUp() {
        SolarDBManager.createAllSchemasIfMissing();
        DatabaseService source = (DatabaseService) SolarDBManager.getDefaultService();
        if (source.getJpaBeans() == null) JpaSourceRegistrar.register(source, context);
        SolarDBManager.getServiceByEntity(Order.class).doUpdate(Order.class, "DELETE FROM orders"); // child table first: every H2 class shares one schema, so rows another class left behind still hold the FK
        SolarDBManager.getServiceByEntity(User.class).doUpdate(User.class, "DELETE FROM user");

        User u = new User(1L, "Before", "before@example.com");
        u.setCreatedAt(Instant.now());
        u.setUpdatedAt(Instant.now());
        u.Upsert();
    }

    @Test
    void plainTransactionalAnnotationDirtyChecksWithNoManualTransactionTemplate() {
        renameService.renameWithNoExplicitCall(1L, "Renamed via @Transactional");

        String nameInDb = SolarDBManager.getServiceByEntity(User.class).doQueryValueNoCache(String.class, "SELECT name FROM user WHERE ID = ?", 1L).orElseThrow();
        assertEquals("Renamed via @Transactional", nameInDb, "the @Transactional annotation alone, with no TransactionTemplate anywhere, must have found the registered JpaTransactionManager and dirty-checked the mutation to the DB");
    }
}
