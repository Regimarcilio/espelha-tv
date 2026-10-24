package br.com.espelhatv.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import org.junit.jupiter.api.Test;

class FrameHubTest {

    @Test
    void sendsLatestFrameAndKeepsOnlyOnePending() throws InterruptedException {
        FrameHub hub = new FrameHub();
        BlockingQueue<Frame> queue = hub.subscribe();
        assertEquals(1, hub.viewerCount());

        Frame first = frame(1);
        Frame second = frame(2);
        hub.publish(first);
        hub.publish(second);

        assertSame(second, hub.latest());
        assertSame(second, queue.poll());
        assertTrue(queue.isEmpty());
    }

    @Test
    void newSubscriberReceivesLatestImmediately() {
        FrameHub hub = new FrameHub();
        Frame frame = frame(7);
        hub.publish(frame);

        BlockingQueue<Frame> queue = hub.subscribe();
        assertSame(frame, queue.poll());
    }

    @Test
    void unsubscribeAndNullPublishAreSafe() {
        FrameHub hub = new FrameHub();
        BlockingQueue<Frame> queue = hub.subscribe();
        hub.unsubscribe(queue);
        assertEquals(0, hub.viewerCount());
        hub.unsubscribe(null);
        hub.publish(null);
        assertNull(hub.latest());
    }

    private static Frame frame(long sequence) {
        return new Frame(new byte[]{1, 2, 3}, sequence, 10, 10, Instant.EPOCH);
    }
}
