package org.solarframework.auth.obj;

import jakarta.persistence.*;
import org.solarframework.db.spring.DatabaseObject;

import java.time.Instant;
import java.util.Set;

@Entity
@Table(name = "account_permissioncategory")
public class Account_PermissionCategory extends DatabaseObject.ID_OBJ<Long, Account_PermissionCategory> {

    @Column(name = "Name", unique = true, nullable = false, length = 100)
    private String name;
    @Column(name = "Description")
    private String description;

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }

    protected Account_PermissionCategory() {}
    public Account_PermissionCategory(Long id, String name, String description) {
        this.ID = id;
        this.name = name;
        this.description = description;
    }
    public Account_PermissionCategory(String name, String description) {
        this(Instant.now().toEpochMilli(), name, description);
    }

    public Set<Account_Permission> getPermissions() {
        return retrieveEntityServiceFor(Account_Permission.class).doQueryAllDistinct("SELECT * FROM account_permission WHERE CategoryID = ?", getID());
    }

    public static Account_PermissionCategory getByName(String name) {
        return retrieveEntityServiceFor(Account_PermissionCategory.class).getWhere("Name = ?", name).orElse(null);
    }
}
