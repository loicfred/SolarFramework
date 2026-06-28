package org.solarframework.proxyserver.netty;

import org.solarframework.proxyserver.obj.Domain;

import java.util.ArrayList;
import java.util.List;

import static org.solarframework.proxyserver.netty.ProxyService.WAMPSERVER;

public class ProxyBuilder {
    public static final String LOCALHOST = "127.0.0.1";

    private final List<Domain> domains = new ArrayList<>();
    private boolean regenCerts = false;

    public ProxyBuilder() {}

    public ProxyBuilder regenerateCerts() {
        regenCerts = true;
        return this;
    }

    public ProxyBuilder registerDomain(Domain domain) {
        domains.add(domain);
        return this;
    }
    public ProxyBuilder registerDomains(Domain... domain) {
        domains.addAll(List.of(domain));
        return this;
    }

    public ProxyService build() throws Exception {
        return WAMPSERVER = new ProxyService(domains, regenCerts);
    }
}
