package org.solarframework.search.elastic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.solarframework.search.dto.SearchHit;
import org.solarframework.search.dto.SearchResults;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ElasticResponse {

    public static SearchResults read(JsonObject body) {
        JsonObject hits = body == null ? null : body.getAsJsonObject("hits");
        JsonArray array = hits == null ? null : hits.getAsJsonArray("hits");
        if (array == null) return SearchResults.empty();
        List<SearchHit> found = new ArrayList<>();
        for (JsonElement e : array) found.add(toHit(e.getAsJsonObject()));
        return new SearchResults(totalOrZero(hits), found);
    }


    private static long totalOrZero(JsonObject hits) {
        JsonObject total = hits.getAsJsonObject("total");
        return total == null || !total.has("value") ? 0 : total.get("value").getAsLong();
    }
    private static SearchHit toHit(JsonObject raw) {
        Map<String, Object> source = new LinkedHashMap<>();
        JsonObject stored = raw.getAsJsonObject("_source");
        if (stored != null) stored.entrySet().forEach(field -> source.put(field.getKey(), toJavaValue(field.getValue())));
        return new SearchHit(raw.get("_id").getAsString(), scoreOrZero(raw), source);
    }
    private static double scoreOrZero(JsonObject raw) { return !raw.has("_score") || raw.get("_score").isJsonNull() ? 0 : raw.get("_score").getAsDouble(); }
    private static Object toJavaValue(JsonElement e) {
        if (e.isJsonNull()) return null;
        if (!e.isJsonPrimitive()) return e.toString();
        JsonPrimitive p = e.getAsJsonPrimitive();
        return p.isNumber() ? p.getAsNumber() : p.isBoolean() ? p.getAsBoolean() : p.getAsString();
    }
}
