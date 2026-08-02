package org.solarframework.discord.obj.other;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ActionServerIDTest {

    private ActionServerID id(String action, Long serverID) {
        ActionServerID I = new ActionServerID();
        I.setAction(action);
        I.setServerID(serverID);
        return I;
    }

    @Test
    void equalityCoversBothParts() {
        assertEquals(id("ban", 1L), id("ban", 1L));
        assertNotEquals(id("ban", 1L), id("kick", 1L));
        assertNotEquals(id("ban", 1L), id("ban", 2L));
    }
    @Test
    void equalInstancesShareAHashCode() {
        assertEquals(id("ban", 1L).hashCode(), id("ban", 1L).hashCode());
        assertEquals(id(null, null).hashCode(), id(null, null).hashCode());
    }
    @Test
    void unsetPartsAreHandled() {
        assertEquals(id(null, null), id(null, null));
        assertNotEquals(id(null, 1L), id("ban", 1L));
    }
    @Test
    void aForeignTypeIsNeverEqual() {
        assertNotEquals("ban1", id("ban", 1L));
        assertNotEquals(null, id("ban", 1L));
    }
}
