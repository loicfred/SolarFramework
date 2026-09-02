package org.solarframework.ai.spring;

import org.solarframework.ai.exception.AIException;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;

import static org.solarframework.json.JSONItem.SimpleGSON;

/**
 * Gets one JSON object of a given shape out of a model and reads it back.
 * <p>The schema is handed to the backend as {@code response_format}, not written into the prompt.
 * Prompting for it invites the model to echo the schema back — gpt-oss returns the whole schema
 * with the values appended, and sometimes without them, which is how a field arrives null. Given
 * the schema natively the server constrains generation and the reply is exactly the object.
 * <p>{@link #parse} stays tolerant of fences and prose anyway, for a backend that ignores it.
 */
public class StructuredOutput<T> {

    private final Class<T> type;

    public StructuredOutput(Class<T> type) { this.type = type; }

    public String getName() { return type.getSimpleName(); }

    public String getJsonSchema() { return JsonSchemaGenerator.generateForType(type); }

    public T parse(String reply) {
        if (reply == null || reply.isBlank()) throw new AIException("Expected JSON for " + getName() + ", got an empty reply");
        String json = extract(reply);
        try {
            return SimpleGSON.fromJson(json, type);
        } catch (RuntimeException e) {
            throw new AIException("Could not read " + getName() + " from model reply: " + json, e);
        }
    }

    /** The outermost {@code {...}} or {@code [...]}, ignoring any fence or prose around it. */
    private String extract(String reply) {
        String s = reply.trim();
        int start = s.indexOf('{'), end = s.lastIndexOf('}');
        if (start < 0 || end < start) { start = s.indexOf('['); end = s.lastIndexOf(']'); }
        return start >= 0 && end > start ? s.substring(start, end + 1) : s;
    }
}
