package org.solarframework.db.test.transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.solarframework.db.spring.DatabaseService;
import org.solarframework.db.spring.JpaSourceRegistrar;
import org.solarframework.db.spring.TransactionResolver;
import org.solarframework.db.test.Database_Main;
import org.solarframework.db.test.SolarH2Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.solarframework.db.spring.DatabaseRegistry.SolarDBManager;

@SolarH2Test
class TransactionResolverTest {

    @Autowired
    ConfigurableApplicationContext context;
    DatabaseService source;

    @BeforeEach
    void setUp() {
        SolarDBManager.createAllSchemasIfMissing();
        source = (DatabaseService) SolarDBManager.getDefaultService();
        if (source.getJpaBeans() == null) JpaSourceRegistrar.register(source, context);
    }

    @Test
    void falseOutsideAnyTransaction() {
        assertFalse(TransactionResolver.isBound(source));
    }

    @Test
    void trueInsideATransactionBoundToThisSource() {
        TransactionTemplate tx = new TransactionTemplate(source.getJpaBeans().transactionManager());
        Boolean bound = tx.execute(status -> TransactionResolver.isBound(source));
        assertTrue(bound);
    }

    @Test
    void falseAgainAfterTheTransactionCompletes() {
        TransactionTemplate tx = new TransactionTemplate(source.getJpaBeans().transactionManager());
        tx.execute(status -> null);
        assertFalse(TransactionResolver.isBound(source), "the resource must be unbound once the transaction finishes, or every later static call would wrongly think one is active");
    }
}
