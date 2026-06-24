package org.solarframework.auth.obj;

import jakarta.persistence.*;
import org.solarframework.db.spring.DatabaseObject;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "web_authorizedaccess")
@IdClass(Web_PathAuthorizedRole.PathRole.class)
public class Web_PathAuthorizedRole extends DatabaseObject<Web_PathAuthorizedRole> {

    @Id
    @Column(name = "PathID", nullable = false)
    private Long pathId;
    @Id
    @Column(name = "RoleID", nullable = false)
    private Long roleId;

    public Long getPathId() {
        return pathId;
    }
    public void setPathId(Long pathId) {
        this.pathId = pathId;
    }

    public Long getRoleId() {
        return roleId;
    }
    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    protected Web_PathAuthorizedRole() {}
    public Web_PathAuthorizedRole(Long pathId, Long roleId) {
        this.pathId = pathId;
        this.roleId = roleId;
    }

    public Account_Role getRole() {
        return retrieveEntityServiceFor(Account_Role.class).getById(getRoleId()).orElse(null);
    }

    @Embeddable
    public static class PathRole implements Serializable {
        private Long pathId;
        private Long roleId;

        public Long getPathId() {
            return pathId;
        }
        public void setPathId(Long pathId) {
            this.pathId = pathId;
        }

        public Long getRoleId() {
            return roleId;
        }
        public void setRoleId(Long roleId) {
            this.roleId = roleId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PathRole that)) return false;
            return Objects.equals(this.pathId, that.pathId) && Objects.equals(this.roleId, that.roleId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(pathId, roleId);
        }
    }
}