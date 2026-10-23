package br.com.espelhatv.capture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class CaptureServiceTest {

    @Test
    @Timeout(10)
    void capturesAndPublishesOnlyForViewers() throws InterruptedException {
        FrameHub hub = new FrameHub();
        CaptureService capture = new CaptureService(
                new DemoScreenSource(320, 240), hub, new JpegEncoder(), 10, 320, 240, 0.5f);
        try {
            capture.start();
            Thread.sleep(250);
            assertNull(capture.lastFrame(), "sem espectador não deve capturar");

            BlockingQueue<Frame> queue = hub.subscribe();
            Frame frame = queue.poll(4, TimeUnit.SECONDS);
            assertNotNull(frame);
            assertEquals(320, frame.width());
            assertEquals(240, frame.height());
            assertArrayEquals(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
                    new byte[]{frame.jpeg()[0], frame.jpeg()[1], frame.jpeg()[2]});
            assertEquals("Demo 320x240", capture.sourceDescription());
            assertNull(capture.lastError());
        } finally {
            capture.close();
        }
    }

    @Test
    @Timeout(10)
    void startAndCloseAreIdempotent() {
        CaptureService capture = new CaptureService(
                new DemoScreenSource(320, 240), new FrameHub(), new JpegEncoder(), 5, 320, 240, 0.4f);
        capture.start();
        capture.start();
        capture.close();
        capture.close();
    }
}
