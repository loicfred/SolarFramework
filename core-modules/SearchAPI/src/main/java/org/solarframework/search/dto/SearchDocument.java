package org.solarframework.search.dto;

import java.util.Map;

public record SearchDocument(String index, String id, Map<String, Object> fields) {
    public String validate() {
        if (index == null || index.isBlank()) return "A document needs the name of the index it belongs to.";
        if (id == null || id.isBlank()) return "A document needs an id.";
        if (fields == null || fields.isEmpty()) return "A document needs at least one field to search on.";
        return null;
    }
}
