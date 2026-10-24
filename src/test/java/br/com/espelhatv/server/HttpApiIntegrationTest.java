package br.com.espelhatv.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.espelhatv.capture.DemoScreenSource;
import br.com.espelhatv.config.AppConfig;
import br.com.espelhatv.input.NoopInputSink;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(20)
class HttpApiIntegrationTest {
    private static final String TOKEN = "token-de-integracao";
    private static final String CSRF = "X-EspelhaTV";

    private AppServer server;
    private HttpClient client;
    private String base;

    @BeforeEach
    void startServer() throws Exception {
        int port = freePort();
        AppConfig config = new AppConfig(port, "127.0.0.1", TOKEN, false,
                5, 320, 240, 0.5f, 300, 720, false, 0, true);
        server = new AppServer(config, new DemoScreenSource(320, 240), new NoopInputSink());
        server.start();
        base = "http://127.0.0.1:" + port;
        client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void servesHealthAndStaticAssetsWithoutSession() throws Exception {
        HttpResponse<String> health = get("/health", null);
        assertEquals(200, health.statusCode());
        assertTrue(health.body().contains("\"status\":\"ok\""));
        assertTrue(health.body().contains("\"demo\":true"));

        HttpResponse<String> loginPage = get("/login", null);
        assertEquals(200, loginPage.statusCode());
        assertTrue(loginPage.body().contains("Espelha-TV"));
        assertEquals("DENY", loginPage.headers().firstValue("X-Frame-Options").orElse(""));
        assertEquals("nosniff", loginPage.headers().firstValue("X-Content-Type-Options").orElse(""));
        assertEquals("same-origin",
                loginPage.headers().firstValue("Cross-Origin-Opener-Policy").orElse(""));
        assertEquals("require-corp",
                loginPage.headers().firstValue("Cross-Origin-Embedder-Policy").orElse(""));
        assertEquals("same-origin",
                loginPage.headers().firstValue("Cross-Origin-Resource-Policy").orElse(""));
        assertTrue(loginPage.headers().firstValue("Content-Security-Policy")
                .orElse("").contains("default-src 'self'"));

        HttpResponse<String> loginWithQuery = get("/login?token=nao-deve-ficar-na-url", null);
        assertEquals(302, loginWithQuery.statusCode());
        assertEquals("/login", loginWithQuery.headers().firstValue("Location").orElse(""));

        HttpResponse<String> script = get("/app.js", null);
        assertEquals(200, script.statusCode());
        assertTrue(script.headers().firstValue("Content-Type").orElse("").contains("javascript"));

        assertEquals(302, get("/", null).statusCode());
        assertEquals(401, get("/api/state", null).statusCode());
    }

    @Test
    void loginCreatesSessionAndLogoutRevokesIt() throws Exception {
        assertEquals(401, postForm("/api/login", "token=errado", null, false, null).statusCode());

        HttpResponse<String> login = postForm("/api/login", "token=" + TOKEN, null, false, null);
        assertEquals(200, login.statusCode());
        String cookie = cookieOf(login);
        assertTrue(cookie.startsWith("espelhatv_session="));
        assertTrue(login.headers().firstValue("Set-Cookie").orElse("").contains("HttpOnly"));
        assertTrue(login.headers().firstValue("Set-Cookie").orElse("").contains("SameSite=Strict"));

        HttpResponse<String> state = get("/api/state", cookie);
        assertEquals(200, state.statusCode());
        assertTrue(state.body().contains("\"authenticated\":true"));
        assertTrue(state.body().contains("\"source\":\"Demo 320x240\""));

        HttpResponse<String> noCsrf = postForm("/api/logout", "", cookie, false, null);
        assertEquals(403, noCsrf.statusCode());

        HttpResponse<String> logout = postForm("/api/logout", "", cookie, true, null);
        assertEquals(200, logout.statusCode());
        assertEquals(401, get("/api/state", cookie).statusCode());
    }

    @Test
    void onlyOneControllerAndCsrfProtectsInput() throws Exception {
        String cookieA = login();
        String cookieB = login();

        HttpResponse<String> claimA = postForm("/api/control", "action=claim", cookieA, true, null);
        assertEquals(200, claimA.statusCode());
        String tokenA = tokenOf(claimA.body());
        assertNotNull(tokenA);

        HttpResponse<String> claimB = postForm("/api/control", "action=claim", cookieB, true, null);
        assertEquals(409, claimB.statusCode());

        assertEquals(403, postForm("/api/input",
                "type=move&x=0.5&y=0.5&token=" + tokenA, cookieA, false, null).statusCode());

        HttpResponse<String> input = postForm("/api/input",
                "type=move&x=0.5&y=0.5&token=" + tokenA, cookieA, true, null);
        assertEquals(204, input.statusCode());

        assertEquals(403, postForm("/api/input",
                "type=move&x=0.5&y=0.5&token=errado", cookieA, true, null).statusCode());

        assertEquals(200, postForm("/api/control",
                "action=release&token=" + tokenA, cookieA, true, null).statusCode());
        assertEquals(403, postForm("/api/input",
                "type=move&x=0.5&y=0.5&token=" + tokenA, cookieA, true, null).statusCode());

        assertEquals(200, postForm("/api/control", "action=claim", cookieB, true, null).statusCode());
    }

    @Test
    void rejectsCrossOriginPosts() throws Exception {
        HttpResponse<String> crossOrigin = postForm(
                "/api/login", "token=" + TOKEN, null, false, "http://evil.example");
        assertEquals(403, crossOrigin.statusCode());
        assertTrue(crossOrigin.body().contains("\"error\":\"origin\""));

        HttpResponse<String> sameOrigin = postForm(
                "/api/login", "token=" + TOKEN, null, false, base);
        assertEquals(200, sameOrigin.statusCode());
    }

    @Test
    void streamSendsMjpegPartWithAuthenticatedSession() throws Exception {
        String cookie = login();
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/stream"))
                .timeout(Duration.ofSeconds(5))
                .header("Cookie", cookie)
                .GET()
                .build();
        HttpResponse<InputStream> response = client.send(request,
                HttpResponse.BodyHandlers.ofInputStream());
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("")
                .startsWith("multipart/x-mixed-replace"));

        byte[] head;
        try (InputStream body = response.body()) {
            head = body.readNBytes(32);
        }
        assertTrue(head.length > 0);
        assertTrue(new String(head, StandardCharsets.US_ASCII).startsWith("--frame"));
    }

    @Test
    void streamRequiresSession() throws Exception {
        assertEquals(401, get("/stream", null).statusCode());
    }

    @Test
    void closingControllerStreamReleasesControlAfterGrace() throws Exception {
        String cookieA = login();
        assertEquals(200, postForm("/api/control", "action=claim", cookieA, true, null).statusCode());

        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/stream"))
                .timeout(Duration.ofSeconds(5))
                .header("Cookie", cookieA)
                .GET()
                .build();
        HttpResponse<InputStream> response = client.send(request,
                HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            assertTrue(body.readNBytes(16).length > 0);
        }

        Thread.sleep(3_500);
        String cookieB = login();
        assertEquals(200, postForm("/api/control", "action=claim", cookieB, true, null).statusCode());
    }

    @Test
    void heartbeatAndControlErrorsAreExplicit() throws Exception {
        String cookie = login();

        assertEquals(403, postForm("/api/control", "action=claim", cookie, false, null).statusCode());
        assertEquals(405, get("/api/control", cookie).statusCode());
        assertEquals(400, postForm("/api/control", "action=nada", cookie, true, null).statusCode());
        assertEquals(403, postForm("/api/control",
                "action=release&token=errado", cookie, true, null).statusCode());

        HttpResponse<String> claim = postForm("/api/control", "action=claim", cookie, true, null);
        String token = tokenOf(claim.body());
        assertEquals(204, postForm("/api/heartbeat", "token=" + token, cookie, true, null).statusCode());
        assertEquals(403, postForm("/api/heartbeat", "token=errado", cookie, true, null).statusCode());
        assertEquals(403, postForm("/api/heartbeat", "token=" + token, cookie, false, null).statusCode());

        assertEquals(400, postForm("/api/input", "type=move&token=" + token, cookie, true, null).statusCode());
        assertEquals(400, postForm("/api/input", "type=move&x=9&y=0&token=" + token,
                cookie, true, null).statusCode());
    }

    @Test
    void logoutReleasesControlForAnotherViewer() throws Exception {
        String cookieA = login();
        String cookieB = login();
        assertEquals(200, postForm("/api/control", "action=claim", cookieA, true, null).statusCode());

        assertEquals(200, postForm("/api/logout", "", cookieA, true, null).statusCode());
        assertEquals(200, postForm("/api/control", "action=claim", cookieB, true, null).statusCode());
    }

    @Test
    void rejectsMalformedUnknownAndMethodMismatches() throws Exception {
        assertEquals(404, get("/api/desconhecida", null).statusCode());
        assertEquals(400, postForm("/api/login", "token=x", null, false, null,
                "text/plain").statusCode());
        assertEquals(405, get("/api/login", null).statusCode());
        assertEquals(405, postForm("/health", "", null, false, null).statusCode());
        assertEquals(403, postForm("/api/login", "token=x", null, false,
                "http://[invalido").statusCode());

        String cookie = login();
        HttpResponse<String> root = get("/", cookie);
        assertEquals(200, root.statusCode());
        assertTrue(root.body().contains("Espelha-TV"));
        assertEquals(302, get("/login", cookie).statusCode());
        assertEquals(302, get("/", "espelhatv_session=inexistente").statusCode());
    }

    private String login() throws Exception {
        HttpResponse<String> response = postForm("/api/login", "token=" + TOKEN, null, false, null);
        assertEquals(200, response.statusCode());
        return cookieOf(response);
    }

    private String cookieOf(HttpResponse<?> response) {
        String setCookie = response.headers().firstValue("Set-Cookie").orElseThrow();
        return setCookie.split(";", 2)[0];
    }

    private String tokenOf(String body) {
        Matcher matcher = Pattern.compile("\"token\":\"([^\"]+)\"").matcher(body);
        assertTrue(matcher.find(), () -> "token ausente em: " + body);
        return matcher.group(1);
    }

    private HttpResponse<String> get(String path, String cookie) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(5))
                .GET();
        if (cookie != null) {
            builder.header("Cookie", cookie);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postForm(String path, String body, String cookie,
                                          boolean csrf, String origin) throws Exception {
        return postForm(path, body, cookie, csrf, origin, "application/x-www-form-urlencoded");
    }

    private HttpResponse<String> postForm(String path, String body, String cookie,
                                          boolean csrf, String origin, String contentType) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (cookie != null) {
            builder.header("Cookie", cookie);
        }
        if (csrf) {
            builder.header(CSRF, "1");
        }
        if (origin != null) {
            builder.header("Origin", origin);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
