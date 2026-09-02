package org.solarframework.db.test.transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.solarframework.db.spring.DatabaseService;
import org.solarframework.db.spring.JpaSourceRegistrar;
import org.solarframework.db.spring.TransactionalAccess;
import org.solarframework.db.test.SolarH2Test;
import org.solarframework.db.test.obj.Order;
import org.solarframework.db.test.obj.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.solarframework.db.spring.DatabaseRegistry.SolarDBManager;

@SolarH2Test
class TransactionalAccessTest {

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

        User u = new User(501L, "Trans", "trans@example.com");
        u.setCreatedAt(Instant.now());
        u.setUpdatedAt(Instant.now());
        u.Upsert();
    }

    @Test
    void getByIdReturnsTheManagedRow() {
        User found = tx.execute(status -> TransactionalAccess.getById(source, User.class, 501L).orElseThrow());
        assertEquals("Trans", found.getName());
    }

    @Test
    void twoReadsInTheSameTransactionAreTheSameInstance() {
        Boolean same = tx.execute(status -> {
            User a = TransactionalAccess.getById(source, User.class, 501L).orElseThrow();
            User b = TransactionalAccess.getById(source, User.class, 501L).orElseThrow();
            return a == b;
        });
        assertTrue(same, "real JPA persistence-context identity must hold within one transaction");
    }

    @Test
    void getAllWhereFindsMatchingRows() {
        List<User> found = tx.execute(status -> TransactionalAccess.getAllWhere(source, User.class, "name = ?", "Trans"));
        assertEquals(1, found.size());
    }

    @Test
    void mutatingAManagedEntityFlushesOnCommitWithNoExplicitCall() {
        tx.execute(status -> {
            User u = TransactionalAccess.getById(source, User.class, 501L).orElseThrow();
            u.setName("Renamed by dirty-checking");
            return null; // no explicit write call anywhere in this transaction
        });

        User reread = tx.execute(status -> TransactionalAccess.getById(source, User.class, 501L).orElseThrow());
        assertEquals("Renamed by dirty-checking", reread.getName(), "commit must have auto-flushed the mutation with no explicit .Update() call");
    }
}
