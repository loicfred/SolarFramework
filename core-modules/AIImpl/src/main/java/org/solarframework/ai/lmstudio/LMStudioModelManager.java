package org.solarframework.ai.lmstudio;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Talks to a local LM Studio server about the models it holds. Internal to AIImpl: what a caller
 * sees is the owning {@code SpringAIService}, which reports its own model directly.
 * <p>Two things about LM Studio shape this class. It answers <strong>HTTP 200 even for unknown
 * endpoints</strong>, putting the failure in an {@code {"error": ...}} body — so the status code is
 * never trusted here. And it exposes <strong>no REST load/unload</strong>, so those go through the
 * {@code lms} CLI; without it they report false and the model still loads just-in-time on first use.
 * <p>Never downloads and never deletes a model.
 */
public class LMStudioModelManager {

    private static final Logger log = LoggerFactory.getLogger(LMStudioModelManager.class);
    private static final long PROBE_CACHE_MS = 5000;

    /** What LM Studio reports about one model. */
    public record Model(String id, String type, String publisher, String arch, String quantization, String state, int maxContextLength, int loadedContextLength, List<String> capabilities) {
        public Model { capabilities = capabilities == null ? List.of() : List.copyOf(capabilities); }

        public boolean isLoaded() { return "loaded".equalsIgnoreCase(state); }
        public int usableContext() { return loadedContextLength > 0 ? loadedContextLength : maxContextLength; }
    }

    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final String cli = findCli();
    private boolean reachable;
    private long probedAt;
    private boolean warnedAboutCli;

    public LMStudioModelManager(String baseUrl) {
        this.baseUrl = baseUrl == null ? "http://localhost:1234" : baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public boolean isAvailable() {
        if (probedAt != 0 && System.currentTimeMillis() - probedAt < PROBE_CACHE_MS) return reachable;
        probedAt = System.currentTimeMillis();
        return reachable = bodyOf("/v1/models") != null;
    }

    public List<Model> list() {
        JsonObject body = bodyOf("/api/v0/models");
        List<Model> models = new ArrayList<>();
        if (body == null || !body.has("data")) return models;
        JsonArray data = body.getAsJsonArray("data");
        for (JsonElement e : data) if (e.isJsonObject()) models.add(toModel(e.getAsJsonObject()));
        return models;
    }

    public Optional<Model> get(String id) {
        return id == null ? Optional.empty() : list().stream().filter(m -> m.id().equals(id)).findFirst();
    }

    public boolean load(String id, Integer contextLength, Integer ttlSeconds) {
        List<String> cmd = new ArrayList<>(List.of("load", id, "--yes"));
        if (contextLength != null) { cmd.add("--context-length"); cmd.add(contextLength.toString()); }
        if (ttlSeconds != null) { cmd.add("--ttl"); cmd.add(ttlSeconds.toString()); }
        return run(cmd);
    }

    public boolean unload(String id) { return run(List.of("unload", id)); }

    /** LM Studio loads on demand, so a reachable server can always serve a model it already holds. */
    public boolean ensureLoaded(String id) {
        return get(id).filter(Model::isLoaded).isPresent() || load(id, null, null) || get(id).isPresent();
    }

    private Model toModel(JsonObject o) {
        List<String> capabilities = new ArrayList<>();
        if (o.has("capabilities") && o.get("capabilities").isJsonArray())
            for (JsonElement c : o.getAsJsonArray("capabilities")) capabilities.add(c.getAsString());
        return new Model(text(o, "id"), text(o, "type"), text(o, "publisher"), text(o, "arch"), text(o, "quantization"),
                text(o, "state"), number(o, "max_context_length"), number(o, "loaded_context_length"), capabilities);
    }

    private static String text(JsonObject o, String key) { return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null; }
    private static int number(JsonObject o, String key) { return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsInt() : 0; }

    /** Null when the server is unreachable, returns a non-object, or reports an error in the body. */
    private JsonObject bodyOf(String path) {
        try {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(5)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            JsonElement parsed = JsonParser.parseString(r.body());
            if (!parsed.isJsonObject()) return null;
            JsonObject o = parsed.getAsJsonObject();
            if (o.has("error")) { log.debug("LM Studio rejected {}: {}", path, o.get("error")); return null; }
            return o;
        } catch (Exception e) {
            log.debug("LM Studio unreachable at {}{}: {}", baseUrl, path, e.toString());
            return null;
        }
    }

    private boolean run(List<String> args) {
        if (cli == null) {
            if (!warnedAboutCli) { warnedAboutCli = true; log.info("lms CLI not found; LM Studio has no REST load/unload, so models will only load just-in-time"); }
            return false;
        }
        List<String> cmd = new ArrayList<>(List.of(cli));
        cmd.addAll(args);
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            if (!p.waitFor(5, TimeUnit.MINUTES)) { p.destroyForcibly(); return false; }
            return p.exitValue() == 0;
        } catch (Exception e) {
            log.warn("lms {} failed: {}", args, e.toString());
            return false;
        }
    }

    private static String findCli() {
        String home = System.getProperty("user.home");
        for (String candidate : List.of(home + "/.lmstudio/bin/lms.exe", home + "/.lmstudio/bin/lms"))
            if (new File(candidate).canExecute()) return candidate;
        return System.getenv("PATH") == null ? null : onPath();
    }

    private static String onPath() {
        for (String dir : System.getenv("PATH").split(File.pathSeparator))
            for (String name : List.of("lms.exe", "lms")) {
                File f = new File(dir, name);
                if (f.canExecute()) return f.getAbsolutePath();
            }
        return null;
    }

    public String baseUrl() { return baseUrl; }
    public boolean hasCli() { return cli != null; }
}
