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
        assertEquals("title", match.getAsJsonArray("fields").get(0).getAsString());
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
        assertEquals(5, bool.getAsJsonArray("filter").get(0).getAsJsonObject().getAsJsonObject("term").get("sectorId").getAsInt());
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
