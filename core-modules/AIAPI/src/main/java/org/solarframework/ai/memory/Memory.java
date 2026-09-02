package org.solarframework.ai.memory;

import org.solarframework.ai.IAIService;
import org.solarframework.ai.Prompts;
import org.solarframework.ai.dto.AIOptions;
import org.solarframework.ai.obj.ChatMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides what part of a history is actually sent, so a long conversation cannot outgrow the model's
 * context. Keeps the newest messages that fit the budget and drops the rest; a chatbot with no
 * memory at all sends everything.
 * <p>Given a service it also <em>summarises</em> instead of simply forgetting: whatever falls out of
 * the window is condensed into a rolling summary carried as a system message, so a resumed
 * conversation still knows what was said. That costs a model call, so it only happens on the turns
 * where something is actually dropped.
 * <p>Stateless and shared across every conversation of a chatbot — the rolling summary travels in
 * and out through {@link Result} so it lives on the conversation instead.
 */
public class Memory {

    private final int maxInputTokens;
    private final IAIService summarizer;

    /**
     * The counterpart to {@code AIOptions.maxOutputTokens}, and the only place an input bound can live: no backend
     * accepts one, so what may be sent is decided here, by trimming, before the request is built.
     *
     * @param maxInputTokens 0 or less means "use whatever context the model reports"
     */
    public Memory(int maxInputTokens) { this(maxInputTokens, null); }

    /** Same, but condenses what it drops instead of forgetting it. */
    public Memory(int maxInputTokens, IAIService summarizer) { this.maxInputTokens = maxInputTokens; this.summarizer = summarizer; }

    public boolean isSummarizing() { return summarizer != null; }

    /**
     * @param history       the full conversation so far
     * @param summary       the rolling summary, or null on the first call
     * @param contextTokens the model's usable context, used when no explicit budget was given
     */
    public Result prepare(List<ChatMessage> history, String summary, int contextTokens) {
        List<ChatMessage> kept = window(history, contextTokens);
        if (summarizer == null) return new Result(kept, summary);

        List<ChatMessage> dropped = history.stream().filter(m -> !m.isSystem() && !kept.contains(m)).toList();
        String merged = dropped.isEmpty() ? summary : summarize(summary, dropped);
        if (merged == null || merged.isBlank()) return new Result(kept, summary);

        List<ChatMessage> out = new ArrayList<>(kept);
        out.add(firstNonSystemIndex(out), ChatMessage.system("Summary of earlier conversation:\n" + merged));
        return new Result(out, merged);
    }

    private List<ChatMessage> window(List<ChatMessage> history, int contextTokens) {
        int limit = maxInputTokens > 0 ? maxInputTokens : contextTokens;
        int left = limit > 0 ? limit : Integer.MAX_VALUE;
        for (ChatMessage m : history) if (m.isSystem()) left -= m.estimateTokens();

        // Keep scanning once the budget runs out rather than breaking: system messages live at the
        // front of the history, so stopping early is what would drop the persona.
        List<ChatMessage> kept = new ArrayList<>();
        boolean full = false;
        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessage m = history.get(i);
            if (m.isSystem()) { kept.addFirst(m); continue; }
            if (full) continue;
            int cost = m.estimateTokens();
            if (cost > left) { full = true; continue; }
            left -= cost;
            kept.addFirst(m);
        }
        return withoutOrphanedToolResults(kept);
    }

    /**
     * Drops any tool result left at the front of the window without the assistant message that asked for it.
     * <p>The window fills from the newest message backwards, so the budget can run out exactly between a tool
     * call and its result and keep only the result. A backend refuses that outright — gpt-oss answers
     * "Message has tool role, but there was no previous assistant message with a tool call" — so the orphans
     * go too. Only the front can be orphaned: anything newer than a kept message is kept as well.
     */
    private List<ChatMessage> withoutOrphanedToolResults(List<ChatMessage> kept) {
        int first = firstNonSystemIndex(kept);
        while (first < kept.size() && kept.get(first).getRole().isTool()) kept.remove(first);
        return kept;
    }

    private String summarize(String previous, List<ChatMessage> dropped) {
        StringBuilder sb = new StringBuilder(Prompts.SUMMARIZER);
        if (previous != null && !previous.isBlank()) sb.append("\n\nSummary so far:\n").append(previous);
        sb.append("\n\nNew messages to fold in:\n");
        for (ChatMessage m : dropped) sb.append(m.getRole()).append(": ").append(m.getText()).append('\n');
        try {
            return summarizer.createChatbot().instruction("QuickSummarizer").build().prompt(AIOptions.INFORMATIONIST.withTemperature(0.0).withTools(false), sb.toString());
        } catch (RuntimeException _) {
            return previous; // a failed summary must not fail the turn the user is waiting on
        }
    }

    private int firstNonSystemIndex(List<ChatMessage> messages) {
        for (int i = 0; i < messages.size(); i++) if (!messages.get(i).isSystem()) return i;
        return messages.size();
    }

    public record Result(List<ChatMessage> messages, String summary) {
        public Result { messages = messages == null ? List.of() : List.copyOf(messages); }
    }
}
