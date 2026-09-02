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
