# Search subsystem — Elasticsearch (SearchAPI / SearchImpl)

Date: 28 August 2026
Status: implemented 28 August 2026; no consumer wired up

## Purpose

Give the framework a full-text search capability backed by Elasticsearch, following the existing
`*API` / `*Impl` split. The framework gains the capability; no consumer is wired up in this pass.

The design target — which is what shapes the interface, even though it is deliberately out of scope
here — is the MauCareers vacancy board. Its search today is `Vacancy.matches()`, a `String.contains`
over every row pulled across the data API: no stemming, no typo tolerance, no relevance ranking, and
the whole table crosses the HTTP boundary on each keystroke.

## Scope

In scope: two new Maven modules inside SolarFramework, their unit tests, root `pom.xml` wiring, and
the module list in `CLAUDE.md`.

Out of scope, deliberately: any change to SolarERP or MauCareers. No consumer calls the new service
yet. `SearchRegistry.SolarSearch` therefore stays on its no-op default in every running application
until a host assigns it.

## Modules

| Module | Package root | Depends on |
|---|---|---|
| `core-modules/SearchAPI` | `org.solarframework.search` | nothing |
| `core-modules/SearchImpl` | `org.solarframework.search.elastic` | SearchAPI, gson, slf4j |

`SearchAPI` has no dependencies at all — the DTOs carry `Map<String, Object>` values and need neither
Gson nor any Elasticsearch type. That keeps every consumer free of the client: nothing outside
`SearchImpl` ever imports an HTTP or JSON class because of search.

Root `pom.xml` gains two `<module>` entries and two `<dependencyManagement>` entries at version `1.0`,
matching every other module.

## SearchAPI

### `org.solarframework.search.ISearchService`

```java
boolean isAvailable();
boolean createIndex(String index, Map<String, String> fieldTypes); // column name -> "text" | "keyword" | "long" | "double" | "date" | "boolean"
boolean deleteIndex(String index);
boolean index(SearchDocument doc);
boolean indexAll(List<SearchDocument> docs);
boolean delete(String index, String id);
SearchResults search(SearchQuery query);
```

Every method returns a value rather than throwing. A search engine that is down is an ordinary
condition here, not an exceptional one — the caller's answer is "fall back", and that reads better as
a `false` or an empty result than as a caught exception at each call site.

### `org.solarframework.search.dto`

Records, each carrying the behaviour derived from its own data:

- `SearchDocument(String index, String id, Map<String, Object> fields)` — `validate()`
- `SearchQuery(String index, String text, List<String> fields, Map<String, Object> filters, int from, int size)` —
  `isMatchAll()`, `clampedSize()`, `validate()`, and a static `of(index, text, fields)` for the common case
- `SearchHit(String id, double score, Map<String, Object> source)` — `text(field)`, `number(field)`
- `SearchResults(long total, List<SearchHit> hits)` — `isEmpty()`, `ids()`, static `empty()`

Fuzziness is always `AUTO` and is not a component of `SearchQuery`. A caller that wants exact matching
uses a filter, not a flag.

`SearchResults.ids()` states the contract the whole design rests on: **Elasticsearch returns ranked
ids and nothing authoritative lives in the index.** A consumer searches to learn the order, then
hydrates the rows from its own store. A stale, empty or wiped index therefore degrades to a worse
ordering, never to wrong or missing data, and no migration is needed when the index is rebuilt.

### `org.solarframework.search.spring.SearchRegistry`

```java
public class SearchRegistry {
    public static ISearchService SolarSearch = new NoSearchService();
}
```

The same handover the database side uses (`DatabaseRegistry.SolarDBManager`): the host assigns the
field at boot. The difference is the default. `NoSearchService` is a null object — `isAvailable()`
returns false, writes return false, searches return `SearchResults.empty()` — so the field is never
null, no call site needs a null check, and a host that never configures Elasticsearch still runs.

`NoSearchService` lives in **SearchAPI**, beside the registry that defaults to it. It cannot live in
SearchImpl: SearchAPI must not depend on the implementation module.

## SearchImpl — `ElasticSearchService`

A thin REST client over `java.net.http.HttpClient` (built into Java 25) and Gson, which the root pom
already manages at 2.14.0. No new third-party dependency is introduced.

Endpoints used:

| Operation | Request |
|---|---|
| health probe | `GET /` |
| create index | `PUT /{index}` with a `mappings` body |
| delete index | `DELETE /{index}` |
| index one | `PUT /{index}/_doc/{id}` |
| index many | `POST /_bulk` (NDJSON) |
| delete one | `DELETE /{index}/_doc/{id}` |
| search | `POST /{index}/_search` |

`isAvailable()` caches its answer for **5 seconds** (probe timeout **1 second**), so an unreachable
node costs one round trip per window rather than one per request — which is what makes the fallback path cheap enough for a page
that searches on every keystroke.

### Request bodies are built, not formatted

Bodies are assembled as Gson `JsonObject` trees and serialised, **never** built by string
concatenation or `String.format`. Interpolating the search text into a JSON literal breaks as soon as
somebody types a `"` in the search box: malformed JSON at best, and at worst a caller able to inject
their own query clauses. This is a correctness requirement, not a style preference.

### Query shape

- text present → `multi_match` over `SearchQuery.fields()` with `fuzziness: AUTO`
- text blank → `match_all`
- `filters` → a `bool.filter` array of `term` clauses, combined with the query above

That covers the shape a search box beside a category dropdown needs, which is exactly the vacancy
board's (a text query narrowed by sector).

## Testing

The framework's Stop hook runs `./mvnw.cmd clean install` after every change, so **the suite must be
green on a machine with no Elasticsearch running.** Three groups:

1. **DTO logic** — `SearchQuery` validation and size clamping, `SearchHit` typed getters,
   `SearchResults.ids()` and `empty()`, and `NoSearchService` returning the degraded answers.
2. **Query-body building** — the body builder is a static returning a `JsonObject`, so it is tested
   with no node at all: a text query, a blank query resolving to `match_all`, filters lowering to
   `bool.filter`, and a query containing `"` and `\` to pin the escaping described above.
3. **One integration test** — index, search, then delete against a live node, guarded by
   `Assumptions.assumeTrue(service.isAvailable())`. It exercises the real path on a developer machine
   with Elasticsearch up and silently no-ops in any build without it.

Group 2 is what makes the design testable without infrastructure: the part most likely to be wrong is
the generated JSON, and it is verified as data rather than over a socket.

## Documentation

- SolarFramework `CLAUDE.md`: add both modules to the module layout list.
- SolarERP `CLAUDE.md` names the framework layout as "complete as of 28 Aug 2026" — that list needs
  the two modules too, or it becomes wrong the moment this lands.

## Noted for another time

Neither is part of this pass.

1. **The MauCareers vacancy-search consumer.** Index vacancies, replace `Vacancy.matches()`, and let
   `VacancyView.board()` take its order from `SearchResults.ids()`. One index serves three
   requirements: NFR-8 (the search bar), FR-29 (suggestions, via `more_like_this`) and FR-4 (notify an
   applicant when a new vacancy matches their saved prospect, via a **percolator** index — stored
   queries matched against an incoming document, which is the piece no SQL alternative gives cheaply).
   The privacy constraint from NFR-17 and NFR-27 applies if the labour pool is ever indexed too: index
   the redacted projection only, since an Elasticsearch index sits outside `AllowedEntity.Select()`'s
   sensitive- and binary-column stripping.
2. **Swapping the thin client for the official SDK.** `co.elastic.clients:elasticsearch-java` was
   considered and set aside: it pulls `elasticsearch-rest-client`, `jackson-databind` and
   `jakarta.json` (none currently in `.m2`), and its builder-lambda API is harder to defend line by
   line at viva. `ISearchService` is the seam that makes the swap a single-module change if the
   trade-off is ever revisited.

## Risks

- **A second store to keep in sync.** Nothing consumes the index yet, so this is dormant until the
  MauCareers work above; the `ids()` contract is what keeps it survivable when it arrives.
- **Elasticsearch is an external service**, and zero-touch deployment is directly marked. Mitigated by
  the `NoSearchService` default and `isAvailable()`: with no node configured or reachable, the
  platform behaves exactly as it does today.
