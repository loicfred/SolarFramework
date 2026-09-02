package org.solarframework.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.solarframework.ai.dto.AgentRun;
import org.solarframework.ai.obj.Conversation;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.time.LocalDateTime;
import java.util.List;

import static org.solarframework.ai.spring.AIRegistry.DefaultAIService;
import static org.solarframework.ai.spring.AIRegistry.SolarAIManager;

/**
 * Manual smoke harness for the Spring path. Needs LM Studio running and {@code spring.ai.openai.base-url} set (see
 * {@code AIConfig}); run it and read the log — every call logs itself.
 * <p>{@link AI_PlainMain} is the same script with no application context at all, which is how the module actually
 * ships inside a plugin.
 */
@SpringBootApplication
public class AI_Main {

    static void main(String[] args) {
        SpringApplication.run(AI_Main.class, args);

        Chatbot quick = DefaultAIService.createChatbot().build();
        quick.chooseBetween("Which one is red?", List.of("Apple", "Cherry", "Banana", "Orange"));
        System.out.println("--------");
        quick.prompt("Hi, how are you?");
        System.out.println("--------");
        quick.stream("Name three planets.", chunk -> {});
        System.out.println("--------");
        Chatbot bot = DefaultAIService.createChatbot().name("timekeeper")
                .systemPrompt("You are an assistant that answers time-related questions.")
                .tools(new Toolbox()).maxSteps(5).build();
        SolarAIManager.addChatbot(bot);
        System.out.println("--------");

        Conversation c = bot.startConversation("demo", "Loïc");
        c.send("What is the current time?");
        c.send("Thank you!");
        c.save();

        //AgentRun run = bot.startConversation().run("Find the current time and tell me whether it is morning or afternoon.");
    }

    public static class Toolbox {
        @AITool(description = "Returns the current date and time.")
        public String getCurrentTime() { return LocalDateTime.now().toString(); }
    }
}
