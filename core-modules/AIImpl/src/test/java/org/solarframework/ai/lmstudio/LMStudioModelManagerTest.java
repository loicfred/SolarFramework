package org.solarframework.ai.lmstudio;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Runs against a stub server, so it never needs LM Studio. */
class LMStudioModelManagerTest {

    private static final String MODELS = """
            {"data":[
              {"id":"openai/gpt-oss-20b","object":"model","type":"llm","publisher":"openai","arch":"gpt-oss",
               "quantization":"MXFP4","state":"loaded","max_context_length":131072,"loaded_context_length":8192,
               "capabilities":["tool_use"]},
              {"id":"google/gemma-3-12b","object":"model","type":"llm","publisher":"google","arch":"gemma3",
               "quantization":"Q4_K_M","state":"not-loaded","max_context_length":131072,"loaded_context_length":0}
            ]}""";

    private HttpServer server;
    private final Map<String, String> routes = new HashMap<>();
    private LMStudioModelManager manager;

    @BeforeEach void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            // LM Studio answers 200 even for endpoints it does not have, with the failure in the body.
            String body = routes.getOrDefault(exchange.getRequestURI().getPath(),
                    "{\"error\":\"Unexpected endpoint or method. (" + exchange.getRequestURI().getPath() + ")\"}");
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        manager = new LMStudioModelManager("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach void stop() { server.stop(0); }

    @Test void readsStateCapabilitiesAndContext() {
        routes.put("/api/v0/models", MODELS);

        assertEquals(2, manager.list().size());
        LMStudioModelManager.Model oss = manager.get("openai/gpt-oss-20b").orElseThrow();
        assertTrue(oss.isLoaded());
        assertEquals(8192, oss.usableContext(), "a model can load far below its maximum");
        assertEquals("MXFP4", oss.quantization());
        assertTrue(oss.capabilities().contains("tool_use"));
    }

    @Test void anUnloadedModelReportsItsMaximumInstead() {
        routes.put("/api/v0/models", MODELS);
        LMStudioModelManager.Model gemma = manager.get("google/gemma-3-12b").orElseThrow();

        assertFalse(gemma.isLoaded());
        assertEquals(131072, gemma.usableContext());
        assertTrue(gemma.capabilities().isEmpty());
    }

    /** The regression that matters: a 200 carrying an error body is a failure, not an empty success. */
    @Test void treatsA200ErrorBodyAsFailure() {
        routes.put("/v1/models", "{\"error\":\"Unexpected endpoint or method.\"}");
        assertFalse(manager.isAvailable());
        assertTrue(manager.list().isEmpty());
    }

    @Test void availabilityFollowsTheProbeEndpoint() {
        routes.put("/v1/models", "{\"data\":[]}");
        assertTrue(manager.isAvailable());
    }

    @Test void unknownModelIsAbsentRatherThanNull() {
        routes.put("/api/v0/models", MODELS);
        assertTrue(manager.get("nope").isEmpty());
        assertTrue(manager.get(null).isEmpty());
    }

    @Test void unreachableServerDegradesInsteadOfThrowing() {
        LMStudioModelManager dead = new LMStudioModelManager("http://127.0.0.1:1");
        assertFalse(dead.isAvailable());
        assertTrue(dead.list().isEmpty());
        assertTrue(dead.get("anything").isEmpty());
    }

    @Test void trailingSlashInBaseUrlIsHarmless() {
        assertEquals("http://localhost:1234", new LMStudioModelManager("http://localhost:1234/").baseUrl());
        assertEquals("http://localhost:1234", new LMStudioModelManager(null).baseUrl());
    }
}
