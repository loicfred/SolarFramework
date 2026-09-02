package org.solarframework.ai.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AIOptionsTest {

    @Test void copiesRatherThanMutatingTheSharedPresets() {
        AIOptions tuned = AIOptions.CONVERSATIONIST.withModel("m").withTemperature(0.9);

        assertEquals("m", tuned.model());
        assertEquals(0.9, tuned.temperature());
        assertNull(AIOptions.CONVERSATIONIST.model(), "the preset must not be mutated");
        assertNotEquals(0.9, AIOptions.CONVERSATIONIST.temperature(), "nor its temperature");
    }

    @Test void everyCopyMethodKeepsTheOtherFields() {
        AIOptions o = AIOptions.AGENT.withModel("m").withMaxOutputTokens(10).withTopP(0.1).withStop("x").withTools(false);

        assertEquals("m", o.model());
        assertEquals(10, o.maxOutputTokens());
        assertEquals(0.1, o.topP());
        assertEquals(AIOptions.AGENT.temperature(), o.temperature(), "temperature should be carried over untouched");
        assertEquals(1, o.stop().size());
        assertFalse(o.allowTools());
    }

    @Test void presetsMatchTheirPurpose() {
        assertTrue(AIOptions.CONVERSATIONIST.allowTools());
        assertTrue(AIOptions.AGENT.allowTools());
        assertFalse(AIOptions.ITEM_CHOOSER.allowTools(), "structured picking must not offer tools");
        assertEquals(0.0, AIOptions.ITEM_CHOOSER.temperature(), "picking should be deterministic");
        assertTrue(AIOptions.AGENT.maxOutputTokens() > AIOptions.CONVERSATIONIST.maxOutputTokens(), "an agent needs room to work");
    }

    @Test void stopSequencesAreNeverNull() {
        assertTrue(new AIOptions(null, null, null, null, null, null, null, true).stop().isEmpty());
    }
}
