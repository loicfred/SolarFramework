package org.solarframework.search.elastic;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.solarframework.search.dto.SearchDocument;
import org.solarframework.search.dto.SearchQuery;

import java.util.List;
import java.util.Map;

public class ElasticQuery {
    private static final Gson GSON = new Gson();

    public static JsonObject searchBody(SearchQuery q) {
        JsonObject body = new JsonObject();
        body.addProperty("from", q.clampedFrom());
        body.addProperty("size", q.clampedSize());
        body.add("query", queryClause(q));
        return body;
    }
    public static JsonObject mappingBody(Map<String, String> fieldTypes) {
        JsonObject properties = new JsonObject();
        fieldTypes.forEach((name, type) -> properties.add(name, wrapIn("type", new JsonPrimitive(type))));
        return wrapIn("mappings", wrapIn("properties", properties));
    }
    public static String bulkBody(List<SearchDocument> docs) {
        StringBuilder body = new StringBuilder();
        for (SearchDocument d : docs) {
            JsonObject target = new JsonObject();
            target.addProperty("_index", d.index());
            target.addProperty("_id", d.id());
            body.append(GSON.toJson(wrapIn("index", target))).append('\n').append(GSON.toJson(d.fields())).append('\n');
        }
        return body.toString();
    }


    private static JsonObject queryClause(SearchQuery q) {
        JsonObject scoring = q.isMatchAll() ? matchAllClause() : fuzzyMultiMatchClause(q.text(), q.fields());
        if (q.filters() == null || q.filters().isEmpty()) return scoring;
        JsonObject bool = new JsonObject();
        bool.add("must", scoring);
        bool.add("filter", exactMatchClauses(q.filters()));
        return wrapIn("bool", bool);
    }
    private static JsonObject matchAllClause() { return wrapIn("match_all", new JsonObject()); }
    private static JsonObject fuzzyMultiMatchClause(String text, List<String> fields) {
        JsonObject match = new JsonObject();
        match.addProperty("query", text);
        JsonArray names = new JsonArray();
        if (fields != null) fields.forEach(names::add);
        match.add("fields", names);
        match.addProperty("fuzziness", "AUTO");
        return wrapIn("multi_match", match);
    }
    private static JsonArray exactMatchClauses(Map<String, Object> filters) {
        JsonArray clauses = new JsonArray();
        filters.forEach((field, value) -> {
            JsonObject term = new JsonObject();
            term.add(field, asPrimitive(value));
            clauses.add(wrapIn("term", term));
        });
        return clauses;
    }


    private static JsonPrimitive asPrimitive(Object value) {
        if (value instanceof Number n) return new JsonPrimitive(n);
        if (value instanceof Boolean b) return new JsonPrimitive(b);
        return new JsonPrimitive(String.valueOf(value));
    }
    private static JsonObject wrapIn(String name, JsonElement child) {
        JsonObject holder = new JsonObject();
        holder.add(name, child);
        return holder;
    }
}
