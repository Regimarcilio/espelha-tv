package br.com.espelhatv.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.espelhatv.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class SessionStoreTest {

    @Test
    void createsOpaqueSessionAndRenewsOnAccess() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        SessionStore store = new SessionStore(clock, Duration.ofMinutes(10));

        Session session = store.create();
        assertTrue(session.id().length() >= 40);
        assertEquals(1, store.size());
        assertNotNull(store.get(session.id()));

        clock.advance(Duration.ofMinutes(9));
        assertNotNull(store.get(session.id()));
        clock.advance(Duration.ofMinutes(9));
        assertNotNull(store.get(session.id()));
    }

    @Test
    void expiresWithoutActivity() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        SessionStore store = new SessionStore(clock, Duration.ofMinutes(5));
        Session session = store.create();

        clock.advance(Duration.ofMinutes(5));
        assertNull(store.get(session.id()));
        assertEquals(0, store.size());
    }

    @Test
    void cleanupReturnsRemovedIds() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        SessionStore store = new SessionStore(clock, Duration.ofMinutes(1));
        Session first = store.create();
        Session second = store.create();

        clock.advance(Duration.ofSeconds(61));
        List<String> removed = store.cleanup();
        assertEquals(2, removed.size());
        assertTrue(removed.contains(first.id()));
        assertTrue(removed.contains(second.id()));
        assertEquals(0, store.size());
    }

    @Test
    void removeAndMissingIdsAreSafe() {
        SessionStore store = new SessionStore(new MutableClock(Instant.EPOCH), Duration.ofMinutes(1));
        Session session = store.create();
        store.remove(session.id());
        assertNull(store.get(session.id()));
        assertNull(store.get(null));
        store.remove(null);
        assertEquals(0, store.size());
    }
}
