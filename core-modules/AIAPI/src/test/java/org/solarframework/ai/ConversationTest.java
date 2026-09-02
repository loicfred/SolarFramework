package org.solarframework.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.solarframework.ai.dto.AgentRun;
import org.solarframework.ai.obj.ChatMessage;
import org.solarframework.ai.obj.Conversation;
import org.solarframework.ai.enums.Role;
import org.solarframework.ai.dto.ToolCall;
import org.solarframework.ai.memory.Memory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The loop, with no backend. */
class ConversationTest {

    private FakeAIService service;
    private Chatbot bot;

    @BeforeEach void setUp() {
        service = new FakeAIService();
        bot = Chatbot.builder(service).systemPrompt("You are terse.").build();
    }

    /**
     * The regression from 30 Aug 2026: a stored conversation whose messages did not load was handed to the model as
     * the bare question, with no persona at all, and answered nothing like the assistant it was configured to be.
     * Attaching a chatbot has to leave a persona behind, not merely correct one that is already there.
     */
    @Test void aTranscriptThatCameBackWithoutItsPersonaIsGivenOne() {
        Conversation c = bot.startConversation("c1", "loic");
        c.getMessages().clear();                                  // what a row read back with no transcript looks like

        c.attach(bot);

        assertEquals(1, c.getMessageCount());
        assertTrue(c.getMessages().getFirst().isSystem());
        assertEquals("You are terse.", c.getSystemPrompt());
    }

    @Test void sendRecordsBothSidesOfTheTurn() {
        service.willAnswer("hello there");
        Conversation c = bot.startConversation();

        assertEquals("hello there", c.send("hi"));
        assertEquals(3, c.getMessageCount(), "persona + user + assistant");
        assertEquals(Role.USER, c.getMessages().get(1).getRole());
        assertEquals("hello there", c.getLastMessage().getText());
        assertEquals(2, c.getUsage().total());
    }

    @Test void aToolCallBecomesTwoMoreTranscriptEntries() {
        service.willCallTool("getTime");
        service.willAnswer("it is noon");
        Conversation c = bot.startConversation();

        AgentRun run = c.run("what time is it");

        assertTrue(run.completed());
        assertEquals(2, run.stepCount());
        assertEquals("result of getTime", run.getAllToolCalls().getFirst().result());

        List<ChatMessage> h = c.getMessages();
        assertTrue(h.stream().anyMatch(ChatMessage::hasToolCalls), "the assistant turn should carry its calls");
        assertTrue(h.stream().anyMatch(m -> m.getRole().isTool()), "the tool result should be in the transcript");
    }

    /** What a chat box redraws: the two people talking, and none of the machinery between them. */
    @Test void theSpokenMessagesLeaveOutThePersonaAndTheToolTraffic() {
        service.willCallTool("getTime");
        service.willAnswer("it is noon");
        Conversation c = bot.startConversation();
        c.run("what time is it");

        List<ChatMessage> spoken = c.getSpokenMessages();
        assertEquals(2, spoken.size(), "the question and the answer, out of five entries");
        assertEquals("what time is it", spoken.getFirst().getText());
        assertEquals("it is noon", spoken.getLast().getText());
        assertTrue(spoken.stream().noneMatch(ChatMessage::isSystem), "the persona is instructions, not speech");
        assertTrue(spoken.stream().noneMatch(m -> m.getRole().isTool() || m.hasToolCalls()), "no tool request and no tool result");
    }

    /** A tool result must carry the call id, or the backend cannot match it to its request. */
    @Test void theToolMessageKeepsTheCallId() {
        service.willCallTool("getTime");
        service.willAnswer("noon");
        Conversation c = bot.startConversation();
        c.run("what time is it");

        ChatMessage toolMessage = c.getMessages().stream().filter(m -> m.getRole().isTool()).findFirst().orElseThrow();
        assertFalse(toolMessage.getToolCalls().isEmpty());
        assertEquals("getTime", toolMessage.getToolCalls().getFirst().name());
    }

    @Test void theStepBoundStopsTheLoop() {
        for (int i = 0; i < 10; i++) service.willCallTool("spin");
        Conversation c = Chatbot.builder(service).maxSteps(3).build().startConversation();

        AgentRun run = c.run("spin forever");

        assertFalse(run.completed());
        assertEquals(3, run.stepCount());
        assertEquals(3, service.turns, "must not call the model past the bound");
    }

    @Test void deniedToolsComeBackDeniedAndTheLoopContinues() {
        service.willCallTool("danger");
        service.willAnswer("could not do that");
        Conversation c = Chatbot.builder(service).approveToolsWith(call -> false).build().startConversation();

        AgentRun run = c.run("do something dangerous");

        List<ToolCall> calls = run.getAllToolCalls();
        assertEquals(1, calls.size());
        assertTrue(calls.getFirst().denied());
        assertFalse(calls.getFirst().isExecuted());
        assertTrue(run.completed(), "a denial should not end the run");
    }

    @Test void everyStepIsReported() {
        service.willCallTool("a");
        service.willAnswer("done");
        List<AgentRun> reported = new ArrayList<>();

        AgentRun run = Chatbot.builder(service).onStep(reported::add).build().startConversation().run("go");
        assertEquals(run.stepCount(), reported.size());
    }

    @Test void perCallToolsJoinTheChatbotsOwn() {
        service.willAnswer("ok");
        Chatbot.builder(service).tools("bot-tool").build().startConversation().send("hi", 1234);

        assertEquals(2, service.seenTools.getFirst().size());
    }

    @Test void memoryTrimsWhatIsSentButNotTheTranscript() {
        Conversation c = Chatbot.builder(service).systemPrompt("persona").memory(new Memory(60)).build().startConversation();
        for (int i = 0; i < 5; i++) { service.willAnswer("reply " + i + " ".repeat(300)); c.send("question " + i + " ".repeat(300)); }

        assertEquals(11, c.getMessageCount(), "the transcript keeps everything");
        assertTrue(service.seenHistories.getLast().size() < c.getMessageCount(), "but the model sees less");
        assertTrue(service.seenHistories.getLast().getFirst().isSystem(), "the persona always survives");
    }

    @Test void withoutMemoryTheWholeHistoryIsSent() {
        Conversation c = bot.startConversation();
        for (int i = 0; i < 3; i++) { service.willAnswer("reply"); c.send("question " + i); }

        assertEquals(c.getMessageCount() - 1, service.seenHistories.getLast().size(),
                "everything except the reply that had not arrived yet");
    }

    @Test void anInMemoryConversationSavesNowhere() {
        service.willAnswer("ok");
        assertDoesNotThrow(() -> bot.startConversation().send("hi"));
    }

    @Test void attachingARewrittenBotReplacesThePersonaTheTranscriptCarried() {
        Conversation c = bot.startConversation();
        assertEquals("You are terse.", c.getSystemPrompt());

        c.attach(Chatbot.builder(service).systemPrompt("You are thorough.").build());
        assertEquals("You are thorough.", c.getSystemPrompt());
        assertEquals(1, c.getMessageCount(), "replaced, not appended");
    }

    @Test void aConversationNobodyKeepsForAWhileNeverFallsDue() {
        Conversation c = bot.startConversation();
        assertNull(c.getExpiresAt());
        assertFalse(c.isExpired());
    }

    @Test void keepForSetsTheDateTheSweeperReadsAndRollsItForward() throws InterruptedException {
        Conversation c = bot.startConversation().keepFor(Duration.ofMinutes(30));
        Instant first = c.getExpiresAt();
        assertNotNull(first);
        assertFalse(c.isExpired());

        Thread.sleep(5);
        assertTrue(c.keepFor(Duration.ofMinutes(30)).getExpiresAt().isAfter(first), "still in use, so still kept");
    }

    @Test void aWindowAlreadyPastLeavesTheConversationDue() {
        assertTrue(bot.startConversation().keepFor(Duration.ofSeconds(-1)).isExpired());
    }

    @Test void estimateTokensGrowsWithTheTranscript() {
        Conversation c = bot.startConversation();
        int before = c.estimateTokens();
        service.willAnswer("a reply");
        c.send("a question");
        assertTrue(c.estimateTokens() > before);
    }
}
