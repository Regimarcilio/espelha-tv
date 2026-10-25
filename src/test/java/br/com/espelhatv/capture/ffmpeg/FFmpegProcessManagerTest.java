package br.com.espelhatv.capture.ffmpeg;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.espelhatv.config.AppConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FFmpegProcessManagerTest {

    private static AppConfig config(String capture, String vaapiDevice) {
        return new AppConfig(8080, "127.0.0.1", "token", false, 10, 1280, 720, 0.6f,
                300, 720, true, 0, false,
                true, "127.0.0.1", 5004, 1366, 768, 45, "ultrafast", 6000, 2,
                "http://127.0.0.1:8889/stream/whip", "/tmp/ffmpeg-whip", vaapiDevice, capture);
    }

    @Test
    void x11grabVaapiPipeline() throws Exception {
        Path fakeDevice = Files.createTempFile("vaapi", ".dev");
        try {
            String command = FFmpegProcessManager.buildPipelineCommand(
                    config("x11grab", fakeDevice.toString()), true);

            assertTrue(command.contains("-f x11grab"));
            assertTrue(command.contains("-vaapi_device"));
            assertTrue(command.contains("h264_vaapi"));
            assertTrue(command.contains("scale_vaapi=w=1366:h=768:format=nv12"));
            assertTrue(command.contains("-f mpegts pipe:1"));
            assertTrue(command.contains("-c copy -f whip"));
            assertTrue(command.contains("http://127.0.0.1:8889/stream/whip"));
        } finally {
            Files.deleteIfExists(fakeDevice);
        }
    }

    @Test
    void stdinUsesRawvideoAndLibx264() {
        String command = FFmpegProcessManager.buildPipelineCommand(config("stdin", ""), false);

        assertTrue(command.contains("-f rawvideo -pix_fmt bgr24 -s 1366x768"));
        assertTrue(command.contains("libx264"));
        assertTrue(command.contains("-f mpegts pipe:1"));
        assertTrue(command.contains("-f whip"));
    }

    @Test
    void x11grabWithoutVaapiUsesLibx264() {
        String command = FFmpegProcessManager.buildPipelineCommand(config("x11grab", ""), true);

        assertTrue(command.contains("-f x11grab"));
        assertTrue(command.contains("scale=1366:768,format=yuv420p"));
        assertTrue(command.contains("libx264"));
        assertFalse(command.contains("h264_vaapi"));
    }

    @Test
    void missingVaapiDeviceFallsBackToLibx264() {
        String command = FFmpegProcessManager.buildPipelineCommand(
                config("stdin", "/caminho/inexistente/renderD128"), false);

        assertFalse(command.contains("-vaapi_device"));
        assertTrue(command.contains("libx264"));
    }
}
