package org.solarframework.proxyserver.obj;

import jakarta.persistence.*;
import org.springframework.http.HttpHeaders;

import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "web_domain")
public class Domain extends BaseDomain<Domain> {
    @OneToMany(mappedBy = "rootDomain", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Subdomain> subdomains = new java.util.ArrayList<>();

    protected Domain() {}
    public Domain(String ip, String name) {
        super(ip, name, "./config/domain/" + name + "/_");
    }
    public Domain(String ip, String name, String path) {
        super(ip, name, path);
    }


    public List<Subdomain> getSubdomains() {
        return subdomains == null ? subdomains = retrieveEntityServiceFor(Subdomain.class).getAllWhere("DomainID = ?", ID) : subdomains;
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
    public List<String> getHosts() {
        List<String> hosts = new java.util.ArrayList<>();
        hosts.add(getHost());
        if (!subdomains.isEmpty()) hosts.addAll(subdomains.stream().map(Subdomain::getHost).toList());
        return hosts;
    }

    public Subdomain addSubdomain(String subdomain) {
        subdomains.add(new Subdomain(this, subdomain));
        return subdomains.getLast();
    }
    public Subdomain addSubdomain(String subdomain, String path) {
        subdomains.add(new Subdomain(this, subdomain, path));
        return subdomains.getLast();
    }

}
