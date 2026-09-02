package org.solarframework.ai;

import org.solarframework.ai.dto.AIOptions;
import org.solarframework.ai.dto.ModelFacts;
import org.solarframework.ai.enums.ProgrammingLanguage;
import org.solarframework.ai.obj.Conversation;
import org.solarframework.ai.obj.ChatMessage;
import org.solarframework.ai.dto.ToolCall;
import org.solarframework.ai.dto.TurnResult;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * One endpoint, one model, and every call made over it — the AI counterpart of a data source.
 * <p>Only the default service reads {@code application.properties}. Every other one is built by
 * {@link IAIManager} and configured through these setters, so a getter's fallback is the real
 * default everywhere else.
 */
public interface IAIService {

    String DEFAULT = "Default";
    String TOOL_USE = "tool_use";
    String VISION = "vision";

    String getName();
    void setName(String name);

    default boolean isDefault() { return DEFAULT.equals(getName()); }

    String getBaseUrl();
    void setBaseUrl(String baseUrl);

    String getApiKey();
    void setApiKey(String apiKey);

    String getModel();
    void setModel(String model);

    /**
     * A local model answers in seconds, not milliseconds, and loads on first use — Spring's own
     * client default is far too short for that, so this is deliberately generous.
     */
    int getTimeoutSeconds();
    void setTimeoutSeconds(int timeoutSeconds);

    /** Requests a minute this service may make. 0 or less is unlimited. */
    int getRequestsPerMinute();
    void setRequestsPerMinute(int requestsPerMinute);

    boolean isAvailable();

    /**
     * The same thing as {@link #describe()}, as data rather than prose, for a screen that has to show it.
     * <p>Every part of it is a call to the model server, so a server that is not answering leaves the
     * answer unknown rather than claiming either way — a screen told "no tools" about a model it never
     * reached would warn about a limitation nobody has.
     */
    default ModelFacts facts() {
        try {
            return new ModelFacts(getAvailableModels(), getModel(), isModelSupportingTools());
        } catch (Exception ex) {
            return new ModelFacts(List.of(), getModel(), null);
        }
    }

    /** What this service is, in one line, for the lifecycle logs. */
    default String describe() {
        return "model " + getModel() + " at " + getBaseUrl() + " (timeout " + getTimeoutSeconds() + "s"
                + (getRequestsPerMinute() > 0 ? ", " + getRequestsPerMinute() + " req/min" : "") + ")";
    }

    // --- the model this service runs ---

    /** Model ids this endpoint offers. Inspecting another one means pointing a service at it. */
    List<String> getAvailableModels();

    /** The backend's own word, e.g. "loaded"; null when it cannot be reached. */
    String getModelState();

    default boolean isModelLoaded() { return "loaded".equalsIgnoreCase(getModelState()); }

    List<String> getModelCapabilities();

    default boolean isModelSupporting(String capability) {
        return getModelCapabilities().stream().anyMatch(c -> c.equalsIgnoreCase(capability));
    }

    default boolean isModelSupportingTools() { return isModelSupporting(TOOL_USE); }

    int getMaxContextLength();

    /** What the model actually loaded with, which is often far below its maximum. */
    int getLoadedContextLength();

    default int getUsableContext() { return getLoadedContextLength() > 0 ? getLoadedContextLength() : getMaxContextLength(); }

    String getQuantization();

    String getPublisher();

    void refreshModelDetails();

    boolean loadModel(Integer contextLength, Integer ttlSeconds);

    boolean unloadModel();

    /** Loads only if it is not already loaded. Backends that load just-in-time report true. */
    boolean ensureModelLoaded();

    // --- what a conversation cannot do without a backend ---

    /**
     * A service is an endpoint and a model, not a speaker: it holds no persona, tools or history, so
     * nothing here talks. Asking a model something is {@link Chatbot}'s work — {@code service.createChatbot()
     * .prompt("Hi")} for a single turn, {@link #createConversation()} for several — and every one of those
     * is a conversation that reaches the backend through the three turns below.
     */

    /**
     * One model turn against an existing history. Tools the model asks for are offered to
     * {@code approver} and only then executed; the calls come back carrying their results, so the
     * caller — not the backend — decides whether to go round again.
     */
    TurnResult runTurn(List<ChatMessage> messages, AIOptions options, List<Object> tools, Predicate<ToolCall> approver);

    String streamTurn(List<ChatMessage> messages, AIOptions options, Consumer<String> onChunk);

    <T> T structuredTurn(List<ChatMessage> messages, AIOptions options, Class<T> type);

    /** A new bot on this service every call — never a shared or registered one. {@code build()} finishes it. */
    default Chatbot.Builder createChatbot() { return Chatbot.builder(this); }

    default Conversation createConversation() { return createChatbot().build().startConversation(); }
}
