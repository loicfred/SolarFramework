package org.solarframework.ai.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * What the model did. One turn and a whole run have the same shape — text, tool calls, usage — so
 * they are the same type: a run carries the steps it took, and a step is a run with none.
 * <p>{@code completed} false means the loop stopped on its step bound rather than on the model
 * answering; the steps so far are still here.
 */
public record AgentRun(String goal, String text, List<ToolCall> toolCalls, TokenUsage usage, List<AgentRun> steps, boolean completed) {

    public AgentRun {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        steps = steps == null ? List.of() : List.copyOf(steps);
        usage = usage == null ? TokenUsage.NONE : usage;
    }

    public static AgentRun step(String text, List<ToolCall> toolCalls, TokenUsage usage) {
        return new AgentRun(null, text, toolCalls, usage, List.of(), toolCalls == null || toolCalls.isEmpty());
    }

    public static AgentRun of(String goal, String text, List<AgentRun> steps, boolean completed) {
        return new AgentRun(goal, text, List.of(), TokenUsage.NONE, steps, completed);
    }

    /** A turn that called no tool is the model answering rather than working. */
    public boolean usedTools() { return !toolCalls.isEmpty(); }

    public int stepCount() { return steps.size(); }

    /** Every tool call made across the whole run, in order. */
    public List<ToolCall> getAllToolCalls() {
        if (steps.isEmpty()) return toolCalls;
        List<ToolCall> all = new ArrayList<>();
        for (AgentRun s : steps) all.addAll(s.getAllToolCalls());
        return all;
    }

    public TokenUsage getTotalUsage() {
        if (steps.isEmpty()) return usage;
        TokenUsage total = usage;
        for (AgentRun s : steps) total = total.plus(s.getTotalUsage());
        return total;
    }

    @Override public String toString() {
        return steps.isEmpty() ? (usedTools() ? toolCalls + " " : "") + (text == null ? "" : text)
                : (completed ? "completed" : "incomplete") + " in " + stepCount() + " step(s): " + text;
    }
}
