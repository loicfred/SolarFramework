package org.solarframework.ai.spring;

import org.junit.jupiter.api.Test;
import org.solarframework.ai.obj.ChatMessage;
import org.solarframework.ai.dto.ToolCall;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MessageMapperTest {

    private final MessageMapper mapper = new MessageMapper();

    @Test void messagesRoundTripThroughSpringAi() {
        for (ChatMessage original : List.of(ChatMessage.system("persona"), ChatMessage.user("hello"), ChatMessage.assistant("hi"))) {
            ChatMessage back = mapper.fromSpring(mapper.toSpring(original));
            assertEquals(original.getRole(), back.getRole());
            assertEquals(original.getText(), back.getText());
        }
    }

    @Test void toolCallsSurviveTheRoundTrip() {
        ChatMessage original = ChatMessage.assistant("working", List.of(new ToolCall("id-1", "getTime", "{\"tz\":\"UTC\"}")));
        ChatMessage back = mapper.fromSpring(mapper.toSpring(original));

        assertEquals(1, back.getToolCalls().size());
        assertEquals("getTime", back.getToolCalls().getFirst().name());
        assertEquals("{\"tz\":\"UTC\"}", back.getToolCalls().getFirst().argumentsJson());
    }

    /** The backend matches a tool result to its request by id, so the id must survive. */
    @Test void aToolResultKeepsItsCallId() {
        ChatMessage original = ChatMessage.tool(new ToolCall("call-7", "getTime", "{}").withResult("12:00"));
        Message spring = mapper.toSpring(original);

        assertInstanceOf(ToolResponseMessage.class, spring);
        ToolResponseMessage.ToolResponse response = ((ToolResponseMessage) spring).getResponses().getFirst();
        assertEquals("call-7", response.id());
        assertEquals("getTime", response.name());
        assertEquals("12:00", response.responseData());

        ChatMessage back = mapper.fromSpring(spring);
        assertEquals("call-7", back.getToolCalls().getFirst().id());
        assertEquals("12:00", back.getText());
    }

    @Test void aDeniedCallStillProducesAResponseForTheModel() {
        Message spring = mapper.toSpring(ChatMessage.tool(new ToolCall("1", "danger", "{}").asDenied()));
        assertTrue(((ToolResponseMessage) spring).getResponses().getFirst().responseData().toLowerCase().contains("denied"));
    }

    @Test void assistantMessageWithoutToolCallsMapsCleanly() {
        Message m = mapper.toSpring(ChatMessage.assistant("plain"));
        assertInstanceOf(AssistantMessage.class, m);
        assertFalse(((AssistantMessage) m).hasToolCalls());
    }

    @Test void aWholeHistoryMapsInOrder() {
        List<Message> spring = mapper.toSpring(List.of(ChatMessage.system("s"), ChatMessage.user("u"), ChatMessage.assistant("a")));
        assertEquals(3, spring.size());
        assertEquals("s", spring.getFirst().getText());
        assertEquals("a", spring.getLast().getText());
    }
}
