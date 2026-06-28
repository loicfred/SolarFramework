package org.solarframework.proxyserver.netty;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.solarframework.proxyserver.obj.BaseDomain;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;
import reactor.netty.tcp.SslProvider;

import javax.net.ssl.KeyManagerFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Map;

import static org.solarframework.proxyserver.netty.ProxyService.WAMPSERVER;

public class ProxyServer {
    private final Logger log = LoggerFactory.getLogger(ProxyServer.class);

    private DisposableServer server;

    private static final Map<String, String> MIME = Map.ofEntries(
            Map.entry(".html", "text/html"),
            Map.entry(".htm",  "text/html"),
            Map.entry(".css",  "text/css"),
            Map.entry(".js",   "application/javascript"),
            Map.entry(".mjs",  "application/javascript"),
            Map.entry(".json", "application/json"),
            Map.entry(".png",  "image/png"),
            Map.entry(".jpg",  "image/jpeg"),
            Map.entry(".jpeg", "image/jpeg"),
            Map.entry(".gif",  "image/gif"),
            Map.entry(".svg",  "image/svg+xml"),
            Map.entry(".ico",  "image/x-icon"),
            Map.entry(".woff", "font/woff"),
            Map.entry(".woff2","font/woff2"),
            Map.entry(".txt",  "text/plain")
    );

    public ProxyServer start() throws Exception {
        SslProvider ssl = SslProvider.builder().sslContext(sslContext()).build();
        this.server = HttpServer.create().port(443).secure(ssl).handle(this::route).bindNow();
        log.info("Started proxy server on port 443.");
        return this;
    }

    public ProxyServer block() {
        server.onDispose().block();
        return this;
    }

    public void stop() {
        if (server != null) {
            server.disposeNow();
            log.info("Stopped proxy server on port 443.");
        }
    }

    // --- routing ---

    private Publisher<Void> route(HttpServerRequest req, HttpServerResponse res) {
        String host = WAMPSERVER.stripPort(req.requestHeaders().get(HttpHeaderNames.HOST));
        BaseDomain<?> d = (host == null) ? null : WAMPSERVER.getDomainOfHost(host);
        if (d == null)   return res.status(HttpResponseStatus.NOT_FOUND).send();
        if (d.isProxy()) return proxy(d.getPath(), req, res);
        return serveStatic(d.getPath(), req, res);
    }

    private Publisher<Void> serveStatic(String dir, HttpServerRequest req, HttpServerResponse res) {
        try {
            Path base = Path.of(dir).toAbsolutePath().normalize();
            String reqPath = req.fullPath();
            if (reqPath.isEmpty() || reqPath.endsWith("/")) reqPath += "index.html";
            Path file = base.resolve(reqPath.substring(1)).normalize();

            if (!file.startsWith(base) || !Files.isReadable(file) || Files.isDirectory(file)) return res.status(HttpResponseStatus.NOT_FOUND).send();

            res.header(HttpHeaderNames.CONTENT_TYPE, contentType(file));
            return res.sendFile(file);
        } catch (Exception e) {
            return res.status(HttpResponseStatus.INTERNAL_SERVER_ERROR).send();
        }
    }

    private Publisher<Void> proxy(String target, HttpServerRequest req, HttpServerResponse res) {
        return HttpClient.create()
                .headers(h -> h.set(req.requestHeaders()))
                .request(req.method())
                .uri(target + req.uri())
                .send((cReq, out) -> out.send(req.receive().retain()))
                .responseConnection((cRes, conn) -> {
                    res.status(cRes.status());
                    cRes.responseHeaders().forEach(e -> res.addHeader(e.getKey(), e.getValue()));
                    return res.send(conn.inbound().receive().retain()).then();
                })
                .then();
    }

    private static String contentType(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String ext = (dot == -1) ? "" : name.substring(dot).toLowerCase();
        return MIME.getOrDefault(ext, "application/octet-stream");
    }

    private SslContext sslContext() throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(Path.of("./config/certs/ssldomains.p12"))) {
            ks.load(in, "password".toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, "password".toCharArray());
        return SslContextBuilder.forServer(kmf).build();
    }
}