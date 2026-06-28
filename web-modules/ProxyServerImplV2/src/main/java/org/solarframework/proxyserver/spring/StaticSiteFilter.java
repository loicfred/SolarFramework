package org.solarframework.proxyserver.spring;

import org.solarframework.proxyserver.obj.Domain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ZeroCopyHttpOutputMessage;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class StaticSiteFilter implements WebFilter, Ordered {

    private final DomainRegistry registry;

    public StaticSiteFilter(DomainRegistry registry) {
        this.registry = registry;
        System.out.println(">>> StaticSiteFilter BEAN CREATED");
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        System.out.println(">>> StaticSiteFilter BEAN RUN");
        String host = stripPort(exchange.getRequest().getHeaders().getFirst(HttpHeaders.HOST));
        Domain d = registry.get(host);

        if (d == null || d.isProxy()) {
            return chain.filter(exchange);
        }

        try {
            Path base = Path.of(d.getPath()).toAbsolutePath().normalize();
            String reqPath = exchange.getRequest().getPath().value();
            if (reqPath.endsWith("/")) reqPath += "index.html";
            Path file = base.resolve(reqPath.substring(1)).normalize();

            ServerHttpResponse response = exchange.getResponse();
            // refuse anything that escapes the directory (path-traversal guard)
            if (!file.startsWith(base) || !Files.isReadable(file) || Files.isDirectory(file)) {
                response.setStatusCode(HttpStatus.NOT_FOUND);
                return response.setComplete();
            }

            MediaTypeFactory.getMediaType(file.getFileName().toString()).ifPresent(mt -> response.getHeaders().setContentType(mt));

            // zero-copy send (kernel sendfile) — no buffering the file through the JVM
            return ((ZeroCopyHttpOutputMessage) response).writeWith(file.toFile(), 0, Files.size(file));
        } catch (IOException e) {
            exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            return exchange.getResponse().setComplete();
        }
    }

    private static String stripPort(String host) {
        if (host == null) return null;
        int i = host.indexOf(':');
        return (i == -1 ? host : host.substring(0, i)).toLowerCase();
    }
}