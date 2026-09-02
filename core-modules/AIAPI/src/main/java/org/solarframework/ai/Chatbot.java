package org.solarframework.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.solarframework.ai.dto.AIOptions;
import org.solarframework.ai.dto.AgentRun;
import org.solarframework.ai.dto.ChatbotDefinition;
import org.solarframework.ai.dto.ToolCall;
import org.solarframework.ai.enums.ProgrammingLanguage;
import org.solarframework.ai.memory.Memory;
import org.solarframework.ai.obj.Conversation;

import java.util.regex.Pattern;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * An agent: a service to talk to, plus the characteristics that make it this bot rather than another
 * one — persona, tools, generation settings, memory policy, and the bounds a run works under.
 * <p>Shared and reusable: one chatbot serves every user, and each {@link #startConversation()} hands
 * back its own {@link Conversation}. Chatbot, assistant and agent are the same thing here; what
 * differs is whether you call {@link Conversation#send} or {@link Conversation#run}.
 * <p>Built once and then fixed: a bot is shared by every conversation running on it, so settings are
 * given to a {@link Builder} and never changed underneath a conversation already using them.
 * <pre>
 * Chatbot support = SolarAIManager.getService("triage").createChatbot()
 *         .name("support").systemPrompt("You are a support assistant.")
 *         .tools(new Toolbox()).memory(new Memory(6000)).maxSteps(8).build();
 * </pre>
 */
public class Chatbot {

    private static final Logger log = LoggerFactory.getLogger(Chatbot.class);

    private final IAIService service;
    private String name = "default";
    private String declaredService;                       // what a stored definition asked for; null when built in code
    private final List<String> attributes = new ArrayList<>();
    private String systemPrompt;
    private String instruction;                           // what to call the persona in the logs
    private AIOptions chatOptions = AIOptions.CONVERSATIONIST;
    private AIOptions agentOptions = AIOptions.AGENT;
    private final List<Object> tools = new ArrayList<>();
    private Memory memory;                                // null sends the whole history
    private Function<String, List<String>> profileFacts;
    private int maxSteps = 6;
    private Consumer<AgentRun> onStep;
    private Predicate<ToolCall> toolApprover;

    private Chatbot(IAIService service) { this.service = service; }

    /** The only way to make one. {@code service.createChatbot()} is the same call, from the service. */
    public static Builder builder(IAIService service) { return new Builder(service); }

    /**
     * Everything a bot is configured with. It fills the bot it will return rather than holding a
     * second copy of every field, so adding a setting is one method here and one field there.
     */
    public static class Builder {

        private final Chatbot bot;

        private Builder(IAIService service) { bot = new Chatbot(service); }

        public Builder name(String name) { bot.name = name; return this; }
        public Builder systemPrompt(String systemPrompt) { bot.systemPrompt = systemPrompt; return this; }
        /** Names the persona for the logs, so its text never has to be printed. Defaults to the bot's name. */
        public Builder instruction(String instruction) { bot.instruction = instruction; return this; }
        public Builder chatOptions(AIOptions chatOptions) { bot.chatOptions = chatOptions; return this; }
        public Builder agentOptions(AIOptions agentOptions) { bot.agentOptions = agentOptions; return this; }
        /** Applies to both the chat and agent settings, so one call switches the whole bot's model. */
        public Builder model(String model) { bot.chatOptions = bot.chatOptions.withModel(model); bot.agentOptions = bot.agentOptions.withModel(model); return this; }
        public Builder tools(Object... toolObjects) { for (Object t : toolObjects) if (t != null && bot.tools.stream().noneMatch(o -> o.getClass().equals(t.getClass()))) bot.tools.add(t); return this; }
        /** Whatever the application needs remembered and this record has no field for — see {@link ChatbotDefinition}. */
        public Builder attributes(List<String> attributes) { bot.attributes.clear(); if (attributes != null) bot.attributes.addAll(attributes); return this; }
        public Builder memory(Memory memory) { bot.memory = memory; return this; }
        /**
         * Durable facts about a user, appended to the persona of every conversation they own — what
         * makes a bot feel like it knows someone across separate conversations. Deciding what is worth
         * remembering is application policy, so this is a plain lookup rather than a framework type.
         */
        public Builder profileFacts(Function<String, List<String>> profileFacts) { bot.profileFacts = profileFacts; return this; }
        /** How many model turns one message may take before the bot gives up. */
        public Builder maxSteps(int maxSteps) { bot.maxSteps = maxSteps; return this; }
        public Builder onStep(Consumer<AgentRun> onStep) { bot.onStep = onStep; return this; }
        /** Return false to block a tool call; the model is told it was denied and carries on. */
        public Builder approveToolsWith(Predicate<ToolCall> toolApprover) { bot.toolApprover = toolApprover; return this; }

        /** Applies a stored definition. Tools, memory and callbacks are untouched — set those in code. */
        public Builder applyDefinition(ChatbotDefinition d) {
            bot.declaredService = d.serviceName();
            return name(d.name()).systemPrompt(d.systemPrompt()).chatOptions(d.options()).agentOptions(d.agentOptions()).maxSteps(d.maxSteps()).attributes(d.attributes());
        }

        public Chatbot build() { return bot; }
    }

    /** Null when this bot names a service the application has not configured: it can still be listed, edited and written back, it simply cannot be talked to. */
    public IAIService getService() { return service; }
    /**
     * Which service this bot asked for, which is not always the one it got: a stored definition may ask for whichever
     * service is default, and is written back asking for that rather than pinned to whichever held the title the day
     * it was read. A bot built in code has asked for the service it was built on.
     */
    public String getServiceName() { return declaredService != null ? declaredService : service == null ? null : service.getName(); }
    /** Follows a service through its own rename - the only setting that moves after a bot is built, because the name it asked for is the service's to change and not the bot's. A bot built in code holds the service itself and follows on its own. */
    public void renameService(String newName) { if (declaredService != null) declaredService = newName; }
    public String getName() { return name; }
    public List<String> getAttributes() { return List.copyOf(attributes); }
    public String getSystemPrompt() { return systemPrompt; }
    public String getInstruction() { return instruction != null ? instruction : name; }
    /** What this bot actually runs on: its own model when it overrides one, the service's otherwise. */
    public String getModelInUse() { return chatOptions.model() != null ? chatOptions.model() : service == null ? null : service.getModel(); }
    public AIOptions getChatOptions() { return chatOptions; }
    public AIOptions getAgentOptions() { return agentOptions; }
    public List<Object> getTools() { return List.copyOf(tools); }
    public Memory getMemory() { return memory; }
    public int getMaxSteps() { return maxSteps; }

    /**
     * A conversation that keeps its transcript, unlike the one-shot helpers below: send it as many
     * messages as you like and each one sees the ones before it.
     * <p>This one has a generated id and no owner. The id is short because it heads every log
     * line of the run: six base-36 characters out of a 2^31 space, rather than a 36-character UUID. A
     * conversation meant to be saved and found again should be given a real id instead.
     */
    public Conversation startConversation() {
        return startConversation("C" + Long.toString(ThreadLocalRandom.current().nextLong(1L << 31), 36).toUpperCase(), null);
    }

    /**
     * A conversation under an id you choose, so it can be found again after {@link Conversation#save()}
     * with {@link Conversation#resume}. Nothing is written until then.
     *
     * @param owner whose conversation it is: it is what {@code profileFacts} is asked about, what the
     *              persona is addressed to, and what {@link Conversation#idsOf} lists by. May be null.
     */
    public Conversation startConversation(String conversationId, String owner) {
        log.info("[{}][{}][{}] conversation started - model {}, chatbot {}, {} tool(s), max {} step(s), memory {}", getServiceName(), conversationId, getInstruction(), getModelInUse(), name, tools.size(), maxSteps, memory == null ? "off" : memory.getClass().getSimpleName());
        return new Conversation(this, conversationId, owner);
    }

    // --- one-shot tasks ---

    /**
     * Opens a throwaway conversation for a single task, and hands it back unused.
     * <p>It runs on this bot's service, tools, step bound, approver and step callback, under the
     * persona and settings the task needs, and with <b>no memory</b> — there is nothing yet to
     * remember. Every helper below is one of these, so a one-shot call is an ordinary conversation
     * rather than a second way of reaching the model: the bounded tool loop, tool approval, the
     * transcript and {@link Conversation#save()} all work exactly as they do in a long chat.
     * <p>Call this directly when you want to keep the conversation the answer came from — the
     * helpers throw it away.
     *
     * @param instruction what to call the persona in the logs, e.g. {@code QuickChooser}; it becomes
     *                    the third tag of every line, {@code [service][conversation][instruction]}
     * @param persona     the system prompt; null falls back to the conversationist persona
     * @param options     generation settings for the turn, used for both chat and agent calls
     */
    public Conversation oneShot(String instruction, String persona, AIOptions options) {
        return builder(service).name(name).instruction(instruction).systemPrompt(persona)
                .chatOptions(options).agentOptions(options).maxSteps(maxSteps).tools(tools.toArray())
                .attributes(attributes).profileFacts(profileFacts).onStep(onStep).approveToolsWith(toolApprover)
                .build().startConversation();
    }

    /** A task preset still runs on this bot's model, the way {@link Conversation#sendAs} does. */
    private AIOptions preset(AIOptions options) { return options.withModel(chatOptions.model()); }

    /**
     * Asks one question in a throwaway conversation and returns the answer.
     * <p>Speaks as this bot: its persona, its chat settings, its tools. The conversation is discarded
     * afterwards, so nothing is remembered between two calls — use {@link #startConversation()} when
     * the next message should know about this one. The model may still call tools to answer, within
     * this bot's step bound and approver.
     * <p>Logged as {@code QuickChatter} when the bot has no persona of its own, under the bot's own
     * name when it has.
     *
     * @param extraTools tool objects for this call only, on top of the bot's own
     * @return the model's reply, or the "stopped after N step(s)" notice if it never answered
     */
    public String prompt(String message, Object... extraTools) { return prompt(chatOptions, message, extraTools); }

    /** The same, with generation settings chosen per call instead of the bot's own. */
    public String prompt(AIOptions options, String message, Object... extraTools) { return oneShot(own("QuickChatter"), systemPrompt, options).send(message, extraTools); }

    /** A task run as this bot when it has a persona of its own, under the task's own name when it does not. */
    private String own(String taskName) { return systemPrompt != null ? getInstruction() : taskName; }

    /**
     * Fills {@code type} from the message: the backend is told to emit exactly that shape and the
     * JSON comes back deserialised, instead of prose to be parsed.
     * <p>A throwaway conversation like {@link #prompt}, but extraction is mechanical, so it runs
     * under the extractor persona and deterministic settings rather than this bot's personality —
     * only the model is taken from the bot. Tools are off. Logged as {@code QuickExtractor}.
     * <p>{@code type} can be any record or class with a no-argument constructor; nested types and
     * lists are supported.
     *
     * @return the filled object, or null if the model returned nothing usable
     */
    public <T> T promptAs(String message, Class<T> type) { return promptAs(preset(AIOptions.ITEM_CHOOSER).withMaxOutputTokens(700), message, type); }

    /** The same, with generation settings chosen per call. */
    public <T> T promptAs(AIOptions options, String message, Class<T> type) { return promptAs("QuickExtractor", options, message, type); }

    /** Named per task, so the log says which one asked — a chooser and an extractor read the same otherwise. */
    private <T> T promptAs(String instruction, AIOptions options, String message, Class<T> type) {
        return oneShot(instruction, Prompts.EXTRACTOR, options).sendAs(message, type, options);
    }

    /**
     * Picks the entries of {@code items} that match {@code description}, and returns those very
     * objects — not text about them.
     * <p>The list is numbered into the prompt and the model answers with indexes, so nothing has to
     * be matched back by name and an object of any type can be chosen. Runs as a throwaway
     * conversation under the extractor persona and deterministic settings, logged as
     * {@code QuickChooser}; an empty or null list costs no model call at all.
     *
     * @param description what a matching entry looks like, e.g. "Which of these are red?"
     * @return the matching items in the list's own order, empty when none match
     */
    public <T> List<T> chooseBetween(String description, List<T> items) {
        if (items == null || items.isEmpty()) return List.of();
        StringBuilder sb = new StringBuilder("From this numbered list, pick the entries that best match: ").append(description).append("\n\n");
        for (int i = 0; i < items.size(); i++) sb.append(i).append(": ").append(items.get(i)).append('\n');
        sb.append("\nReturn the numbers of the matching entries. If several match, return the most commonly known ones.");

        Choice choice = promptAs("QuickChooser", preset(AIOptions.ITEM_CHOOSER), sb.toString(), Choice.class);
        List<T> chosen = new ArrayList<>();
        if (choice != null && choice.indexes() != null)
            for (Integer i : choice.indexes()) if (i != null && i >= 0 && i < items.size()) chosen.add(items.get(i));
        return chosen;
    }

    /** The shape {@link #chooseBetween} asks for — indexes, rather than prose to be split apart. */
    public record Choice(List<Integer> indexes) {}

    /**
     * Continues {@code text} and returns <b>only the continuation</b> — models like to echo the
     * prompt back first, and that prefix is stripped before you see it.
     * <p>Completion is mechanical, so this ignores the bot's persona and runs under the completion
     * one with tools off, keeping only the bot's model. Logged as {@code QuickCompleter}.
     */
    public String complete(String text) { return without(text, oneShot("QuickCompleter", Prompts.AUTO_COMPLETIST, preset(AIOptions.AUTO_COMPLETIST)).send(text)); }

    /**
     * {@link #complete} for source code: the persona names the language, so the model continues the
     * snippet instead of explaining it. Logged as {@code QuickCoder(JAVA)}.
     */
    public String completeCode(String code, ProgrammingLanguage language) { return without(code, oneShot("QuickCoder(" + language.name() + ")", Prompts.autoCompletist(language), preset(AIOptions.AUTO_COMPLETIST)).send(code)); }

    /**
     * Answers a factual question in plain prose — short paragraphs, no lists unless asked, and no
     * internal identifiers such as database ids or binary values leaked into the reply.
     * <p>A throwaway conversation like {@link #prompt}, but tighter and more factual: the
     * informationist settings replace the bot's chat ones, keeping its model. Speaks as this bot
     * when it has a persona, otherwise as the informationist. Logged as {@code QuickAnswerer}.
     *
     * @param extraTools tool objects for this call only, on top of the bot's own
     */
    public String askForInformation(String question, Object... extraTools) {
        return oneShot(own("QuickAnswerer"), systemPrompt != null ? systemPrompt : Prompts.INFORMATIONIST, preset(AIOptions.INFORMATIONIST)).send(question, extraTools);
    }

    /**
     * {@link #prompt}, delivered as it is generated: {@code onChunk} is called with each fragment as
     * the model produces it, on the calling thread, and the whole reply is returned at the end.
     * <p>A streamed turn cannot call tools — the tool loop needs a complete reply to act on — so the
     * bot's tools are not offered here. Everything else is a normal throwaway conversation: this
     * bot's persona and chat settings, logged as {@code QuickChatter} unless the bot has its own
     * persona. The log shows one line when the stream finishes, never a line per chunk.
     *
     * @param onChunk called with every fragment, in order; never with null or an empty string
     * @return the complete reply, the concatenation of every chunk
     */
    public String stream(String message, Consumer<String> onChunk) { return oneShot(own("QuickChatter"), systemPrompt, chatOptions).sendStreaming(message, onChunk); }

    /** Completion models often echo the prompt back before continuing it. */
    private static String without(String prefix, String reply) {
        return reply == null ? null : reply.replaceFirst("^\\s*" + Pattern.quote(prefix), "");
    }

    /** The persona with any known facts about the owner appended. */
    public String getSystemPromptFor(String owner) {
        if (profileFacts == null || owner == null) return systemPrompt;
        List<String> facts = profileFacts.apply(owner);
        if (facts == null || facts.isEmpty()) return systemPrompt;
        String section = "What you already know about this user:\n- " + String.join("\n- ", facts);
        return systemPrompt == null ? section : systemPrompt + "\n\n" + section;
    }

    public boolean isToolApproved(ToolCall call) { return toolApprover == null || toolApprover.test(call); }

    public void reportStep(AgentRun step) { if (onStep != null) onStep.accept(step); }

    /** The part of this bot that is data, for writing to a config file. */
    public ChatbotDefinition getDefinition() {
        return new ChatbotDefinition(name, getServiceName(), systemPrompt, chatOptions, agentOptions, maxSteps, attributes);
    }

    @Override public String toString() { return "Chatbot[" + name + " on " + getServiceName() + "]"; }
}
