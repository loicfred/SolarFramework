package org.solarframework.auth.obj;

import jakarta.persistence.*;
import org.solarframework.db.spring.DatabaseObject;

import java.time.Instant;
import java.util.Set;

@Entity
@Table(name = "account_permission")
public class Account_Permission extends DatabaseObject.ID_OBJ<Long, Account_Permission> {

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "account_role_to_permission",
            joinColumns = @JoinColumn(name = "PermissionID"),
            inverseJoinColumns = @JoinColumn(name = "RoleID")
    )
    private Set<Account_Role> roles;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(referencedColumnName = "ID", name = "CategoryID", insertable = false, updatable = false)
    private Account_PermissionCategory category;

    @Column(name = "Name", unique = true, nullable = false)
    private String name;
    @Column(name = "Description", nullable = false)
    private String description;
    @Column(name = "CategoryID")
    private Long categoryId;

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

    public Long getCategoryId() {
        return categoryId;
    }
    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
        category = null;
    }

    public Account_PermissionCategory getCategory() {
        if (categoryId == null) return null;
        return category == null ? category = retrieveEntityServiceFor(Account_PermissionCategory.class).getById(categoryId).orElse(null) : category;
    }

    protected Account_Permission() {}
    public Account_Permission(Long id, String name, String description, Long categoryId) {
        this.ID = id;
        this.name = name;
        this.description = description;
        this.categoryId = categoryId;
    }
    public Account_Permission(Long id, String name, String description) {
        this(id, name, description, null);
    }
    public Account_Permission(String name, String description, Long categoryId) {
        this(Instant.now().toEpochMilli(), name, description, categoryId);
    }
    public Account_Permission(String name, String description) {
        this(Instant.now().toEpochMilli(), name, description, null);
    }
}