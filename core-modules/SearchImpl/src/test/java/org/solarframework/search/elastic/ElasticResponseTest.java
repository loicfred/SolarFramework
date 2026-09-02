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
