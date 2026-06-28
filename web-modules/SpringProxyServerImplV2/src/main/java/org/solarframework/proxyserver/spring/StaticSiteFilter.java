package org.solarframework.proxyserver.spring;

import org.jspecify.annotations.NonNull;
import org.solarframework.proxyserver.obj.BaseDomain;
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

import static org.solarframework.proxyserver.spring.ProxyService.WAMPSERVER;

@Component
public class StaticSiteFilter implements WebFilter, Ordered {

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        String host = WAMPSERVER.stripPort(exchange.getRequest().getHeaders().getFirst(HttpHeaders.HOST));
        BaseDomain<?> d = WAMPSERVER.getDomainOfHost(host);

        if (d == null || d.isProxy()) return chain.filter(exchange);
        try {
            Path base = Path.of(d.getPath()).toAbsolutePath().normalize();
            String reqPath = exchange.getRequest().getPath().value();
            if (reqPath.endsWith("/")) reqPath += "index.html";
            Path file = base.resolve(reqPath.substring(1)).normalize();

            ServerHttpResponse response = exchange.getResponse();
            if (!file.startsWith(base) || !Files.isReadable(file) || Files.isDirectory(file)) {
                response.setStatusCode(HttpStatus.NOT_FOUND);
                return response.setComplete();
            }

            MediaTypeFactory.getMediaType(file.getFileName().toString()).ifPresent(mt -> response.getHeaders().setContentType(mt));

            return ((ZeroCopyHttpOutputMessage) response).writeWith(file.toFile(), 0, Files.size(file));
        } catch (IOException e) {
            exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            return exchange.getResponse().setComplete();
        }
    }

}