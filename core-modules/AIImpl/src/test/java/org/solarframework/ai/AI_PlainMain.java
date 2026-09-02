package org.solarframework.ai;

import org.solarframework.ai.obj.Conversation;
import org.solarframework.ai.spring.AIManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.solarframework.ai.spring.AIRegistry.DefaultAIService;
import static org.solarframework.ai.spring.AIRegistry.SolarAIManager;

/**
 * The same script as {@link AI_Main}, with no application context anywhere - building the manager and naming one
 * endpoint is the whole of the setup, which is what a plugin carrying this module does too. Needs LM Studio running.
 */
public class AI_PlainMain {

    static void main(String[] args) {
        new AIManager().makeNewService(IAIService.DEFAULT, "http://localhost:1234", "N/A", "openai/gpt-oss-20b");

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
    }

    public static class Toolbox {
        @AITool(description = "Returns the current date and time.")
        public String getCurrentTime() { return LocalDateTime.now().toString(); }
    }
}
