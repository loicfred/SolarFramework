package org.solarframework.ai.dto;

import java.util.List;

/**
 * Immutable generation settings — every knob the backend is given for one turn. Every {@code with*}
 * returns a copy, so the presets below are safe to share.
 * <p>There is deliberately <b>no input bound here</b>, because no backend accepts one:
 * {@code maxOutputTokens} caps the reply only. What may be <em>sent</em> is limited by the model's
 * context, and staying under it is the caller's job — that is
 * {@link org.solarframework.ai.memory.Memory}, which trims the history before the request is built
 * rather than asking the server to.
 * <p>A note on the two that are easy to confuse: <b>temperature and topP do the same job by different
 * means</b>, so vary one and leave the other alone. Temperature reshapes the odds of every candidate
 * word; topP throws the unlikely ones away before choosing. Turning both up compounds them and the
 * answers wander.
 *
 * @param model            which model answers. Null means whatever the service is configured with, which is
 *                         the ordinary case — a second model is a second service.
 * @param maxOutputTokens  the longest a single reply may run. The backend stops <em>mid-sentence</em> when it
 *                         is reached, so it is a safety net rather than a way to ask for brevity: to get short
 *                         answers, say so in the persona. Roughly one token per four characters of English.
 *                         <br>200 ≈ a short paragraph · 500 ≈ a few paragraphs · 1500 ≈ a long, structured answer.
 * @param temperature      how much randomness is allowed when picking each next word, 0 to 2. Low is careful and
 *                         repeatable, high is inventive and eventually incoherent.
 *                         <br>Asked "what does the API Manager do?": at <b>0.0</b> it answers the same sentence
 *                         every time, near word-for-word from the manual — right for classifying, extracting or
 *                         picking from a list. At <b>0.4</b> it words the same facts differently each time and
 *                         reads like a person — right for conversation. At <b>1.2</b> it starts reaching for
 *                         analogies and flourishes nobody asked for, and at length it invents details.
 * @param topP             nucleus sampling, 0 to 1: consider only the likeliest words whose probabilities add up
 *                         to this share, and ignore the rest of the tail outright.
 *                         <br>At <b>0.1</b> only the few most obvious continuations survive, so the answer is
 *                         blunt and formulaic. At <b>0.9</b> the sensible tail is kept and the odd unusual-but-apt
 *                         word can appear — the usual setting. At <b>1.0</b> nothing is discarded, so a genuinely
 *                         unlikely word can be chosen and a long answer can drift off.
 * @param frequencyPenalty pushes a word down the more times it has <em>already been used</em>, -2 to 2. Aimed at
 *                         repeated wording.
 *                         <br>At <b>0.0</b> a model explaining a table will say "table" in nearly every sentence.
 *                         At <b>0.25</b> it reaches for "it" and "that one" instead. At <b>1.5</b> it avoids the
 *                         word so hard the sentences turn vague or clumsy.
 * @param presencePenalty  pushes a word down for having <em>appeared at all</em>, -2 to 2, regardless of how often.
 *                         Aimed at repeated subject matter rather than repeated wording.
 *                         <br>At <b>0.0</b> an answer happily circles the same point. At <b>0.25</b> it moves on
 *                         to the next aspect once one is covered. At <b>1.5</b> it changes subject so eagerly it
 *                         wanders off the question.
 * @param stop             strings that end generation the moment the model produces one; the stop string itself is
 *                         not returned. Useful when a reply has a known terminator, e.g. {@code "\n\n"} to take the
 *                         first paragraph only. Empty means the model stops when it is done or runs out of room.
 * @param allowTools       whether tools may be offered for this turn at all. False on the mechanical presets below
 *                         because extraction and completion have nothing to look up, and offering tools there only
 *                         invites a call nobody wanted.
 */
public record AIOptions(String model, Integer maxOutputTokens, Double temperature, Double topP,
                        Double frequencyPenalty, Double presencePenalty, List<String> stop, boolean allowTools) {

    public AIOptions { stop = stop == null ? List.of() : List.copyOf(stop); }

    /** Chatty and warm: general conversation. */
    public static final AIOptions CONVERSATIONIST = new AIOptions(null, 400, 0.4, 0.9, 0.25, 0.25, List.of(), true);
    /** Tight and factual: question answering. */
    public static final AIOptions INFORMATIONIST = new AIOptions(null, 700, 0.2, 0.6, 0.25, 0.25, List.of(), true);
    /** Deterministic: picking from a list, classifying, structured output. */
    public static final AIOptions ITEM_CHOOSER = new AIOptions(null, 200, 0.0, 0.05, 0.0, 0.0, List.of(), false);
    /** Continuation of text or code, never a conversation. */
    public static final AIOptions AUTO_COMPLETIST = new AIOptions(null, 200, 0.3, 0.9, 0.0, 0.1, List.of(), false);
    /** Room to think and call tools repeatedly: the default for {@code run()}. */
    public static final AIOptions AGENT = new AIOptions(null, 1500, 0.2, 0.8, 0.0, 0.0, List.of(), true);

    public AIOptions withModel(String model) { return new AIOptions(model, maxOutputTokens, temperature, topP, frequencyPenalty, presencePenalty, stop, allowTools); }
    public AIOptions withMaxOutputTokens(int maxOutputTokens) { return new AIOptions(model, maxOutputTokens, temperature, topP, frequencyPenalty, presencePenalty, stop, allowTools); }
    public AIOptions withTemperature(double temperature) { return new AIOptions(model, maxOutputTokens, temperature, topP, frequencyPenalty, presencePenalty, stop, allowTools); }
    public AIOptions withTopP(double topP) { return new AIOptions(model, maxOutputTokens, temperature, topP, frequencyPenalty, presencePenalty, stop, allowTools); }
    public AIOptions withStop(String... stop) { return new AIOptions(model, maxOutputTokens, temperature, topP, frequencyPenalty, presencePenalty, List.of(stop), allowTools); }
    public AIOptions withTools(boolean allowTools) { return new AIOptions(model, maxOutputTokens, temperature, topP, frequencyPenalty, presencePenalty, stop, allowTools); }
}
