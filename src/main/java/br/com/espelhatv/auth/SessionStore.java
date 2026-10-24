package br.com.espelhatv.auth;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Store de sessões em memória. A aplicação é single-node por definição (LAN
 * doméstica), então não há necessidade de Redis ou banco.
 */
public final class SessionStore {
    private static final int TOKEN_BYTES = 32;

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;
    private final Duration ttl;

    public SessionStore(Clock clock, Duration ttl) {
        this.clock = clock;
        this.ttl = ttl;
    }

    public Session create() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String id = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Session session = new Session(id, ttl, clock.instant());
        sessions.put(id, session);
        return session;
    }

    /**
     * Busca e renova a sessão (sliding expiration). Retorna null se não existir
     * ou se já tiver expirado.
     */
    public Session get(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        Session session = sessions.get(id);
        if (session == null) {
            return null;
        }
        Instant now = clock.instant();
        if (session.isExpired(now)) {
            sessions.remove(id, session);
            return null;
        }
        session.touch(now);
        return session;
    }

    public void remove(String id) {
        if (id != null) {
            sessions.remove(id);
        }
    }

    /** Remove expiradas e devolve os IDs removidos para liberar controles. */
    public List<String> cleanup() {
        Instant now = clock.instant();
        List<String> removed = new ArrayList<>();
        sessions.forEach((id, session) -> {
            if (session.isExpired(now) && sessions.remove(id, session)) {
                removed.add(id);
            }
        });
        return removed;
    }

    public int size() {
        return sessions.size();
    }
}
