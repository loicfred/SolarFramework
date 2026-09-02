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
