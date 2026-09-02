# SolarFramework Search Subsystem Implementation Plan

**Status: implemented 28 August 2026.** All five tasks executed; `./mvnw.cmd clean install` passes across every module, SearchAPI 9 tests, SearchImpl 16 (the live test skipping with no node running). Not committed — see *Outside this plan* at the foot.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give SolarFramework a full-text search capability backed by Elasticsearch, as a `SearchAPI` / `SearchImpl` module pair, with no consumer wired up yet.

**Architecture:** `SearchAPI` holds the interface, four DTO records and a null-object default, and depends on nothing. `SearchImpl` talks to Elasticsearch over its REST API using `java.net.http.HttpClient` and Gson, with request bodies assembled as Gson object trees rather than formatted strings. Callers reach the service through the static `SearchRegistry.SolarSearch`, which defaults to a no-op so an unconfigured host still runs.

**Tech Stack:** Java 25, Maven (`./mvnw.cmd`), Gson 2.14.0 (already managed in the root pom), SLF4J, JUnit Jupiter. No new third-party dependency.

**Spec:** `docs/superpowers/specs/2026-08-28-search-elasticsearch-design.md`

## Global Constraints

- **No comments and no javadoc.** Every method name must carry its own meaning; if a method needs a comment to be understood, rename or split it instead. Rationale that genuinely cannot live in a name belongs in this plan or the spec, not in the source.
- **Never run `git commit`, `git push`, `git pull`, `git fetch`, or `gh`.** Loïc commits his own work. Every task below ends in a build, not a commit.
- Build with the wrapper only — `./mvnw.cmd`; plain `mvn` is not on the PATH.
- **`./mvnw.cmd clean install` must pass after every change**, and must pass on a machine with **no Elasticsearch running**. Only the one guarded live test may touch a real node.
- Java 25; all modules `org.solarframework.mu`, version `1.0`.
- A method signature, constructor or record header stays on **one line**, however long.
- Related methods sit directly under each other; **two** blank lines separate one group from the next.
- Behaviour derived from one record's own data goes **on that record** as a method, not in a helper class.
- `SearchAPI` must depend on nothing — no Gson, no HTTP, no Elasticsearch type.

---

### Task 1: SearchAPI — interface, DTOs, registry

**Files:**
- Create: `core-modules/SearchAPI/pom.xml`
- Create: `core-modules/SearchAPI/src/main/java/org/solarframework/search/ISearchService.java`
- Create: `core-modules/SearchAPI/src/main/java/org/solarframework/search/NoSearchService.java`
- Create: `core-modules/SearchAPI/src/main/java/org/solarframework/search/dto/SearchDocument.java`
- Create: `core-modules/SearchAPI/src/main/java/org/solarframework/search/dto/SearchQuery.java`
- Create: `core-modules/SearchAPI/src/main/java/org/solarframework/search/dto/SearchHit.java`
- Create: `core-modules/SearchAPI/src/main/java/org/solarframework/search/dto/SearchResults.java`
- Create: `core-modules/SearchAPI/src/main/java/org/solarframework/search/spring/SearchRegistry.java`
- Modify: root `pom.xml` — add `<module>core-modules/SearchAPI</module>` and a managed dependency
- Test: `core-modules/SearchAPI/src/test/java/org/solarframework/search/SearchApiTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `ISearchService` (7 methods, below); `SearchDocument(String index, String id, Map<String,Object> fields)` with `String validate()`; `SearchQuery(String index, String text, List<String> fields, Map<String,Object> filters, int from, int size)` with `static SearchQuery of(String, String, List<String>)`, `boolean isMatchAll()`, `int clampedSize()`, `int clampedFrom()`, `String validate()`, `int MAX_SIZE = 500`, `int DEFAULT_SIZE = 20`; `SearchHit(String id, double score, Map<String,Object> source)` with `String text(String)`, `Long number(String)`; `SearchResults(long total, List<SearchHit> hits)` with `static empty()`, `boolean isEmpty()`, `List<String> ids()`; `NoSearchService`; `SearchRegistry.SolarSearch`.

**Design facts the names cannot carry** (kept here, deliberately not in the source):
`createIndex`'s `fieldTypes` maps a field name to an Elasticsearch type — `"text"`, `"keyword"`, `"long"`, `"double"`, `"date"` or `"boolean"`. Every `ISearchService` method answers rather than throws because an engine that is down is an ordinary condition and the caller's response is to fall back. `SearchResults.ids()` is the contract the design rests on: nothing authoritative lives in the index, so a caller searches for the ranked order and reads the rows from its own store.

- [ ] **Step 1: Write the pom**

`core-modules/SearchAPI/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.solarframework.mu</groupId>
        <artifactId>SolarFramework</artifactId>
        <version>1.0</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>

    <artifactId>SearchAPI</artifactId>

    <dependencies>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: Wire it into the root pom**

In root `pom.xml`, add after `<module>core-modules/JSON</module>`:

```xml
        <module>core-modules/SearchAPI</module>
```

And inside `<dependencyManagement><dependencies>`, beside the other `org.solarframework.mu` entries:

```xml
            <dependency>
                <groupId>org.solarframework.mu</groupId>
                <artifactId>SearchAPI</artifactId>
                <version>1.0</version>
            </dependency>
```

- [ ] **Step 3: Write the failing test**

`core-modules/SearchAPI/src/test/java/org/solarframework/search/SearchApiTest.java`:

```java
package org.solarframework.search;

import org.junit.jupiter.api.Test;
import org.solarframework.search.dto.SearchDocument;
import org.solarframework.search.dto.SearchHit;
import org.solarframework.search.dto.SearchQuery;
import org.solarframework.search.dto.SearchResults;
import org.solarframework.search.spring.SearchRegistry;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SearchApiTest {

    @Test
    void ofBuildsAFirstPageWithNoFilters() {
        SearchQuery q = SearchQuery.of("vacancy", "welder", List.of("title", "description"));
        assertEquals("vacancy", q.index());
        assertEquals(0, q.clampedFrom());
        assertEquals(20, q.clampedSize());
        assertTrue(q.filters().isEmpty());
        assertNull(q.validate());
    }

    @Test
    void blankTextIsMatchAll() {
        assertTrue(SearchQuery.of("vacancy", null, List.of("title")).isMatchAll());
        assertTrue(SearchQuery.of("vacancy", "   ", List.of("title")).isMatchAll());
        assertFalse(SearchQuery.of("vacancy", "welder", List.of("title")).isMatchAll());
    }

    @Test
    void sizeIsClampedSoOneCallerCannotDrainTheNode() {
        assertEquals(20, new SearchQuery("v", "x", List.of("t"), Map.of(), 0, 0).clampedSize());
        assertEquals(50, new SearchQuery("v", "x", List.of("t"), Map.of(), 0, 50).clampedSize());
        assertEquals(SearchQuery.MAX_SIZE, new SearchQuery("v", "x", List.of("t"), Map.of(), 0, 5000).clampedSize());
        assertEquals(0, new SearchQuery("v", "x", List.of("t"), Map.of(), -5, 10).clampedFrom());
    }

    @Test
    void aQueryWithoutAnIndexOrFieldsIsRefused() {
        assertNotNull(new SearchQuery(null, "x", List.of("t"), Map.of(), 0, 10).validate());
        assertNotNull(new SearchQuery("  ", "x", List.of("t"), Map.of(), 0, 10).validate());
        assertNotNull(new SearchQuery("v", "x", List.of(), Map.of(), 0, 10).validate());
        assertNull(new SearchQuery("v", "", List.of(), Map.of(), 0, 10).validate());
    }

    @Test
    void aDocumentNeedsAnIndexAnIdAndAtLeastOneField() {
        assertNull(new SearchDocument("vacancy", "7", Map.of("title", "Welder")).validate());
        assertNotNull(new SearchDocument(null, "7", Map.of("title", "Welder")).validate());
        assertNotNull(new SearchDocument("vacancy", " ", Map.of("title", "Welder")).validate());
        assertNotNull(new SearchDocument("vacancy", "7", Map.of()).validate());
    }

    @Test
    void hitNarrowsJsonNumbersToLong() {
        SearchHit hit = new SearchHit("7", 1.5, Map.of("title", "Welder", "sectorId", 5.0));
        assertEquals("Welder", hit.text("title"));
        assertEquals(5L, hit.number("sectorId"));
        assertNull(hit.text("missing"));
        assertNull(hit.number("title"));
    }

    @Test
    void resultsHandBackTheRankedIds() {
        SearchResults r = new SearchResults(2, List.of(new SearchHit("9", 2.0, Map.of()), new SearchHit("4", 1.0, Map.of())));
        assertEquals(List.of("9", "4"), r.ids());
        assertFalse(r.isEmpty());
        assertTrue(SearchResults.empty().isEmpty());
        assertEquals(List.of(), SearchResults.empty().ids());
    }

    @Test
    void theDefaultServiceDegradesInsteadOfFailing() {
        ISearchService none = new NoSearchService();
        assertFalse(none.isAvailable());
        assertFalse(none.index(new SearchDocument("v", "1", Map.of("a", "b"))));
        assertFalse(none.indexAll(List.of()));
        assertFalse(none.delete("v", "1"));
        assertFalse(none.createIndex("v", Map.of("a", "text")));
        assertFalse(none.deleteIndex("v"));
        assertTrue(none.search(SearchQuery.of("v", "x", List.of("a"))).isEmpty());
    }

    @Test
    void theRegistryIsNeverNullSoNoCallerNeedsANullCheck() {
        assertNotNull(SearchRegistry.SolarSearch);
        assertFalse(SearchRegistry.SolarSearch.isAvailable());
    }
}
```

- [ ] **Step 4: Run it to verify it fails**

Run: `./mvnw.cmd -pl core-modules/SearchAPI test`
Expected: FAIL — compilation errors, the classes do not exist yet.

- [ ] **Step 5: Write the DTOs**

`dto/SearchDocument.java`:

```java
package org.solarframework.search.dto;

import java.util.Map;

public record SearchDocument(String index, String id, Map<String, Object> fields) {
    public String validate() {
        if (index == null || index.isBlank()) return "A document needs the name of the index it belongs to.";
        if (id == null || id.isBlank()) return "A document needs an id.";
        if (fields == null || fields.isEmpty()) return "A document needs at least one field to search on.";
        return null;
    }
}
```

`dto/SearchQuery.java`:

```java
package org.solarframework.search.dto;

import java.util.List;
import java.util.Map;

public record SearchQuery(String index, String text, List<String> fields, Map<String, Object> filters, int from, int size) {
    public static final int MAX_SIZE = 500;
    public static final int DEFAULT_SIZE = 20;

    public static SearchQuery of(String index, String text, List<String> fields) { return new SearchQuery(index, text, fields, Map.of(), 0, DEFAULT_SIZE); }


    public boolean isMatchAll() { return text == null || text.isBlank(); }
    public int clampedSize() { return size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE); }
    public int clampedFrom() { return Math.max(from, 0); }
    public String validate() {
        if (index == null || index.isBlank()) return "A search needs the name of the index to look in.";
        if (!isMatchAll() && (fields == null || fields.isEmpty())) return "A text search needs at least one field to look in.";
        return null;
    }
}
```

`dto/SearchHit.java`:

```java
package org.solarframework.search.dto;

import java.util.Map;

public record SearchHit(String id, double score, Map<String, Object> source) {
    public String text(String field) { return storedValue(field) == null ? null : String.valueOf(storedValue(field)); }
    public Long number(String field) { return storedValue(field) instanceof Number n ? n.longValue() : null; }
    private Object storedValue(String field) { return source == null ? null : source.get(field); }
}
```

`dto/SearchResults.java`:

```java
package org.solarframework.search.dto;

import java.util.List;

public record SearchResults(long total, List<SearchHit> hits) {
    public static SearchResults empty() { return new SearchResults(0, List.of()); }


    public boolean isEmpty() { return hits == null || hits.isEmpty(); }
    public List<String> ids() { return hits == null ? List.of() : hits.stream().map(SearchHit::id).toList(); }
}
```

- [ ] **Step 6: Write the interface, the null object and the registry**

`ISearchService.java`:

```java
package org.solarframework.search;

import org.solarframework.search.dto.SearchDocument;
import org.solarframework.search.dto.SearchQuery;
import org.solarframework.search.dto.SearchResults;

import java.util.List;
import java.util.Map;

public interface ISearchService {
    boolean isAvailable();
    boolean createIndex(String index, Map<String, String> fieldTypes);
    boolean deleteIndex(String index);
    boolean index(SearchDocument doc);
    boolean indexAll(List<SearchDocument> docs);
    boolean delete(String index, String id);
    SearchResults search(SearchQuery query);
}
```

`NoSearchService.java`:

```java
package org.solarframework.search;

import org.solarframework.search.dto.SearchDocument;
import org.solarframework.search.dto.SearchQuery;
import org.solarframework.search.dto.SearchResults;

import java.util.List;
import java.util.Map;

public class NoSearchService implements ISearchService {
    @Override public boolean isAvailable() { return false; }
    @Override public boolean createIndex(String index, Map<String, String> fieldTypes) { return false; }
    @Override public boolean deleteIndex(String index) { return false; }
    @Override public boolean index(SearchDocument doc) { return false; }
    @Override public boolean indexAll(List<SearchDocument> docs) { return false; }
    @Override public boolean delete(String index, String id) { return false; }
    @Override public SearchResults search(SearchQuery query) { return SearchResults.empty(); }
}
```

`spring/SearchRegistry.java`:

```java
package org.solarframework.search.spring;

import org.solarframework.search.ISearchService;
import org.solarframework.search.NoSearchService;

public class SearchRegistry {
    public static ISearchService SolarSearch = new NoSearchService();
}
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./mvnw.cmd -pl core-modules/SearchAPI test`
Expected: PASS, 9 tests.

- [ ] **Step 8: Verify the module installs**

Run: `./mvnw.cmd -pl core-modules/SearchAPI clean install`
Expected: BUILD SUCCESS. **Do not commit.**

---

### Task 2: SearchImpl — request bodies (`ElasticQuery`)

**Files:**
- Create: `core-modules/SearchImpl/pom.xml`
- Create: `core-modules/SearchImpl/src/main/java/org/solarframework/search/elastic/ElasticQuery.java`
- Modify: root `pom.xml` — add `<module>core-modules/SearchImpl</module>` and a managed dependency
- Test: `core-modules/SearchImpl/src/test/java/org/solarframework/search/elastic/ElasticQueryTest.java`

**Interfaces:**
- Consumes: `SearchQuery`, `SearchDocument` from Task 1.
- Produces: `ElasticQuery.searchBody(SearchQuery) -> JsonObject`, `ElasticQuery.mappingBody(Map<String,String>) -> JsonObject`, `ElasticQuery.bulkBody(List<SearchDocument>) -> String`.

**Design facts the names cannot carry:** bodies are assembled as Gson objects and serialised, never formatted into a string — a quotation mark typed into a search box would otherwise break the request, and a caller could close the value and add clauses of its own. `quotesAndBackslashesSurviveSerialisation` is the test that pins this, and it is the reason the builder is a separate class from the transport.

- [ ] **Step 1: Write the pom**

`core-modules/SearchImpl/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.solarframework.mu</groupId>
        <artifactId>SolarFramework</artifactId>
        <version>1.0</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>

    <artifactId>SearchImpl</artifactId>

    <dependencies>
        <dependency>
            <groupId>org.solarframework.mu</groupId>
            <artifactId>SearchAPI</artifactId>
        </dependency>
        <dependency>
            <groupId>com.google.code.gson</groupId>
            <artifactId>gson</artifactId>
        </dependency>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: Wire it into the root pom**

Add the module entry after `SearchAPI`'s:

```xml
        <module>core-modules/SearchImpl</module>
```

And the managed dependency beside `SearchAPI`'s:

```xml
            <dependency>
                <groupId>org.solarframework.mu</groupId>
                <artifactId>SearchImpl</artifactId>
                <version>1.0</version>
            </dependency>
```

- [ ] **Step 3: Write the failing test**

`core-modules/SearchImpl/src/test/java/org/solarframework/search/elastic/ElasticQueryTest.java`:

```java
package org.solarframework.search.elastic;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.solarframework.search.dto.SearchDocument;
import org.solarframework.search.dto.SearchQuery;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ElasticQueryTest {
    private static final Gson GSON = new Gson();

    @Test
    void textBecomesAFuzzyMultiMatch() {
        JsonObject body = ElasticQuery.searchBody(SearchQuery.of("vacancy", "welder", List.of("title", "description")));
        assertEquals(0, body.get("from").getAsInt());
        assertEquals(20, body.get("size").getAsInt());
        JsonObject match = body.getAsJsonObject("query").getAsJsonObject("multi_match");
        assertEquals("welder", match.get("query").getAsString());
        assertEquals("AUTO", match.get("fuzziness").getAsString());
        assertEquals(2, match.getAsJsonArray("fields").size());
        assertEquals("title", match.getAsJsonArray("fields").getFirst().getAsString());
    }

    @Test
    void blankTextBecomesMatchAll() {
        JsonObject body = ElasticQuery.searchBody(SearchQuery.of("vacancy", "  ", List.of("title")));
        assertTrue(body.getAsJsonObject("query").has("match_all"));
        assertFalse(body.getAsJsonObject("query").has("multi_match"));
    }

    @Test
    void filtersBecomeTermClausesUnderBool() {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("sectorId", 5);
        filters.put("published", true);
        JsonObject query = ElasticQuery.searchBody(new SearchQuery("vacancy", "welder", List.of("title"), filters, 0, 10)).getAsJsonObject("query");
        JsonObject bool = query.getAsJsonObject("bool");
        assertTrue(bool.getAsJsonObject("must").has("multi_match"));
        assertEquals(2, bool.getAsJsonArray("filter").size());
        assertEquals(5, bool.getAsJsonArray("filter").getFirst().getAsJsonObject().getAsJsonObject("term").get("sectorId").getAsInt());
        assertTrue(bool.getAsJsonArray("filter").get(1).getAsJsonObject().getAsJsonObject("term").get("published").getAsBoolean());
    }

    @Test
    void filtersAloneStillProduceABoolWithMatchAll() {
        JsonObject query = ElasticQuery.searchBody(new SearchQuery("vacancy", null, List.of(), Map.of("sectorId", 5), 0, 10)).getAsJsonObject("query");
        assertTrue(query.getAsJsonObject("bool").getAsJsonObject("must").has("match_all"));
    }

    @Test
    void quotesAndBackslashesSurviveSerialisation() {
        String nasty = "he said \"weld\" \\ then {\"match_all\":{}} \n done";
        String json = GSON.toJson(ElasticQuery.searchBody(SearchQuery.of("vacancy", nasty, List.of("title"))));
        JsonObject reparsed = GSON.fromJson(json, JsonObject.class);
        assertEquals(nasty, reparsed.getAsJsonObject("query").getAsJsonObject("multi_match").get("query").getAsString());
        assertTrue(reparsed.getAsJsonObject("query").has("multi_match"));
    }

    @Test
    void mappingNamesEachFieldsType() {
        Map<String, String> types = new LinkedHashMap<>();
        types.put("title", "text");
        types.put("sectorId", "long");
        JsonObject properties = ElasticQuery.mappingBody(types).getAsJsonObject("mappings").getAsJsonObject("properties");
        assertEquals("text", properties.getAsJsonObject("title").get("type").getAsString());
        assertEquals("long", properties.getAsJsonObject("sectorId").get("type").getAsString());
    }

    @Test
    void bulkIsTwoNewlineTerminatedLinesPerDocument() {
        String body = ElasticQuery.bulkBody(List.of(new SearchDocument("vacancy", "7", Map.of("title", "Welder")), new SearchDocument("vacancy", "9", Map.of("title", "Mason"))));
        String[] lines = body.split("\n");
        assertEquals(4, lines.length);
        assertTrue(body.endsWith("\n"));
        assertEquals("vacancy", GSON.fromJson(lines[0], JsonObject.class).getAsJsonObject("index").get("_index").getAsString());
        assertEquals("7", GSON.fromJson(lines[0], JsonObject.class).getAsJsonObject("index").get("_id").getAsString());
        assertEquals("Welder", GSON.fromJson(lines[1], JsonObject.class).get("title").getAsString());
    }
}
```

- [ ] **Step 4: Run it to verify it fails**

Run: `./mvnw.cmd -pl core-modules/SearchImpl test`
Expected: FAIL — `ElasticQuery` does not exist.

- [ ] **Step 5: Write `ElasticQuery`**

```java
package org.solarframework.search.elastic;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.solarframework.search.dto.SearchDocument;
import org.solarframework.search.dto.SearchQuery;

import java.util.List;
import java.util.Map;

public class ElasticQuery {
    private static final Gson GSON = new Gson();

    public static JsonObject searchBody(SearchQuery q) {
        JsonObject body = new JsonObject();
        body.addProperty("from", q.clampedFrom());
        body.addProperty("size", q.clampedSize());
        body.add("query", queryClause(q));
        return body;
    }
    public static JsonObject mappingBody(Map<String, String> fieldTypes) {
        JsonObject properties = new JsonObject();
        fieldTypes.forEach((name, type) -> properties.add(name, wrapIn("type", new JsonPrimitive(type))));
        return wrapIn("mappings", wrapIn("properties", properties));
    }
    public static String bulkBody(List<SearchDocument> docs) {
        StringBuilder body = new StringBuilder();
        for (SearchDocument d : docs) {
            JsonObject target = new JsonObject();
            target.addProperty("_index", d.index());
            target.addProperty("_id", d.id());
            body.append(GSON.toJson(wrapIn("index", target))).append('\n').append(GSON.toJson(d.fields())).append('\n');
        }
        return body.toString();
    }


    private static JsonObject queryClause(SearchQuery q) {
        JsonObject scoring = q.isMatchAll() ? matchAllClause() : fuzzyMultiMatchClause(q.text(), q.fields());
        if (q.filters() == null || q.filters().isEmpty()) return scoring;
        JsonObject bool = new JsonObject();
        bool.add("must", scoring);
        bool.add("filter", exactMatchClauses(q.filters()));
        return wrapIn("bool", bool);
    }
    private static JsonObject matchAllClause() { return wrapIn("match_all", new JsonObject()); }
    private static JsonObject fuzzyMultiMatchClause(String text, List<String> fields) {
        JsonObject match = new JsonObject();
        match.addProperty("query", text);
        JsonArray names = new JsonArray();
        if (fields != null) fields.forEach(names::add);
        match.add("fields", names);
        match.addProperty("fuzziness", "AUTO");
        return wrapIn("multi_match", match);
    }
    private static JsonArray exactMatchClauses(Map<String, Object> filters) {
        JsonArray clauses = new JsonArray();
        filters.forEach((field, value) -> {
            JsonObject term = new JsonObject();
            term.add(field, asPrimitive(value));
            clauses.add(wrapIn("term", term));
        });
        return clauses;
    }


    private static JsonPrimitive asPrimitive(Object value) {
        if (value instanceof Number n) return new JsonPrimitive(n);
        if (value instanceof Boolean b) return new JsonPrimitive(b);
        return new JsonPrimitive(String.valueOf(value));
    }
    private static JsonObject wrapIn(String name, JsonElement child) {
        JsonObject holder = new JsonObject();
        holder.add(name, child);
        return holder;
    }
}
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./mvnw.cmd -pl core-modules/SearchImpl test`
Expected: PASS, 7 tests.

---

### Task 3: SearchImpl — reading responses (`ElasticResponse`)

**Files:**
- Create: `core-modules/SearchImpl/src/main/java/org/solarframework/search/elastic/ElasticResponse.java`
- Test: `core-modules/SearchImpl/src/test/java/org/solarframework/search/elastic/ElasticResponseTest.java`

**Interfaces:**
- Consumes: `SearchResults`, `SearchHit` from Task 1.
- Produces: `ElasticResponse.read(JsonObject) -> SearchResults`.

**Design facts the names cannot carry:** a body missing the parts read here becomes an empty result rather than a failure, because the caller's fallback is the same either way. Values are kept as `Number` so `SearchHit.number` can narrow them — JSON has no integer type of its own, so a stored id arrives as a double.

- [ ] **Step 1: Write the failing test**

```java
package org.solarframework.search.elastic;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.solarframework.search.dto.SearchResults;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ElasticResponseTest {
    private static final Gson GSON = new Gson();

    private static JsonObject json(String raw) { return GSON.fromJson(raw, JsonObject.class); }

    @Test
    void readsTheTotalAndTheRankedIds() {
        SearchResults r = ElasticResponse.read(json("""
            {"hits":{"total":{"value":2,"relation":"eq"},"hits":[
              {"_id":"9","_score":2.5,"_source":{"title":"Welder","sectorId":5}},
              {"_id":"4","_score":1.25,"_source":{"title":"Mason","sectorId":7}}]}}"""));
        assertEquals(2, r.total());
        assertEquals(List.of("9", "4"), r.ids());
        assertEquals(2.5, r.hits().getFirst().score());
        assertEquals("Welder", r.hits().getFirst().text("title"));
        assertEquals(5L, r.hits().getFirst().number("sectorId"));
    }

    @Test
    void anAnswerWithoutHitsIsEmptyRatherThanAFailure() {
        assertTrue(ElasticResponse.read(json("{}")).isEmpty());
        assertTrue(ElasticResponse.read(json("{\"hits\":{\"hits\":[]}}")).isEmpty());
        assertEquals(0, ElasticResponse.read(json("{\"hits\":{\"hits\":[]}}")).total());
    }

    @Test
    void aNullScoreFromMatchAllIsReadAsZero() {
        SearchResults r = ElasticResponse.read(json("{\"hits\":{\"total\":{\"value\":1},\"hits\":[{\"_id\":\"3\",\"_score\":null,\"_source\":{}}]}}"));
        assertEquals(0.0, r.hits().getFirst().score());
        assertEquals(List.of("3"), r.ids());
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw.cmd -pl core-modules/SearchImpl test`
Expected: FAIL — `ElasticResponse` does not exist.

- [ ] **Step 3: Write `ElasticResponse`**

```java
package org.solarframework.search.elastic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.solarframework.search.dto.SearchHit;
import org.solarframework.search.dto.SearchResults;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ElasticResponse {

    public static SearchResults read(JsonObject body) {
        JsonObject hits = body == null ? null : body.getAsJsonObject("hits");
        JsonArray array = hits == null ? null : hits.getAsJsonArray("hits");
        if (array == null) return SearchResults.empty();
        List<SearchHit> found = new ArrayList<>();
        for (JsonElement e : array) found.add(toHit(e.getAsJsonObject()));
        return new SearchResults(totalOrZero(hits), found);
    }


    private static long totalOrZero(JsonObject hits) {
        JsonObject total = hits.getAsJsonObject("total");
        return total == null || !total.has("value") ? 0 : total.get("value").getAsLong();
    }
    private static SearchHit toHit(JsonObject raw) {
        Map<String, Object> source = new LinkedHashMap<>();
        JsonObject stored = raw.getAsJsonObject("_source");
        if (stored != null) stored.entrySet().forEach(field -> source.put(field.getKey(), toJavaValue(field.getValue())));
        return new SearchHit(raw.get("_id").getAsString(), scoreOrZero(raw), source);
    }
    private static double scoreOrZero(JsonObject raw) { return !raw.has("_score") || raw.get("_score").isJsonNull() ? 0 : raw.get("_score").getAsDouble(); }
    private static Object toJavaValue(JsonElement e) {
        if (e.isJsonNull()) return null;
        if (!e.isJsonPrimitive()) return e.toString();
        JsonPrimitive p = e.getAsJsonPrimitive();
        return p.isNumber() ? p.getAsNumber() : p.isBoolean() ? p.getAsBoolean() : p.getAsString();
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./mvnw.cmd -pl core-modules/SearchImpl test`
Expected: PASS, 10 tests (7 from Task 2, 3 here).

---

### Task 4: SearchImpl — the transport (`ElasticSearchService`)

**Files:**
- Create: `core-modules/SearchImpl/src/main/java/org/solarframework/search/elastic/ElasticSearchService.java`
- Test: `core-modules/SearchImpl/src/test/java/org/solarframework/search/elastic/ElasticSearchServiceTest.java`

**Interfaces:**
- Consumes: `ISearchService` and the DTOs from Task 1; `ElasticQuery` from Task 2; `ElasticResponse` from Task 3.
- Produces: `ElasticSearchService implements ISearchService`, constructor `ElasticSearchService(String node)`, accessor `String node()`.

**Design facts the names cannot carry:** `requestOrNull` returning null covers refusal, error status and unreachability alike, because every caller responds identically — fall back. `isAvailable` caches for `PROBE_CACHE_MS` so a node that is down costs one round trip per window rather than one per search, which is what makes the fallback cheap enough for a page that searches on each keystroke. `_bulk` is newline-delimited JSON and the node refuses it under the ordinary JSON content type. The test points at port 1, which is reserved and unlistened, so connections are refused immediately rather than timing out.

- [ ] **Step 1: Write the failing test**

```java
package org.solarframework.search.elastic;

import org.junit.jupiter.api.Test;
import org.solarframework.search.dto.SearchDocument;
import org.solarframework.search.dto.SearchQuery;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ElasticSearchServiceTest {
    private final ElasticSearchService offline = new ElasticSearchService("http://127.0.0.1:1");

    @Test
    void anUnreachableNodeDegradesAndNeverThrows() {
        assertFalse(offline.isAvailable());
        assertFalse(offline.index(new SearchDocument("vacancy", "7", Map.of("title", "Welder"))));
        assertFalse(offline.delete("vacancy", "7"));
        assertFalse(offline.createIndex("vacancy", Map.of("title", "text")));
        assertFalse(offline.deleteIndex("vacancy"));
        assertTrue(offline.search(SearchQuery.of("vacancy", "welder", List.of("title"))).isEmpty());
    }

    @Test
    void anInvalidDocumentOrQueryIsRefusedWithoutATripToTheNode() {
        assertFalse(offline.index(null));
        assertFalse(offline.index(new SearchDocument("vacancy", "", Map.of("title", "Welder"))));
        assertTrue(offline.search(null).isEmpty());
        assertTrue(offline.search(new SearchQuery(null, "x", List.of("t"), Map.of(), 0, 10)).isEmpty());
    }

    @Test
    void nothingToIndexSucceedsWithoutATripToTheNode() {
        assertTrue(offline.indexAll(List.of()));
        assertTrue(offline.indexAll(null));
    }

    @Test
    void aTrailingSlashOnTheNodeUrlIsNotDoubledIntoThePath() {
        assertEquals("http://127.0.0.1:9200", new ElasticSearchService("http://127.0.0.1:9200/").node());
        assertEquals("http://127.0.0.1:9200", new ElasticSearchService("http://127.0.0.1:9200").node());
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw.cmd -pl core-modules/SearchImpl test`
Expected: FAIL — `ElasticSearchService` does not exist.

- [ ] **Step 3: Write `ElasticSearchService`**

```java
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


    @Override public boolean createIndex(String index, Map<String, String> fieldTypes) { return requestOrNull("PUT", "/" + index, gson.toJson(ElasticQuery.mappingBody(fieldTypes))) != null; }
    @Override public boolean deleteIndex(String index) { return requestOrNull("DELETE", "/" + index, null) != null; }
    @Override public boolean index(SearchDocument doc) {
        if (doc == null || doc.validate() != null) return false;
        return requestOrNull("PUT", "/" + doc.index() + "/_doc/" + asPathSegment(doc.id()), gson.toJson(doc.fields())) != null;
    }
    @Override public boolean indexAll(List<SearchDocument> docs) {
        if (docs == null || docs.isEmpty()) return true;
        return requestOrNull("POST", "/_bulk", ElasticQuery.bulkBody(docs)) != null;
    }
    @Override public boolean delete(String index, String id) { return requestOrNull("DELETE", "/" + index + "/_doc/" + asPathSegment(id), null) != null; }
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
    private static String contentTypeFor(String path) { return path.equals("/_bulk") ? "application/x-ndjson" : "application/json"; }
    private static String asPathSegment(String id) { return URLEncoder.encode(id, StandardCharsets.UTF_8).replace("+", "%20"); }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./mvnw.cmd -pl core-modules/SearchImpl test`
Expected: PASS, 14 tests.

---

### Task 5: The live-node test, and the docs

**Files:**
- Create: `core-modules/SearchImpl/src/test/java/org/solarframework/search/elastic/ElasticSearchServiceLiveTest.java`
- Modify: `CLAUDE.md` — the module layout list
- Modify: `docs/superpowers/specs/2026-08-28-search-elasticsearch-design.md` — the status line

**Interfaces:**
- Consumes: everything from Tasks 1–4.
- Produces: nothing new.

**Design facts the names cannot carry:** this is the only test needing a real node, and it must **skip** rather than fail where none is running, or the Stop hook's build breaks for everyone. Start a node with `docker run -p 9200:9200 -e discovery.type=single-node -e xpack.security.enabled=false docker.elastic.co/elasticsearch/elasticsearch:8.15.0`. A write is not searchable until the index refreshes, which the node does about once a second on its own — hence `searchUntilFound`.

- [ ] **Step 1: Write the guarded live test**

```java
package org.solarframework.search.elastic;

import org.junit.jupiter.api.Test;
import org.solarframework.search.dto.SearchDocument;
import org.solarframework.search.dto.SearchQuery;
import org.solarframework.search.dto.SearchResults;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ElasticSearchServiceLiveTest {
    private static final String INDEX = "solarframework_selftest";
    private final ElasticSearchService es = new ElasticSearchService(System.getProperty("es.node", "http://localhost:9200"));

    @Test
    void indexesThenFindsThenDeletes() throws Exception {
        assumeTrue(es.isAvailable(), "no Elasticsearch node reachable - skipping");
        es.deleteIndex(INDEX);
        assertTrue(es.createIndex(INDEX, Map.of("title", "text", "sectorId", "long")));
        assertTrue(es.indexAll(List.of(new SearchDocument(INDEX, "7", Map.of("title", "Experienced welder wanted", "sectorId", 5)), new SearchDocument(INDEX, "9", Map.of("title", "Mason for site work", "sectorId", 7)))));

        SearchResults found = searchUntilFound(SearchQuery.of(INDEX, "welding", List.of("title")));
        assertEquals(List.of("7"), found.ids());
        assertEquals(5L, found.hits().getFirst().number("sectorId"));

        assertEquals(List.of("9"), searchUntilFound(new SearchQuery(INDEX, null, List.of(), Map.of("sectorId", 7), 0, 10)).ids());
        assertTrue(es.delete(INDEX, "7"));
        assertTrue(es.deleteIndex(INDEX));
    }

    private SearchResults searchUntilFound(SearchQuery query) throws Exception {
        for (int attempt = 0; attempt < 30; attempt++) {
            SearchResults found = es.search(query);
            if (!found.isEmpty()) return found;
            Thread.sleep(100);
        }
        return es.search(query);
    }
}
```

- [ ] **Step 2: Run the full build**

Run: `./mvnw.cmd clean install`
Expected: BUILD SUCCESS across every module. With no node running the live test reports as **skipped**, not failed.

- [ ] **Step 3: Update the framework's CLAUDE.md**

In the *Module layout* section, change the `core-modules/` line to:

```
- `core-modules/` — DatabaseAPI, DatabaseImpl, AI, Excel, Plugin, Language, JSON, SearchAPI, SearchImpl
```

- [ ] **Step 4: Mark the spec implemented**

In `docs/superpowers/specs/2026-08-28-search-elasticsearch-design.md`, change the `Status:` line to `implemented 28 August 2026; no consumer wired up`.

- [ ] **Step 5: Final verification**

Run: `./mvnw.cmd clean install`
Expected: BUILD SUCCESS. Report the test counts. **Do not commit.**

---

## Outside this plan, for Loïc to do

- **Commit the work** — no task above commits anything.
- **SolarERP's `CLAUDE.md`** describes the framework layout as "complete as of 28 Aug 2026" and needs the two new modules added, and `requirements.md` § *Outstanding work* should gain the two deferred items (the MauCareers vacancy-search consumer, and the option of swapping to the official `elasticsearch-java` SDK). Both are edits to the SolarERP repo, which this session was told not to touch.
