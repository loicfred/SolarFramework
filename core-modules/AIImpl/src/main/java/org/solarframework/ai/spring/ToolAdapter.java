package org.solarframework.ai.spring;

import org.solarframework.ai.AITool;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns objects into Spring AI tool callbacks.
 * <p>Methods annotated {@link AITool} are described from the annotation and their signature. An
 * object with none is handed to Spring AI untouched, so an object using its native {@code @Tool}
 * annotation keeps working — that fallback is the only reason a consumer would ever need the
 * Spring AI classpath.
 */
public class ToolAdapter {

    public List<ToolCallback> callbacks(Object... toolObjects) {
        List<ToolCallback> out = new ArrayList<>();
        if (toolObjects == null) return out;
        for (Object o : toolObjects) if (o != null) out.addAll(callbacksOf(o));
        return out;
    }

    private List<ToolCallback> callbacksOf(Object o) {
        List<ToolCallback> own = new ArrayList<>();
        for (Method m : o.getClass().getMethods()) {
            AITool a = m.getAnnotation(AITool.class);
            if (a != null) own.add(callbackFor(o, m, a));
        }
        if (!own.isEmpty()) return own;
        try {
            return List.of(ToolCallbacks.from(o));
        } catch (RuntimeException e) {
            return List.of(); // Spring AI throws when an object declares no tools; that is not an error here
        }
    }

    private ToolCallback callbackFor(Object target, Method method, AITool a) {
        ToolDefinition definition = ToolDefinition.builder()
                .name(a.name().isBlank() ? method.getName() : a.name())
                .description(a.description())
                .inputSchema(JsonSchemaGenerator.generateForMethodInput(method))
                .build();
        return MethodToolCallback.builder().toolDefinition(definition).toolMethod(method).toolObject(target).build();
    }
}
