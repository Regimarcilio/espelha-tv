package br.com.espelhatv.server;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Serve os assets embutidos em src/main/resources/web. */
public final class StaticFiles {
    private static final Map<String, String> CONTENT_TYPES = Map.ofEntries(
            Map.entry("index.html", "text/html; charset=utf-8"),
            Map.entry("login.html", "text/html; charset=utf-8"),
            Map.entry("test-webrtc.html", "text/html; charset=utf-8"),
            Map.entry("test-webrtc.css", "text/css; charset=utf-8"),
            Map.entry("test-webrtc.js", "text/javascript; charset=utf-8"),
            Map.entry("diagnostico.html", "text/html; charset=utf-8"),
            Map.entry("diagnostico.js", "text/javascript; charset=utf-8"),
            Map.entry("app.js", "text/javascript; charset=utf-8"),
            Map.entry("login.js", "text/javascript; charset=utf-8"),
            Map.entry("style.css", "text/css; charset=utf-8"),
            Map.entry("favicon.svg", "image/svg+xml"));

    private final Map<String, byte[]> cache = new ConcurrentHashMap<>();

    public boolean serve(HttpExchange exchange, String resource) throws IOException {
        byte[] bytes = cache.computeIfAbsent(resource, this::read);
        if (bytes == null) {
            return false;
        }
        String contentType = CONTENT_TYPES.getOrDefault(resource, "application/octet-stream");
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        return true;
    }

    private byte[] read(String resource) {
        try (InputStream input = StaticFiles.class.getResourceAsStream("/web/" + resource)) {
            return input == null ? null : input.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler recurso web: " + resource, e);
        }
    }
}
