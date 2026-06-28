package org.solarframework.proxyserver.spring;

import org.solarframework.proxyserver.obj.Domain;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Component
public class DomainRegistry implements RouteDefinitionLocator {

    private final Map<String, Domain> domains = new ConcurrentHashMap<>();
    private final ApplicationEventPublisher events;

    public DomainRegistry(ApplicationEventPublisher events) {
        this.events = events;
    }

    public Domain get(String host)        { return host == null ? null : domains.get(host.toLowerCase()); }
    public Collection<Domain> all()       { return domains.values(); }

    public void add(Domain d) {
        domains.put(d.getHost().toLowerCase(), d);
        events.publishEvent(new RefreshRoutesEvent(this));   // tell the gateway to rebuild its routes
    }

    public void remove(String host) {
        domains.remove(host.toLowerCase());
        events.publishEvent(new RefreshRoutesEvent(this));
    }

    // The gateway calls this to learn the PROXY routes. STATIC ones are handled by the filter below.
    @Override
    public Flux<RouteDefinition> getRouteDefinitions() {
        return Flux.fromIterable(domains.values())
                .filter(Domain::isProxy)
                .map(d -> {
                    RouteDefinition rd = new RouteDefinition();
                    rd.setId(d.getHost());
                    rd.setUri(URI.create(d.getPath()));
                    rd.setPredicates(List.of(new PredicateDefinition("Host=" + d.getHost())));
                    return rd;
                });
    }
}