package org.solarframework.db;

import org.solarframework.db.api.DatabaseType;
import org.solarframework.db.spring.DatabaseManager;
import org.solarframework.db.spring.DatabaseService;
import org.solarframework.db.test.obj.Order;
import org.solarframework.db.test.obj.User;

import static org.solarframework.db.spring.DatabaseRegistry.SolarDBManager;

/**
 * The same script as {@link Database_Main}, with no application context anywhere - describing one source and handing
 * it to the manager is the whole of the setup, which is what a plugin carrying this module does too. Runs against an
 * in-memory H2 so it needs no server; entities are still found by the classpath scan the constructor triggers.
 */
public class Database_PlainMain {

    static void main(String[] args) {
        DatabaseService source = new DatabaseService();
        source.setConnectionString("jdbc:h2:mem:solarplain;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER");
        source.setUsername("sa");
        source.setPassword("test");
        source.setDatabaseType(DatabaseType.H2);
        new DatabaseManager(source);
        SolarDBManager.verifyEntities();

        User u = new User(1L, "Loic", "loic@gmail.com");
        u.Upsert();
        new Order(1L, u, "Steak", 1).Upsert();
        new Order(2L, u,"Egg").Upsert();
        System.err.println(u.getOrders());

        System.err.println("------");
        u = SolarDBManager.getById(User.class, 1).orElseThrow();
        System.err.println(u);
        System.err.println(u.getOrders().getFirst().getUser());
        System.err.println(u.getOrders().getLast().getUser());
        System.err.println(SolarDBManager.getById(Order.class, 1).orElseThrow().getUser());
    }
}
