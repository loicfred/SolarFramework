package org.solarframework.auth.obj;

import jakarta.persistence.*;
import org.solarframework.db.spring.DatabaseObject;

import java.time.Instant;

@Entity
@Table(name = "web_pathpermission")
public class Web_PathPermission extends DatabaseObject.ID_OBJ<Long, Web_PathPermission> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(referencedColumnName = "ID", name = "PermissionID", nullable = false, insertable = false, updatable = false)
    private Account_Permission permissionNeeded;

    @Column(name = "Path", nullable = false, unique = true, length = 128)
    private String path;
    @Column(name = "PermissionID", length = 64)
    private Long PermissionID;


    public String getPath() {
        return path;
    }
    public void setPath(String path) {
        this.path = path;
    }

    public Long getPermissionID() {
        return PermissionID;
    }
    public void setPermissionID(Long permissionID) {
        PermissionID = permissionID;
    }

    protected Web_PathPermission() {}
    public Web_PathPermission(Long ID, String path, Long permissionID) {
        this.ID = ID;
        this.path = path;
        this.PermissionID = permissionID;
    }
    public Web_PathPermission(String path, Long permissionID) {
        this(Instant.now().toEpochMilli(), path, permissionID);
    }


    public Account_Permission getPermission() {
        return permissionNeeded == null ? permissionNeeded = Account_Permission.retrieveEntityServiceFor(Account_Permission.class).getById(PermissionID).orElse(null) : permissionNeeded;
    }

}