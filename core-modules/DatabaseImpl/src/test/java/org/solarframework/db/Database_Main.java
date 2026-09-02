package org.solarframework.db;

import org.solarframework.db.test.obj.Order;
import org.solarframework.db.test.obj.User;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

import static org.solarframework.db.spring.DatabaseRegistry.SolarDBManager;

/**
 * Boot's own JDBC/JPA autoconfiguration is excluded: it would build a second, competing
 * DataSource/EntityManagerFactory/TransactionManager stack from spring.datasource.* properties,
 * leaving 2 PlatformTransactionManager beans and breaking plain unqualified @Transactional.
 * Every DataSource/EMF/TransactionManager in this framework is registered by JpaSourceRegistrar instead.
 * <p>{@link Database_PlainMain} is the same script with no application context at all, which is how the module
 * also ships inside a plugin.
 */
@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        DataJpaRepositoriesAutoConfiguration.class
})
@ComponentScan({"org.solarframework"})
public class Database_Main {

    static void main(String[] args) {
        SpringApplication.run(Database_Main.class, args);
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
