package br.com.espelhatv.capture.ffmpeg;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.espelhatv.capture.Frame;
import br.com.espelhatv.capture.FrameHub;
import br.com.espelhatv.config.AppConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

class MjpegTapTest {

    private static AppConfig config() {
        return new AppConfig(8080, "127.0.0.1", "token", false, 30, 1366, 768, 0.6f,
                300, 720, true, 0, false,
                true, "127.0.0.1", 5004, 1366, 768, 30, "ultrafast", 6000, 2,
                "http://127.0.0.1:8889/stream/whip", "/tmp/ffmpeg-whip", "", "x11grab");
    }

    @Test
    void buildsFfmpegMjpegCommand() {
        List<String> command = MjpegTap.buildCommand(config());

        assertEquals("ffmpeg", command.get(0));
        assertTrue(command.contains("-f"));
        assertTrue(command.contains("x11grab"));
        assertTrue(command.contains("-framerate"));
        assertTrue(command.contains("30"));
        assertTrue(command.contains("scale=1366:768,format=yuvj420p"));
        assertTrue(command.contains("mjpeg"));
        assertTrue(command.contains("-q:v"));
        assertTrue(command.contains("10"));
        assertTrue(command.contains("image2pipe"));
        assertEquals("pipe:1", command.get(command.size() - 1));
    }

    @Test
    void usesConfiguredFpsAndDrawCursor() {
        List<String> command = MjpegTap.buildCommand(config());

        int framerate = command.indexOf("-framerate");
        assertEquals("30", command.get(framerate + 1));
        int drawMouse = command.indexOf("-draw_mouse");
        assertEquals("1", command.get(drawMouse + 1));
    }

    @Test
    void publishesFramesFromInjectedProcess() throws Exception {
        FrameHub hub = new FrameHub();
        List<String> command = List.of("sh", "-c",
                "printf '\\377\\330\\001\\002\\377\\331'; sleep 5");
        MjpegTap tap = new MjpegTap(config(), hub, command);

        tap.start();
        Frame frame = awaitFrame(hub, 1, 3_000);

        assertNotNull(frame);
        assertArrayEquals(new byte[]{(byte) 0xFF, (byte) 0xD8, 0x01, 0x02, (byte) 0xFF, (byte) 0xD9},
                frame.jpeg());
        assertEquals(1366, frame.width());
        assertEquals(768, frame.height());
        tap.close();
        tap.close();
    }

    @Test
    void restartsProcessAfterUnexpectedExit() throws Exception {
        FrameHub hub = new FrameHub();
        List<String> command = List.of("sh", "-c", "printf '\\377\\330\\001\\002\\377\\331'");
        MjpegTap tap = new MjpegTap(config(), hub, command);

        tap.start();
        Frame frame = awaitFrame(hub, 2, 5_000);

        assertNotNull(frame);
        assertTrue(frame.sequence() >= 2);
        tap.close();
    }

    private static Frame awaitFrame(FrameHub hub, long minSequence, long timeoutMillis)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            Frame frame = hub.latest();
            if (frame != null && frame.sequence() >= minSequence) {
                return frame;
            }
            Thread.sleep(25);
        }
        return hub.latest();
    }
}
