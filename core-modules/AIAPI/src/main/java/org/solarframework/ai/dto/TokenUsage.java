package org.solarframework.ai.dto;

public record TokenUsage(long prompt, long completion) {

    public static final TokenUsage NONE = new TokenUsage(0, 0);

    public long total() { return prompt + completion; }
    public TokenUsage plus(TokenUsage other) { return other == null ? this : new TokenUsage(prompt + other.prompt, completion + other.completion); }

    @Override public String toString() { return prompt + " prompt + " + completion + " completion = " + total() + " tokens"; }
}
