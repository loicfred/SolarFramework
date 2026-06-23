package org.solarframework.auth.obj;

import jakarta.persistence.*;
import org.solarframework.db.spring.DatabaseObject;

@Entity
@Table(name = "account_rolepermission")
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
}