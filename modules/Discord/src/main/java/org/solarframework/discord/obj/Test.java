package org.solarframework.discord.obj;

import jakarta.persistence.*;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import org.solarframework.db.spring.DatabaseObject;
import org.solarframework.discord.obj.other.ActionServerID;

@Entity
@Table(name = "test")
@IdClass(ActionServerID.class)
public class Test extends DatabaseObject<Test> {
    private transient Guild G;
    private transient Role R;

    @Id
    @Column(name = "Action", length = 32, nullable = false)
    private String Action;
    @Id
    @Column(name = "ServerID", nullable = false)
    private Long ServerID;

    @Column(name = "RoleID", nullable = false)
    private Long RoleID;

    public Test() {}
}