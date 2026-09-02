package org.solarframework.ai;

import org.junit.jupiter.api.Test;
import org.solarframework.ai.dto.ToolCall;
import org.solarframework.ai.obj.ChatMessage;
import org.solarframework.ai.memory.Memory;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MemoryTest {

    private List<ChatMessage> history(int turns) {
        List<ChatMessage> h = new ArrayList<>();
        h.add(ChatMessage.system("You are a bot."));
        for (int i = 0; i < turns; i++) {
            h.add(ChatMessage.user("question " + i + " ".repeat(200)));
            h.add(ChatMessage.assistant("answer " + i + " ".repeat(200)));
        }
        return h;
    }

    @Test void keepsTheNewestMessagesWithinBudget() {
        List<ChatMessage> h = history(20);
        List<ChatMessage> kept = new Memory(400).prepare(h, null, 8192).messages();

        assertTrue(kept.size() < h.size(), "should have dropped something");
        assertEquals(h.getLast(), kept.getLast(), "the newest message must survive");
        assertTrue(kept.stream().mapToInt(ChatMessage::estimateTokens).sum() <= 400);
    }

    /**
     * The regression from 30 Aug 2026: the budget ran out between an assistant's tool call and the result
     * answering it, and the window kept only the result. gpt-oss refuses that outright - "Message has tool
     * role, but there was no previous assistant message with a tool call" - and the whole turn fails.
     */
    @Test void neverSendsAToolResultWithoutTheCallThatAskedForIt() {
        ToolCall call = new ToolCall("c1", "readPage", "{}");
        List<ChatMessage> h = new ArrayList<>(List.of(
                ChatMessage.system("You are a bot."),
                ChatMessage.user("q" + " ".repeat(400)),
                ChatMessage.assistant("I will look that up." + " ".repeat(800), List.of(call)),
                ChatMessage.tool(call.withResult("ok")),
                ChatMessage.user("and now?")));

        // tight enough that the assistant message falls out of the window while its short result still fits
        List<ChatMessage> kept = new Memory(60).prepare(h, null, 8192).messages();

        assertTrue(kept.stream().noneMatch(m -> m.getRole().isTool()), "an orphaned tool result must be dropped, not sent alone");
        assertEquals("and now?", kept.getLast().getText(), "the newest message still survives");
    }

    /** A tool result travels once, as the tool message's own content - charging it to the assistant message that only named the call made a tool run look about twice its size. */
    @Test void aToolResultIsNotCountedTwice() {
        ToolCall call = new ToolCall("c1", "readPage", "{}").withResult("x".repeat(400));

        assertEquals(ChatMessage.assistant("", List.of(new ToolCall("c1", "readPage", "{}"))).estimateTokens(),
                ChatMessage.assistant("", List.of(call)).estimateTokens(),
                "the result is never sent with the request, so it must not be charged to it");
    }

    /** The regression: a backwards scan that stops early loses the persona sitting in front of it. */
    @Test void neverDropsTheSystemPrompt() {
        List<ChatMessage> kept = new Memory(1).prepare(history(20), null, 8192).messages();
        assertEquals(1, kept.size());
        assertTrue(kept.getFirst().isSystem());
    }

    @Test void keepsEverySystemMessageHoweverTightTheBudget() {
        List<ChatMessage> h = history(5);
        h.add(2, ChatMessage.system("Extra standing instruction."));

        List<ChatMessage> kept = new Memory(1).prepare(h, null, 8192).messages();
        assertEquals(2, kept.stream().filter(ChatMessage::isSystem).count());
    }

    @Test void shortHistoryIsUntouched() {
        List<ChatMessage> h = history(1);
        assertEquals(h, new Memory(8192).prepare(h, null, 8192).messages());
    }

    @Test void zeroMaxTokensFallsBackToTheModelContext() {
        List<ChatMessage> h = history(20);
        assertTrue(new Memory(0).prepare(h, null, 300).messages().size() < h.size());
    }

    @Test void noBudgetAtAllKeepsEverything() {
        List<ChatMessage> h = history(20);
        assertEquals(h.size(), new Memory(0).prepare(h, null, 0).messages().size());
    }

    @Test void theSummaryTravelsThroughUnchanged() {
        assertEquals("earlier notes", new Memory(8192).prepare(history(1), "earlier notes", 8192).summary());
    }
}
