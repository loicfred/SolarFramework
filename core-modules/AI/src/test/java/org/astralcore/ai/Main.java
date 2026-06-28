package org.solarframework.ai;

import org.solarframework.ai.spring.Conversation;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.time.LocalDateTime;
import java.util.List;

import static org.solarframework.ai.spring.AIService.aiService;

@SpringBootApplication
public class Main {

    static void main(String[] args) {
        SpringApplication.run(Main.class, args);

        List<String> outputs = aiService.chooseBetween("Which one is red?", List.of("Apple", "Cherry", "Banana", "Orange"));
        System.out.println(outputs); // [Apple,Cherry]

        String output = aiService.prompt("Hi, how are you?");
        System.out.println(output); // Hi, how can I help you today?

        output = aiService.prompt("What is the current time?", new Toolbox());
        System.out.println(output); // [The current time from Toolbox]

        SystemMessage instruction = SystemMessage.builder().text("You are an AI assistent designed answer the time related questions of the user.").build();
        Conversation c = aiService.startConversation("Loïc", instruction, new Toolbox());
        System.out.println(c.talk("What is the current time?"));
        System.out.println(c.talk("Thank you!"));
    }

    public static class Toolbox {
        @Tool(description = "Returns the current time.")
        public String getCurrentTime(String name) {
            return LocalDateTime.now().toString();
        }
    }
}
