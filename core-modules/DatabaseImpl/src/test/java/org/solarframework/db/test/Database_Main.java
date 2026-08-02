package org.solarframework.db.test;

import org.solarframework.db.test.obj.Order;
import org.solarframework.db.test.obj.User;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

import static org.solarframework.db.spring.DatabaseRegistry.SolarDBManager;

@SpringBootApplication
@ComponentScan({"org.solarframework"})
public class Database_Main {

    static void main(String[] args) {
        SpringApplication.run(Database_Main.class, args);
        SolarDBManager.createAllSchemasIfMissing();
        SolarDBManager.verifyEntities();
        User u = new User(1L, "Loic", "loic@gmail.com");
        u.Upsert();
        new Order(1L, u, "Steak", 1).Upsert();
        new Order(2L, u,"Egg").Upsert();

        System.err.println(u.getOrders());
        System.err.println("------");
        System.err.println(SolarDBManager.getById(User.class, 1).orElseThrow().getOrders());
    }
}
