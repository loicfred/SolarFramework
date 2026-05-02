package org.solarframework.discord.obj.other;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ActionServerID implements Serializable {
    private String Action;
    private Integer ServerID;

    public String getAction() {
        return Action;
    }
    public void setAction(String action) {
        Action = action;
    }

    public Integer getServerID() {
        return ServerID;
    }
    public void setServerID(Integer serverID) {
        ServerID = serverID;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ActionServerID entity = (ActionServerID) o;
        return Objects.equals(this.Action, entity.Action) && Objects.equals(this.ServerID, entity.ServerID);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Action, ServerID);
    }
}