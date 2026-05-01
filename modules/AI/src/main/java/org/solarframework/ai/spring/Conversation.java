package org.solarframework.ai.spring;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Map;

import static org.solarframework.ai.spring.AIService.aiService;

public class Conversation {

    private final List<Message> msgs = new java.util.ArrayList<>();
    private List<Object> toolsObjects = new java.util.ArrayList<>();

    protected Conversation(String myName, SystemMessage systemMessage, Object... toolsObjects) {
        this.toolsObjects.addAll(List.of(toolsObjects));
        msgs.add(systemMessage != null ? systemMessage : SystemMessage.builder().text("""
                You are an AI model designed to answer have a conversation with %s.
                Keep answers clear and short paragraphs, avoid switching lines too much.
                Avoid answering in list format unless the user asks for it.
                """.formatted(myName != null ? myName : "a user")).build());
    }

    public String talk(String yourMessage, Object... toolsObj) {
        addToTools(toolsObj);
        return chat(yourMessage, toolsObjects, aiService.Conversationist);
    }
    public String askForInformation(String yourMessage, Object... toolsObj) {
        addToTools(toolsObj);
        toolsObjects = new java.util.ArrayList<>(new java.util.LinkedHashSet<>(toolsObjects));
        return chat(yourMessage, toolsObjects, aiService.Informationist);
    }
    public String askToChooseBetweenItems(String yourMessage, Object... toolsObj) {
        addToTools(toolsObj);
        return chat(yourMessage, toolsObjects, aiService.ItemChooser);
    }

    private void addToTools(Object[] toolsObj) {
        for (Object obj : toolsObj) {
            if (toolsObjects.stream().noneMatch(o -> o.getClass().equals(obj.getClass()))) {
                toolsObjects.add(obj);
            }
        }
    }

    private @Nullable String chat(String yourMessage, List<Object> toolsObjects, ChatOptions options) {
        msgs.add(UserMessage.builder().metadata(Map.of("timestamp", System.currentTimeMillis())).text(yourMessage).build());
        msgs.add(aiService.doPrompt(Prompt.builder().messages(msgs).chatOptions(options).build(), toolsObjects.toArray()));
        return msgs.getLast().getText();
    }

    public List<Message> getMessages() {
        return msgs;
    }
    public void add(Message msg) {
        msgs.add(msg);
    }

}
