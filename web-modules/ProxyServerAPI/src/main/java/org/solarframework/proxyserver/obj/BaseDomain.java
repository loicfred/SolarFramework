package org.solarframework.proxyserver.obj;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import org.solarframework.db.spring.DatabaseObject;
import org.springframework.http.HttpHeaders;

import java.time.Instant;

@MappedSuperclass
public abstract class BaseDomain<T> extends DatabaseObject.ID_OBJ<Long, T> {

    @Column(name = "IP", nullable = false)
    private String ip;
    @Column(name = "Name", nullable = false)
    private String name;
    @Column(name = "Path", nullable = false)
    private String path;

    public String getIp() {
        return ip;
    }
    public String getName() {
        return name;
    }
    public String getPath() {
        return path;
    }
    public void setIp(String ip) {
        this.ip = ip;
    }
    public void setName(String name) {
        this.name = name;
    }
    public void setPath(String path) {
        this.path = path;
    }

    public boolean isProxy() {
        return path != null && (path.startsWith("http://") || path.startsWith("https://"));
    }

    public abstract String getHost();

    protected BaseDomain() {
        this.ID = Instant.now().toEpochMilli();
    }
    protected BaseDomain(String ip, String name, String path) {
        this.ID = Instant.now().toEpochMilli();
        this.name = name.replace("*.", "");
        this.ip = ip;
        this.path = path;
    }
}