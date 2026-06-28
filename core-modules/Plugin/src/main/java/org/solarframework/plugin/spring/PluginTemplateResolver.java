package org.solarframework.plugin.spring;

import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;
import org.springframework.context.annotation.Bean;
import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.AbstractConfigurableTemplateResolver;
import org.thymeleaf.templateresolver.FileTemplateResolver;
import org.thymeleaf.templateresource.ITemplateResource;
import org.thymeleaf.templateresource.UrlTemplateResource;

import java.net.URL;
import java.util.Map;
import java.util.Set;

public class PluginTemplateResolver extends AbstractConfigurableTemplateResolver {

    private final PluginManager pluginManager;

    public PluginTemplateResolver(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
        setOrder(1);
    }

    @Override
    protected ITemplateResource computeTemplateResource(IEngineConfiguration configuration, String ownerTemplate, String template, String resourceName, String characterEncoding, Map<String, Object> templateResolutionAttributes) {
        for (PluginWrapper plugin : pluginManager.getPlugins()) {
            ClassLoader cl = plugin.getPluginClassLoader();
            URL url = cl.getResource("templates/" + resourceName + ".html");
            if (url != null) return new UrlTemplateResource(url, characterEncoding);
        }
        return null;
    }

    @Bean
    public SpringTemplateEngine templateEngine(PluginManager pluginManager) {
        SpringTemplateEngine engine = new SpringTemplateEngine();
        FileTemplateResolver defaultResolver = new FileTemplateResolver();
        defaultResolver.setPrefix("classpath:/templates/");
        defaultResolver.setSuffix(".html");
        defaultResolver.setOrder(2);
        PluginTemplateResolver pluginResolver = new PluginTemplateResolver(pluginManager);
        pluginResolver.setOrder(1);
        engine.setTemplateResolvers(Set.of(pluginResolver, defaultResolver));
        return engine;
    }
}