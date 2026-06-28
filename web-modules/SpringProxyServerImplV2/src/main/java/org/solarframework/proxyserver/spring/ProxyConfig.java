package org.solarframework.proxyserver.spring;

import org.solarframework.certs.MKCert;
import org.springframework.boot.reactor.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.server.Ssl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

@Configuration
public class ProxyConfig {

    @Bean
    public NettyReactiveWebServerFactory reactiveWebServerFactory() throws Exception {
        if (!new File("./config/certs/ssldomains.p12").exists()) MKCert.GenerateCertificateFor("localhost");

        NettyReactiveWebServerFactory factory = new NettyReactiveWebServerFactory();
        factory.setPort(443);

        Ssl ssl = new Ssl();
        ssl.setEnabled(true);                                   // explicit, safest
        ssl.setKeyStore("./config/certs/ssldomains.p12");
        ssl.setKeyStorePassword("password");
        ssl.setKeyStoreType("PKCS12");
        ssl.setKeyAlias("wildcard");

        factory.setSsl(ssl);
        return factory;
    }
}