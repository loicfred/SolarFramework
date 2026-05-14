package org.solarframework.authentication.obj;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "persistent_logins")
public class PersistentLogin {
    @Id
    @Column(name = "series", nullable = false, length = 64)
    private String series;

    @Column(name = "username", nullable = false, length = 64)
    private String username;

    @Column(name = "token", nullable = false, length = 64)
    private String token;

    @Column(name = "last_used", nullable = false)
    private Instant lastUsed;

    public String getSeries() {
        return series;
    }
    public String getUsername() {
        return username;
    }
    public String getToken() {
        return token;
    }
    public Instant getLastUsed() {
        return lastUsed;
    }

    public void setSeries(String series) {
        this.series = series;
    }
    public void setUsername(String username) {
        this.username = username;
    }
    public void setToken(String token) {
        this.token = token;
    }
    public void setLastUsed(Instant lastUsed) {
        this.lastUsed = lastUsed;
    }

}