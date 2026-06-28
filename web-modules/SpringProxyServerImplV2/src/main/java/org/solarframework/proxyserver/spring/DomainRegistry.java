package org.solarframework.proxyserver.spring;

import org.solarframework.proxyserver.obj.BaseDomain;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.solarframework.proxyserver.spring.ProxyService.WAMPSERVER;

@Component
public class DomainRegistry implements RouteDefinitionLocator {
    protected static DomainRegistry registry;

    private final ApplicationEventPublisher events;

    public DomainRegistry(ApplicationEventPublisher events) {
        this.events = events;
        registry = this;
    }

    public void UpdateDomains() {
        events.publishEvent(new RefreshRoutesEvent(this));   // tell the gateway to rebuild its routes
    }

    // The gateway calls this to learn the PROXY routes. STATIC ones are handled by the filter below.
    @Override
    public Flux<RouteDefinition> getRouteDefinitions() {
        return Flux.fromIterable(WAMPSERVER == null ? new ArrayList<>() : WAMPSERVER.getAllDomains())
                .filter(BaseDomain::isProxy)
                .map(d -> {
                    RouteDefinition rd = new RouteDefinition();
                    rd.setId(d.getHost());
                    rd.setUri(URI.create(d.getPath()));
                    rd.setPredicates(List.of(new PredicateDefinition("Host=" + d.getHost())));
                    return rd;
                });
    }
}