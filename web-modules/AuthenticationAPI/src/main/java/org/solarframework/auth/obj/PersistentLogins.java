package org.solarframework.auth.obj;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.solarframework.db.spring.DatabaseObject;

import java.time.LocalDateTime;

@Entity
@Table(name = "persistent_logins")
public class PersistentLogins extends DatabaseObject<PersistentLogins> {
    @Id
    @Column(name = "series", nullable = false, length = 64)
    private String series;
    @Column(name = "username", nullable = false, length = 64)
    private String username;
    @Column(name = "token", nullable = false, length = 64)
    private String token;
    @Column(name = "last_used", nullable = false)
    private LocalDateTime lastUsed;

    public String getSeries() {
        return series;
    }
    public String getUsername() {
        return username;
    }
    public String getToken() {
        return token;
    }
    public LocalDateTime getLastUsed() {
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
    public void setLastUsed(LocalDateTime lastUsed) {
        this.lastUsed = lastUsed;
    }

}