# AI — how to use it

Three objects, one rule: **the service is the endpoint, the chatbot does the talking, the conversation
is the transcript.** Every call to a model is a conversation, even a one-liner.

| | what it is | what it holds |
|---|---|---|
| `IAIService` | one endpoint, one model | url, key, model, timeout, rate limit, model details |
| `Chatbot` | an agent using one | persona, tools, options, memory, step bound, approval — set once through its `Builder`, getters only afterwards |
| `Conversation` | one chat with a bot | the messages, the loop, the token usage |

## 1. Setup

Add `AIAPI` + `AIImpl`, then point it at your endpoint:

```properties
spring.ai.openai.base-url=http://localhost:1234
spring.ai.openai.api-key=N/A
spring.ai.openai.chat.options.model=openai/gpt-oss-20b
solar.ai.timeout-seconds=300
```

`AIRegistry.DefaultAIService` and `AIRegistry.SolarAIManager` are assigned at startup.

## 2. One-shots

`service.createChatbot()` opens a **builder** for a new bot on that endpoint; `build()` closes it. With
nothing configured you get the name `default`, no persona, no tools, no memory, 6 steps. Keep the bot in a
variable and reuse it; it is not shared or registered anywhere.

```java
import static org.solarframework.ai.spring.AIRegistry.DefaultAIService;

Chatbot bot = DefaultAIService.createChatbot().build();

bot.prompt("Hi, how are you?");
bot.stream("Name three planets.", System.out::print);
bot.chooseBetween("Which of these are red?", List.of("Apple", "Cherry", "Banana"));   // [Apple, Cherry]
bot.promptAs("Give the capital and population of France.", Country.class);            // any record/class
bot.complete("The three primary colours are red, blue and");
bot.completeCode(src, ProgrammingLanguage.JAVA);
bot.askForInformation("What is the capital of Japan?");
```

Each is a throwaway one-turn conversation — same loop, same logs, nothing special-cased.

## 3. Chatbots and conversations

```java
Chatbot bot = DefaultAIService.createChatbot().name("timekeeper")
        .systemPrompt("You are an assistant that answers time-related questions.")
        .tools(new Toolbox()).maxSteps(5).build();   // settings are the builder's; the bot only has getters

Conversation c = bot.startConversation("conversation-1", "Loïc");
c.send("What is the current time?");   // one turn that answers, tools included
c.send("Thank you!");                  // same transcript
c.save();                              // only now does it touch the database
```

- `send()` → the answer. `run()` → an `AgentRun` with every step. Same loop.
- `sendStreaming(msg, onChunk)` and `sendAs(msg, Type.class)` are the streaming and structured versions.
- `Conversation.resume(bot, id, owner)` picks a stored one back up, `Conversation.idsOf(owner)` lists them,
  `keepFor(Duration)` makes it expire, `discard()` removes it and its messages.
- Persistence is opt-in: without `save()` nothing needs a data source.

## 4. Tools

```java
public class Toolbox {
    @AITool(description = "Returns the current date and time.")
    public String getCurrentTime() { return LocalDateTime.now().toString(); }
}
```

`@AITool` is ours, so a toolbox needs no Spring AI on its classpath. Tools are never executed by the
backend: each call is offered to `approveToolsWith(...)` first, results go into the transcript, and the
loop goes round until the model answers or `maxSteps` is reached.

```java
Chatbot careful = service.createChatbot().tools(new Toolbox())
        .approveToolsWith(call -> !call.name().equals("deleteEverything"))
        .onStep(step -> log.info("{}", step)).build();
```

## 5. Memory

Off by default — the whole transcript is sent every turn.

```java
.memory(new Memory(6000))                    // keep the newest messages that fit
.memory(new Memory(6000, DefaultAIService))  // and condense what it drops into a rolling summary
```

## 6. Several services and bots

```java
IAIService fast = SolarAIManager.makeNewService("fast", "qwen2.5-7b");   // inherits url/key/timeout
SolarAIManager.addChatbot(bot);
SolarAIManager.SaveAsFile(path);   // services + chatbots as JSON, like DatabaseManager
```

## 7. Reading the log

```
AI service Default ready: model openai/gpt-oss-20b at http://localhost:1234 (timeout 300s)
[Default][demo][timekeeper] conversation started - model openai/gpt-oss-20b, chatbot timekeeper, 1 tool(s), max 5 step(s), memory off
[Default][demo][timekeeper] User: What is the current time?
[Default][demo][timekeeper] Assistant wants getCurrentTime({}) (6359 ms, 133 prompt + 26 completion = 159 tokens, 2 messages sent)
[Default][demo][timekeeper] step 1/5: getCurrentTime -> "2026-08-29T19:18:26"
[Default][demo][timekeeper] Assistant: The current date and time is 2026-08-29 19:18. (7015 ms, 179 prompt + 41 completion = 220 tokens, 4 messages sent)
```

`[service][conversation][instruction]`. The persona is named, never printed. `N messages sent` is the
whole transcript resent that turn — that is why prompt tokens climb. `step n/max` appears only for a
tool round-trip; `maxSteps` bounds turns **per message**, not the length of the conversation.
