package org.solarframework.ai.spring;

import org.junit.jupiter.api.Test;
import org.solarframework.ai.AITool;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ToolAdapterTest {

    private final ToolAdapter adapter = new ToolAdapter();

    @Test void annotatedMethodsBecomeToolsWithTheirDescription() {
        ToolCallback callback = adapter.callbacks(new Clock()).getFirst();
        assertEquals("getCurrentTime", callback.getToolDefinition().name());
        assertEquals("Returns the current time.", callback.getToolDefinition().description());
        assertNotNull(callback.getToolDefinition().inputSchema());
    }

    @Test void explicitToolNameWins() {
        assertEquals("now", adapter.callbacks(new Renamed()).getFirst().getToolDefinition().name());
    }

    @Test void objectsWithNoToolsContributeNothing() {
        assertTrue(adapter.callbacks(new Object()).isEmpty());
        assertTrue(adapter.callbacks((Object[]) null).isEmpty());
    }

    /** Why AIAPI can own its annotation without orphaning anyone already using Spring AI's. */
    @Test void springAiOwnToolAnnotationStillWorks() {
        List<ToolCallback> callbacks = adapter.callbacks(new SpringNative());
        assertEquals(1, callbacks.size());
        assertEquals("springNativeTool", callbacks.getFirst().getToolDefinition().name());
    }

    @Test void ourAnnotationWinsWhenAnObjectCarriesBoth() {
        List<ToolCallback> callbacks = adapter.callbacks(new BothAnnotations());
        assertEquals(1, callbacks.size());
        assertEquals("ours", callbacks.getFirst().getToolDefinition().name());
    }

    @Test void schemaCarriesEveryParameterByName() {
        String schema = adapter.callbacks(new Converter()).getFirst().getToolDefinition().inputSchema();
        for (String param : List.of("amount", "fromCurrency", "toCurrency"))
            assertTrue(schema.contains(param), "schema should name " + param + "; got: " + schema);
    }

    @Test void schemaDescribesParameterTypes() {
        String schema = adapter.callbacks(new Converter()).getFirst().getToolDefinition().inputSchema();
        assertTrue(schema.contains("number"), "the double should be typed; got: " + schema);
        assertTrue(schema.contains("string"), "the strings should be typed; got: " + schema);
    }

    @Test void enumParametersAreOfferedAsAChoice() {
        String schema = adapter.callbacks(new Ticket()).getFirst().getToolDefinition().inputSchema();
        assertTrue(schema.contains("HIGH"), "enum constants should reach the schema; got: " + schema);
    }

    @Test void severalObjectsContributeAllTheirTools() {
        assertEquals(3, adapter.callbacks(new Clock(), new Converter(), new Ticket()).size());
    }

    public static class Clock {
        @AITool(description = "Returns the current time.")
        public String getCurrentTime() { return "12:00"; }
    }

    public static class Renamed {
        @AITool(name = "now", description = "Returns the current time.")
        public String getCurrentTime() { return "12:00"; }
    }

    public static class SpringNative {
        @org.springframework.ai.tool.annotation.Tool(description = "A tool declared the Spring AI way.")
        public String springNativeTool() { return "ok"; }
    }

    public static class BothAnnotations {
        @AITool(name = "ours", description = "Declared with the framework's annotation.")
        public String ours() { return "ok"; }

        @org.springframework.ai.tool.annotation.Tool(description = "Declared the Spring AI way.")
        public String theirs() { return "ok"; }
    }

    public static class Converter {
        @AITool(description = "Converts an amount between two currencies.")
        public double convert(double amount, String fromCurrency, String toCurrency) { return amount; }
    }

    public static class Ticket {
        public enum Priority { LOW, MEDIUM, HIGH }

        @AITool(description = "Raises a ticket.")
        public String raise(String title, Priority priority) { return "ok"; }
    }
}
