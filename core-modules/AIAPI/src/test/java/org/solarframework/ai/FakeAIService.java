package org.solarframework.ai;

import org.solarframework.ai.dto.*;
import org.solarframework.ai.enums.ProgrammingLanguage;
import org.solarframework.ai.obj.ChatMessage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A scripted service. Because the conversation loop is provider-neutral, everything about it —
 * step bounds, tool approval, the transcript, memory — can be tested without a model.
 */
class FakeAIService implements IAIService {

    final Deque<TurnResult> scripted = new ArrayDeque<>();
    final List<List<ChatMessage>> seenHistories = new ArrayList<>();
    final List<List<Object>> seenTools = new ArrayList<>();
    int turns;
    private String model = "fake-model";
    private String name = DEFAULT;
    private int usableContext = 8192;

    void willAnswer(String text) { scripted.add(TurnResult.answer(text, new TokenUsage(1, 1))); }

    void willCallTool(String name) {
        scripted.add(new TurnResult(null, List.of(new ToolCall("call-" + scripted.size(), name, "{}")), new TokenUsage(2, 2)));
    }

    @Override public TurnResult runTurn(List<ChatMessage> messages, AIOptions options, List<Object> tools, Predicate<ToolCall> approver) {
        turns++;
        seenHistories.add(List.copyOf(messages));
        seenTools.add(tools == null ? List.of() : List.copyOf(tools));

        TurnResult next = scripted.isEmpty() ? TurnResult.answer("done", new TokenUsage(1, 1)) : scripted.poll();
        if (next.isAnswer()) return next;

        List<ToolCall> outcomes = new ArrayList<>();
        for (ToolCall c : next.toolCalls())
            outcomes.add(approver == null || approver.test(c) ? c.withResult("result of " + c.name()) : c.asDenied());
        return new TurnResult(next.text(), outcomes, next.usage());
    }

    @Override public String streamTurn(List<ChatMessage> messages, AIOptions options, Consumer<String> onChunk) {
        onChunk.accept("chunk");
        return "chunk";
    }

    @Override public <T> T structuredTurn(List<ChatMessage> messages, AIOptions options, Class<T> type) { return null; }

    @Override public String getName() { return name; }
    @Override public void setName(String name) { this.name = name; }
    @Override public String getBaseUrl() { return "http://fake"; }
    @Override public void setBaseUrl(String baseUrl) {}
    @Override public String getApiKey() { return "N/A"; }
    @Override public void setApiKey(String apiKey) {}
    @Override public String getModel() { return model; }
    @Override public void setModel(String model) { this.model = model; }
    @Override public int getTimeoutSeconds() { return 300; }
    @Override public void setTimeoutSeconds(int timeoutSeconds) {}
    @Override public int getRequestsPerMinute() { return 0; }
    @Override public void setRequestsPerMinute(int requestsPerMinute) {}
    @Override public boolean isAvailable() { return true; }

    @Override public List<String> getAvailableModels() { return List.of(model); }
    @Override public String getModelState() { return "loaded"; }
    @Override public List<String> getModelCapabilities() { return List.of(TOOL_USE); }
    @Override public int getMaxContextLength() { return 131072; }
    @Override public int getLoadedContextLength() { return usableContext; }
    void setUsableContext(int usableContext) { this.usableContext = usableContext; }
    @Override public String getQuantization() { return "none"; }
    @Override public String getPublisher() { return "test"; }
    @Override public void refreshModelDetails() {}
    @Override public boolean loadModel(Integer contextLength, Integer ttlSeconds) { return true; }
    @Override public boolean unloadModel() { return true; }
    @Override public boolean ensureModelLoaded() { return true; }

}
