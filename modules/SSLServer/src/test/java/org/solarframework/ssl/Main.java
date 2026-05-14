package org.solarframework.ssl;

import org.solarframework.ssl.spring.SSLBuilder;
import org.solarframework.ssl.obj.Domain;
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

        Domain H1 = new Domain(SSLBuilder.LOCALHOST, "mysite.com").headers(headers);
        H1.addSubdomain("www").headers(headers);
        H1.addSubdomain("admin").headers(headers);
        H1.addSubdomain("accounts").headers(headers);

        Domain H2 = new Domain(SSLBuilder.LOCALHOST, "test2.com", "C:/Website/test2/");
        H2.addSubdomain("www", "C:/Website/test2_www/");

        Domain H3 = new Domain(SSLBuilder.LOCALHOST, "myothersite.com", "http://localhost:8080");

        SSLBuilder builder = new SSLBuilder()
                .registerDomains(H1, H2, H3)
                //.regenerateCerts()
        ;
        builder.build();
    }
}
