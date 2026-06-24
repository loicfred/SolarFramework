package org.solarframework.auth.obj;

import jakarta.persistence.*;
import org.solarframework.db.spring.DatabaseObject;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "web_pathaccesslevel")
public class Web_PathAccessLevel extends DatabaseObject.ID_OBJ<Long, Web_PathAccessLevel> {
    private transient Set<Account_Role> roles;

    @OneToMany
    @JoinColumn(referencedColumnName = "ID", name = "PathID")
    private transient Set<Web_PathAuthorizedRole> authorizedRoles;

    @Column(name = "Path", nullable = false, unique = true, length = 128)
    private String path;
    @Column(name = "MinimumAccessLevel", nullable = false)
    private Integer minimumAccessLevel = 0;


    public String getPath() {
        return path;
    }
    public void setPath(String path) {
        this.path = path;
    }

    public Integer getMinimumAccessLevel() {
        return minimumAccessLevel;
    }
    public void setMinimumAccessLevel(Integer minimumAccessLevel) {
        this.minimumAccessLevel = minimumAccessLevel;
    }

    protected Web_PathAccessLevel() {}
    public Web_PathAccessLevel(Long ID, String path, Integer minimumAccessLevel) {
        this.ID = ID;
        this.path = path;
        this.minimumAccessLevel = minimumAccessLevel;
    }
    public Web_PathAccessLevel(String path, Integer minimumAccessLevel) {
        this.ID = Instant.now().toEpochMilli();
        this.path = path;
        this.minimumAccessLevel = minimumAccessLevel;
    }

    public Set<Account_Role> getAuthorizedRoles() {
        authorizedRoles = authorizedRoles == null ? retrieveEntityServiceFor(Web_PathAuthorizedRole.class).getAllWhereDistinct("PathID=?", getID()) : authorizedRoles;
        return roles == null ? roles = authorizedRoles.stream().map(Web_PathAuthorizedRole::getRole).collect(Collectors.toSet()) : roles;
    }

}