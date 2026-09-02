package org.solarframework.search.dto;

import java.util.Map;

public record SearchHit(String id, double score, Map<String, Object> source) {
    public String text(String field) { Object stored = storedValue(field); return stored == null ? null : String.valueOf(stored); }
    public Long number(String field) { return storedValue(field) instanceof Number n ? n.longValue() : null; }
    private Object storedValue(String field) { return source == null ? null : source.get(field); }
}
