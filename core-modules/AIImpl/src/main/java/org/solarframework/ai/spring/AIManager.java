package org.solarframework.ai.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.solarframework.ai.Chatbot;
import org.solarframework.ai.IAIManager;
import org.solarframework.ai.IAIService;
import org.solarframework.ai.dto.ChatbotDefinition;
import org.solarframework.ai.dto.ServiceDefinition;
import org.solarframework.json.JSONItem;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.solarframework.ai.spring.AIRegistry.DefaultAIService;
import static org.solarframework.ai.spring.AIRegistry.SolarAIManager;

/**
 * {@link IAIManager} over Spring AI services, mirroring {@code DatabaseManager}.
 * <p>Not a Spring bean, and it starts empty. An installation that has configured no endpoint holds no
 * service and no default, which is the ordinary case: the whole stack ships inside a plugin, and the
 * services come from whatever that plugin has stored — never from a property file.
 */
public class AIManager implements IAIManager {

    private static final Logger log = LoggerFactory.getLogger(AIManager.class);

    private final ToolCallingManager toolCallingManager;
    private final Map<String, IAIService> services = new ConcurrentHashMap<>();
    private final Map<String, Chatbot> chatbots = new ConcurrentHashMap<>();
    /** Which service a caller naming none gets. Null until one is registered. */
    private volatile String defaultName;

    public AIManager() { this(ToolCallingManager.builder().build()); }
    public AIManager(ToolCallingManager toolCallingManager) {
        this.toolCallingManager = toolCallingManager;
        SolarAIManager = this;
    }

    /** The one named "Default" if there is one, otherwise the first that was registered, otherwise null. */
    @Override public IAIService getDefaultService() { return defaultName == null ? null : services.get(defaultName); }

    /** Falls back to the default - which is itself null until a service has been registered, so a caller on an unconfigured installation gets null rather than a service pointing nowhere. A null name asks for the default outright, and must not reach the map, which refuses one. */
    @Override public IAIService getService(String name) { return name == null ? getDefaultService() : services.getOrDefault(name, getDefaultService()); }

    @Override public boolean hasService(String name) { return services.containsKey(name); }

    @Override public Set<IAIService> getServices() { return new LinkedHashSet<>(services.values()); }

    @Override public IAIService makeNewService(String name) { return makeNewService(name, requireDefault().getModel()); }

    @Override public IAIService makeNewService(String name, String model) {
        IAIService from = requireDefault();
        return makeNewService(name, from.getBaseUrl(), from.getApiKey(), model);
    }
    /** These two overloads copy the default's endpoint, so they are the one place that cannot answer without one. */
    private IAIService requireDefault() {
        IAIService from = getDefaultService();
        if (from == null) throw new IllegalStateException("No AI service has been configured to copy the endpoint from. Add one with makeNewService(name, baseUrl, apiKey, model).");
        return from;
    }

    @Override public IAIService makeNewService(String name, String baseUrl, String apiKey, String model) {
        IAIService from = getDefaultService();
        AIService service = new AIService(toolCallingManager);
        service.setName(name);
        service.setBaseUrl(baseUrl);
        service.setApiKey(apiKey);
        service.setModel(model);
        service.setTimeoutSeconds(from == null ? 300 : from.getTimeoutSeconds());
        service.setRequestsPerMinute(from == null ? 0 : from.getRequestsPerMinute());
        addService(service);
        return service;
    }

    @Override public boolean addService(IAIService service) {
        if (service == null || services.put(service.getName(), service) == service) return false;
        rememberDefault(service.getName());
        log.info("Added AI service {}: {}", service.getName(), service.describe());
        return true;
    }

    @Override public boolean removeService(String name) {
        if (services.remove(name) == null) return false;
        if (name.equals(defaultName)) forgetDefault();
        log.info("Removed AI service {}", name);
        return true;
    }

    @Override public boolean renameService(String oldName, String newName) {
        if (oldName.equals(newName)) return true;
        IAIService service = services.get(oldName);
        if (service == null || services.containsKey(newName)) return false;
        services.remove(oldName);
        service.setName(newName);
        services.put(newName, service);
        if (oldName.equals(defaultName)) defaultName = newName;
        DefaultAIService = getDefaultService();
        // an agent that named this service by hand is carried with it, so a rename never silently swaps which model answers as it
        for (Chatbot bot : chatbots.values()) if (oldName.equals(bot.getServiceName())) bot.renameService(newName);
        log.info("Renamed AI service {} to {}", oldName, newName);
        return true;
    }

    @Override public boolean removeNonDefaultServices() {
        boolean removed = services.keySet().removeIf(n -> !n.equals(defaultName));
        return removed;
    }

    /** The first service registered is the default until one actually called "Default" arrives, which then takes it. */
    private void rememberDefault(String name) {
        if (defaultName == null || IAIService.DEFAULT.equals(name)) defaultName = name;
        DefaultAIService = getDefaultService();
    }
    private void forgetDefault() {
        defaultName = services.keySet().stream().findFirst().orElse(null);
        DefaultAIService = getDefaultService();
    }

    @Override public Chatbot getChatbot(String name) { return chatbots.get(name); }

    @Override public boolean addChatbot(Chatbot bot) {
        if (bot == null || chatbots.put(bot.getName(), bot) == bot) return false;
        log.info("Added chatbot {} on service {}: {} tool(s), max {} step(s)", bot.getName(), bot.getServiceName(), bot.getTools().size(), bot.getMaxSteps());
        return true;
    }

    @Override public boolean addChatbot(ChatbotDefinition definition) {
        return addChatbot(Chatbot.builder(getService(definition.serviceName())).applyDefinition(definition).build());
    }

    @Override public boolean removeChatbot(String name) {
        if (chatbots.remove(name) == null) return false;
        log.info("Removed chatbot {}", name);
        return true;
    }

    @Override public Set<Chatbot> getChatbots() { return new LinkedHashSet<>(chatbots.values()); }

    @Override public List<ServiceDefinition> getServiceDefinitions() {
        List<ServiceDefinition> out = new ArrayList<>();
        for (IAIService s : services.values())
            out.add(new ServiceDefinition(s.getName(), s.getBaseUrl(), s.getApiKey(), s.getModel(), s.getTimeoutSeconds(), s.getRequestsPerMinute()));
        return out;
    }

    @Override public List<ChatbotDefinition> getChatbotDefinitions() {
        List<ChatbotDefinition> out = new ArrayList<>();
        for (Chatbot b : chatbots.values()) out.add(b.getDefinition());
        return out;
    }

    @Override public void LoadFromFile(String path) {
        log.info("Opening AI config file: {}", path);
        AIConfigFile file = AIConfigFile.ReadFrom(path, this);

        // The file is the whole truth now - nothing is seeded from anywhere else, so what it does not
        // name does not exist. Clearing first is what makes a removal on the managing screen stick.
        services.clear();
        defaultName = null;
        for (ServiceDefinition d : file.getServices()) {
            IAIService target = makeNewService(d.name(), d.baseUrl(), d.apiKey(), d.model());
            target.setTimeoutSeconds(d.timeoutSeconds());
            target.setRequestsPerMinute(d.requestsPerMinute());
        }

        // A restored agent has its persona, model and bounds but no tools - those are live objects and belong to
        // whoever runs it.
        chatbots.clear();
        for (ChatbotDefinition d : file.getChatbots()) addChatbot(d);

        log.info("Loaded {} service(s) and {} chatbot(s)", services.size(), chatbots.size());
    }

    @Override public void SaveAsFile(String path) {
        if (new AIConfigFile(this).WriteTo(path) == null) log.error("Failed to write AI config file: {}", path);
    }

    /** The file itself: the endpoints and the agents that run on them, as data, mirroring {@code DataSourceFile}. */
    protected static class AIConfigFile extends JSONItem<AIConfigFile> {
        private final List<ServiceDefinition> services = new ArrayList<>();
        private final List<ChatbotDefinition> chatbots = new ArrayList<>();

        public AIConfigFile(IAIManager manager) {
            services.addAll(manager.getServiceDefinitions());
            chatbots.addAll(manager.getChatbotDefinitions());
        }

        public List<ServiceDefinition> getServices() { return services; }
        public List<ChatbotDefinition> getChatbots() { return chatbots; }

        public AIConfigFile WriteTo(String path) {
            try {
                return WriteJSON(path);
            } catch (Exception _) {
                return null;
            }
        }

        /** A missing or unreadable file is written from what the manager currently holds. */
        public static AIConfigFile ReadFrom(String path, IAIManager manager) {
            try {
                AIConfigFile file = ReadJSON(path, AIConfigFile.class);
                if (file == null) throw new IllegalStateException("empty file");
                return file;
            } catch (Exception _) {
                log.warn("No readable AI config file at {}, creating one from the current setup", path);
                AIConfigFile written = new AIConfigFile(manager);
                return written.WriteTo(path) instanceof AIConfigFile f ? f : written;
            }
        }
    }

    @Override public String toString() { return "AIManager[" + services.keySet() + ", " + chatbots.size() + " chatbot(s)]"; }
}
