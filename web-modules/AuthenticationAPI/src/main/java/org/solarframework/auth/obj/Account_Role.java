package org.solarframework.auth.obj;

import jakarta.persistence.*;
import org.solarframework.db.spring.DatabaseObject;

import java.time.Instant;
import java.util.Set;

@Entity
@Table(name = "account_role")
public class Account_Role extends DatabaseObject.ID_RECORD_OBJ<Long, Account_Role> {

    @OneToMany(mappedBy = "role", fetch = FetchType.LAZY)
    private Set<Account_User> users;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "account_role_to_permission",
            joinColumns = @JoinColumn(name = "RoleID"),
            inverseJoinColumns = @JoinColumn(name = "PermissionID")
    )
    private Set<Account_Permission> permissions;


    @Column(name = "Name", nullable = false, length = 50)
    private String name;

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    protected Account_Role() {}
    public Account_Role(Long ID, String name) {
        this.ID = ID;
        this.name = name;
    }
    public Account_Role(String name) {
        this(Instant.now().toEpochMilli(), name);
    }

    public Set<Account_Permission> getPermissions() {
        return permissions == null ? permissions = retrieveEntityServiceFor(Account_Permission.class).doQueryAllDistinct("SELECT * FROM account_permission WHERE ID IN (SELECT PermissionID FROM account_role_to_permission WHERE RoleID = ?)", getID()) : permissions;
    }

    public static Account_Role getByName(String name) {
        return retrieveEntityServiceFor(Account_Role.class).getWhere("Name = ?", name).orElse(null);
    }

}