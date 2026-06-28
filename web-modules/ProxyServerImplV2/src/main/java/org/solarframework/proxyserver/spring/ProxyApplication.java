package org.solarframework.proxyserver.spring;

import org.solarframework.proxyserver.obj.Domain;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ProxyApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProxyApplication.class, args);
    }

    // Register your two existing sites at startup ("by code")
    @Bean
    CommandLineRunner seed(DomainRegistry registry) {
        Domain H1 = new Domain("127.0.0.1", "mysite.com", "C:/Website/test2/");
        System.err.println(H1.isProxy());
        Domain H3 = new Domain("127.0.0.1", "myothersite.com", "http://localhost:8081");
        System.err.println(H3.isProxy());
        return args -> {
            registry.add(H1);
            registry.add(H3);
        };
    }
}