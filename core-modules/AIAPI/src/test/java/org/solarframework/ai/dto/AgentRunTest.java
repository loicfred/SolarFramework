package org.solarframework.ai.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgentRunTest {

    private AgentRun toolStep() {
        return AgentRun.step("working", List.of(new ToolCall("1", "t", "{}").withResult("r")), new TokenUsage(10, 5));
    }

    @Test void aStepThatCalledNoToolIsAnAnswer() {
        assertFalse(AgentRun.step("done", List.of(), new TokenUsage(1, 1)).usedTools());
        assertTrue(AgentRun.step("done", List.of(), null).completed());
        assertTrue(toolStep().usedTools());
        assertFalse(toolStep().completed());
    }

    @Test void aRunAggregatesItsStepsToolCallsAndUsage() {
        AgentRun run = AgentRun.of("goal", "done", List.of(toolStep(), AgentRun.step("done", List.of(), new TokenUsage(20, 7))), true);

        assertEquals(2, run.stepCount());
        assertEquals(1, run.getAllToolCalls().size());
        assertEquals(42, run.getTotalUsage().total());
        assertTrue(run.completed());
    }

    @Test void anIncompleteRunStillReportsWhatItDid() {
        AgentRun run = AgentRun.of("goal", "gave up", List.of(toolStep()), false);

        assertFalse(run.completed());
        assertEquals(1, run.stepCount());
        assertEquals(1, run.getAllToolCalls().size());
    }

    @Test void emptyCollectionsRatherThanNulls() {
        AgentRun run = new AgentRun(null, null, null, null, null, false);
        assertTrue(run.toolCalls().isEmpty());
        assertTrue(run.steps().isEmpty());
        assertEquals(TokenUsage.NONE, run.usage());
        assertEquals(0, run.getTotalUsage().total());
    }

    @Test void usageAddsUp() {
        assertEquals(new TokenUsage(3, 7), new TokenUsage(1, 2).plus(new TokenUsage(2, 5)));
        assertEquals(new TokenUsage(1, 2), new TokenUsage(1, 2).plus(null));
        assertEquals(3, new TokenUsage(1, 2).total());
    }

    @Test void aToolCallTracksItsOutcome() {
        ToolCall c = new ToolCall("1", "getTime", "{}");
        assertFalse(c.isExecuted());
        assertFalse(c.denied());
        assertTrue(c.withResult("12:00").isExecuted());
        assertTrue(c.asDenied().denied());
        assertFalse(c.asDenied().isExecuted());
    }

    @Test void aTurnWithoutToolCallsIsAnAnswer() {
        assertTrue(TurnResult.answer("hi", null).isAnswer());
        assertFalse(new TurnResult("working", List.of(new ToolCall("1", "t", "{}")), null).isAnswer());
    }
}
