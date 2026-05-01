package org.solarframework.ssl.obj;

import org.springframework.http.HttpHeaders;

import java.util.List;
import java.util.Objects;

public class Domain extends BaseDomain {
    private final transient List<Subdomain> Subdomains = new java.util.ArrayList<>();
    public Domain(String ip, String name) {
        super(ip, name, "./WAMP/domain/" + name + "/_");
    }
    public Domain(String ip, String name, String path) {
        super(ip, name, path);
    }


    public List<Subdomain> getSubdomains() {
        return Subdomains;
    }

    @Override
    public String getHost() {
        return getName();
    }

    public String getPath(String subdomain) {
        if (Objects.equals(subdomain, getName())) return getPath();
        Subdomain sub = getSubdomains().stream().filter(s -> s.getHost().equals(subdomain)).findFirst().orElse(null);
        return sub == null ? null : sub.getPath();
    }
    public HttpHeaders getHeaders(String subdomain) {
        if (Objects.equals(subdomain, getName())) return getHeaders();
        Subdomain sub = getSubdomains().stream().filter(s -> s.getHost().equals(subdomain)).findFirst().orElse(null);
        return sub == null ? null : sub.getHeaders();
    }

    public List<String> getHosts() {
        List<String> hosts = new java.util.ArrayList<>();
        hosts.add(getHost());
        if (!Subdomains.isEmpty()) hosts.addAll(Subdomains.stream().map(Subdomain::getHost).toList());
        return hosts;
    }

    public Subdomain addSubdomain(String subdomain) {
        Subdomains.add(new Subdomain(this, subdomain));
        return Subdomains.getLast();
    }
    public Subdomain addSubdomain(String subdomain, String path) {
        Subdomains.add(new Subdomain(this, subdomain, path));
        return Subdomains.getLast();
    }

    public Domain headers(HttpHeaders headers) {
        this.Headers = headers;
        return this;
    }
}
