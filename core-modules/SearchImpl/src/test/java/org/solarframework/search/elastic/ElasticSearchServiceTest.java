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
    void anIndexWithoutANameOrAMappingIsRefusedWithoutATripToTheNode() {
        assertFalse(offline.createIndex("vacancy", null));
        assertFalse(offline.createIndex("vacancy", Map.of()));
        assertFalse(offline.createIndex(" ", Map.of("title", "text")));
        assertFalse(offline.deleteIndex(null));
        assertFalse(offline.delete("vacancy", " "));
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
