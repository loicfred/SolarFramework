package org.solarframework.ai.obj;

import jakarta.persistence.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.solarframework.ai.Chatbot;
import org.solarframework.ai.Prompts;
import org.solarframework.ai.dto.AIOptions;
import org.solarframework.ai.dto.AgentRun;
import org.solarframework.ai.dto.TokenUsage;
import org.solarframework.ai.dto.ToolCall;
import org.solarframework.ai.dto.TurnResult;
import org.solarframework.ai.memory.Memory;
import org.solarframework.db.spring.DatabaseObject;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.solarframework.db.spring.DatabaseRegistry.SolarDBManager;

/**
 * One conversation with a {@link Chatbot}: the transcript, plus the loop that adds to it.
 * <p>An ordinary object until {@link #save()} is called, and a database row afterwards — nothing
 * here reaches the database otherwise, so a chatbot that never saves needs no data source.
 * <p>Chatbot, assistant and agent need no separate types: {@link #send} is a turn that answers,
 * {@link #run} is the same loop reported step by step. The loop reaches the model only through
 * {@link org.solarframework.ai.IAIService}, so it is provider-neutral.
 */
@Entity
@Table(name = "ai_conversation")
public class Conversation extends DatabaseObject.ID_RECORD_OBJ<String, Conversation> {

    private static final Logger log = LoggerFactory.getLogger(Conversation.class);
    /** Puts the conversation id in front of every log line the turn produces, this class or the service. */
    public static final String LOG_TAG = "conversation";
    /** And which instruction it is running under, so the persona never has to be logged as text. */
    public static final String LOG_INSTRUCTION = "instruction";

    /**
     * Mapped by the owning side, which is {@link ChatMessage#conversation} and its {@code ConversationID} join
     * column. Declaring the join column here instead made this the only inverse collection in the framework without
     * a {@code mappedBy}, and {@code DBInstanceService.oneToManyFieldsOf} keeps only the ones that have it — so this
     * collection was never given a lazy loader and a resumed conversation came back with no messages at all.
     */
    @OneToMany(mappedBy = "conversation", fetch = FetchType.LAZY)
    @OrderBy("Position")
    private List<ChatMessage> messages;

    @Column(name = "Owner") private String owner;
    @Column(name = "BotID") private String botId;
    @Column(name = "Model") private String model;
    @Lob @Column(name = "Summary") private String summary;
    @Column(name = "PromptTokens") private Long promptTokens = 0L;
    @Column(name = "CompletionTokens") private Long completionTokens = 0L;
    @Column(name = "CreatedAt") private Instant createdAt = Instant.now();
    @Column(name = "UpdatedAt") private Instant updatedAt = Instant.now();
    @Column(name = "ExpiresAt") private Instant expiresAt;

    @Transient
    private Chatbot bot;
    @Transient
    private Duration keepFor;

    protected Conversation() {}

    public Conversation(Chatbot bot, String id, String owner) {
        this.ID = id;
        this.owner = owner;
        this.messages = new ArrayList<>();
        this.bot = bot;
        String persona = bot.getSystemPromptFor(owner);
        messages.add(ChatMessage.system(persona != null ? persona : Prompts.conversationist(owner)));
    }

    /** The stored conversation, or a new one under that id. */
    public static Conversation resume(Chatbot bot, String id, String owner) {
        return load(bot, id).orElseGet(() -> new Conversation(bot, id, owner));
    }

    /** An expired conversation is already gone as far as a reader is concerned, whether or not the sweeper has run yet. */
    public static Optional<Conversation> load(Chatbot bot, String id) {
        return SolarDBManager.<Conversation>getById(Conversation.class, id).filter(c -> !c.isExpired()).map(c -> c.attach(bot));
    }

    /** Conversations whose keeping time has passed, for whatever sweeps them. */
    public static List<Conversation> expired() {
        return SolarDBManager.getAllWhere(Conversation.class, "ExpiresAt IS NOT NULL AND ExpiresAt < ?", Instant.now());
    }

    /** Conversation ids belonging to one owner, newest first. */
    public static List<String> idsOf(String owner) {
        return SolarDBManager.getAllWhere("SELECT ID FROM ai_conversation", String.class, "Owner = ? ORDER BY UpdatedAt DESC", owner);
    }

    /**
     * A row read from the database has no chatbot until one is given to it.
     * <p>The persona is taken from the chatbot rather than from the stored transcript, so a bot whose instructions were
     * rewritten answers as its new self on the very next turn instead of carrying the old ones until somebody starts
     * over. It is the chatbot's, not the conversation's — the transcript only ever held a copy.
     */
    public Conversation attach(Chatbot bot) {
        this.bot = bot;
        String persona = bot == null ? null : bot.getSystemPromptFor(owner);
        if (persona == null) return this;
        // A transcript that comes back without a persona is given one rather than left alone: rewriting only what is
        // already there meant a row whose messages did not load was sent to the model as the bare question, and a
        // model with no instructions answers nothing like the one the administrator described.
        if (!getMessages().isEmpty() && getMessages().getFirst().isSystem()) getMessages().getFirst().setText(persona);
        else getMessages().addFirst(ChatMessage.system(persona));
        return this;
    }

    public String getId() { return ID; }
    public String getOwner() { return owner; }
    public Chatbot getBot() { return bot; }
    public String getBotId() { return botId; }
    public String getModel() { return model; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isExpired() { return expiresAt != null && expiresAt.isBefore(Instant.now()); }

    /**
     * Makes the conversation temporary. Every save pushes the removal this far forward again, so a conversation still
     * being used is never swept out from under its reader, and one nobody came back to falls due on its own.
     * <p>The window is not stored, only the date it produces - a resumed conversation is told again how long to keep
     * itself, the same way it is handed its chatbot again.
     */
    public Conversation keepFor(Duration window) { keepFor = window; return rollExpiry(); }
    private Conversation rollExpiry() { if (keepFor != null) expiresAt = Instant.now().plus(keepFor); return this; }

    /**
     * Removes the conversation and its whole transcript for good: something kept only for a while is not a record to
     * flag as deleted and leave behind.
     * <p>A message that was never saved has no id yet, and deleting by a null key would take out whatever the database
     * matched instead - so only what was actually written is removed.
     */
    public void discard() {
        // Deleted by the id they carry rather than by walking the loaded transcript: a conversation read back
        // without its messages would otherwise take its row away and leave every one of them orphaned, with
        // nothing left pointing at them to find them by.
        SolarDBManager.getServiceByEntity(ChatMessage.class).doUpdate(ChatMessage.class, "DELETE FROM ai_chat_message WHERE ConversationID = ?", ID);
        getMessages().clear();
        TrueDelete();
    }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    /** The rows fault in on first access, already in order. */
    public List<ChatMessage> getMessages() {
        if (messages == null) messages = new ArrayList<>();
        return messages;
    }

    /**
     * The turns a person actually said and heard, for anything that draws the conversation back.
     * <p>The persona and the tool traffic are the machinery: the system message is instructions rather than
     * speech, a tool message is a result nobody typed, and the assistant message that asks for a tool carries
     * no text of its own. A consumer drawing a transcript should not have to know any of that.
     */
    public List<ChatMessage> getSpokenMessages() {
        return getMessages().stream().filter(m -> m.getRole().isUser() || m.getRole().isAssistant())
                .filter(m -> m.getText() != null && !m.getText().isBlank() && !m.hasToolCalls()).toList();
    }

    public TokenUsage getUsage() {
        return new TokenUsage(promptTokens == null ? 0 : promptTokens, completionTokens == null ? 0 : completionTokens);
    }

    public void setUsage(TokenUsage usage) {
        promptTokens = usage.prompt();
        completionTokens = usage.completion();
    }

    /**
     * Sends one message and returns the answer, adding both to the transcript.
     * <p>The model may call tools on the way — each round trip is a step, bounded by the chatbot's
     * {@code maxSteps} — but this returns only once it has actually answered. Use {@link #run} to see
     * the steps it took.
     *
     * @param extraTools tool objects for this message only, on top of the chatbot's own
     */
    public String send(String message, Object... extraTools) {
        return loop(message, bot.getChatOptions(), extraTools).text();
    }

    /**
     * The same loop as {@link #send}, reported step by step: every tool call, its result and the token
     * usage of each turn come back in the {@link AgentRun}, and {@code onStep} fires as they happen.
     * <p>Runs under the chatbot's agent settings — more room to think and to call tools repeatedly.
     */
    public AgentRun run(String goal, Object... extraTools) {
        return loop(goal, bot.getAgentOptions(), extraTools);
    }

    /**
     * Tool execution is never left to the backend, so every call can be reported and approved, and
     * the run stops at a known bound rather than when the model happens to be satisfied.
     */
    public AgentRun loop(String message, AIOptions options, Object... extraTools) {
        tag();
        try { return runLoop(message, options, extraTools); } finally { untag(); }
    }

    /** What every log line of this conversation is prefixed with, here and in the service. */
    public String tag() {
        MDC.put(LOG_TAG, ID);
        MDC.put(LOG_INSTRUCTION, bot.getInstruction());
        return "[" + bot.getServiceName() + "][" + ID + "][" + bot.getInstruction() + "]";
    }

    private void untag() { MDC.remove(LOG_TAG); MDC.remove(LOG_INSTRUCTION); }

    private AgentRun runLoop(String message, AIOptions options, Object... extraTools) {
        getMessages().add(ChatMessage.user(message));
        List<Object> tools = collectTools(extraTools);
        List<AgentRun> steps = new ArrayList<>();
        String text = null;
        boolean completed = false;

        for (int i = 0; i < bot.getMaxSteps(); i++) {
            TurnResult turn = bot.getService().runTurn(applyMemory(), options, tools, bot::isToolApproved);
            // Only a tool round-trip is worth a line: the service already logged the turn that asked for it,
            // and the answer that ends the loop. One line per call, carrying what the call actually returned.
            for (ToolCall c : turn.toolCalls())
                log.info("{} step {}/{}: {} -> {}", tag(), i + 1, bot.getMaxSteps(), c.name(), c.isExecuted() ? ChatMessage.brief(c.result()) : "denied");
            setUsage(getUsage().plus(turn.usage()));
            AgentRun step = AgentRun.step(turn.text(), turn.toolCalls(), turn.usage());
            bot.reportStep(step);
            steps.add(step);

            if (turn.isAnswer()) { text = turn.text(); getMessages().add(ChatMessage.assistant(text)); completed = true; break; }

            getMessages().add(ChatMessage.assistant(turn.text(), turn.toolCalls()));
            for (ToolCall call : turn.toolCalls()) getMessages().add(ChatMessage.tool(call));
        }

        if (!completed) {
            log.warn("{} stopped after {} step(s) without reaching an answer", tag(), steps.size());
            getMessages().add(ChatMessage.assistant(text = "Stopped after " + steps.size() + " step(s) without reaching an answer."));
        }
        return AgentRun.of(message, text, steps, completed);
    }

    /**
     * {@link #send}, delivered as it is generated: {@code onChunk} receives each fragment in order on
     * the calling thread, and the complete reply is returned and added to the transcript at the end.
     * <p>No tools: a streamed turn hands back text as it arrives, with no complete reply for the tool
     * loop to act on.
     */
    public String sendStreaming(String message, Consumer<String> onChunk) {
        tag();
        getMessages().add(ChatMessage.user(message));
        try {
            String text = bot.getService().streamTurn(applyMemory(), bot.getChatOptions(), onChunk);
            getMessages().add(ChatMessage.assistant(text));
            return text;
        } finally { untag(); }
    }

    /**
     * Asks for JSON matching {@code type} and deserialises it, instead of parsing prose.
     * <p>Runs deterministically rather than at the chatbot's conversational temperature: filling a
     * fixed set of fields is extraction, not conversation, and warmth there only produces null
     * fields and invented names. Only the model is taken from the chatbot.
     */
    public <T> T sendAs(String message, Class<T> type) {
        return sendAs(message, type, AIOptions.ITEM_CHOOSER.withModel(bot.getChatOptions().model()).withMaxOutputTokens(700));
    }

    /** The same, for a caller that has already decided how the extraction should run. */
    public <T> T sendAs(String message, Class<T> type, AIOptions options) {
        getMessages().add(ChatMessage.user(message));
        tag();
        try {
            T value = bot.getService().structuredTurn(applyMemory(), options, type);
            getMessages().add(ChatMessage.assistant(String.valueOf(value)));
            return value;
        } finally { untag(); }
    }

    /** The conversation and its whole transcript, in one upsert each. */
    public void save() {
        // the model the turn actually ran on, which is the bot's own when it overrides the service's
        if (bot != null) { model = bot.getModelInUse(); botId = bot.getName(); }
        updatedAt = Instant.now();
        rollExpiry();
        Upsert();

        List<ChatMessage> current = getMessages();
        for (int i = 0; i < current.size(); i++) current.get(i).placeIn(ID, i);
        DatabaseObject.UpsertAll(current);
    }

    public void addMessage(ChatMessage message) { getMessages().add(message); }

    /** Drops the history but keeps the persona. */
    public void clearHistory() {
        ChatMessage persona = getMessages().isEmpty() ? null : getMessages().getFirst();
        getMessages().clear();
        summary = null;
        if (persona != null && persona.isSystem()) getMessages().add(persona);
    }

    public boolean isEmpty() { return getMessages().isEmpty(); }
    public int getMessageCount() { return getMessages().size(); }
    public ChatMessage getLastMessage() { return getMessages().isEmpty() ? null : getMessages().getLast(); }
    public int estimateTokens() { return getMessages().stream().mapToInt(ChatMessage::estimateTokens).sum(); }
    public String getSystemPrompt() { return getMessages().isEmpty() ? null : getMessages().getFirst().getText(); }

    private List<ChatMessage> applyMemory() {
        Memory memory = bot.getMemory();
        if (memory == null) return List.copyOf(getMessages());
        Memory.Result result = memory.prepare(getMessages(), summary, bot.getService().getUsableContext());
        summary = result.summary();
        return result.messages();
    }

    private List<Object> collectTools(Object[] extra) {
        List<Object> all = new ArrayList<>(bot.getTools());
        if (extra != null) for (Object o : extra) if (o != null && all.stream().noneMatch(x -> x.getClass().equals(o.getClass()))) all.add(o);
        return all;
    }

    @Override public String toString() { return "Conversation[" + ID + ", " + getMessageCount() + " messages]"; }
}
