package org.solarframework.discord.obj.other;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ActionServerID implements Serializable {
    private String Action;
    private Long ServerID;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ActionServerID that)) return false;
        return Objects.equals(this.Action, that.Action) && Objects.equals(this.ServerID, that.ServerID);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Action, ServerID);
    }
}