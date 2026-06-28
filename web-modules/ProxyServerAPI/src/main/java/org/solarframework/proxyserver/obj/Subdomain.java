package org.solarframework.proxyserver.obj;

import jakarta.persistence.*;
import org.springframework.http.HttpHeaders;

@Entity
@Table(name = "web_subdomain")
public class Subdomain extends BaseDomain<Subdomain> {
    @ManyToOne
    @JoinColumn(referencedColumnName = "ID", name = "DomainID")
    private transient Domain rootDomain;

    @Column(name = "DomainID", nullable = false)
    private Long domainId;

    public Long getDomainId() {
        return domainId;
    }
    public void setDomainId(Long domainId) {
        this.domainId = domainId;
        rootDomain = null;
    }

    protected Subdomain() {}
    protected Subdomain(Domain rootDomain, String name) {
        super(rootDomain.getIp(), name, "./config/domain/" + rootDomain.getName() + "/_" + name);
        this.rootDomain = rootDomain;
    }
    protected Subdomain(Domain rootDomain, String name, String path) {
        super(rootDomain.getIp(), name, path);
        this.rootDomain = rootDomain;
    }

    public Domain getRootDomain() {
        return rootDomain == null ? retrieveEntityServiceFor(Domain.class).getById(domainId).orElse(null) : rootDomain;
    }

    @Override
    public String getHost() {
        return getName() + "." + getRootDomain().getName();
    }

}