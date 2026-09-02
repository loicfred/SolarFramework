package org.solarframework.search.elastic;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.solarframework.search.dto.SearchDocument;
import org.solarframework.search.dto.SearchQuery;
import org.solarframework.search.dto.SearchResults;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("live")
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
