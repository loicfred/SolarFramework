package org.solarframework.ai.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.solarframework.ai.*;
import org.solarframework.ai.dto.*;
import org.solarframework.ai.obj.ChatMessage;
import org.solarframework.ai.obj.Conversation;
import org.slf4j.MDC;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.solarframework.ai.exception.AIException;
import org.solarframework.ai.lmstudio.LMStudioModelManager;
import org.solarframework.core.util.RateLimiter;
import org.solarframework.core.util.StringUtils;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * {@link IAIService} over Spring AI — one endpoint, one default model.
 * <p>Not a Spring bean and not seeded from any property file: every service is built by
 * {@link AIManager#makeNewService} from whatever the host has stored, carries its own settings and
 * builds its own model lazily — the same shape as {@code DatabaseService} owning its pool. That is
 * what lets the whole AI stack live inside a plugin, where there is no application context to read
 * properties from and no component scan to find a bean.
 */
public class AIService implements IAIService {

    private static final Logger log = LoggerFactory.getLogger(AIService.class);
    private static final AtomicLong CALLS = new AtomicLong();

    private String name = DEFAULT;
    private final ToolCallingManager toolCallingManager;
    private final MessageMapper messages = new MessageMapper();
    private final ToolAdapter toolAdapter = new ToolAdapter();

    private String baseUrl;
    private String apiKey = "N/A";
    private String model;
    private int timeoutSeconds = 300;
    private int requestsPerMinute;
    private final RateLimiter limiter = new RateLimiter();
    private ChatModel chatModel;
    private LMStudioModelManager models;
    private LMStudioModelManager.Model info;
    private long infoAt;

    /** The only constructor. A service arrives with nothing configured and is told its endpoint, key and model
     * through the setters, by {@link AIManager#makeNewService} — the same shape as {@code DatabaseService}. */
    public AIService(ToolCallingManager toolCallingManager) { this.toolCallingManager = toolCallingManager; }

    @Override public String getName() { return name; }
    @Override public void setName(String name) { this.name = name; }
    @Override public String getBaseUrl() { return baseUrl; }
    @Override public String getApiKey() { return apiKey; }
    @Override public String getModel() { return model; }
    @Override public int getTimeoutSeconds() { return timeoutSeconds; }
    @Override public int getRequestsPerMinute() { return requestsPerMinute; }
    @Override public void setRequestsPerMinute(int requestsPerMinute) { this.requestsPerMinute = requestsPerMinute; }
    @Override public void setModel(String model) { this.model = model; }
    @Override public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; reset(); }
    @Override public void setApiKey(String apiKey) { this.apiKey = apiKey; reset(); }
    @Override public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; reset(); }

    /** Drops what was built from the old settings so the next call rebuilds against the new ones. */
    private void reset() { models = null; chatModel = null; }

    @Override public boolean isAvailable() { return models().isAvailable(); }

    private LMStudioModelManager models() { return models == null ? models = new LMStudioModelManager(baseUrl) : models; }

    /** The configured model's own details, cached briefly — they only change when it is loaded or unloaded. */
    private LMStudioModelManager.Model info() {
        if (info != null && System.currentTimeMillis() - infoAt < 5000) return info;
        infoAt = System.currentTimeMillis();
        return info = models().get(model).orElse(null);
    }

    @Override public void refreshModelDetails() { info = null; infoAt = 0; }

    @Override public List<String> getAvailableModels() { return models().list().stream().map(LMStudioModelManager.Model::id).toList(); }
    @Override public String getModelState() { return info() == null ? null : info().state(); }
    @Override public List<String> getModelCapabilities() { return info() == null ? List.of() : info().capabilities(); }
    @Override public int getMaxContextLength() { return info() == null ? 0 : info().maxContextLength(); }
    @Override public int getLoadedContextLength() { return info() == null ? 0 : info().loadedContextLength(); }
    @Override public String getQuantization() { return info() == null ? null : info().quantization(); }
    @Override public String getPublisher() { return info() == null ? null : info().publisher(); }

    @Override public boolean loadModel(Integer contextLength, Integer ttlSeconds) { return afterModelChange("Loading", models().load(model, contextLength, ttlSeconds)); }
    @Override public boolean unloadModel() { return afterModelChange("Unloading", models().unload(model)); }
    @Override public boolean ensureModelLoaded() { return afterModelChange("Ensuring loaded", models().ensureLoaded(model)); }

    private boolean afterModelChange(String what, boolean result) {
        log.info("{} model {} on service {}: {}", what, model, name, result ? "done" : "failed");
        refreshModelDetails();
        return result;
    }

    /**
     * One model turn. Tool execution is taken away from Spring AI so every call can be offered to
     * {@code approver} first, and so the caller keeps control of whether to go round again.
     */
    @Override public TurnResult runTurn(List<ChatMessage> history, AIOptions o, List<Object> tools, Predicate<ToolCall> approver) {
        List<ToolCallback> callbacks = toolAdapter.callbacks(tools == null ? new Object[0] : tools.toArray());
        Prompt prompt = new Prompt(messages.toSpring(history), toSpring(o, callbacks, false));
        ChatResponse response = callModel(prompt);

        AssistantMessage out = response.getResult().getOutput();
        TokenUsage usage = usageOf(response);
        if (!response.hasToolCalls()) return TurnResult.answer(out.getText(), usage);

        List<AssistantMessage.ToolCall> requested = out.getToolCalls(), approved = new ArrayList<>();
        for (AssistantMessage.ToolCall c : requested)
            if (approver == null || approver.test(new ToolCall(c.id(), c.name(), c.arguments()))) approved.add(c);

        Map<String, String> results = approved.isEmpty() ? Map.of() : execute(prompt, response, out, approved, requested.size());
        List<ToolCall> outcomes = new ArrayList<>();
        for (AssistantMessage.ToolCall c : requested) {
            ToolCall call = new ToolCall(c.id(), c.name(), c.arguments());
            String result = results.get(c.id());
            outcomes.add(result != null ? call.withResult(result) : call.asDenied());
        }
        return new TurnResult(out.getText(), outcomes, usage);
    }

    /** Runs the approved calls and returns their results by call id. */
    private Map<String, String> execute(Prompt prompt, ChatResponse response, AssistantMessage out, List<AssistantMessage.ToolCall> approved, int requested) {
        ChatResponse executable = approved.size() == requested ? response
                : new ChatResponse(List.of(new Generation(AssistantMessage.builder().content(out.getText()).toolCalls(approved).build())));
        Map<String, String> byId = new LinkedHashMap<>();
        for (Message m : toolCallingManager.executeToolCalls(prompt, executable).conversationHistory())
            if (m instanceof ToolResponseMessage t) for (ToolResponseMessage.ToolResponse r : t.getResponses()) byId.put(r.id(), r.responseData());
        return byId;
    }

    @Override public String streamTurn(List<ChatMessage> history, AIOptions o, Consumer<String> onChunk) {
        return stream(messages.toSpring(history), o, onChunk);
    }

    @Override public <T> T structuredTurn(List<ChatMessage> history, AIOptions o, Class<T> type) {
        StructuredOutput<T> shape = new StructuredOutput<>(type);
        OpenAiChatOptions options = toSpring(o.withTools(false), List.of(), false, shape.getJsonSchema(), shape.getName());
        return shape.parse(textOf(callModel(new Prompt(messages.toSpring(history), options))));
    }

    /**
     * @param internalToolExecution true lets Spring AI run the tool loop itself (one turn, tools
     *                              resolved transparently); false returns the tool calls to us,
     *                              which is what an observable, bounded agent loop needs.
     */
    OpenAiChatOptions toSpring(AIOptions o, List<ToolCallback> tools, boolean internalToolExecution) {
        return toSpring(o, tools, internalToolExecution, null, null);
    }

    /**
     * @param jsonSchema when set, the backend is told to emit exactly this shape rather than being
     *                   asked for it in the prompt — see {@link StructuredOutput}
     */
    OpenAiChatOptions toSpring(AIOptions o, List<ToolCallback> tools, boolean internalToolExecution, String jsonSchema, String schemaName) {
        OpenAiChatOptions.Builder b = OpenAiChatOptions.builder()
                .model(o.model() != null ? o.model() : model)
                .maxTokens(o.maxOutputTokens()).temperature(o.temperature()).topP(o.topP())
                .frequencyPenalty(o.frequencyPenalty()).presencePenalty(o.presencePenalty());
        if (!o.stop().isEmpty()) b.stop(o.stop());
        if (o.allowTools() && tools != null && !tools.isEmpty()) b.toolCallbacks(tools).internalToolExecutionEnabled(internalToolExecution);
        if (jsonSchema != null) b.responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_SCHEMA)
                .jsonSchema(ResponseFormat.JsonSchema.builder().name(schemaName).schema(jsonSchema).strict(true).build()).build());
        return b.build();
    }

    /**
     * Waits for this service's own rate allowance rather than refusing: a caller asking an AI a
     * question wants the answer a moment later, not an exception.
     */
    private void awaitPermit() {
        if (requestsPerMinute <= 0) return;
        RateLimiter.Limit limit = new RateLimiter.Limit(requestsPerMinute / 60d, Math.max(1, requestsPerMinute), 0, 0);
        long wait;
        while ((wait = limit.takePermit(limiter, name, System.nanoTime())) > 0) {
            try {
                Thread.sleep(Math.min(wait, 5) * 1000L);
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
                throw new AIException("Interrupted while waiting for the rate limit on service " + name);
            }
        }
    }

    private ChatResponse callModel(Prompt prompt) {
        awaitPermit();
        ChatModel chat = chatModel();
        long start = System.nanoTime();
        String tag = tag();
        List<Message> msgs = prompt.getInstructions();
        logNew(tag, msgs);
        try {
            ChatResponse response = chat.call(prompt);
            log.info("{} {} ({} ms, {}, {} sent)", tag, replyOf(response), (System.nanoTime() - start) / 1_000_000, usageOf(response), StringUtils.plural(msgs.size(), "message"));
            return response;
        } catch (AIException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("{} request to {} failed after {} ms: {}", tag, baseUrl, (System.nanoTime() - start) / 1_000_000, e.getMessage());
            throw new AIException("AI request to " + baseUrl + " failed: " + e.getMessage(), e);
        }
    }

    /**
     * What every log line for one model call is prefixed with: the service, then the conversation it belongs
     * to when there is one, otherwise a number for this call — a one-shot prompt is a conversation of one turn.
     */
    private String tag() {
        String conversation = MDC.get(Conversation.LOG_TAG), instruction = MDC.get(Conversation.LOG_INSTRUCTION);
        return "[" + name + "][" + (conversation != null ? conversation : String.format("C%05d", CALLS.incrementAndGet())) + "]"
                + (instruction != null ? "[" + instruction + "]" : "");
    }

    /**
     * The lines this call actually adds to the transcript, one per message, so the log reads as the
     * conversation itself. Every request resends the whole history — the count on the reply line is
     * that whole history, not a batch of questions — and the assistant's own turns were already
     * logged as they came back, so what is new is whatever follows the last assistant message.
     */
    private static void logNew(String tag, List<Message> msgs) {
        int from = 0;
        for (int i = msgs.size() - 1; i >= 0; i--) if (msgs.get(i).getMessageType() == MessageType.ASSISTANT) { from = i + 1; break; }
        if (from == msgs.size()) log.info("{} (continuing on {})", tag, StringUtils.plural(msgs.size(), "message"));
        for (Message m : msgs.subList(from, msgs.size())) if (worthLogging(m)) log.info("{} {}", tag, describe(m));
    }

    /**
     * The persona is named in the tag rather than printed, and a conversation logs each tool result
     * beside the step that asked for it — so only log those here when there is no conversation to.
     */
    private static boolean worthLogging(Message m) {
        return switch (m.getMessageType()) {
            case SYSTEM -> false;
            case TOOL -> MDC.get(Conversation.LOG_TAG) == null;
            default -> true;
        };
    }

    /** Who said it and what they said — a tool result carries its text in its responses, not in {@code getText}. */
    private static String describe(Message m) {
        if (m instanceof ToolResponseMessage t)
            return t.getResponses().stream().map(r -> "Tool " + r.name() + " -> " + ChatMessage.brief(r.responseData())).collect(Collectors.joining("; "));
        return StringUtils.capitalize(m.getMessageType().getValue()) + ": " + ChatMessage.brief(m.getText());
    }

    /** A turn that only asks for tools has no text at all, so the calls themselves are the reply. */
    private static String replyOf(ChatResponse r) {
        AssistantMessage m = r == null || r.getResult() == null ? null : r.getResult().getOutput();
        if (m == null) return "Assistant: (no reply)";
        String text = ChatMessage.brief(m.getText());
        if (!m.hasToolCalls()) return "Assistant: " + (text.isEmpty() ? "(empty)" : text);
        return "Assistant wants " + m.getToolCalls().stream().map(c -> c.name() + "(" + ChatMessage.brief(c.arguments()) + ")").collect(Collectors.joining(", ")) + (text.isEmpty() ? "" : " | Assistant: " + text);
    }

    String stream(List<Message> msgs, AIOptions o, Consumer<String> onChunk) {
        StringBuilder full = new StringBuilder();
        ChatModel chat = chatModel();
        String tag = tag();
        long start = System.nanoTime();
        logNew(tag, msgs);
        try {
            chat.stream(new Prompt(msgs, toSpring(o, List.of(), true))).toStream().forEach(r -> {
                String chunk = r.getResult() == null ? null : r.getResult().getOutput().getText();
                if (chunk != null && !chunk.isEmpty()) { full.append(chunk); onChunk.accept(chunk); }
            });
        } catch (RuntimeException e) {
            log.warn("{} stream from {} failed: {}", tag, baseUrl, e.getMessage());
            throw new AIException("AI stream from " + baseUrl + " failed: " + e.getMessage(), e);
        }
        log.info("{} Assistant (streamed): {} ({} ms, {} sent)", tag, ChatMessage.brief(full.toString()), (System.nanoTime() - start) / 1_000_000, StringUtils.plural(msgs.size(), "message"));
        return full.toString();
    }

    /**
     * Built here rather than injected, so every service owns its endpoint the way a
     * {@code DatabaseService} owns its pool, and so the HTTP timeout is ours to set.
     * <p>Spring's stock client gives up after a few seconds. A local model needs far longer: it
     * answers in seconds even when warm, and the first request to a cold model waits out a
     * multi-gigabyte load.
     */
    ChatModel chatModel() {
        if (chatModel != null) return chatModel;
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory();
        requests.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        OpenAiApi api = OpenAiApi.builder().baseUrl(baseUrl)
                .apiKey(apiKey == null || apiKey.isBlank() ? "N/A" : apiKey)
                .restClientBuilder(RestClient.builder().requestFactory(requests))
                .build();
        return chatModel = OpenAiChatModel.builder().openAiApi(api).toolCallingManager(toolCallingManager).build();
    }

    ToolCallingManager toolCallingManager() { return toolCallingManager; }
    MessageMapper messages() { return messages; }
    ToolAdapter toolAdapter() { return toolAdapter; }

    private static String textOf(ChatResponse r) { return r == null || r.getResult() == null ? null : r.getResult().getOutput().getText(); }

    static TokenUsage usageOf(ChatResponse response) {
        Usage u = response == null || response.getMetadata() == null ? null : response.getMetadata().getUsage();
        if (u == null) return TokenUsage.NONE;
        return new TokenUsage(u.getPromptTokens() == null ? 0 : u.getPromptTokens(), u.getCompletionTokens() == null ? 0 : u.getCompletionTokens());
    }

    @Override public String toString() { return "AIService[" + name + " -> " + baseUrl + (model == null ? "" : ", " + model) + "]"; }
}
