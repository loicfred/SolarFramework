package org.solarframework.ai;

import org.junit.jupiter.api.Test;
import org.solarframework.ai.dto.ModelFacts;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** What a service says about its model, and what it says when it cannot ask. */
class ServiceFactsTest {

    @Test void factsCarryTheModelTheOfferAndTheToolAnswer() {
        ModelFacts facts = new FakeAIService().facts();

        assertEquals("fake-model", facts.model());
        assertEquals(List.of("fake-model"), facts.models());
        assertEquals(Boolean.TRUE, facts.toolsSupported());
    }

    /** A server that cannot be reached leaves it unknown: a screen told "no tools" would warn about a limitation nobody has. */
    @Test void anUnreachableServerLeavesToolSupportUnknown() {
        ModelFacts facts = new FakeAIService() {
            @Override public List<String> getAvailableModels() { throw new IllegalStateException("connection refused"); }
        }.facts();

        assertNull(facts.toolsSupported());
        assertTrue(facts.models().isEmpty());
        assertEquals("fake-model", facts.model(), "the configured model is known without asking anyone");
    }
}
