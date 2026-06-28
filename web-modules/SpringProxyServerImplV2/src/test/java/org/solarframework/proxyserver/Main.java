package org.solarframework.proxyserver;

import org.solarframework.proxyserver.obj.Domain;
import org.solarframework.proxyserver.spring.ProxyBuilder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpHeaders;

@SpringBootApplication
public class Main {

    static void main(String[] args) throws Exception {
        SpringApplication.run(Main.class, args);
        setupProxy();
    }

    private static void setupProxy() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Access-Control-Allow-Origin", "*");

        Domain H1 = new Domain(ProxyBuilder.LOCALHOST, "mysite.com");
        H1.addSubdomain("www");
        H1.addSubdomain("admin");
        H1.addSubdomain("accounts");

        Domain H2 = new Domain(ProxyBuilder.LOCALHOST, "myothersite.com", "http://localhost:8081");

        ProxyBuilder builder = new ProxyBuilder()
                .registerDomains(H1, H2)
                .regenerateCerts();
        builder.build();
    }
}
