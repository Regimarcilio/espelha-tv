package br.com.espelhatv.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AppConfigTest {

    @Test
    void parseFlagsAndValues() {
        AppConfig config = AppConfig.parse(new String[]{
                "--token", "segredo",
                "--demo",
                "--no-cursor",
                "--fps=5",
                "--width=640",
                "--height=360",
                "--quality=0,75",
                "--port=9090",
                "--bind=127.0.0.1"
        });

        assertEquals(9090, config.port());
        assertEquals("127.0.0.1", config.bind());
        assertEquals("segredo", config.token());
        assertFalse(config.tokenGenerated());
        assertEquals(5, config.fps());
        assertEquals(640, config.maxWidth());
        assertEquals(360, config.maxHeight());
        assertEquals(0.75f, config.jpegQuality(), 0.0001f);
        assertFalse(config.drawCursor());
        assertTrue(config.demo());
    }

    @Test
    void generatesTokenWithoutLeakingItInToString() {
        AppConfig config = AppConfig.parse(new String[]{"--token=super-secreto"});
        assertTrue(config.toString().contains("<definido>"));
        assertFalse(config.toString().contains("super-secreto"));
    }

    @Test
    void generatedTokenHasExpectedShape() {
        String token = AppConfig.generateToken();
        assertEquals(10, token.length());
        for (char c : token.toCharArray()) {
            assertTrue("ABCDEFGHJKLMNPQRSTUVWXYZ23456789".indexOf(c) >= 0);
        }
        assertFalse(token.equals(AppConfig.generateToken()));
    }

    @Test
    void rejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class,
                () -> AppConfig.parse(new String[]{"--token=x", "--fps=0"}));
        assertThrows(IllegalArgumentException.class,
                () -> AppConfig.parse(new String[]{"--token=x", "--width=100"}));
        assertThrows(IllegalArgumentException.class,
                () -> AppConfig.parse(new String[]{"--token=x", "--quality=2"}));
        assertThrows(IllegalArgumentException.class,
                () -> AppConfig.parse(new String[]{"--token=x", "--port=0"}));
        assertThrows(IllegalArgumentException.class,
                () -> AppConfig.parse(new String[]{"--token=x", "--unknown=1"}));
    }

    @Test
    void helpStopsParsing() {
        assertThrows(AppConfig.HelpRequested.class,
                () -> AppConfig.parse(new String[]{"--help"}));
    }

    @Test
    void parsesFfmpegWhipOptions() {
        AppConfig config = AppConfig.parse(new String[]{
                "--token=x",
                "--ffmpeg-enabled=true",
                "--ffmpeg-whip-url=http://127.0.0.1:8889/stream/whip",
                "--ffmpeg-whip-bin=/tmp/ffmpeg-whip",
                "--ffmpeg-vaapi-device=",
                "--ffmpeg-capture=stdin",
                "--ffmpeg-fps=45",
                "--ffmpeg-width=1366",
                "--ffmpeg-height=768"
        });

        assertTrue(config.ffmpegEnabled());
        assertEquals("http://127.0.0.1:8889/stream/whip", config.ffmpegWhipUrl());
        assertEquals("/tmp/ffmpeg-whip", config.ffmpegWhipBin());
        assertEquals("", config.ffmpegVaapiDevice());
        assertEquals("stdin", config.ffmpegCapture());
        assertEquals(45, config.ffmpegFps());
        assertEquals(1366, config.ffmpegWidth());
        assertEquals(768, config.ffmpegHeight());
    }

    @Test
    void ffmpegDefaultsUseX11grab() {
        AppConfig config = AppConfig.parse(new String[]{"--token=x"});
        assertEquals("x11grab", config.ffmpegCapture());
        assertEquals("/dev/dri/renderD128", config.ffmpegVaapiDevice());
        assertEquals("tools/ffmpeg-whip", config.ffmpegWhipBin());
    }

    @Test
    void rejectsInvalidFfmpegCaptureMode() {
        assertThrows(IllegalArgumentException.class,
                () -> AppConfig.parse(new String[]{
                        "--token=x", "--ffmpeg-enabled=true", "--ffmpeg-capture=janela"}));
    }
}
