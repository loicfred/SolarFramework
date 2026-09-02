package org.solarframework.ai;

import org.solarframework.ai.dto.ChatbotDefinition;
import org.solarframework.ai.dto.ServiceDefinition;

import java.util.List;
import java.util.Set;

/**
 * Every {@link IAIService} the application has, the way {@code IDatabaseManager} holds every data
 * source. One service is one endpoint and one default model, so several agents on several models is
 * the ordinary case.
 * <p>The default service is the Spring bean seeded from {@code application.properties}. Every other
 * one is built here and is not a bean, so it inherits the default's endpoint, key, model and
 * timeout unless told otherwise — three agents on the configured model cost three lines:
 * <pre>
 * SolarAIManager.makeNewService("triage");
 * SolarAIManager.makeNewService("summarizer");
 * SolarAIManager.makeNewService("coder", "qwen/qwen3-coder-30b");
 * </pre>
 */
public interface IAIManager {

    IAIService getDefaultService();

    /** The default service when {@code name} is unknown, so a caller always gets something usable. */
    IAIService getService(String name);

    boolean hasService(String name);

    Set<IAIService> getServices();

    /** A new service on the default's endpoint, key and model. */
    IAIService makeNewService(String name);

    /** A new service on the default's endpoint and key, running a different model. */
    IAIService makeNewService(String name, String model);

    IAIService makeNewService(String name, String baseUrl, String apiKey, String model);

    boolean addService(IAIService service);

    boolean removeService(String name);

    /** Moves a service to a new name, carrying the default title with it if it held one. Fails when {@code oldName} is unknown or {@code newName} already names a different service. */
    boolean renameService(String oldName, String newName);

    boolean removeNonDefaultServices();

    /**
     * Named chatbots, so an agent can be defined once at startup and looked up anywhere — the same
     * reason sources are named rather than passed around.
     */
    Chatbot getChatbot(String name);

    boolean addChatbot(Chatbot bot);

    /**
     * Registers a stored definition as a chatbot on the service it names, toolless — the shape both a config file and
     * a managing screen hand one over in. A definition naming a service that is not configured is registered all the
     * same, so an agent written down before any endpoint exists is not quietly dropped the next time it is saved.
     */
    boolean addChatbot(ChatbotDefinition definition);

    boolean removeChatbot(String name);

    Set<Chatbot> getChatbots();

    /**
     * Reads services and agents back from a JSON file, as {@code DatabaseManager} reads its data
     * sources. The file is the whole truth: what it does not name does not exist afterwards, which
     * is what makes a removal on a managing screen stick.
     * <p>A restored agent has its persona, model and bounds but no tools — hand those to
     * {@code getChatbot(name)} rebuilt with its tools afterwards.
     */
    void LoadFromFile(String path);

    void SaveAsFile(String path);

    List<ServiceDefinition> getServiceDefinitions();

    List<ChatbotDefinition> getChatbotDefinitions();
}
