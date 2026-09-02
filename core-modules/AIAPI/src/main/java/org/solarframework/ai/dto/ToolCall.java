package org.solarframework.ai.dto;

/** One tool invocation the model asked for, plus its outcome once executed. */
public record ToolCall(String id, String name, String argumentsJson, String result, boolean denied) {

    public ToolCall(String id, String name, String argumentsJson) { this(id, name, argumentsJson, null, false); }

    public boolean isExecuted() { return result != null; }
    public ToolCall withResult(String result) { return new ToolCall(id, name, argumentsJson, result, denied); }
    public ToolCall asDenied() { return new ToolCall(id, name, argumentsJson, result, true); }

    @Override public String toString() { return name + "(" + argumentsJson + ")" + (isExecuted() ? " -> " + result : denied ? " -> denied" : ""); }
}
