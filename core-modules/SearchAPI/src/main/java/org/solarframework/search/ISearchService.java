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
