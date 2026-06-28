package org.solarframework.proxyserver.spring;

import org.solarframework.proxyserver.obj.Domain;

import java.util.ArrayList;
import java.util.List;

import static org.solarframework.proxyserver.spring.SSLController.WAMPSERVER;

public class SSLBuilder {
    public static final String LOCALHOST = "127.0.0.1";

    private final List<Domain> domains = new ArrayList<>();
    private boolean regenCerts = false;

    public SSLBuilder() {}

    public SSLBuilder regenerateCerts() {
        regenCerts = true;
        return this;
    }

    public SSLBuilder registerDomain(Domain domain) {
        domains.add(domain);
        return this;
    }
    public SSLBuilder registerDomains(Domain... domain) {
        domains.addAll(List.of(domain));
        return this;
    }

    public SSLService build() throws Exception {
        return WAMPSERVER = new SSLService(domains, regenCerts);
    }
}
