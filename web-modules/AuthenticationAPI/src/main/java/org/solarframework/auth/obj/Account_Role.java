package org.solarframework.auth.obj;

import jakarta.persistence.*;
import org.solarframework.db.spring.DatabaseObject;

import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;

@Entity
@Table(name = "account_user")
public class Account_Role extends DatabaseObject.ID_OBJ_RECORD<Long, Account_Role> {

    @Column(name = "Name", nullable = false, length = 50)
    private String name;
    @Column(name = "access_level", nullable = false)
    private Integer accessLevel = 0;

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    public Integer getAccessLevel() {
        return accessLevel;
    }
    public void setAccessLevel(Integer access_level) {
        this.accessLevel = access_level;
    }

    public Account_Role() {
        this.ID = Instant.now().toEpochMilli();
    }


    public static Account_Role getByName(String name) {
        return retrieveServiceFor(Account_Role.class).getWhere(Account_Role.class, "Name = ?", name).orElse(null);
    }
}