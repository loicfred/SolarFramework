package org.solarframework.ai.spring;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static org.solarframework.ai.spring.AIConst.*;
import static org.solarframework.core.util.ClassUtils.getSerializableFieldsOfClassFamily;
import static org.solarframework.json.JSONItem.SimpleGSON;

@Service
@SuppressWarnings("all")
public class AIService {
    public static AIService aiService;
    @EventListener(ApplicationReadyEvent.class)
    public void setStaticReference() {
        AIService.aiService = context.getBean(AIService.class);
    }

    private final ApplicationContext context;
    private final CacheManager aiCacheManager;

    private final ChatClient chatClient;

    protected final OpenAiChatOptions Conversationist = OpenAiChatOptions.builder().maxTokens(400)
            .temperature(0.4).topP(0.9).frequencyPenalty(0.25).presencePenalty(0.25).stop(List.of("\n\n"))
            .toolChoice(OpenAiApi.ChatCompletionRequest.ToolChoiceBuilder.AUTO)
            .build();
    protected final OpenAiChatOptions Informationist = OpenAiChatOptions.builder().maxTokens(700)
            .temperature(0.2).topP(0.6).frequencyPenalty(0.25).presencePenalty(0.25).stop(List.of("\n\n\n"))
            .toolChoice(OpenAiApi.ChatCompletionRequest.ToolChoiceBuilder.AUTO)
            .build();
    protected final OpenAiChatOptions ItemChooser = OpenAiChatOptions.builder().maxTokens(200)
            .temperature(0.0).topP(0.05).frequencyPenalty(0.0).presencePenalty(0.0).stop(List.of("\n")).build();
    protected final OpenAiChatOptions AutoCompletist = OpenAiChatOptions.builder().maxTokens(200)
            .temperature(0.3).topP(0.9).frequencyPenalty(0.0).presencePenalty(0.1).stop(List.of("\n\n\n")).build();

    public AIService(ApplicationContext context, @Qualifier("aiCacheManager") CacheManager aiCacheManager, ChatClient chatClient) {
        this.context = context;
        this.aiCacheManager = aiCacheManager;
        this.chatClient = chatClient;
    }

    protected AssistantMessage doPrompt(Prompt prompt, Object... toolsObjects) {
        return chatClient.prompt(prompt).tools(toolsObjects).call().chatResponse().getResult().getOutput();
    }


    public Conversation startConversation() {
        return new Conversation(null, null);
    }
    public org.solarframework.ai.spring.Conversation startConversation(SystemMessage instruction, Object... toolsObjects) {
        return new Conversation(null, instruction, toolsObjects);
    }
    public Conversation startConversation(String username, SystemMessage instruction, Object... toolsObjects) {
        return new Conversation(username, instruction, toolsObjects);
    }
    public void startConversation(String username, SystemMessage instruction, Consumer<Conversation> conv, Object... toolsObjects) {
        conv.accept(new Conversation(username, instruction, toolsObjects));
    }



    public String prompt(String prompt, Object... toolsObjects) {
        return customizedPrompt(Prompt.builder().chatOptions(Conversationist).content(prompt).build(), toolsObjects);
    }
    public String customizedPrompt(Prompt prompt, Object... toolsObjects) {
        return doPrompt(prompt, toolsObjects).getText();
    }


    public String askForInformation(String prompt, Object... toolsObjects) {
        return customizedPrompt(Prompt.builder().chatOptions(Informationist)
                .messages(getInformationist_Instruction(), UserMessage.builder().text(prompt).build())
                .build());
    }


    public String autoCompleteCode(String code, ProgrammingLanguage language) {
        return customizedPrompt(Prompt.builder().chatOptions(AutoCompletist)
                .messages(getAutoCompletist_Instruction(language), UserMessage.builder().text(code).build())
                .build()).replaceFirst(code, "");
    }
    public void autoCompleteCodeAsync(String code, ProgrammingLanguage language, Consumer<String> remaining) {
        try (ExecutorService E = Executors.newSingleThreadExecutor()) {
            E.execute(() -> autoCompleteCode(code, language));
        } catch (Exception ignored) {}
    }
    public String autoComplete(String text) {
        return customizedPrompt(Prompt.builder().chatOptions(AutoCompletist)
                .messages(getAutoCompletist_Instruction(), UserMessage.builder().text(text).build())
                .build()).replaceFirst(text, "");
    }


    public <T> List<T> chooseBetween(String description, List<T> items) {
        Map<String, T> s = new HashMap<>();
        int i = 0;
        boolean isPrimitive = items.get(0).getClass().isPrimitive() || items.get(0).getClass().equals(String.class);
        for (T item : items) {
            if (!isPrimitive) for (Field f : getSerializableFieldsOfClassFamily(item.getClass())) try {
                f.set(item, null);
            } catch (IllegalAccessException ignored) {}
            s.put(i++ + "", item);
        }

        Prompt p = Prompt.builder().chatOptions(ItemChooser)
                .messages(getLister_Instruction(SimpleGSON.toJson(s)), UserMessage.builder().text(description).build())
                .build();
        String answer = customizedPrompt(p);
        items = new ArrayList<>();
        for (String k : answer.split(" \\| ")) items.add(s.get(k));
        items.removeIf(ii -> ii == null);
        return items;
    }

}
