package org.solarframework.auth.obj;

import jakarta.persistence.*;
import org.solarframework.db.spring.DatabaseObject;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

@Entity
@Table(name = "account_role")
public class Account_Role extends DatabaseObject.ID_OBJ_RECORD<Long, Account_Role> {

    @ManyToMany
    @JoinTable(name = "account_role_to_permission",
            joinColumns = @JoinColumn(name = "RoleID"),
            inverseJoinColumns = @JoinColumn(name = "PermissionID")
    )
    private transient Set<Account_Permission> permissions;


    @Column(name = "Name", nullable = false, length = 50)
    private String name;
    @Column(name = "AccessLevel", nullable = false)
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

    protected Account_Role() {}
    public Account_Role(Long ID, String name, Integer accessLevel) {
        this.ID = ID;
        this.name = name;
        this.accessLevel = accessLevel;
    }
    public Account_Role(String name, Integer accessLevel) {
        this.ID = Instant.now().toEpochMilli();
        this.name = name;
        this.accessLevel = accessLevel;
    }

    public Set<Account_Permission> getPermissions() {
        return permissions == null ? permissions = retrieveEntityServiceFor(Account_Permission.class).doQueryAllDistinct("SELECT * FROM account_permission WHERE ID IN (SELECT PermissionID FROM account_role_to_permission WHERE RoleID = ?)", getID()) : permissions;
    }

    public static Account_Role getByName(String name) {
        return retrieveEntityServiceFor(Account_Role.class).getWhere("Name = ?", name).orElse(null);
    }

}