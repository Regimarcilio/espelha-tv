package br.com.espelhatv.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Sessão de navegador autenticada. O ID é um segredo aleatório entregue em
 * cookie HttpOnly; nunca é exibido em logs.
 */
public final class Session {
    private final String id;
    private final Instant createdAt;
    private final Duration ttl;
    private volatile Instant lastSeen;
    private volatile Instant expiresAt;

    Session(String id, Duration ttl, Instant now) {
        this.id = Objects.requireNonNull(id, "id");
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        this.createdAt = Objects.requireNonNull(now, "now");
        this.lastSeen = now;
        this.expiresAt = now.plus(ttl);
    }

    public String id() {
        return id;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant lastSeen() {
        return lastSeen;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Duration ttl() {
        return ttl;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public void touch(Instant now) {
        this.lastSeen = now;
        this.expiresAt = now.plus(ttl);
    }

    @Override
    public String toString() {
        return "Session[createdAt=" + createdAt + ", lastSeen=" + lastSeen + ", expiresAt=" + expiresAt + ']';
    }
}
