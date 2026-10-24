package br.com.espelhatv.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.espelhatv.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AuthServiceTest {

    private static final String TOKEN = "token-de-teste";

    @Test
    void acceptsOnlyTheConfiguredToken() {
        AuthService auth = new AuthService(TOKEN);

        assertEquals(AuthService.LoginResult.OK, auth.login(TOKEN, "10.0.0.2"));
        assertEquals(AuthService.LoginResult.INVALID, auth.login("errado", "10.0.0.3"));
        assertEquals(AuthService.LoginResult.INVALID, auth.login("", "10.0.0.3"));
        assertEquals(AuthService.LoginResult.INVALID, auth.login(null, "10.0.0.3"));
    }

    @Test
    void rateLimitsFailuresPerIpAndRecoversAfterWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        AuthService auth = new AuthService(TOKEN, clock, 3, 60_000L);

        assertEquals(AuthService.LoginResult.INVALID, auth.login("a", "10.0.0.4"));
        assertEquals(AuthService.LoginResult.INVALID, auth.login("b", "10.0.0.4"));
        assertEquals(AuthService.LoginResult.INVALID, auth.login("c", "10.0.0.4"));
        assertEquals(AuthService.LoginResult.RATE_LIMITED, auth.login(TOKEN, "10.0.0.4"));
        assertEquals(AuthService.LoginResult.OK, auth.login(TOKEN, "10.0.0.5"));

        clock.advance(Duration.ofSeconds(61));
        assertEquals(AuthService.LoginResult.OK, auth.login(TOKEN, "10.0.0.4"));
    }

    @Test
    void cleanupDropsExpiredAttempts() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        AuthService auth = new AuthService(TOKEN, clock, 3, 30_000L);

        auth.login("x", "10.0.0.6");
        assertEquals(1, auth.trackedClients());
        clock.advance(Duration.ofSeconds(31));
        auth.cleanup();
        assertEquals(0, auth.trackedClients());
    }

    @Test
    void rejectsBlankConfiguredToken() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        assertThrows(IllegalArgumentException.class, () -> new AuthService("  ", clock, 3, 30_000L));
        AuthService auth = new AuthService(TOKEN, clock, 3, 30_000L);
        assertFalse(auth.login("  ", "10.0.0.7") == AuthService.LoginResult.OK);
        assertTrue(auth.login("", "10.0.0.7") == AuthService.LoginResult.INVALID);
    }
}
