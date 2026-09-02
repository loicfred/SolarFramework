package org.solarframework.ai.obj;

import com.google.gson.reflect.TypeToken;
import jakarta.persistence.*;
import org.solarframework.ai.enums.Role;
import org.solarframework.ai.dto.ToolCall;
import org.solarframework.db.spring.DatabaseObject;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.solarframework.json.JSONItem.SimpleGSON;

/**
 * One message of a {@link Conversation}. A row when the conversation is saved, an ordinary object
 * until then — nothing here touches the database unless {@link Conversation#save()} is called.
 * <p>{@code Position} keeps the transcript in order and gives the row its id, so re-saving replaces
 * it rather than adding another. Tool calls stay a JSON column: a message can carry several, they
 * are only ever read with the message, and they mean nothing apart from it.
 */
@Entity
@Table(name = "ai_chat_message")
public class ChatMessage extends DatabaseObject.ID_RECORD_OBJ<String, ChatMessage> {

    private static final Type TOOL_CALL_LIST = new TypeToken<List<ToolCall>>() {}.getType();
    private static final String DENIED = "Denied: the application did not permit this tool call.";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(referencedColumnName = "ID", name = "ConversationID", insertable = false, updatable = false)
    private Conversation conversation;

    @Column(name = "ConversationID") private String conversationId;
    @Column(name = "Position") private Integer position;
    @Column(name = "Role", nullable = false) private String role;
    @Lob @Column(name = "Content") private String content;
    @Column(name = "ToolName") private String toolName;
    // Named for its column, not for what it holds: the write path matches a field to a column by the field's own
    // name, so "toolCallsJson" was silently never written while the transient list below - which did match
    // "ToolCalls" - was handed to the query instead and failed as an unmappable parameter.
    @Lob @Column(name = "ToolCalls") private String toolCalls;
    @Column(name = "CreatedAt") private Instant createdAt = Instant.now();

    @Transient private List<ToolCall> parsedToolCalls;

    protected ChatMessage() {}

    private ChatMessage(Role role, String text, List<ToolCall> toolCalls, String toolName) {
        this.role = role.name();
        this.content = text;
        this.toolName = toolName;
        setToolCalls(toolCalls);
    }

    public static ChatMessage system(String text) { return new ChatMessage(Role.SYSTEM, text, null, null); }
    public static ChatMessage user(String text) { return new ChatMessage(Role.USER, text, null, null); }
    public static ChatMessage assistant(String text) { return new ChatMessage(Role.ASSISTANT, text, null, null); }
    public static ChatMessage assistant(String text, List<ToolCall> toolCalls) { return new ChatMessage(Role.ASSISTANT, text, toolCalls, null); }

    /** Keeps the call itself, because a backend matches a tool result to its request by id. */
    public static ChatMessage tool(ToolCall call) {
        return new ChatMessage(Role.TOOL, call.isExecuted() ? call.result() : DENIED, List.of(call), call.name());
    }

    public Role getRole() { return Role.valueOf(role); }
    public String getText() { return content; }
    public void setText(String text) { this.content = text; }
    public String getToolName() { return toolName; }
    public Instant getCreatedAt() { return createdAt; }
    public Integer getPosition() { return position; }
    public String getConversationId() { return conversationId; }

    /** Faults itself on first access; null only on an instance built with {@code new}. */
    public Conversation getConversation() { return conversation; }

    public List<ToolCall> getToolCalls() {
        if (parsedToolCalls == null)
            parsedToolCalls = toolCalls == null ? new ArrayList<>() : new ArrayList<>(SimpleGSON.<List<ToolCall>>fromJson(toolCalls, TOOL_CALL_LIST));
        return parsedToolCalls;
    }

    public void setToolCalls(List<ToolCall> calls) {
        parsedToolCalls = calls == null ? new ArrayList<>() : new ArrayList<>(calls);
        toolCalls = parsedToolCalls.isEmpty() ? null : SimpleGSON.toJson(parsedToolCalls);
    }

    /** Called by the conversation as it saves, so the row knows where it belongs. */
    ChatMessage placeIn(String conversationId, int position) {
        this.conversationId = conversationId;
        this.position = position;
        this.ID = conversationId + ":" + position;
        return this;
    }

    public boolean isSystem() { return getRole().isSystem(); }
    public boolean hasToolCalls() { return !getToolCalls().isEmpty(); }

    /**
     * Rough size in tokens. Deliberately an estimate: an exact count needs a model-specific
     * tokenizer, and a memory budget only has to be conservative.
     * <p>A call's <em>result</em> is deliberately not counted, because it is never sent twice: on a tool
     * message the result already is the content, and on the assistant message that asked for the call only
     * the name and arguments ever travel. Counting it here charged a tool result to both messages and made
     * a long tool run look about twice its real size.
     */
    public int estimateTokens() {
        int n = 4 + (content == null ? 0 : content.length() / 4);
        for (ToolCall c : getToolCalls()) n += 8 + (c.argumentsJson() == null ? 0 : c.argumentsJson().length() / 4);
        return n;
    }

    /** One line of message text for a log: whole when short, ellipsised with its real size when not. */
    public static String brief(String text) {
        if (text == null) return "";
        String flat = text.strip().replaceAll("\\s+", " ");
        return flat.length() <= 200 ? flat : flat.substring(0, 200) + "… (" + text.length() + " chars)";
    }

    @Override public String toString() { return role + ": " + (content == null ? "" : content) + (hasToolCalls() ? " " + getToolCalls() : ""); }
}
