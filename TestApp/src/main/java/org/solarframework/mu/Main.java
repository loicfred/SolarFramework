package org.solarframework.mu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

import static org.solarframework.db.spring.DatabaseRegistry.SolarDBManager;

@SpringBootApplication
@ComponentScan({"org.solarframework"})
public class Main {

    static void main(String[] args) {
        SpringApplication.run(Main.class, args);
        SolarDBManager.createAllSchemasIfMissing();
        SolarDBManager.verifyEntities();

        User u = SolarDBManager.getById(User.class, 1).orElseThrow();
        System.err.println(u);
        System.err.println(u.getOrders());
        System.exit(0);
    }
}
