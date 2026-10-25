package br.com.espelhatv.server;

import br.com.espelhatv.BuildInfo;
import br.com.espelhatv.auth.AuthService;
import br.com.espelhatv.auth.Session;
import br.com.espelhatv.auth.SessionStore;
import br.com.espelhatv.capture.CaptureService;
import br.com.espelhatv.capture.Frame;
import br.com.espelhatv.capture.FrameHub;
import br.com.espelhatv.config.AppConfig;
import br.com.espelhatv.control.ControlManager;
import br.com.espelhatv.input.InputController;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HTTP API e servidor de assets. Tudo é same-origin; não há CORS.
 */
public final class HttpApi implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger("espelhatv.http");
    private static final int MAX_FORM_BYTES = 4 * 1024;
    private static final String SESSION_COOKIE = "espelhatv_session";
    private static final String CSRF_HEADER = "X-EspelhaTV";
    private static final String CSRF_VALUE = "1";
    private static final long STREAM_RELEASE_GRACE_SECONDS = 3;

    private final AppConfig config;
    private final AuthService auth;
    private final SessionStore sessions;
    private final ControlManager control;
    private final InputController input;
    private final FrameHub hub;
    private final CaptureService capture;
    private final StaticFiles staticFiles = new StaticFiles();
    private final Map<String, AtomicInteger> streamCounts = new ConcurrentHashMap<>();
    private final Instant startedAt = Instant.now();

    private HttpServer server;
    private ExecutorService executor;

    public HttpApi(AppConfig config, AuthService auth, SessionStore sessions,
                   ControlManager control, InputController input,
                   FrameHub hub, CaptureService capture) {
        this.config = config;
        this.auth = auth;
        this.sessions = sessions;
        this.control = control;
        this.input = input;
        this.hub = hub;
        this.capture = capture;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(config.bind(), config.port()), 0);
        server.createContext("/", this::handle);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.start();
    }

    public int port() {
        return server == null ? config.port() : server.getAddress().getPort();
    }

    @Override
    public void close() {
        if (server != null) {
            server.stop(1);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void handle(HttpExchange exchange) {
        try {
            addSecurityHeaders(exchange);
            route(exchange);
        } catch (IllegalArgumentException e) {
            safeError(exchange, 400, "bad_request", e.getMessage());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Erro inesperado em " + exchange.getRequestURI(), e);
            safeError(exchange, 500, "internal_error", "Erro interno.");
        } finally {
            exchange.close();
        }
    }

    private void route(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        if (path.equals("/health")) {
            if (!method.equals("GET")) {
                methodNotAllowed(exchange, "GET");
                return;
            }
            health(exchange);
            return;
        }

        if (path.equals("/")) {
            if (!method.equals("GET")) {
                methodNotAllowed(exchange, "GET");
                return;
            }
            if (session(exchange) == null) {
                redirect(exchange, "/login");
            } else {
                if (!staticFiles.serve(exchange, "index.html")) {
                    notFound(exchange);
                }
            }
            return;
        }

        if (path.equals("/login")) {
            if (!method.equals("GET")) {
                methodNotAllowed(exchange, "GET");
                return;
            }
            if (exchange.getRequestURI().getRawQuery() != null) {
                // Não aceita token/segredo em query string; o login é sempre POST.
                redirect(exchange, "/login");
                return;
            }
            if (session(exchange) != null) {
                redirect(exchange, "/");
            } else if (!staticFiles.serve(exchange, "login.html")) {
                notFound(exchange);
            }
            return;
        }

        if (isAsset(path)) {
            if (!method.equals("GET")) {
                methodNotAllowed(exchange, "GET");
                return;
            }
            if (!staticFiles.serve(exchange, path.substring(1))) {
                notFound(exchange);
            }
            return;
        }

        if (path.equals("/stream")) {
            if (!method.equals("GET")) {
                methodNotAllowed(exchange, "GET");
                return;
            }
            stream(exchange);
            return;
        }

        if (path.startsWith("/api/")) {
            api(exchange, path, method);
            return;
        }

        notFound(exchange);
    }

    private void api(HttpExchange exchange, String path, String method) throws IOException {
        if (method.equals("POST") && !checkOrigin(exchange)) {
            return;
        }

        switch (path) {
            case "/api/login" -> {
                if (!requireMethod(exchange, method, "POST")) {
                    return;
                }
                login(exchange);
            }
            case "/api/logout" -> {
                if (!requireMethod(exchange, method, "POST")) {
                    return;
                }
                logout(exchange);
            }
            case "/api/state" -> {
                if (!requireMethod(exchange, method, "GET")) {
                    return;
                }
                state(exchange);
            }
            case "/api/control" -> {
                if (!requireMethod(exchange, method, "POST")) {
                    return;
                }
                control(exchange);
            }
            case "/api/input" -> {
                if (!requireMethod(exchange, method, "POST")) {
                    return;
                }
                input(exchange);
            }
            case "/api/heartbeat" -> {
                if (!requireMethod(exchange, method, "POST")) {
                    return;
                }
                heartbeat(exchange);
            }
            default -> notFound(exchange);
        }
    }

    private void login(HttpExchange exchange) throws IOException {
        Map<String, String> form = parseForm(exchange);
        AuthService.LoginResult result = auth.login(form.get("token"), remoteIp(exchange));
        switch (result) {
            case OK -> {
                Session session = sessions.create();
                setSessionCookie(exchange, session);
                sendJson(exchange, 200, "{\"ok\":true}");
            }
            case INVALID -> sendJson(exchange, 401, "{\"error\":\"invalid_token\"}");
            case RATE_LIMITED -> sendJson(exchange, 429, "{\"error\":\"rate_limited\"}");
        }
    }

    private void logout(HttpExchange exchange) throws IOException {
        Session session = requireSession(exchange);
        if (session == null || !requireCsrf(exchange)) {
            return;
        }
        control.releaseBySession(session.id());
        sessions.remove(session.id());
        clearSessionCookie(exchange);
        sendJson(exchange, 200, "{\"ok\":true}");
    }

    private void state(HttpExchange exchange) throws IOException {
        Session session = requireSession(exchange);
        if (session == null) {
            return;
        }
        Frame frame = hub.latest();
        if (frame == null) {
            frame = capture.lastFrame();
        }
        int width = frame == null ? config.maxWidth() : frame.width();
        int height = frame == null ? config.maxHeight() : frame.height();
        String error = capture.lastError();

        StringBuilder json = new StringBuilder(384);
        json.append('{')
                .append("\"authenticated\":true")
                .append(",\"demo\":").append(config.demo())
                .append(",\"controllerActive\":").append(control.isActive())
                .append(",\"isController\":").append(control.isControllerSession(session.id()))
                .append(",\"viewers\":").append(hub.viewerCount())
                .append(",\"width\":").append(width)
                .append(",\"height\":").append(height)
                .append(",\"fps\":").append(config.fps())
                .append(",\"controlTimeoutSeconds\":").append(config.controlTimeoutSeconds())
                .append(",\"source\":\"").append(jsonEscape(capture.sourceDescription())).append('"')
                .append(",\"version\":\"").append(BuildInfo.VERSION).append('"');
        if (error != null) {
            json.append(",\"captureError\":\"").append(jsonEscape(error)).append('"');
        }
        json.append('}');
        sendJson(exchange, 200, json.toString());
    }

    private void control(HttpExchange exchange) throws IOException {
        Session session = requireSession(exchange);
        if (session == null || !requireCsrf(exchange)) {
            return;
        }
        Map<String, String> form = parseForm(exchange);
        String action = form.get("action");
        if ("claim".equals(action)) {
            ControlManager.ClaimResult result = control.claim(session.id());
            if (result.granted()) {
                sendJson(exchange, 200, "{\"granted\":true,\"token\":\""
                        + jsonEscape(result.token()) + "\",\"message\":\""
                        + jsonEscape(result.message()) + "\"}");
            } else {
                sendJson(exchange, 409, "{\"granted\":false,\"message\":\""
                        + jsonEscape(result.message()) + "\"}");
            }
            return;
        }
        if ("release".equals(action)) {
            if (control.release(session.id(), form.get("token"))) {
                sendJson(exchange, 200, "{\"released\":true}");
            } else {
                sendJson(exchange, 403, "{\"error\":\"not_controller\"}");
            }
            return;
        }
        sendJson(exchange, 400, "{\"error\":\"invalid_action\"}");
    }

    private void input(HttpExchange exchange) throws IOException {
        Session session = requireSession(exchange);
        if (session == null || !requireCsrf(exchange)) {
            return;
        }
        Map<String, String> form = parseForm(exchange);
        if (!control.touch(session.id(), form.get("token"))) {
            sendJson(exchange, 403, "{\"error\":\"not_controller\"}");
            return;
        }
        if (!input.handle(form)) {
            sendJson(exchange, 400, "{\"error\":\"invalid_input\"}");
            return;
        }
        sendEmpty(exchange, 204);
    }

    private void heartbeat(HttpExchange exchange) throws IOException {
        Session session = requireSession(exchange);
        if (session == null || !requireCsrf(exchange)) {
            return;
        }
        Map<String, String> form = parseForm(exchange);
        if (!control.touch(session.id(), form.get("token"))) {
            sendJson(exchange, 403, "{\"error\":\"not_controller\"}");
            return;
        }
        sendEmpty(exchange, 204);
    }

    private void stream(HttpExchange exchange) throws IOException {
        Session session = requireSession(exchange);
        if (session == null) {
            return;
        }

        AtomicInteger streams = streamCounts.computeIfAbsent(session.id(), key -> new AtomicInteger());
        streams.incrementAndGet();
        BlockingQueue<Frame> queue = null;
        try {
            queue = hub.subscribe();
            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", "multipart/x-mixed-replace; boundary=frame");
            headers.set("Cache-Control", "no-store, no-cache, must-revalidate");
            headers.set("Pragma", "no-cache");
            exchange.sendResponseHeaders(200, 0);

            OutputStream output = exchange.getResponseBody();
            while (!Thread.currentThread().isInterrupted()) {
                Frame frame = queue.poll(5, TimeUnit.SECONDS);
                if (frame == null) {
                    output.write("\r\n".getBytes(StandardCharsets.US_ASCII));
                    output.flush();
                    continue;
                }
                output.write("--frame\r\n".getBytes(StandardCharsets.US_ASCII));
                output.write("Content-Type: image/jpeg\r\n".getBytes(StandardCharsets.US_ASCII));
                output.write(("Content-Length: " + frame.size() + "\r\n").getBytes(StandardCharsets.US_ASCII));
                output.write(("X-Sequence: " + frame.sequence() + "\r\n").getBytes(StandardCharsets.US_ASCII));
                output.write("\r\n".getBytes(StandardCharsets.US_ASCII));
                output.write(frame.jpeg());
                output.write("\r\n".getBytes(StandardCharsets.US_ASCII));
                output.flush();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            LOG.log(Level.FINE, "Espectador desconectou: " + e.getMessage());
        } finally {
            hub.unsubscribe(queue);
            if (streams.decrementAndGet() <= 0) {
                streamCounts.remove(session.id(), streams);
                CompletableFuture.delayedExecutor(STREAM_RELEASE_GRACE_SECONDS, TimeUnit.SECONDS)
                        .execute(() -> {
                            if (!streamCounts.containsKey(session.id())) {
                                control.releaseBySession(session.id());
                            }
                        });
            }
        }
    }

    private void health(HttpExchange exchange) throws IOException {
        Frame frame = hub.latest();
        if (frame == null) {
            frame = capture.lastFrame();
        }
        String lastFrame = frame == null ? "null" : "\"" + frame.timestamp() + "\"";
        long uptime = Math.max(0, Duration.between(startedAt, Instant.now()).toSeconds());
        String json = "{"
                + "\"status\":\"ok\""
                + ",\"name\":\"" + BuildInfo.NAME + "\""
                + ",\"version\":\"" + BuildInfo.VERSION + "\""
                + ",\"uptimeSeconds\":" + uptime
                + ",\"demo\":" + config.demo()
                + ",\"viewers\":" + hub.viewerCount()
                + ",\"controllerActive\":" + control.isActive()
                + ",\"lastFrameAt\":" + lastFrame
                + "}";
        sendJson(exchange, 200, json);
    }

    private Session requireSession(HttpExchange exchange) throws IOException {
        Session session = session(exchange);
        if (session == null) {
            sendJson(exchange, 401, "{\"error\":\"unauthorized\"}");
        }
        return session;
    }

    private Session session(HttpExchange exchange) {
        return sessions.get(cookie(exchange, SESSION_COOKIE));
    }

    private boolean requireCsrf(HttpExchange exchange) throws IOException {
        String value = exchange.getRequestHeaders().getFirst(CSRF_HEADER);
        if (!CSRF_VALUE.equals(value)) {
            sendJson(exchange, 403, "{\"error\":\"csrf\"}");
            return false;
        }
        return true;
    }

    private boolean checkOrigin(HttpExchange exchange) throws IOException {
        Headers headers = exchange.getRequestHeaders();
        String fetchSite = headers.getFirst("Sec-Fetch-Site");
        if (fetchSite != null && !fetchSite.equals("same-origin") && !fetchSite.equals("none")) {
            sendJson(exchange, 403, "{\"error\":\"origin\"}");
            return false;
        }

        String origin = headers.getFirst("Origin");
        if (origin == null) {
            return true;
        }
        String host = headers.getFirst("Host");
        try {
            URI uri = URI.create(origin);
            if (host == null || uri.getAuthority() == null
                    || !host.equalsIgnoreCase(uri.getAuthority())) {
                sendJson(exchange, 403, "{\"error\":\"origin\"}");
                return false;
            }
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 403, "{\"error\":\"origin\"}");
            return false;
        }
        return true;
    }

    private Map<String, String> parseForm(HttpExchange exchange) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null
                || !contentType.toLowerCase(Locale.ROOT).startsWith("application/x-www-form-urlencoded")) {
            throw new IllegalArgumentException("Content-Type deve ser application/x-www-form-urlencoded.");
        }
        byte[] body = exchange.getRequestBody().readNBytes(MAX_FORM_BYTES + 1);
        if (body.length > MAX_FORM_BYTES) {
            throw new IllegalArgumentException("Corpo da requisição muito grande.");
        }
        String raw = new String(body, StandardCharsets.UTF_8);
        Map<String, String> form = new LinkedHashMap<>();
        if (raw.isEmpty()) {
            return form;
        }
        for (String pair : raw.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String name = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            String decodedName = URLDecoder.decode(name, StandardCharsets.UTF_8);
            String decodedValue = URLDecoder.decode(value, StandardCharsets.UTF_8);
            form.putIfAbsent(decodedName, decodedValue);
        }
        return form;
    }

    private boolean requireMethod(HttpExchange exchange, String actual, String expected) throws IOException {
        if (!actual.equals(expected)) {
            methodNotAllowed(exchange, expected);
            return false;
        }
        return true;
    }

    private void methodNotAllowed(HttpExchange exchange, String allowed) throws IOException {
        exchange.getResponseHeaders().set("Allow", allowed);
        sendJson(exchange, 405, "{\"error\":\"method_not_allowed\"}");
    }

    private void notFound(HttpExchange exchange) throws IOException {
        sendJson(exchange, 404, "{\"error\":\"not_found\"}");
    }

    private void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
    }

    private void safeError(HttpExchange exchange, int status, String code, String message) {
        try {
            sendJson(exchange, status, "{\"error\":\"" + jsonEscape(code)
                    + "\",\"message\":\"" + jsonEscape(message == null ? "" : message) + "\"}");
        } catch (IOException ignored) {
            // a conexão já pode ter sido fechada
        }
    }

    private void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    private void sendEmpty(HttpExchange exchange, int status) throws IOException {
        exchange.sendResponseHeaders(status, -1);
    }

    private void setSessionCookie(HttpExchange exchange, Session session) {
        long maxAge = Math.max(1, session.ttl().toSeconds());
        exchange.getResponseHeaders().add("Set-Cookie",
                SESSION_COOKIE + "=" + session.id()
                        + "; HttpOnly; SameSite=Strict; Path=/; Max-Age=" + maxAge);
    }

    private void clearSessionCookie(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Set-Cookie",
                SESSION_COOKIE + "=; HttpOnly; SameSite=Strict; Path=/; Max-Age=0");
    }

    private static String cookie(HttpExchange exchange, String name) {
        List<String> headers = exchange.getRequestHeaders().get("Cookie");
        if (headers == null) {
            return null;
        }
        for (String header : headers) {
            for (String part : header.split(";")) {
                int eq = part.indexOf('=');
                if (eq < 0) {
                    continue;
                }
                if (name.equals(part.substring(0, eq).trim())) {
                    return part.substring(eq + 1).trim();
                }
            }
        }
        return null;
    }

    private static String remoteIp(HttpExchange exchange) {
        if (exchange.getRemoteAddress() == null) {
            return "desconhecido";
        }
        if (exchange.getRemoteAddress().getAddress() != null) {
            return exchange.getRemoteAddress().getAddress().getHostAddress();
        }
        return exchange.getRemoteAddress().getHostString();
    }

    private static boolean isAsset(String path) {
        return path.equals("/app.js")
                || path.equals("/login.js")
                || path.equals("/style.css")
                || path.equals("/test-webrtc.html")
                || path.equals("/test-webrtc.css")
                || path.equals("/test-webrtc.js")
                || path.equals("/diagnostico.html")
                || path.equals("/diagnostico.js")
                || path.equals("/favicon.svg");
    }

    private static void addSecurityHeaders(HttpExchange exchange) {
        Headers headers = exchange.getResponseHeaders();
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Frame-Options", "DENY");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        headers.set("Cross-Origin-Opener-Policy", "same-origin");
        // COEP fica de fora: o player HLS carrega mídia do MediaMTX (porta 8888) na LAN.
        headers.set("Cross-Origin-Resource-Policy", "same-origin");
        headers.set("Content-Security-Policy",
                "default-src 'self'; img-src 'self' data:; style-src 'self'; "
                        + "script-src 'self'; connect-src 'self' http://*:8889; "
                        + "media-src 'self' http://*:8888; frame-ancestors 'none'; "
                        + "base-uri 'none'; form-action 'self'");
    }

    private static String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
