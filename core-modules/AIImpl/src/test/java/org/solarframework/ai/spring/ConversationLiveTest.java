package org.solarframework.ai.spring;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.solarframework.ai.Chatbot;
import org.solarframework.ai.obj.Conversation;
import org.solarframework.ai.memory.Memory;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Multi-turn behaviour against a real model. Self-skips without LM Studio. */
@Tag("live")
class ConversationLiveTest {

    private static AIService service;

    @BeforeAll static void connect() {
        service = TestAI.service("live-test", "http://localhost:1234", "openai/gpt-oss-20b");
        assumeTrue(service.isAvailable(), "LM Studio is not running on localhost:1234");
    }

    private Chatbot.Builder terseBot() { return service.createChatbot().systemPrompt("You are terse."); }

    @Test void remembersEarlierTurns() {
        Conversation c = terseBot().build().startConversation();
        c.send("My favourite colour is teal. Remember it.");
        String reply = c.send("What is my favourite colour? Reply with one word.");

        assertTrue(reply.toLowerCase().contains("teal"), "got: " + reply);
        assertEquals(5, c.getMessageCount(), "persona + 2 user + 2 assistant");
        assertTrue(c.getUsage().total() > 0, "usage should be recorded");
    }

    @Test void streamsWithinAConversationAndKeepsTheReply() {
        List<String> chunks = new ArrayList<>();
        Conversation c = terseBot().build().startConversation();
        String full = c.sendStreaming("Count from 1 to 3.", chunks::add);

        assertFalse(chunks.isEmpty());
        assertEquals(full, c.getLastMessage().getText());
        assertEquals(3, c.getMessageCount());
    }

    @Test void returnsStructuredOutputFromTheConversation() {
        Conversation c = terseBot().build().startConversation();
        c.send("My name is Mira and I am 31.");
        Person p = c.sendAs("Give my name and age as JSON.", Person.class);

        assertNotNull(p);
        assertEquals("mira", p.name().toLowerCase().trim());
        assertEquals(31, p.age());
    }

    public record Person(String name, int age) {}

    @Test void aWindowedConversationStaysWithinItsBudget() {
        Conversation c = terseBot().memory(new Memory(500)).build().startConversation();
        for (int i = 0; i < 3; i++) c.send("Say the number " + i + " and nothing else.");

        assertEquals(7, c.getMessageCount(), "the transcript keeps everything");
        assertTrue(c.estimateTokens() > 0);
    }

    @Test void clearingStartsOverButKeepsThePersona() {
        Conversation c = terseBot().build().startConversation();
        c.send("Remember the number 12345.");
        c.clearHistory();

        assertEquals(1, c.getMessageCount());
        String reply = c.send("What number did I ask you to remember? Say 'none' if there was none.");
        assertFalse(reply.contains("12345"), "the history was cleared; got: " + reply);
    }
}
