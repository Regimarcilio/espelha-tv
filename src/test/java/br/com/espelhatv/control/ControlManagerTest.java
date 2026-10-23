package br.com.espelhatv.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.espelhatv.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ControlManagerTest {

    @Test
    void grantsControlToOnlyOneSession() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        AtomicInteger releases = new AtomicInteger();
        ControlManager control = new ControlManager(clock, Duration.ofSeconds(30), releases::incrementAndGet);

        ControlManager.ClaimResult first = control.claim("sessao-a");
        assertTrue(first.granted());
        assertFalse(first.token().isBlank());
        assertTrue(control.isController("sessao-a", first.token()));
        assertTrue(control.isControllerSession("sessao-a"));

        ControlManager.ClaimResult second = control.claim("sessao-b");
        assertFalse(second.granted());
        assertFalse(control.isController("sessao-b", first.token()));
        assertEquals(0, releases.get());

        assertTrue(control.release("sessao-a", first.token()));
        assertEquals(1, releases.get());
        assertFalse(control.isActive());
        assertTrue(control.claim("sessao-b").granted());
    }

    @Test
    void sameSessionCanRetrieveToken() {
        ControlManager control = new ControlManager(
                new MutableClock(Instant.EPOCH), Duration.ofSeconds(30), () -> { });
        ControlManager.ClaimResult first = control.claim("sessao-a");
        ControlManager.ClaimResult again = control.claim("sessao-a");
        assertTrue(again.granted());
        assertEquals(first.token(), again.token());
    }

    @Test
    void wrongTokenCannotTouchOrRelease() {
        MutableClock clock = new MutableClock(Instant.EPOCH);
        AtomicInteger releases = new AtomicInteger();
        ControlManager control = new ControlManager(clock, Duration.ofSeconds(30), releases::incrementAndGet);
        ControlManager.ClaimResult claim = control.claim("sessao-a");

        assertFalse(control.touch("sessao-a", "errado"));
        assertFalse(control.release("sessao-a", "errado"));
        assertTrue(control.isActive());
        assertTrue(control.touch("sessao-a", claim.token()));
        assertEquals(0, releases.get());
    }

    @Test
    void idleControlIsReleasedAndCallbackRunsOnce() {
        MutableClock clock = new MutableClock(Instant.EPOCH);
        AtomicInteger releases = new AtomicInteger();
        ControlManager control = new ControlManager(clock, Duration.ofSeconds(10), releases::incrementAndGet);
        control.claim("sessao-a");

        clock.advance(Duration.ofSeconds(9));
        assertFalse(control.cleanupIdle());
        clock.advance(Duration.ofSeconds(1));
        assertTrue(control.cleanupIdle());
        assertEquals(1, releases.get());
        assertFalse(control.isActive());
        assertFalse(control.cleanupIdle());
    }

    @Test
    void releaseBySessionAndForceReleaseAreSafe() {
        MutableClock clock = new MutableClock(Instant.EPOCH);
        AtomicInteger releases = new AtomicInteger();
        ControlManager control = new ControlManager(clock, Duration.ofSeconds(10), releases::incrementAndGet);

        control.releaseBySession("inexistente");
        assertEquals(0, releases.get());
        control.forceRelease();
        assertEquals(0, releases.get());

        control.claim("sessao-a");
        control.releaseBySession("outra");
        assertTrue(control.isActive());
        control.releaseBySession("sessao-a");
        assertEquals(1, releases.get());

        control.claim("sessao-b");
        control.forceRelease();
        assertEquals(2, releases.get());
    }

    @Test
    void tokensAreRandomAndLongEnough() {
        ControlManager control = new ControlManager(
                new MutableClock(Instant.EPOCH), Duration.ofSeconds(10), () -> { });
        String first = control.claim("a").token();
        control.forceRelease();
        String second = control.claim("b").token();
        assertNotEquals(first, second);
        assertTrue(first.length() >= 30);
    }
}
