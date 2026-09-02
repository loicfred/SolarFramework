package org.solarframework.db.test.transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.solarframework.db.spring.DatabaseService;
import org.solarframework.db.spring.JpaSourceRegistrar;
import org.solarframework.db.test.SolarH2Test;
import org.solarframework.db.test.obj.Order;
import org.solarframework.db.test.obj.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.solarframework.db.spring.DatabaseRegistry.SolarDBManager;

@SolarH2Test
class InverseAssociationWiringTest {

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
        SolarDBManager.getServiceByEntity(Order.class).doUpdate(Order.class, "DELETE FROM orders");
        SolarDBManager.getServiceByEntity(User.class).doUpdate(User.class, "DELETE FROM user");

        User u = new User(801L, "Owner", "owner@example.com");
        u.Upsert();
        new Order(80101L, u, "Widget", 1).Upsert();
    }

    @Test
    void gettingUserThroughACollectionElementInsideATransactionNeedsNoExtraQuery() {
        Boolean same = tx.execute(status -> {
            User u = SolarDBManager.getById(User.class, 801L).orElseThrow();
            Order first = u.getOrders().getFirst();
            return first.getUser() == u;
        });
        assertTrue(same, "order.user must resolve to the exact owner instance while the collection loads, inside a transaction");
    }

    @Test
    void reverseOrderStillMatches_orderFirstThenUser() {
        Boolean same = tx.execute(status -> {
            Order first = SolarDBManager.getById(Order.class, 80101L).orElseThrow();
            User viaAssociation = first.getUser();
            User viaGetById = SolarDBManager.getById(User.class, 801L).orElseThrow();
            return viaAssociation == viaGetById;
        });
        assertTrue(same, "fetching the child first, then the owner, must still resolve to the same managed instance");
    }

    @Test
    void collectionElementsThemselvesMatchDirectReads() {
        Boolean same = tx.execute(status -> {
            User u = SolarDBManager.getById(User.class, 801L).orElseThrow();
            Order fromCollection = u.getOrders().getFirst();
            Order fromDirectRead = SolarDBManager.getById(Order.class, 80101L).orElseThrow();
            return fromCollection == fromDirectRead;
        });
        assertTrue(same, "an Order reached through the collection must be the same managed instance as one reached directly by id");
    }
}
