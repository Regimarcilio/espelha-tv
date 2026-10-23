package br.com.espelhatv.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verificação do token de acesso com comparação em tempo constante e
 * limitação simples de tentativas por IP.
 */
public final class AuthService {

    public enum LoginResult {
        OK,
        INVALID,
        RATE_LIMITED
    }

    private final byte[] expectedHash;
    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int maxAttempts;
    private final long windowMillis;

    public AuthService(String token) {
        this(token, Clock.systemUTC(), 10, 60_000L);
    }

    public AuthService(String token, Clock clock, int maxAttempts, long windowMillis) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token de acesso não pode ser vazio.");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts deve ser >= 1.");
        }
        if (windowMillis < 1) {
            throw new IllegalArgumentException("windowMillis deve ser >= 1.");
        }
        this.expectedHash = sha256(token);
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.windowMillis = windowMillis;
    }

    public LoginResult login(String providedToken, String clientIp) {
        String ip = clientIp == null || clientIp.isBlank() ? "desconhecido" : clientIp;
        long now = clock.millis();
        boolean[] limited = {false};

        attempts.compute(ip, (key, old) -> {
            Attempt current = old;
            if (current == null || now >= current.resetAtMillis) {
                current = new Attempt(now + windowMillis, 0);
            }
            if (current.count >= maxAttempts) {
                limited[0] = true;
                return current;
            }
            current.count++;
            return current;
        });

        if (limited[0]) {
            return LoginResult.RATE_LIMITED;
        }

        byte[] receivedHash = sha256(providedToken == null ? "" : providedToken);
        if (MessageDigest.isEqual(expectedHash, receivedHash)) {
            attempts.remove(ip);
            return LoginResult.OK;
        }
        return LoginResult.INVALID;
    }

    /** Remove entradas antigas para manter o mapa pequeno em execução longa. */
    public void cleanup() {
        long now = clock.millis();
        attempts.entrySet().removeIf(entry -> now >= entry.getValue().resetAtMillis);
        if (attempts.size() > 2_000) {
            attempts.clear();
        }
    }

    public int trackedClients() {
        return attempts.size();
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível nesta JVM.", e);
        }
    }

    private static final class Attempt {
        private final long resetAtMillis;
        private int count;

        private Attempt(long resetAtMillis, int count) {
            this.resetAtMillis = resetAtMillis;
            this.count = count;
        }
    }
}
