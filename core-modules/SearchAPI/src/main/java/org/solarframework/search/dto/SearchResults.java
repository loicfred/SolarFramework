package org.solarframework.search.dto;

import java.util.List;

public record SearchResults(long total, List<SearchHit> hits) {
    public static SearchResults empty() { return new SearchResults(0, List.of()); }


    public boolean isEmpty() { return hits == null || hits.isEmpty(); }
    public List<String> ids() { return hits == null ? List.of() : hits.stream().map(SearchHit::id).toList(); }
}
