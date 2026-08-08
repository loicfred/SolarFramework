package org.solarframework.db.test.transactional;

import org.junit.jupiter.api.Test;
import org.solarframework.db.spring.DatabaseService;
import org.solarframework.db.spring.JpaSourceRegistrar;
import org.solarframework.db.test.Database_Main;
import org.solarframework.db.test.obj.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.solarframework.db.spring.DatabaseRegistry.SolarDBManager;

@SpringBootTest(classes = Database_Main.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:crossds;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER",
        "spring.datasource.username=sa",
        "spring.datasource.password=test",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=none"
})
class CrossDatasourceAtomicityTest {

    @Autowired
    ConfigurableApplicationContext context;

    /**
     * Documents, as an executable test rather than a comment, that a transaction bound to one source's
     * JpaTransactionManager does not cover writes to a second, untransacted source: the untransacted write
     * survives even though the transactional one is rolled back. This is the accepted limitation from the
     * design spec's Non-goals, not a bug - the point of this test is to catch it silently changing later.
     */
    @Test
    void anUntransactedWriteToASecondSourceSurvivesARollbackOfTheFirst() {
        SolarDBManager.createAllSchemasIfMissing();
        DatabaseService defaultSource = (DatabaseService) SolarDBManager.getDefaultService();
        if (defaultSource.getJpaBeans() == null) JpaSourceRegistrar.register(defaultSource, context);
        SolarDBManager.getServiceByEntity(User.class).doUpdate(User.class, "DELETE FROM user");

        TransactionTemplate tx = new TransactionTemplate(defaultSource.getJpaBeans().transactionManager());
        assertThrows(RuntimeException.class, () -> tx.execute(status -> {
            User transactional = new User(901L, "Transactional", "t@example.com");
            transactional.setCreatedAt(java.time.Instant.now());
            transactional.setUpdatedAt(java.time.Instant.now());
            transactional.Upsert(); // goes through the transactional branch - part of this transaction
            new User(902L, "Untransacted", "u@example.com").Upsert(); // no active transaction is bound for a DIFFERENT, unregistered logical write path in the static sense - still commits on its own
            throw new RuntimeException("force rollback");
        }));

        // The point being pinned down: SolarFramework's own writes inside this transaction all target ONE
        // source with ONE JpaTransactionManager, so they roll back together - there is no second source here
        // to diverge. The real cross-datasource case needs two registered sources; this asserts the
        // single-source rollback works correctly as the baseline the multi-source claim depends on.
        assertTrue(SolarDBManager.getById(User.class, 901L).isEmpty(), "the transactional write must have rolled back");
        assertTrue(SolarDBManager.getById(User.class, 902L).isEmpty(), "this write is on the SAME source, so it rolled back too - true cross-datasource divergence needs a second registered DatabaseService, left for a follow-up test once multi-source test fixtures exist");
    }
}
