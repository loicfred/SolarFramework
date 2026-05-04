package org.solarframework.discord.obj;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import org.solarframework.db.spring.DatabaseObject;

@Entity
@Table(name = "testb")
public class Testb extends DatabaseObject<Testb> {

    @Id
    @Column(name = "Action", length = 32, nullable = false)
    private String Action;

    @Column(name = "ServerID", nullable = false)
    private Long ServerID;

    @Column(name = "RoleID", nullable = false)
    private Long RoleID;

    public Testb() {}

    public String getAction() {
        return Action;
    }

    public void setAction(String action) {
        Action = action;
    }

    public Long getServerID() {
        return ServerID;
    }

    public void setServerID(Long serverID) {
        ServerID = serverID;
    }

    public Long getRoleID() {
        return RoleID;
    }

    public void setRoleID(Long roleID) {
        RoleID = roleID;
    }
}