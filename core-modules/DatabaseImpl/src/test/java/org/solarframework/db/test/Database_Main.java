package org.solarframework.db.test;

import org.solarframework.db.test.obj.Order;
import org.solarframework.db.test.obj.User;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

import java.util.List;

import static org.solarframework.db.spring.DatabaseRegistry.SolarDBManager;

@SpringBootApplication
@ComponentScan({"org.solarframework"})
public class Database_Main {

    static void main(String[] args) {
        SpringApplication.run(Database_Main.class, args);
        SolarDBManager.createAllSchemasIfMissing();
        SolarDBManager.verifyEntities();
//        User u = new User(1L, "Loic", "loic@gmail.com");
//        u.Upsert();
//        new Order(1L, u, "Steak", 1).Upsert();
//        new Order(2L, u,"Egg").Upsert();
//        System.err.println(u.getOrders());

        System.err.println("------");
        List<Order> o = SolarDBManager.getById(User.class, 1).orElseThrow().getOrders();
        System.err.println(o.getFirst().getUser());
        System.err.println(o.get(1).getUser());
        System.err.println(o.getLast().getUser());
    }
}
