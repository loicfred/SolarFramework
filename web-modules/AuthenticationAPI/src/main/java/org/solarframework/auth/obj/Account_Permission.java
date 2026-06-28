package org.solarframework.auth.obj;

import jakarta.persistence.*;
import org.solarframework.db.spring.DatabaseObject;

import java.time.Instant;

@Entity
@Table(name = "account_permission")
public class Account_Permission extends DatabaseObject.ID_OBJ<Long, Account_Permission> {

    @Column(name = "Name", nullable = false)
    private String name;

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    protected Account_Permission() {}
    public Account_Permission(String name) {
        this.ID = Instant.now().toEpochMilli();
        this.name = name;
    }
}