package org.solarframework.ai.dto;

import java.util.List;

/**
 * What one model turn produced: either an answer, or the tool calls it made on the way to one.
 * <p>This is the whole provider seam for a conversation. Everything around it — the step bound,
 * the transcript, the memory strategy, the approval hook — is provider-neutral and lives in
 * {@code Conversation}.
 */
public record TurnResult(String text, List<ToolCall> toolCalls, TokenUsage usage) {

    public TurnResult {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        usage = usage == null ? TokenUsage.NONE : usage;
    }

    public static TurnResult answer(String text, TokenUsage usage) { return new TurnResult(text, List.of(), usage); }

    /** No tool calls means the model stopped working and answered. */
    public boolean isAnswer() { return toolCalls.isEmpty(); }
}
