# AI subsystem — chatbots, conversations and agents (AIAPI / AIImpl)

Date: 28 August 2026
Status: **superseded — historical record of the design, not of the code.**

> The subsystem was built and then restructured further than this document describes: the interface
> count fell from 8 to 2 (`IAIService`, `IAIManager`), `IConversation` was folded into `Conversation`
> as ordinary methods, `Conversation` and `ChatMessage` became `@Entity` classes in **AIAPI**, and
> structured output moved to the backend's native `response_format` rather than being asked for in
> the prompt. **`CLAUDE.md` is the source of truth for the AI module.** Read this only for the
> reasoning behind the original split.

## Purpose

Turn the single `core-modules/AI` module into a general-purpose library for AI chatbots, assistants
and agents, following the existing `*API` / `*Impl` split.

The current module is a thin wrapper over Spring AI: one `AIService` with four hard-coded option
presets, and a `Conversation` that holds a message list. It works, but every type a consumer touches
is a Spring AI type, the conversation cannot be persisted, resumed, streamed or bounded to a context
window, and there is no way to drive a multi-step tool loop. Nothing outside the module imports
`org.solarframework.ai`, so there is no migration cost to restructuring it now.

The target is one set of concepts that covers all three situations the library is meant to serve:

- **Chatbot** — a persona you talk to; `conversation.send(msg)`.
- **Assistant** — the same, with tools and a task focus.
- **Agent** — a bounded multi-step tool loop; `conversation.run(goal)`.

An agent is not a separate hierarchy. It is a chatbot you call `run()` on.

## Scope

In scope: two new Maven modules, removal of `core-modules/AI`, root `pom.xml` wiring, the module
list in `CLAUDE.md`, unit tests, and live tests against a local LM Studio.

Out of scope, deliberately:

- **RAG / embeddings / vector store.** The other persistence axis; its own design cycle.
- **Providers other than the OpenAI-compatible one.** The API is provider-neutral so an
  `AIOllamaImpl` or `AIAnthropicImpl` can be added later, but only the Spring AI OpenAI path is
  built here.
- **Any consumer.** `AIRegistry.SolarAI` stays on its no-op default in every running application
  until a host assigns it, exactly like `SearchRegistry.SolarSearch`.

## Modules

| Module | Package root | Depends on |
|---|---|---|
| `core-modules/AIAPI` | `org.solarframework.ai` | nothing |
| `core-modules/AIImpl` | `org.solarframework.ai.spring` | AIAPI, core, JSON, spring-ai-starter-model-openai, spring-boot-starter-cache, caffeine, DatabaseAPI *(optional)* |

`AIAPI` has **no dependencies at all** — not Spring AI, not Spring, not Gson. A consumer that
programs against `IChatbot` / `IConversation` never sees a Spring AI type, which is what insulates
callers from `spring-ai` 2.0.0-M2 being a milestone with breaking changes ahead of it.

`DatabaseAPI` is `<optional>true</optional>` in AIImpl. Persistence is a wiring choice: a host that
never sets a store gets the in-memory default and pulls in no database code.

Root `pom.xml`: the `AI` `<module>` and `<dependencyManagement>` entries are replaced by `AIAPI` and
`AIImpl` at version `1.0`. `core-modules/AI` is deleted; `AIService`, `Conversation`, `AIConst`,
`AIConfig` and `ProgrammingLanguage` are superseded by the types below, and `AI_Main` moves to
AIImpl's test sources.

## AIAPI

### `IAIService` — one endpoint, stateless single-shot calls

```java
String getName();                 // "default" for the bean from application.properties
String getBaseUrl();  void setBaseUrl(String);
String getApiKey();   void setApiKey(String);
String getModel();    void setModel(String);
int getTimeoutSeconds();  void setTimeoutSeconds(int);

boolean isAvailable();

String  prompt(String prompt, Object... tools);
String  prompt(AIOptions options, String prompt, Object... tools);
<T> T   promptAs(String prompt, Class<T> type);              // structured output
<T> T   promptAs(AIOptions options, String prompt, Class<T> type);
<T> List<T> chooseBetween(String description, List<T> items);
String  complete(String text);
String  completeCode(String code, ProgrammingLanguage language);
void    stream(String prompt, Consumer<String> onChunk);

IChatbot.Builder chatbot();      // fluent factory
IConversation    conversation(); // default persona, fresh conversation
```

`promptAs` replaces the `" | "` string-splitting that `chooseBetween` uses today: the model is asked
for JSON matching `type` and the result is deserialised. `chooseBetween` keeps its signature — it is
useful — but is reimplemented on top of `promptAs` with an index-list record, so a chatty model can
no longer break it by prefixing prose.

### `IChatbot` — the template

A chatbot is a *configuration*: persona, tools, options, memory policy, store, agent limits. It is
immutable and a factory for conversations.

```java
String id();
IConversation start();
IConversation start(String conversationId, String owner);
IConversation resume(String conversationId);   // via the store; empty -> start()
```

Built fluently:

```java
IChatbot bot = SolarAI.chatbot()
    .id("support")
    .systemPrompt("You are a support assistant for SolarERP.")
    .options(AIOptions.CONVERSATIONIST.withModel("openai/gpt-oss-20b"))
    .tools(new Toolbox())
    .memory(new WindowMemory(6000))
    .store(new DatabaseConversationStore())   // optional
    .maxSteps(8)                              // agent loop bound
    .onStep(step -> log.info("step {}", step.index()))
    .toolApprover(call -> !call.name().equals("deleteEverything"))
    .build();
```

`maxSteps`, `onStep` and `toolApprover` are the agent controls. They are inert for `send()` and only
govern `run()`.

### `IConversation` — the live instance

```java
String id();  String owner();  IChatbot bot();
List<ChatMessage> history();

String    send(String message, Object... extraTools);
void      sendStreaming(String message, Consumer<String> onChunk);
<T> T     sendAs(String message, Class<T> type);
AgentRun  run(String goal);          // bounded multi-step tool loop

void add(ChatMessage m);
void clear();
TokenUsage usage();
void save();                          // no-op unless a store is configured
```

`send` is one turn. `run` drives the tool loop up to `maxSteps`, reporting each `AgentStep` to
`onStep` and gating each tool call through `toolApprover`, and returns an `AgentRun` carrying the
steps, the tool calls made, whether it terminated by answering or by hitting the bound, and usage.

### `IMemoryStrategy` — context-window control

```java
List<ChatMessage> prepare(List<ChatMessage> history, int contextTokens);
```

Called before every request. Three pure-Java implementations ship in AIAPI:

- `NoMemory` — pass-through. The default.
- `WindowMemory(maxTokens)` — drops oldest non-system messages until the estimate fits.
- `SummarizingMemory(maxTokens, service)` — same, but condenses what it drops into a rolling summary
  message. Depends only on `IAIService`, so it stays provider-neutral.

Token counting is an estimate (`chars/4`), not a tokenizer. That is deliberate: an exact count needs
a model-specific tokenizer, and the budget only has to be conservative. `ModelInfo.loadedContextLength()`
supplies the real ceiling — relevant here, since `gpt-oss-20b` loads at 8192 of its 131072 maximum.

### `IConversationStore` — optional persistence

```java
void save(ConversationState state);
Optional<ConversationState> load(String id);
List<String> listFor(String owner);
void delete(String id);
```

`ConversationState` is a plain DTO: id, owner, botId, systemPrompt, messages, summary, model,
createdAt, updatedAt. `InMemoryConversationStore` (a `ConcurrentHashMap`) is the default in AIAPI.

This is the transcript, summary and chatbot-definition layers of persistence. Tool-call audit and
token usage ride along on the same state object. Per-user profile memory — durable facts injected
into new conversations — is `IProfileMemory` with a no-op default, because the rules for what is
worth remembering about a user are application-specific.

### `IModelManager` — local model lifecycle

```java
boolean isAvailable();
List<ModelInfo> list();
Optional<ModelInfo> get(String id);
boolean isLoaded(String id);
boolean supports(String id, String capability);   // e.g. "tool_use"
boolean load(String id, Integer contextLength, Integer ttlSeconds);
boolean unload(String id);
boolean ensureLoaded(String id);
```

`ModelInfo` is a record: id, type, publisher, arch, quantization, state, maxContextLength,
loadedContextLength, capabilities.

### DTOs and annotations

`ChatMessage(Role role, String text, Instant timestamp, List<ToolCall> toolCalls, String name)`,
`Role` (`SYSTEM`, `USER`, `ASSISTANT`, `TOOL`), `ToolCall(id, name, argumentsJson, result)`,
`AgentRun`, `AgentStep`, `TokenUsage(prompt, completion, total)`, `ModelInfo`, `AIException`.

`AIOptions` is an immutable builder-record carrying model, maxTokens, temperature, topP, frequency
and presence penalties, stop sequences and tool choice, with `with*` copy methods. The four presets
from the old `AIConst` survive as constants: `CONVERSATIONIST`, `INFORMATIONIST`, `ITEM_CHOOSER`,
`AUTO_COMPLETIST`. The system-prompt texts move to `Prompts` — they are plain strings and need no
Spring AI.

Tools are declared with AIAPI's own `@AITool(name, description)` annotation so consumers stay off
the Spring AI classpath. As a convenience, an object with no `@AITool` methods is passed through to
Spring AI untouched, so existing `@Tool`-annotated objects keep working.

### `IAIManager` — many services, the way `DatabaseManager` holds many sources

One `IAIService` is one endpoint and one default model, so several agents on several models is the
ordinary case. `IAIManager` owns them all and mirrors `IDatabaseManager` method for method:
`getDefaultService`, `getService(name)`, `makeNewService(...)`, `addService`, `removeService`,
`removeNonDefaultServices`, `getServices`, plus a named-chatbot registry (`getChatbot(id)`,
`addChatbot`, `getChatbots`) so an agent can be defined once at startup and looked up anywhere.

The same rule as data sources governs configuration, and it is the one that causes bugs when
forgotten: **the default service is the Spring bean seeded from `application.properties`; every
other service is not a bean, so its `@Value` fields are null and it is configured only through
setters.** `makeNewService(name)` therefore inherits the default's base URL, key, model and timeout,
and `makeNewService(name, model)` changes only the model. Three agents sharing the configured model
cost three lines:

```java
SolarAIManager.makeNewService("triage");
SolarAIManager.makeNewService("summarizer");
SolarAIManager.makeNewService("coder", "qwen/qwen3-coder-30b");
```

Adding a setting means touching four places: the `@Value` field + getter + setter on
`SpringAIService`, the getter + setter on `IAIService`, `ServiceDefinition`, and
`AIManager.LoadFromFile`.

### Config file — `LoadFromFile` / `SaveAsFile`

Services and agents round-trip through one JSON file, as data sources do through `DataSourceFile`.
`AIConfigFile extends JSONItem` holds a `List<ServiceDefinition>` and a `List<ChatbotDefinition>`;
a missing or unreadable file is rewritten from the manager's current state.

`ChatbotDefinition` carries only what is data — id, service name, system prompt, both `AIOptions`,
`maxSteps`. Tools, memory strategy, store and the step and approval callbacks are live objects, not
settings, so a loaded agent arrives with its persona, model and bounds and is handed its tools in
code. Two deliberate asymmetries with `DataSourceFile`: definitions are DTOs rather than serialised
services, because a `ChatModel` and an HTTP client are not data; and **the default service keeps the
endpoint `application.properties` gave it**, taking only its model and timeout from the file, so a
stale config file can never redirect the application at the wrong server.

### `spring/AIRegistry`

```java
public class AIRegistry {
    public static IAIService         DefaultAIService   = new NoAIService();
    public static IAIManager         SolarAIManager     = new NoAIManager();
    public static IConversationStore SolarConversations = new InMemoryConversationStore();
    public static IProfileMemory     SolarProfiles      = new NoProfileMemory();
}
```

`DefaultAIService` and `SolarAIManager` mirror `DatabaseRegistry`'s `DefaultDBService` and
`SolarDBManager` exactly, and are assigned on `ApplicationReadyEvent`. `NoAIService` throws
`AIException` on any call that would need a model and reports `isAvailable() == false`, so an
unwired application fails loudly at the call site rather than silently returning null.

## AIImpl

| Type | Role |
|---|---|
| `SpringAIService` | `IAIService` over Spring AI — one endpoint, one default model, its own HTTP client |
| `AIManager` | `IAIManager`: every service, the named agents, and the JSON config file |
| `SpringChatbot` / `SpringConversation` | the template and instance |
| `MessageMapper` | `ChatMessage` ↔ `o.s.ai.chat.messages.Message` |
| `OptionsMapper` | `AIOptions` → `OpenAiChatOptions` |
| `ToolAdapter` | `@AITool` methods → Spring AI `ToolCallback` |

The three adapters exist only because the two type systems must not meet — AIAPI cannot name a
Spring AI type, so the conversion has to live somewhere on the Impl side. They are the exception,
not the pattern.
| `AIConfig` | beans; assigns `AIRegistry` on `ApplicationReadyEvent` |
| `store/DatabaseConversationStore` | `IConversationStore` over `DatabaseAPI` |
| `store/StoredConversation` | `extends DatabaseObject<StoredConversation>`; messages as a JSON column |
| `lmstudio/LMStudioModelManager` | `IModelManager` for LM Studio |

Registration follows the framework idiom: `AIConfig` sets the `AIRegistry` fields on
`ApplicationReadyEvent`, the way `AIService` already sets its own static reference.

### The HTTP timeout, which is not optional

Spring's stock client gives up long before a local model answers. `ReactorClientHttpRequestFactory`
carries an exchange timeout of a few seconds; `gpt-oss-20b` takes **~4.2 s for a trivial prompt**,
and far longer for a tool loop or a cold multi-gigabyte load. Left alone, every request fails with
`I/O error on POST … : null` after retrying for minutes — measured, not predicted.

`SpringAIService` therefore builds its own `OpenAiApi` over a `JdkClientHttpRequestFactory` with a
read timeout of `solar.ai.timeout-seconds` (default 300) rather than using the autoconfigured
`ChatModel` bean. Every service owns its endpoint, default or not — the same shape as
`DatabaseService` owning its pool rather than borrowing Spring's `DataSource`.

### LM Studio specifics

Verified against LM Studio with `openai/gpt-oss-20b` loaded, 28 August 2026:

- `GET /api/v0/models` returns `state`, `capabilities`, `max_context_length`,
  `loaded_context_length`, `quantization` per model — everything `ModelInfo` needs.
- **There is no REST load/unload endpoint.** `POST /api/v0/models/load` answers
  `{"error":"Unexpected endpoint or method."}`.
- **LM Studio returns HTTP 200 on unknown endpoints**, with the error only in the body.
  `LMStudioModelManager` must therefore parse the body and never trust the status code. This is the
  single most important implementation note in this document.

So `load` / `unload` shell out to the `lms` CLI (`lms load <id> --context-length N --ttl S`,
`lms unload <id>`), resolved from `PATH` or `~/.lmstudio/bin/lms`. When the CLI is absent both
return `false` and log once; `ensureLoaded` still works, because LM Studio JIT-loads any model named
in a request. `LMStudioModelManager` never downloads and never deletes a model — there is no API
surface for either.

## Code style

Object-oriented: behaviour belongs to the object that owns the data, never to a static helper that
reaches into it. `msg.isSystem()`, `msg.estimateTokens()`, `options.withModel(id)`,
`model.supports("tool_use")`, `model.isLoaded()`, `state.lastMessage()`, `run.completed()` — not
`isSystem(msg)` or `supports(model, cap)`. The `IModelManager.supports(id, cap)` convenience is a
lookup that then delegates to `ModelInfo.supports`, which is where the logic actually lives.

Beyond that, the rules in `CLAUDE.md` hold: single-line code stays on one line, no comments
restating the obvious, short names in short scopes, no field that can be derived.

## Error handling

Every failure path surfaces as `AIException` (unchecked, from AIAPI) wrapping the cause. Three cases
get explicit treatment rather than a generic wrap:

- **Server unreachable** — `isAvailable()` returns false rather than throwing, so callers can degrade.
  It probes `GET /v1/models` with a short timeout and caches the result for 5 s, matching
  `ElasticSearchService.isAvailable()`.
- **Model lacks a capability** — `run()` on a model whose `capabilities` omit `tool_use` throws with
  the model id and the missing capability named, instead of failing obscurely mid-loop.
- **Agent bound reached** — not an error. `AgentRun.completed()` is false and the partial steps are
  returned.

## Testing

Unit tests, no server required (43): `MessageMapper` round-trip including tool calls, `OptionsMapper`,
`ToolAdapter` reflection over `@AITool`, all three memory strategies at their boundaries,
`InMemoryConversationStore`, `AIOptions` copy semantics, the config-file round-trip against a temp
file, and `LMStudioModelManager` parsing against a stub server — including the 200-with-error-body
case, which is the regression that matters.

Live tests against LM Studio, self-skipping when `isAvailable()` is false, following
`ElasticSearchServiceLiveTest`: single prompt, structured output, `chooseBetween`, streaming, memory
across turns, a tool-calling turn, a multi-step `run()` with step reporting, tool denial, persistence
and resume, the manager handing out named services, and `ModelInfo` reporting `tool_use`.

`AI_Main` is kept as a manual smoke harness in AIImpl's test sources, rewritten against the new API.

Two bugs the tests caught during implementation, both now fixed and covered: `WindowMemory` scanned
the history backwards and `break`ed on the first message that would not fit, which dropped the system
prompt sitting in front of it — the one thing it promises to keep; and `ToolAdapter` let Spring AI's
`ToolCallbacks.from` throw on an object declaring no tools, where contributing nothing is correct.

## Consequences

- Consumers depend on `AIAPI` and get no transitive Spring AI. Only the composition root needs `AIImpl`.
- A second provider is a new `*Impl` module with no change to AIAPI.
- Persistence, profile memory, streaming, agent limits and model management are each opt-in with an
  inert default, so the smallest useful program stays `SolarAI.prompt("...")`.

Not committed — this repository's rules reserve all git write operations to Loïc.
