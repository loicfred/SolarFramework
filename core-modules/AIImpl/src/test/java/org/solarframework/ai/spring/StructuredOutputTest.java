package org.solarframework.ai.spring;

import org.junit.jupiter.api.Test;
import org.solarframework.ai.exception.AIException;

import static org.junit.jupiter.api.Assertions.*;

class StructuredOutputTest {

    private final StructuredOutput<Point> shape = new StructuredOutput<>(Point.class);

    @Test void readsPlainJson() {
        assertEquals(3, shape.parse("{\"x\":3}").x());
    }

    /** Local models fence their JSON and chat around it; parsing prose is what made this fragile before. */
    @Test void readsJsonOutOfAChattyReply() {
        assertEquals(3, shape.parse("Sure! Here you go:\n```json\n{\"x\": 3}\n```\nHope that helps.").x());
    }

    @Test void failsLoudlyOnNonsense() {
        assertThrows(AIException.class, () -> shape.parse(""));
        assertThrows(AIException.class, () -> shape.parse(null));
        assertThrows(AIException.class, () -> shape.parse("{not json at all"));
    }

    /** The schema goes to the backend as response_format, never into the prompt. */
    @Test void exposesTheSchemaForTheBackendToEnforce() {
        assertTrue(shape.getJsonSchema().contains("\"x\""));
        assertEquals("Point", shape.getName());
    }

    /** gpt-oss echoes the schema back and appends the values; the outermost object still parses. */
    @Test void survivesAModelThatEchoesTheSchema() {
        String echoed = "{\"type\":\"object\",\"properties\":{\"x\":{\"type\":\"integer\"}},\"required\":[\"x\"],\"x\":7}";
        assertEquals(7, shape.parse(echoed).x());
    }

    public record Point(int x) {}
}
