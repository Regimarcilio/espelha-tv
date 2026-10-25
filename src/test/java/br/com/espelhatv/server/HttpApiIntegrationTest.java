package br.com.espelhatv.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.espelhatv.capture.DemoScreenSource;
import br.com.espelhatv.config.AppConfig;
import br.com.espelhatv.input.NoopInputSink;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(20)
class HttpApiIntegrationTest {
    private static final String TOKEN = "token-de-integracao";
    private static final String CSRF = "X-EspelhaTV";

    private AppServer server;
    private java.net.http.HttpClient client;
    private String base;

    @BeforeEach
    void startServer() throws Exception {
        int port = freePort();
        AppConfig config = new AppConfig(port, "127.0.0.1", "token-de-integracao", false,
                5, 320, 240, 0.5f, 300, 720, false, 0, true,
                false, "127.0.0.1", 5004, 1920, 1080, 30, "ultrafast", 8000, 2,
                "http://127.0.0.1:8889/stream/whip", "tools/ffmpeg-whip", "/dev/dri/renderD128", "x11grab");
        server = new br.com.espelhatv.server.AppServer(config, 
                new br.com.espelhatv.capture.DemoScreenSource(320, 240), 
                new br.com.espelhatv.input.NoopInputSink());
        server.start();
        base = "http://127.0.0.1:" + port;
        client = java.net.http.HttpClient.newBuilder()
                .version(java.net.http.HttpClient.Version.HTTP_1_1)
                .connectTimeout(java.time.Duration.ofSeconds(3))
                .followRedirects(java.net.http.HttpClient.Redirect.NEVER)
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
        java.net.http.HttpResponse<String> health = get("/health", null);
        assertEquals(200, health.statusCode());
        assertTrue(health.body().contains("ok"));
        
        java.net.http.HttpResponse<String> root = get("/", null);
        assertEquals(302, root.statusCode());
        assertEquals("/login", root.headers().firstValue("Location").orElse(""));
        
        java.net.http.HttpResponse<String> login = get("/login", null);
        assertEquals(200, login.statusCode());
        assertTrue(login.body().contains("Espelha-TV"));
    }

    @Test
    void loginSucceedsWithCorrectToken() throws Exception {
        java.net.http.HttpResponse<String> login = post("/api/login", "token=token-de-integracao", null);
        assertEquals(200, login.statusCode());
        assertTrue(login.headers().firstValue("Set-Cookie").isPresent());
        
        java.net.http.HttpResponse<String> state = get("/api/state", cookieFrom(login));
        assertEquals(200, state.statusCode());
        assertTrue(state.body().contains("authenticated"));
    }

    @Test
    void loginFailsWithWrongToken() throws Exception {
        java.net.http.HttpResponse<String> login = post("/api/login", "token=errado", null);
        assertEquals(401, login.statusCode());
    }

    @Test
    void rateLimitOnLogin() throws Exception {
        for (int i = 0; i < 12; i++) {
            java.net.http.HttpResponse<String> login = post("/api/login", "token=errado", null);
            if (i < 10) {
                assertEquals(401, login.statusCode());
            } else {
                assertEquals(429, login.statusCode());
            }
        }
    }

    @Test
    void loginRedirectsToRoot() throws Exception {
        java.net.http.HttpResponse<String> login = post("/api/login", "token=token-de-integracao", null);
        assertEquals(200, login.statusCode());
        String cookie = cookieFrom(login);
        
        java.net.http.HttpResponse<String> root = get("/", cookie);
        assertEquals(200, root.statusCode());
        assertFalse(root.body().contains("login"));
    }

    @Test
    void claimRequiresLogin() throws Exception {
        java.net.http.HttpResponse<String> claim = post("/api/control", "action=claim", null);
        assertEquals(401, claim.statusCode());
    }

    @Test
    void claimSucceedsAfterLogin() throws Exception {
        String cookie = loginAndGetCookie();
        java.net.http.HttpResponse<String> claim = post("/api/control", "action=claim", cookie);
        assertEquals(200, claim.statusCode());
        assertTrue(claim.body().contains("granted"));
    }

    @Test
    void secondClaimFails() throws Exception {
        String cookieA = loginAndGetCookie();
        java.net.http.HttpResponse<String> claimA = post("/api/control", "action=claim", cookieA);
        assertEquals(200, claimA.statusCode());

        String cookieB = loginAndGetCookie();
        java.net.http.HttpResponse<String> claimB = post("/api/control", "action=claim", cookieB);
        assertEquals(409, claimB.statusCode());
    }

    @Test
    void releaseWorks() throws Exception {
        String cookie = loginAndGetCookie();
        java.net.http.HttpResponse<String> claim = post("/api/control", "action=claim", cookie);
        String controlToken = extractControlToken(claim.body());
        java.net.http.HttpResponse<String> release = post("/api/control", "action=release&token=" + controlToken, cookie);
        assertEquals(200, release.statusCode());
    }

    @Test
    void inputRequiresControlToken() throws Exception {
        String cookie = loginAndGetCookie();
        java.net.http.HttpResponse<String> input = post("/api/input", "type=move&x=0.5&y=0.5", cookie);
        assertEquals(403, input.statusCode());
    }

    @Test
    void inputWorksWithControlToken() throws Exception {
        String cookie = loginAndGetCookie();
        java.net.http.HttpResponse<String> claim = post("/api/control", "action=claim", cookie);
        assertEquals(200, claim.statusCode());
        
        String body = claim.body();
        String controlToken = extractControlToken(body);
        
        java.net.http.HttpResponse<String> input = post("/api/input", "type=move&x=0.5&y=0.5&token=" + controlToken, cookie);
        assertEquals(204, input.statusCode());
    }

    @Test
    void heartbeatExtendsControl() throws Exception {
        String cookie = loginAndGetCookie();
        java.net.http.HttpResponse<String> claim = post("/api/control", "action=claim", cookie);
        String controlToken = extractControlToken(claim.body());
        
        Thread.sleep(1100);
        java.net.http.HttpResponse<String> hb = post("/api/heartbeat", "token=" + controlToken, cookie);
        assertEquals(204, hb.statusCode());
    }

    @Test
    void logoutInvalidatesSession() throws Exception {
        String cookie = loginAndGetCookie();
        post("/api/logout", "", cookie);
        java.net.http.HttpResponse<String> state = get("/api/state", cookie);
        assertEquals(401, state.statusCode());
    }

    @Test
    void loginWithQueryStringRedirects() throws Exception {
        java.net.http.HttpResponse<String> resp = client.send(java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(base + "/login?token=abc"))
                .GET()
                .build(), java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(302, resp.statusCode());
        assertEquals("/login", resp.headers().firstValue("Location").orElse(""));
    }

    private int freePort() throws Exception {
        try (java.net.ServerSocket s = new java.net.ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private java.net.http.HttpResponse<String> get(String path, String cookie) throws Exception {
        java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(base + path))
                .GET();
        if (cookie != null) {
            builder.header("Cookie", cookie);
        }
        return client.send(builder.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
    }

    private java.net.http.HttpResponse<String> post(String path, String body, String cookie) throws Exception {
        java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(base + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body));
        if (cookie != null) {
            builder.header("Cookie", cookie);
            builder.header("X-EspelhaTV", "1");
        }
        return client.send(builder.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
    }

    private String loginAndGetCookie() throws Exception {
        java.net.http.HttpResponse<String> login = post("/api/login", "token=token-de-integracao", null);
        return cookieFrom(login);
    }

    private String cookieFrom(java.net.http.HttpResponse<String> resp) {
        return resp.headers().firstValue("Set-Cookie").orElse("");
    }

    private String extractControlToken(String json) {
        int idx = json.indexOf("\"token\"");
        if (idx < 0) return "";
        int start = json.indexOf(":", idx) + 1;
        int end = json.indexOf(",", start);
        if (end < 0) end = json.indexOf("}", start);
        String token = json.substring(start, end).trim();
        return token.replaceAll("^\"|\"$", "");
    }

}
