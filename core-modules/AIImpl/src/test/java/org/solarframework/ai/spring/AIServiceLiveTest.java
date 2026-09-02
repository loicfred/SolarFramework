package org.solarframework.ai.spring;

import org.solarframework.ai.Chatbot;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** What the service reports, and the single-shot calls its bot makes, against a local LM Studio. Self-skips when none is reachable. */
@Tag("live")
class AIServiceLiveTest {

    private static final String MODEL = "openai/gpt-oss-20b";
    private static AIService service;
    /** The service holds the endpoint; talking to it is the chatbot's job. */
    private static Chatbot bot;

    @BeforeAll static void connect() {
        service = TestAI.service("live-test", "http://localhost:1234", MODEL);
        assumeTrue(service.isAvailable(), "LM Studio is not running on localhost:1234");
        bot = service.createChatbot().build();
    }

    @Test void reportsWhatItsOwnModelCanDo() {
        assertEquals(MODEL, service.getModel());
        assertTrue(service.isModelLoaded() || service.getModelState() != null, "state: " + service.getModelState());
        assertTrue(service.isModelSupportingTools(), MODEL + " should report tool_use");
        assertTrue(service.getUsableContext() > 0);
        assertTrue(service.getMaxContextLength() >= service.getUsableContext());
        assertEquals("openai", service.getPublisher());
    }

    @Test void listsTheModelsOnTheEndpoint() {
        assertTrue(service.getAvailableModels().contains(MODEL), "got: " + service.getAvailableModels());
    }

    @Test void answersAPlainPrompt() {
        String reply = bot.prompt("Reply with exactly the word: pong");
        assertNotNull(reply);
        assertTrue(reply.toLowerCase().contains("pong"), "got: " + reply);
    }

    @Test void returnsStructuredOutput() {
        Country c = bot.promptAs("Give the capital and population in millions of France.", Country.class);
        assertNotNull(c);
        assertEquals("paris", c.capital().toLowerCase().trim());
        assertTrue(c.populationMillions() > 1, "got: " + c.populationMillions());
    }

    public record Country(String capital, double populationMillions) {}

    @Test void picksItemsOutOfAList() {
        List<String> chosen = bot.chooseBetween("Which of these are red?", List.of("Apple", "Cherry", "Banana", "Orange"));

        assertFalse(chosen.isEmpty(), "expected at least one match");
        assertTrue(chosen.contains("Apple") || chosen.contains("Cherry"), "got: " + chosen);
        assertFalse(chosen.contains("Banana"), "bananas are not red; got: " + chosen);
    }

    @Test void anEmptyListNeedsNoModelCall() {
        assertTrue(bot.chooseBetween("anything", List.of()).isEmpty());
    }

    @Test void streamsInChunks() {
        List<String> chunks = new ArrayList<>();
        String full = bot.stream("Count from 1 to 5, separated by spaces.", chunks::add);

        assertFalse(chunks.isEmpty(), "expected streamed chunks");
        assertEquals(full, String.join("", chunks));
        assertTrue(full.contains("5"), "got: " + full);
    }

    @Test void answersAFactualQuestionInProse() {
        String reply = bot.askForInformation("In one sentence, what is the capital of Japan?");
        assertTrue(reply.toLowerCase().contains("tokyo"), "got: " + reply);
    }

    @Test void completesTextWithoutRepeatingIt() {
        String continuation = bot.complete("The three primary colours are red, blue and");
        assertNotNull(continuation);
        assertFalse(continuation.startsWith("The three primary colours"), "the prompt should not be echoed; got: " + continuation);
    }

    @Test void anUnreachableServiceDegradesRatherThanThrowing() {
        AIService dead = TestAI.service("dead", "http://127.0.0.1:1", MODEL);
        assertFalse(dead.isAvailable());
        assertTrue(dead.getAvailableModels().isEmpty());
        assertNull(dead.getModelState());
        assertEquals(0, dead.getUsableContext());
    }
}
