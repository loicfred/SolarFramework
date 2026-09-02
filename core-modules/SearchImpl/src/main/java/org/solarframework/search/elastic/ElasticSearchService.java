package org.solarframework.search.elastic;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.solarframework.search.ISearchService;
import org.solarframework.search.dto.SearchDocument;
import org.solarframework.search.dto.SearchQuery;
import org.solarframework.search.dto.SearchResults;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class ElasticSearchService implements ISearchService {
    private static final Logger log = LoggerFactory.getLogger(ElasticSearchService.class);
    private static final long PROBE_CACHE_MS = 5000;
    private final String node;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
    private final Gson gson = new Gson();
    private boolean reachable;
    private long probedAt;

    public ElasticSearchService(String node) { this.node = node.endsWith("/") ? node.substring(0, node.length() - 1) : node; }

    public String node() { return node; }


    @Override public boolean isAvailable() {
        if (probedAt != 0 && System.currentTimeMillis() - probedAt < PROBE_CACHE_MS) return reachable;
        probedAt = System.currentTimeMillis();
        reachable = requestOrNull("GET", "/", null) != null;
        return reachable;
    }


    @Override public boolean createIndex(String index, Map<String, String> fieldTypes) { return isNamed(index) && fieldTypes != null && !fieldTypes.isEmpty() && requestOrNull("PUT", "/" + index, gson.toJson(ElasticQuery.mappingBody(fieldTypes))) != null; }
    @Override public boolean deleteIndex(String index) { return isNamed(index) && requestOrNull("DELETE", "/" + index, null) != null; }
    @Override public boolean index(SearchDocument doc) {
        if (doc == null || doc.validate() != null) return false;
        return requestOrNull("PUT", "/" + doc.index() + "/_doc/" + asPathSegment(doc.id()), gson.toJson(doc.fields())) != null;
    }
    @Override public boolean indexAll(List<SearchDocument> docs) {
        if (docs == null || docs.isEmpty()) return true;
        return requestOrNull("POST", "/_bulk", ElasticQuery.bulkBody(docs)) != null;
    }
    @Override public boolean delete(String index, String id) { return isNamed(index) && isNamed(id) && requestOrNull("DELETE", "/" + index + "/_doc/" + asPathSegment(id), null) != null; }
    @Override public SearchResults search(SearchQuery query) {
        if (query == null || query.validate() != null) return SearchResults.empty();
        JsonObject answer = requestOrNull("POST", "/" + query.index() + "/_search", gson.toJson(ElasticQuery.searchBody(query)));
        return answer == null ? SearchResults.empty() : ElasticResponse.read(answer);
    }


    private JsonObject requestOrNull(String method, String path, String body) {
        try {
            HttpRequest.BodyPublisher payload = body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body);
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(node + path)).timeout(Duration.ofSeconds(5)).header("Content-Type", contentTypeFor(path)).method(method, payload).build();
            HttpResponse<String> answer = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (answer.statusCode() >= 300) { log.warn("Elasticsearch answered {} to {} {}", answer.statusCode(), method, path); return null; }
            return gson.fromJson(answer.body(), JsonObject.class);
        } catch (Exception e) {
            log.warn("Elasticsearch at {} could not be reached: {}", node, e.getMessage());
            return null;
        }
    }
    private static boolean isNamed(String value) { return value != null && !value.isBlank(); }
    private static String contentTypeFor(String path) { return path.equals("/_bulk") ? "application/x-ndjson" : "application/json"; }
    private static String asPathSegment(String id) { return URLEncoder.encode(id, StandardCharsets.UTF_8).replace("+", "%20"); }
}
