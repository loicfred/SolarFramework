package org.solarframework.ai.spring;

import org.solarframework.ai.dto.ToolCall;
import org.solarframework.ai.obj.ChatMessage;
import org.springframework.ai.chat.messages.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts between the framework's {@link ChatMessage} and Spring AI's {@link Message}. Exists only
 * because the two type systems must not meet outside AIImpl.
 */
public class MessageMapper {

    public Message toSpring(ChatMessage m) {
        return switch (m.getRole()) {
            case SYSTEM -> new SystemMessage(m.getText());
            case USER -> new UserMessage(m.getText());
            case ASSISTANT -> AssistantMessage.builder().content(m.getText()).toolCalls(toSpringToolCalls(m.getToolCalls())).build();
            // A backend matches a tool result to its request by id, so the call travels with it.
            case TOOL -> {
                String id = m.hasToolCalls() ? m.getToolCalls().getFirst().id() : m.getToolName();
                yield ToolResponseMessage.builder().responses(List.of(new ToolResponseMessage.ToolResponse(id, m.getToolName(), m.getText()))).build();
            }
        };
    }

    public List<Message> toSpring(List<ChatMessage> messages) {
        List<Message> out = new ArrayList<>(messages.size());
        for (ChatMessage m : messages) out.add(toSpring(m));
        return out;
    }

    public ChatMessage fromSpring(Message m) {
        return switch (m) {
            case SystemMessage s -> ChatMessage.system(s.getText());
            case UserMessage u -> ChatMessage.user(u.getText());
            case AssistantMessage a -> ChatMessage.assistant(a.getText(), fromSpringToolCalls(a.getToolCalls()));
            case ToolResponseMessage t -> {
                ToolResponseMessage.ToolResponse first = t.getResponses().isEmpty() ? null : t.getResponses().getFirst();
                yield first == null ? ChatMessage.assistant("")
                        : ChatMessage.tool(new ToolCall(first.id(), first.name(), null).withResult(first.responseData()));
            }
            default -> ChatMessage.assistant(m.getText());
        };
    }

    private List<AssistantMessage.ToolCall> toSpringToolCalls(List<ToolCall> calls) {
        List<AssistantMessage.ToolCall> out = new ArrayList<>(calls.size());
        for (ToolCall c : calls) out.add(new AssistantMessage.ToolCall(c.id(), "function", c.name(), c.argumentsJson()));
        return out;
    }

    public List<ToolCall> fromSpringToolCalls(List<AssistantMessage.ToolCall> calls) {
        if (calls == null) return List.of();
        List<ToolCall> out = new ArrayList<>(calls.size());
        for (AssistantMessage.ToolCall c : calls) out.add(new ToolCall(c.id(), c.name(), c.arguments()));
        return out;
    }
}
