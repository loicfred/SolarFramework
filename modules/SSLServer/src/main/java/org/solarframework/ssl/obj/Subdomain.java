package org.solarframework.ssl.obj;

import org.springframework.http.HttpHeaders;

public class Subdomain extends BaseDomain {
    private final transient Domain rootDomain;

    protected Subdomain(Domain rootDomain, String name) {
        super(rootDomain.getIP(), name, "./WAMP/domain/" + rootDomain.getName() + "/_" + name);
        this.rootDomain = rootDomain;
    }
    protected Subdomain(Domain rootDomain, String name, String path) {
        super(rootDomain.getIP(), name, path);
        this.rootDomain = rootDomain;
    }

    public Domain getRootDomain() {
        return rootDomain;
    }

    @Override
    public String getHost() {
        return getName() + "." + getRootDomain().getName();
    }

    public Subdomain headers(HttpHeaders headers) {
        this.Headers = headers;
        return this;
    }
}