package org.solarframework.ai.spring;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.solarframework.ai.AITool;
import org.solarframework.ai.Chatbot;
import org.solarframework.ai.obj.ChatMessage;
import org.solarframework.ai.obj.Conversation;
import org.solarframework.ai.dto.AgentRun;
import org.solarframework.ai.dto.ToolCall;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Proves tools actually run, rather than that the model's prose looked plausible: every tool here
 * records its own invocations and returns a value the model could not otherwise know, so each
 * assertion is made on the Java side.
 * <p>Self-skips without LM Studio. Never downloads or deletes a model.
 */
@Tag("live")
class ToolUseLiveTest {

    private static final String MODEL = "openai/gpt-oss-20b";
    private static AIService service;

    @BeforeAll static void connect() {
        service = TestAI.service("tool-test", "http://localhost:1234", MODEL);
        assumeTrue(service.isAvailable(), "LM Studio is not running on localhost:1234");
        assumeTrue(service.isModelSupportingTools(), MODEL + " does not report tool_use");
    }

    private Chatbot bot(int maxSteps, Object... tools) {
        return service.createChatbot()
                .systemPrompt("You are a precise assistant. Use the tools available to you; never guess a value a tool can give you.")
                .tools(tools).maxSteps(maxSteps).build();
    }

    /** Models reformat: en-dashes for hyphens, thousands separators, stray markdown. Compare on the substance. */
    private static String plain(String s) {
        return s == null ? "" : s.replaceAll("\\p{Pd}", "-").replace(",", "").replace("*", "").replace(" ", " ");
    }

    @Test void toolIsActuallyInvokedAndItsResultUsed() {
        Vault vault = new Vault();
        String reply = bot(4, vault).startConversation().send("What is the access code for the archive room?");

        assertEquals(1, vault.calls.get(), "the tool must have been called exactly once");
        assertTrue(plain(reply).contains(Vault.CODE), "the unguessable code must reach the answer; got: " + reply);
    }

    @Test void theModelPassesTheRightArgument() {
        Vault vault = new Vault();
        bot(4, vault).startConversation().send("Call getAccessCode for the room named basement and tell me what it returns.");

        assertEquals(1, vault.calls.get());
        assertEquals("basement", vault.rooms.getFirst().toLowerCase().trim(),
                "the room name should bind to the named parameter; got: " + vault.rooms);
    }

    @Test void theModelPicksTheRightToolAmongSeveral() {
        Vault vault = new Vault();
        Weather weather = new Weather();
        String reply = bot(4, vault, weather).startConversation().send("What is the temperature in Oslo?");

        assertEquals(1, weather.calls.get(), "the weather tool should have run");
        assertEquals(0, vault.calls.get(), "the vault tool is irrelevant here");
        assertTrue(plain(reply).contains(Weather.TEMPERATURE), "got: " + reply);
    }

    @Test void toolResultsChainAcrossAgentSteps() {
        Directory directory = new Directory();
        AgentRun run = bot(6, directory).startConversation()
                .run("Look up the account id for the user named mira, then report that account's balance.");

        assertEquals(1, directory.idCalls.get(), "the id lookup should have run");
        assertEquals(1, directory.balanceCalls.get(), "the balance lookup should have run");
        assertEquals(Directory.ID, directory.balanceArgs.getFirst().trim(),
                "the second tool should receive the first one's output; got: " + directory.balanceArgs);
        assertTrue(run.completed(), "expected an answer within the bound; got: " + run);
        assertTrue(plain(run.text()).contains(Directory.BALANCE), "the balance should reach the answer; got: " + run.text());
        assertTrue(run.stepCount() >= 2, "chaining two tools needs more than one step; got: " + run.steps());
    }

    @Test void toolsPassedForOneTurnOnlyAreAvailableThatTurn() {
        Vault vault = new Vault();
        Conversation c = bot(4).startConversation();                    // the chatbot itself has no tools
        String reply = c.send("What is the access code for the archive room?", vault);

        assertEquals(1, vault.calls.get(), "a per-call tool should be offered to the model");
        assertTrue(plain(reply).contains(Vault.CODE), "got: " + reply);
    }

    @Test void severalTypedParametersAreBoundCorrectly() {
        Converter converter = new Converter();
        bot(4, converter).startConversation().send("Convert 125.5 EUR to USD using your tool.");

        assertEquals(1, converter.calls.get());
        assertEquals(125.5, converter.amount, 0.001, "a decimal should bind to the double parameter");
        assertEquals("EUR", converter.from.toUpperCase().trim());
        assertEquals("USD", converter.to.toUpperCase().trim());
    }

    @Test void aNonStringReturnValueReachesTheAnswer() {
        Converter converter = new Converter();
        String reply = bot(4, converter).startConversation().send("Convert 100 EUR to USD using your tool and state the result.");

        assertEquals(1, converter.calls.get());
        assertTrue(plain(reply).contains("201"), "100*2+1 is a result it cannot invent; got: " + reply);
    }

    @Test void booleanAndIntegerParametersBind() {
        Inventory inventory = new Inventory();
        bot(4, inventory).startConversation()
                .send("How many units of widget are in stock at warehouse number 7, including reserved units?");

        assertEquals(1, inventory.calls.get());
        assertTrue(inventory.includeReserved, "the boolean should have been set from the question");
        assertEquals(7, inventory.warehouse, "the integer parameter should bind");
    }

    @Test void aCollectionOfRecordsIsReturnedAndRead() {
        Catalogue catalogue = new Catalogue();
        String reply = bot(4, catalogue).startConversation().send("List the catalogue items and give me the SKU of the lamp.");

        assertEquals(1, catalogue.calls.get());
        assertTrue(plain(reply).contains(Catalogue.LAMP_SKU), "a record field should survive serialisation; got: " + reply);
    }

    @Test void anEnumParameterAndARenamedToolWork() {
        Tickets tickets = new Tickets();
        bot(4, tickets).startConversation().send("Raise a ticket titled 'server down' with high priority.");

        assertEquals(1, tickets.calls.get());
        assertEquals(Tickets.Priority.HIGH, tickets.priority, "the enum should bind from the word 'high'");
        assertEquals("raise_ticket", new ToolAdapter().callbacks(tickets).getFirst().getToolDefinition().name());
    }

    @Test void onlyAnnotatedMethodsAreExposedToTheModel() {
        Mixed mixed = new Mixed();
        assertEquals(1, new ToolAdapter().callbacks(mixed).size(), "only the annotated method is a tool");

        bot(4, mixed).startConversation().send("Use your tool to get the supported value, and also the secret value if you can.");
        assertTrue(mixed.supportedCalls.get() > 0, "the annotated method should be reachable");
        assertEquals(0, mixed.secretCalls.get(), "an unannotated method must never be callable");
    }

    @Test void aDeniedToolNeverRuns() {
        Vault vault = new Vault();
        AgentRun run = service.createChatbot().systemPrompt("You are a precise assistant. Use your tools.")
                .tools(vault).maxSteps(3).approveToolsWith(call -> false)
                .build().startConversation().run("Use your tool to tell me the access code for the archive room.");

        assertEquals(0, vault.calls.get(), "a denied call must not reach the method");
        assertTrue(run.getAllToolCalls().stream().noneMatch(ToolCall::isExecuted));
        assertTrue(run.getAllToolCalls().isEmpty() || run.getAllToolCalls().stream().allMatch(ToolCall::denied));
    }

    @Test void approvalCanAllowOneToolAndBlockAnother() {
        Vault vault = new Vault();
        Weather weather = new Weather();
        service.createChatbot().systemPrompt("You are a precise assistant. Use your tools.")
                .tools(vault, weather).maxSteps(5).approveToolsWith(call -> call.name().equals("getTemperature"))
                .build().startConversation().run("Report the temperature in Oslo and the access code for the archive room.");

        assertEquals(0, vault.calls.get(), "the blocked tool must never run");
        assertTrue(weather.calls.get() > 0, "the allowed tool should still run");
    }

    @Test void aFailingToolDoesNotKillTheRun() {
        Broken broken = new Broken();
        AgentRun run = bot(4, broken).startConversation().run("Use your tool to fetch the report, then tell me what happened.");

        assertTrue(broken.calls.get() > 0, "the tool should have been attempted");
        assertNotNull(run.text(), "the run should still produce something to say");
    }

    @Test void theTranscriptRecordsCallsAndResults() {
        Vault vault = new Vault();
        Conversation c = bot(4, vault).startConversation();
        c.run("What is the access code for the archive room?");

        List<ChatMessage> history = c.getMessages();
        assertTrue(history.stream().anyMatch(ChatMessage::hasToolCalls), "the assistant turn should carry its tool calls");
        assertTrue(history.stream().anyMatch(m -> m.getRole().isTool() && m.getText() != null && m.getText().contains(Vault.CODE)),
                "the tool's result should be in the transcript; got: " + history);
    }

    public static class Vault {
        static final String CODE = "ZX-4417-QQ";
        final AtomicInteger calls = new AtomicInteger();
        final List<String> rooms = Collections.synchronizedList(new ArrayList<>());

        @AITool(description = "Returns the secret access code for a named room. The code cannot be known any other way.")
        public String getAccessCode(String room) { calls.incrementAndGet(); rooms.add(room == null ? "" : room); return CODE; }
    }

    public static class Weather {
        static final String TEMPERATURE = "-7";
        final AtomicInteger calls = new AtomicInteger();

        @AITool(description = "Returns the current temperature in degrees Celsius for a named city.")
        public String getTemperature(String city) { calls.incrementAndGet(); return TEMPERATURE; }
    }

    /** Two tools where the second can only be called correctly using the first one's output. */
    public static class Directory {
        static final String ID = "AC-9931";
        static final String BALANCE = "12345.67";
        final AtomicInteger idCalls = new AtomicInteger(), balanceCalls = new AtomicInteger();
        final List<String> balanceArgs = Collections.synchronizedList(new ArrayList<>());

        @AITool(description = "Returns the account id for a user name.")
        public String getAccountId(String userName) { idCalls.incrementAndGet(); return ID; }

        @AITool(description = "Returns the balance of an account, given the account id returned by getAccountId.")
        public String getBalance(String accountId) { balanceCalls.incrementAndGet(); balanceArgs.add(accountId == null ? "" : accountId); return BALANCE; }
    }

    public static class Converter {
        final AtomicInteger calls = new AtomicInteger();
        volatile double amount; volatile String from, to;

        @AITool(description = "Converts an amount of money from one currency to another. Returns the converted amount.")
        public double convert(double amount, String fromCurrency, String toCurrency) {
            calls.incrementAndGet();
            this.amount = amount; this.from = fromCurrency; this.to = toCurrency;
            return amount * 2 + 1;
        }
    }

    public static class Inventory {
        final AtomicInteger calls = new AtomicInteger();
        volatile boolean includeReserved; volatile int warehouse;

        @AITool(description = "Returns how many units of a product are in stock at a numbered warehouse.")
        public int countStock(String product, int warehouseNumber, boolean includeReserved) {
            calls.incrementAndGet();
            this.warehouse = warehouseNumber; this.includeReserved = includeReserved;
            return 42;
        }
    }

    public static class Catalogue {
        static final String LAMP_SKU = "LMP-8823";
        final AtomicInteger calls = new AtomicInteger();

        public record Item(String sku, String name, int quantity) {}

        @AITool(description = "Returns every item in the catalogue, with its SKU, name and quantity.")
        public List<Item> listItems() {
            calls.incrementAndGet();
            return List.of(new Item(LAMP_SKU, "lamp", 3), new Item("CHR-1140", "chair", 8));
        }
    }

    public static class Tickets {
        public enum Priority { LOW, MEDIUM, HIGH }
        final AtomicInteger calls = new AtomicInteger();
        volatile Priority priority;

        @AITool(name = "raise_ticket", description = "Raises a support ticket with a title and a priority of LOW, MEDIUM or HIGH.")
        public String raiseTicket(String title, Priority priority) {
            calls.incrementAndGet(); this.priority = priority;
            return "ticket created";
        }
    }

    public static class Mixed {
        final AtomicInteger supportedCalls = new AtomicInteger(), secretCalls = new AtomicInteger();

        @AITool(description = "Returns the supported value.")
        public String getSupportedValue() { supportedCalls.incrementAndGet(); return "supported-ok"; }

        /** Deliberately unannotated: the model must not be able to reach this. */
        public String getSecretValue() { secretCalls.incrementAndGet(); return "should-never-appear"; }
    }

    public static class Broken {
        final AtomicInteger calls = new AtomicInteger();

        @AITool(description = "Fetches the monthly report.")
        public String fetchReport() { calls.incrementAndGet(); throw new IllegalStateException("report service is down"); }
    }
}
