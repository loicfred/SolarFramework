package org.solarframework.auth.obj.join;

import jakarta.persistence.*;
import org.solarframework.auth.obj.Account_Permission;
import org.solarframework.auth.obj.Account_Role;
import org.solarframework.auth.obj.Account_User;
import org.solarframework.db.spring.DatabaseObject;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "account_role_to_permission")
@IdClass(Account_Role_To_Permission.RolePermission.class)
public class Account_Role_To_Permission extends DatabaseObject<Account_Role_To_Permission> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(referencedColumnName = "ID", name = "RoleID", nullable = false, insertable = false, updatable = false)
    private Account_Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(referencedColumnName = "ID", name = "PermissionID", nullable = false, insertable = false, updatable = false)
    private Account_Permission permission;

    @Id
    @Column(name = "RoleID", nullable = false)
    private Long roleId;
    @Id
    @Column(name = "PermissionID", nullable = false)
    private Long permissionId;

    public Long getRoleId() {
        return roleId;
    }
    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public Long getPermissionId() {
        return permissionId;
    }
    public void setPermissionId(Long permissionId) {
        this.permissionId = permissionId;
    }

    protected Account_Role_To_Permission() {}
    public Account_Role_To_Permission(Long roleId, Long permissionId) {
        this.roleId = roleId;
        this.permissionId = permissionId;
    }

    @Embeddable
    public static class RolePermission implements Serializable {
        private Long roleId;
        private Long permissionId;

        public Long getRoleId() {
            return roleId;
        }
        public void setRoleId(Long roleId) {
            this.roleId = roleId;
        }

        public Long getPermissionId() {
            return permissionId;
        }
        public void setPermissionId(Long pathId) {
            this.permissionId = pathId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RolePermission that)) return false;
            return Objects.equals(this.roleId, that.roleId) && Objects.equals(this.permissionId, that.permissionId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(roleId, permissionId);
        }
    }
}