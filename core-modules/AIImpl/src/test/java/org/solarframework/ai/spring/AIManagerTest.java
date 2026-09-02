package org.solarframework.ai.spring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.solarframework.ai.Chatbot;
import org.solarframework.ai.IAIService;
import org.springframework.ai.model.tool.ToolCallingManager;

import static org.junit.jupiter.api.Assertions.*;

/** Needs no server: nothing here talks to a model. */
class AIManagerTest {

    private AIManager manager;

    static AIManager freshManager() {
        AIService defaultService = TestAI.service(IAIService.DEFAULT, "http://localhost:1234", "openai/gpt-oss-20b");
        defaultService.setTimeoutSeconds(120);
        defaultService.setRequestsPerMinute(30);
        AIManager manager = new AIManager(ToolCallingManager.builder().build());
        manager.addService(defaultService);
        return manager;
    }

    @BeforeEach void setUp() { manager = freshManager(); }

    @Test void startsWithOnlyTheDefaultService() {
        assertEquals(1, manager.getServices().size());
        assertTrue(manager.getDefaultService().isDefault());
        assertTrue(manager.hasService(IAIService.DEFAULT));
    }

    @Test void aNewServiceInheritsTheDefaultsEndpointAndSettings() {
        IAIService triage = manager.makeNewService("triage");

        assertEquals("openai/gpt-oss-20b", triage.getModel(), "the model should be inherited");
        assertEquals("http://localhost:1234", triage.getBaseUrl());
        assertEquals(120, triage.getTimeoutSeconds());
        assertEquals(30, triage.getRequestsPerMinute());
        assertFalse(triage.isDefault());
    }

    @Test void onlyTheModelChangesWhenOneIsNamed() {
        IAIService coder = manager.makeNewService("coder", "qwen/qwen3-coder-30b");
        assertEquals("qwen/qwen3-coder-30b", coder.getModel());
        assertEquals(manager.getDefaultService().getBaseUrl(), coder.getBaseUrl());
    }

    @Test void unknownNamesFallBackToTheDefault() {
        assertSame(manager.getDefaultService(), manager.getService("nope"));
        assertFalse(manager.hasService("nope"));
    }

    /** Nothing is protected any more: the default is a registered service like any other, and removing the last one leaves an installation with no AI at all - which is a state it is now allowed to be in. */
    @Test void removingTheDefaultHandsTheTitleOnAndThenLeavesNone() {
        manager.makeNewService("triage");

        assertTrue(manager.removeService(IAIService.DEFAULT));
        assertFalse(manager.hasService(IAIService.DEFAULT));
        assertEquals("triage", manager.getDefaultService().getName(), "the survivor becomes the one a caller naming none gets");

        assertTrue(manager.removeService("triage"));
        assertNull(manager.getDefaultService());
        assertNull(manager.getService("anything"));
    }

    /** A service actually called "Default" takes the title from whoever held it, so the name means what it says. */
    @Test void aServiceNamedDefaultTakesTheTitle() {
        AIManager empty = new AIManager(ToolCallingManager.builder().build());
        empty.makeNewService("first", "http://localhost:1234", "N/A", "a-model");
        assertEquals("first", empty.getDefaultService().getName());

        empty.makeNewService(IAIService.DEFAULT, "http://localhost:1234", "N/A", "a-model");
        assertEquals(IAIService.DEFAULT, empty.getDefaultService().getName());
    }

    @Test void renamingMovesTheServiceUnderItsNewName() {
        manager.makeNewService("triage");

        assertTrue(manager.renameService("triage", "support"));
        assertFalse(manager.hasService("triage"));
        assertTrue(manager.hasService("support"));
        assertEquals("support", manager.getService("support").getName());
    }

    @Test void renamingTheDefaultCarriesTheTitleWithIt() {
        assertTrue(manager.renameService(IAIService.DEFAULT, "primary"));
        assertEquals("primary", manager.getDefaultService().getName());
        assertFalse(manager.hasService(IAIService.DEFAULT));
    }

    @Test void renamingFailsWhenTheNameIsUnknownOrAlreadyTaken() {
        manager.makeNewService("triage");

        assertFalse(manager.renameService("nope", "somewhere"));
        assertFalse(manager.renameService("triage", IAIService.DEFAULT), "Default is already taken by another service");
        assertTrue(manager.hasService("triage"));
        assertTrue(manager.hasService(IAIService.DEFAULT));
    }

    @Test void removingNonDefaultServicesLeavesTheDefault() {
        manager.makeNewService("a");
        manager.makeNewService("b");
        assertEquals(3, manager.getServices().size());

        assertTrue(manager.removeNonDefaultServices());
        assertEquals(1, manager.getServices().size());
        assertTrue(manager.hasService(IAIService.DEFAULT));
    }

    @Test void chatbotsAreLookedUpByName() {
        Chatbot bot = manager.getDefaultService().createChatbot().name("support").build();
        assertTrue(manager.addChatbot(bot));

        assertSame(bot, manager.getChatbot("support"));
        assertEquals(1, manager.getChatbots().size());
        assertTrue(manager.removeChatbot("support"));
        assertNull(manager.getChatbot("support"));
    }

    @Test void definitionsDescribeEveryServiceAndAgent() {
        manager.makeNewService("coder", "qwen/qwen3-coder-30b");
        manager.addChatbot(manager.getService("coder").createChatbot().name("reviewer").maxSteps(9).build());

        assertEquals(2, manager.getServiceDefinitions().size());
        assertEquals(1, manager.getChatbotDefinitions().size());
        assertEquals("coder", manager.getChatbotDefinitions().getFirst().serviceName());
        assertEquals(9, manager.getChatbotDefinitions().getFirst().maxSteps());
    }
}
