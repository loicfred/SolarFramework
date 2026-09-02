package org.solarframework.ai;

import org.junit.jupiter.api.Test;
import org.solarframework.ai.dto.AIOptions;
import org.solarframework.ai.dto.ChatbotDefinition;
import org.solarframework.ai.dto.ToolCall;
import org.solarframework.ai.memory.Memory;
import org.solarframework.ai.obj.ChatMessage;
import org.solarframework.ai.obj.Conversation;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Needs no backend: a chatbot is configuration until a conversation is started. */
class ChatbotTest {

    private Chatbot.Builder bot() { return Chatbot.builder(new FakeAIService()); }

    @Test void keepsOneToolPerType() {
        Chatbot bot = bot().tools(new Object(), new Object(), "a", null).build();
        assertEquals(2, bot.getTools().size());
    }

    @Test void modelAppliesToBothChatAndAgentSettings() {
        Chatbot bot = bot().model("qwen/qwen3-coder-30b").build();
        assertEquals("qwen/qwen3-coder-30b", bot.getChatOptions().model());
        assertEquals("qwen/qwen3-coder-30b", bot.getAgentOptions().model());
        assertNull(AIOptions.CONVERSATIONIST.model(), "the shared preset must not be mutated");
    }

    @Test void profileFactsAreAppendedToThePersona() {
        Chatbot bot = bot().systemPrompt("You are a bot.").profileFacts(owner -> List.of("works on SolarERP")).build();

        String prompt = bot.getSystemPromptFor("loic");
        assertTrue(prompt.startsWith("You are a bot."));
        assertTrue(prompt.contains("works on SolarERP"));
        assertEquals("You are a bot.", bot.getSystemPromptFor(null), "no owner means no profile section");
    }

    @Test void noProfileLeavesThePersonaAlone() {
        assertEquals("You are a bot.", bot().systemPrompt("You are a bot.").build().getSystemPromptFor("loic"));
        assertNull(bot().profileFacts(o -> List.of()).build().getSystemPromptFor("loic"));
    }

    @Test void toolsAreApprovedUnlessAnApproverSaysOtherwise() {
        ToolCall call = new ToolCall("1", "danger", "{}");
        assertTrue(bot().build().isToolApproved(call));
        assertFalse(bot().approveToolsWith(c -> !c.name().equals("danger")).build().isToolApproved(call));
    }

    @Test void aDefinitionCarriesTheSettingsAndNotTheLiveObjects() {
        Chatbot bot = bot().name("reviewer").systemPrompt("You review code.").maxSteps(9)
                .chatOptions(AIOptions.INFORMATIONIST).tools(new Object()).memory(new Memory(500)).build();

        ChatbotDefinition d = bot.getDefinition();
        assertEquals("reviewer", d.name());
        assertEquals(IAIService.DEFAULT, d.serviceName());
        assertEquals(9, d.maxSteps());
        assertEquals(AIOptions.INFORMATIONIST, d.options(), "the chat options should be carried as given");
    }

    @Test void applyingADefinitionLeavesToolsAndMemoryUntouched() {
        Memory memory = new Memory(500);
        Chatbot bot = bot().tools(new Object()).memory(memory)
                .applyDefinition(new ChatbotDefinition("loaded", "default", "New persona.", AIOptions.AGENT, AIOptions.AGENT, 3)).build();

        assertEquals("loaded", bot.getName());
        assertEquals("New persona.", bot.getSystemPrompt());
        assertEquals(1, bot.getTools().size(), "tools are live objects, not settings");
        assertSame(memory, bot.getMemory());
    }

    @Test void definitionDefaultsFillInMissingValues() {
        ChatbotDefinition d = new ChatbotDefinition("x", "default", null, null, null, 0);
        assertEquals(AIOptions.CONVERSATIONIST, d.options());
        assertEquals(AIOptions.AGENT, d.agentOptions());
        assertEquals(6, d.maxSteps());
        assertEquals(List.of(), d.attributes());
    }

    @Test void attributesCarryWhatTheFrameworkHasNoFieldFor() {
        ChatbotDefinition d = new ChatbotDefinition("x", "", null, null, null, 6, List.of("readsManual", "keepHours:48", "greeting:Hello there"));

        assertEquals("Hello there", d.attribute("greeting"), "a value may hold spaces and is taken whole");
        assertNull(d.attribute("readsManual"), "a bare flag has no value");
        assertTrue(d.attributes().contains("readsManual"));
        assertEquals(48, d.attribute("keepHours", 24));
        assertEquals(24, d.attribute("maxInputTokens", 24), "an absent number falls back");
    }

    @Test void anAgentAskingForTheDefaultServiceIsWrittenBackAskingForIt() {
        Chatbot bot = bot().applyDefinition(new ChatbotDefinition("loaded", "", null, null, null, 6, List.of("readsManual"))).build();

        assertEquals("", bot.getDefinition().serviceName(), "not pinned to whichever service held the title when it was read");
        assertEquals(List.of("readsManual"), bot.getDefinition().attributes());
    }

    @Test void aFreshConversationOpensWithThePersona() {
        Conversation c = bot().systemPrompt("You are terse.").build().startConversation("c1", "loic");

        assertEquals("c1", c.getId());
        assertEquals("loic", c.getOwner());
        assertEquals(1, c.getMessageCount());
        assertEquals("You are terse.", c.getSystemPrompt());
    }

    @Test void clearingKeepsThePersonaAndDropsTheRest() {
        Conversation c = bot().systemPrompt("You are terse.").build().startConversation();
        c.addMessage(org.solarframework.ai.obj.ChatMessage.user("hello"));
        c.setSummary("notes");

        c.clearHistory();
        assertEquals(1, c.getMessageCount());
        assertTrue(c.getMessages().getFirst().isSystem());
        assertNull(c.getSummary());
    }
}
