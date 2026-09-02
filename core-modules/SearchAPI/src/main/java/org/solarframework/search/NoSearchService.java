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
