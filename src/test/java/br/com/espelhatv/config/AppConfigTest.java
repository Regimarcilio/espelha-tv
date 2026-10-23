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
}
