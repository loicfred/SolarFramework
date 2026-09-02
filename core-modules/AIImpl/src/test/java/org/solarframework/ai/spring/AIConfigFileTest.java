package org.solarframework.ai.spring;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.solarframework.ai.Chatbot;
import org.solarframework.ai.IAIService;
import org.solarframework.ai.dto.AIOptions;
import org.solarframework.ai.dto.ChatbotDefinition;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Config-file round-trip. Needs no server. */
class AIConfigFileTest {

    private Path file;
    private AIManager manager;

    @BeforeEach void setUp() throws Exception {
        file = Files.createTempFile("ai-config", ".json");
        Files.delete(file);
        manager = AIManagerTest.freshManager();
    }

    @AfterEach void tearDown() throws Exception { Files.deleteIfExists(file); }

    @Test void savesServicesThenReadsThemBack() {
        manager.makeNewService("coder", "qwen/qwen3-coder-30b");
        manager.SaveAsFile(file.toString());

        assertTrue(new File(file.toString()).isFile());

        AIManager reloaded = AIManagerTest.freshManager();
        reloaded.LoadFromFile(file.toString());

        IAIService coder = reloaded.getService("coder");
        assertEquals("coder", coder.getName());
        assertEquals("qwen/qwen3-coder-30b", coder.getModel());
        assertEquals(120, coder.getTimeoutSeconds(), "the timeout should round-trip");
        assertEquals(30, coder.getRequestsPerMinute(), "the rate limit should round-trip");
    }

    /** The file is where an agent lives between two runs, so everything that is settings has to come back - persona, model, bounds, and whatever the application asked to have remembered alongside them. */
    @Test void savesChatbotsThenReadsThemBack() {
        manager.makeNewService("coder", "qwen/qwen3-coder-30b");
        manager.addChatbot(manager.getService("coder").createChatbot().name("reviewer")
                .systemPrompt("You review code.").maxSteps(9).chatOptions(AIOptions.INFORMATIONIST)
                .attributes(List.of("readsManual", "keepHours:48")).build());
        manager.SaveAsFile(file.toString());

        AIManager reloaded = AIManagerTest.freshManager();
        reloaded.LoadFromFile(file.toString());

        Chatbot reviewer = reloaded.getChatbot("reviewer");
        assertNotNull(reviewer);
        assertEquals("You review code.", reviewer.getSystemPrompt());
        assertEquals(9, reviewer.getMaxSteps());
        assertEquals("coder", reviewer.getService().getName(), "it should come back on the service it named");
        assertEquals(List.of("readsManual", "keepHours:48"), reviewer.getAttributes());
        assertTrue(reviewer.getTools().isEmpty(), "tools are live objects, handed to a restored agent in code");
    }

    /** An agent may be written down before any endpoint has been configured. It cannot answer, but it must survive the next save rather than be dropped for having nowhere to run. */
    @Test void anAgentWithNoServiceStillComesBack() {
        manager.removeService(IAIService.DEFAULT);
        manager.addChatbot(new ChatbotDefinition("orphan", "", "You wait.", null, null, 6));
        manager.SaveAsFile(file.toString());

        AIManager reloaded = AIManagerTest.freshManager();
        reloaded.LoadFromFile(file.toString());

        assertNotNull(reloaded.getChatbot("orphan"));
        assertEquals("You wait.", reloaded.getChatbot("orphan").getSystemPrompt());
    }

    /** Nothing is seeded from a property file any more, so the file is the whole truth - including for the default. */
    @Test void theFileGivesTheDefaultServiceItsEndpointToo() {
        manager.SaveAsFile(file.toString());

        AIManager reloaded = AIManagerTest.freshManager();
        reloaded.getDefaultService().setBaseUrl("http://elsewhere:9999");
        reloaded.LoadFromFile(file.toString());

        assertEquals("http://localhost:1234", reloaded.getDefaultService().getBaseUrl(), "what the file says, not what the manager held before");
        assertEquals("openai/gpt-oss-20b", reloaded.getDefaultService().getModel());
    }

    @Test void loadingReplacesNonDefaultServices() {
        manager.makeNewService("kept");
        manager.SaveAsFile(file.toString());
        manager.makeNewService("not-in-the-file");

        manager.LoadFromFile(file.toString());
        assertTrue(manager.hasService("kept"));
        assertFalse(manager.hasService("not-in-the-file"), "services absent from the file should not survive");
    }

    @Test void missingFileIsCreatedFromTheCurrentSetup() {
        manager.makeNewService("triage");
        manager.LoadFromFile(file.toString());

        assertTrue(new File(file.toString()).isFile());
        assertTrue(manager.hasService("triage"));
    }
}
