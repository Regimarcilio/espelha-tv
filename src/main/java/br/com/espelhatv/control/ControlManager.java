package br.com.espelhatv.control;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

/**
 * Garante um único controlador por vez. O token de controle é separado do
 * cookie de sessão: mesmo que outro cliente veja o stream, não consegue enviar
 * input sem o token efêmero.
 */
public final class ControlManager {
    private static final int CONTROL_TOKEN_BYTES = 24;

    private final Clock clock;
    private final Duration idleTimeout;
    private final Runnable onRelease;
    private final SecureRandom random = new SecureRandom();
    private State state;

    public ControlManager(Clock clock, Duration idleTimeout, Runnable onRelease) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.idleTimeout = Objects.requireNonNull(idleTimeout, "idleTimeout");
        this.onRelease = Objects.requireNonNull(onRelease, "onRelease");
    }

    public synchronized ClaimResult claim(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        Instant now = clock.instant();
        if (state != null) {
            if (state.sessionId.equals(sessionId)) {
                state.lastInputAt = now;
                return ClaimResult.granted(state.token);
            }
            return ClaimResult.denied("Outro dispositivo está no controle.");
        }
        State created = new State(sessionId, generateToken(), now);
        state = created;
        return ClaimResult.granted(created.token);
    }

    public boolean release(String sessionId, String token) {
        boolean released;
        synchronized (this) {
            if (!matches(sessionId, token)) {
                return false;
            }
            state = null;
            released = true;
        }
        if (released) {
            onRelease.run();
        }
        return true;
    }

    public void releaseBySession(String sessionId) {
        boolean released = false;
        synchronized (this) {
            if (state != null && state.sessionId.equals(sessionId)) {
                state = null;
                released = true;
            }
        }
        if (released) {
            onRelease.run();
        }
    }

    public void forceRelease() {
        boolean released = false;
        synchronized (this) {
            if (state != null) {
                state = null;
                released = true;
            }
        }
        if (released) {
            onRelease.run();
        }
    }

    public synchronized boolean touch(String sessionId, String token) {
        if (!matches(sessionId, token)) {
            return false;
        }
        state.lastInputAt = clock.instant();
        return true;
    }

    public synchronized boolean isController(String sessionId, String token) {
        return matches(sessionId, token);
    }

    public synchronized boolean isControllerSession(String sessionId) {
        return state != null && state.sessionId.equals(sessionId);
    }

    public synchronized boolean isActive() {
        return state != null;
    }

    public synchronized Optional<String> controllerSessionId() {
        return state == null ? Optional.empty() : Optional.of(state.sessionId);
    }

    /**
     * Libera o controle após inatividade. O callback de release é chamado fora
     * do lock para evitar deadlocks com o sink de input.
     */
    public boolean cleanupIdle() {
        boolean released = false;
        synchronized (this) {
            if (state != null) {
                Instant now = clock.instant();
                if (Duration.between(state.lastInputAt, now).compareTo(idleTimeout) >= 0) {
                    state = null;
                    released = true;
                }
            }
        }
        if (released) {
            onRelease.run();
        }
        return released;
    }

    public Duration idleTimeout() {
        return idleTimeout;
    }

    private boolean matches(String sessionId, String token) {
        if (state == null || sessionId == null || token == null) {
            return false;
        }
        return state.sessionId.equals(sessionId) && constantTimeEquals(state.token, token);
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }

    private String generateToken() {
        byte[] bytes = new byte[CONTROL_TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record ClaimResult(boolean granted, String token, String message) {
        public static ClaimResult granted(String token) {
            return new ClaimResult(true, token, "Controle concedido.");
        }

        public static ClaimResult denied(String message) {
            return new ClaimResult(false, null, message);
        }
    }

    private static final class State {
        private final String sessionId;
        private final String token;
        private Instant lastInputAt;

        private State(String sessionId, String token, Instant lastInputAt) {
            this.sessionId = sessionId;
            this.token = token;
            this.lastInputAt = lastInputAt;
        }
    }
}
