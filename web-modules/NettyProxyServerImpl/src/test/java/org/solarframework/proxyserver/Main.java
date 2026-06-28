package org.solarframework.proxyserver;

import org.solarframework.proxyserver.obj.Domain;
import org.solarframework.proxyserver.netty.ProxyBuilder;
import org.solarframework.proxyserver.netty.ProxyServer;

public class Main {

    static void main(String[] args) throws Exception {
        setupProxy();
    }

    private static void setupProxy() throws Exception {
        Domain H1 = new Domain(ProxyBuilder.LOCALHOST, "mysite.com");
        H1.addSubdomain("www");
        H1.addSubdomain("admin");
        H1.addSubdomain("accounts");

        Domain H2 = new Domain(ProxyBuilder.LOCALHOST, "myothersite.com", "http://localhost:8081");

        ProxyBuilder builder = new ProxyBuilder()
                .registerDomains(H1, H2)
                .regenerateCerts();
        builder.build();

        new ProxyServer().start().block();
    }
}
