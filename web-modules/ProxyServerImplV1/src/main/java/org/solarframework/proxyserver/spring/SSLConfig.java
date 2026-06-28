package org.solarframework.proxyserver.spring;

import org.solarframework.proxyserver.utils.MKCert;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.Ssl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

@Configuration
public class SSLConfig {

    @Bean
    public TomcatServletWebServerFactory servletContainer() throws Exception {
        if (!new File("./WAMP/certs/wampdomains.p12").exists()) MKCert.GenerateCertificateFor("localhost");

        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
        factory.setPort(443);
        Ssl ssl = new Ssl();
        ssl.setKeyStore("./WAMP/certs/wampdomains.p12");
        ssl.setKeyStorePassword("password");
        ssl.setKeyStoreType("PKCS12");
        ssl.setKeyAlias("wildcard");

        factory.setSsl(ssl);

        return factory;
    }
}