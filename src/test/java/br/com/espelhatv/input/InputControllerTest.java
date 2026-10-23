package br.com.espelhatv.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.espelhatv.support.RecordingInputSink;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InputControllerTest {

    private RecordingInputSink sink;
    private InputController controller;

    @BeforeEach
    void setUp() {
        sink = new RecordingInputSink();
        controller = new InputController(sink);
    }

    @Test
    void movesUsingNormalizedCoordinates() {
        assertTrue(controller.handle(map("type", "move", "x", "0", "y", "0")));
        assertTrue(controller.handle(map("type", "move", "x", "1", "y", "1")));
        assertTrue(controller.handle(map("type", "move", "x", "0,5", "y", "0,5")));

        assertEquals("move:0,0", sink.events.get(0));
        assertEquals("move:99,49", sink.events.get(1));
        assertEquals("move:50,25", sink.events.get(2));
    }

    @Test
    void rejectsCoordinatesOutsideUnitRange() {
        assertThrows(IllegalArgumentException.class,
                () -> controller.handle(map("type", "move", "x", "-0.1", "y", "0")));
        assertThrows(IllegalArgumentException.class,
                () -> controller.handle(map("type", "move", "x", "NaN", "y", "0")));
        assertThrows(IllegalArgumentException.class,
                () -> controller.handle(map("type", "move", "x", "0", "y", "1.1")));
    }

    @Test
    void handlesRelativeMovementAndClampedRanges() {
        assertTrue(controller.handle(map("type", "moveRelative", "dx", "-10", "dy", "20")));
        assertEquals("moveRelative:-10,20", sink.events.get(0));
        assertThrows(IllegalArgumentException.class,
                () -> controller.handle(map("type", "moveRelative", "dx", "501", "dy", "0")));
    }

    @Test
    void handlesClicksAndWheel() {
        assertTrue(controller.handle(map("type", "click", "button", "2", "down", "true")));
        assertTrue(controller.handle(map("type", "click", "button", "2", "down", "false")));
        assertTrue(controller.handle(map("type", "wheel", "delta", "-3")));

        assertEquals("press:2", sink.events.get(0));
        assertEquals("release:2", sink.events.get(1));
        assertEquals("wheel:-3", sink.events.get(2));
        assertThrows(IllegalArgumentException.class,
                () -> controller.handle(map("type", "click", "button", "4", "down", "true")));
    }

    @Test
    void handlesKeysAndUnknownCodes() {
        assertTrue(controller.handle(map("type", "key", "code", "ArrowLeft", "down", "true")));
        assertTrue(controller.handle(map("type", "key", "code", "ArrowLeft", "down", "false")));
        assertFalse(controller.handle(map("type", "key", "code", "MediaPlayPause", "down", "true")));

        assertEquals("keyPress:37", sink.events.get(0));
        assertEquals("keyRelease:37", sink.events.get(1));
        assertEquals(2, sink.events.size());
    }

    @Test
    void typesTextAndEnforcesLimit() {
        assertTrue(controller.handle(map("type", "text", "text", "olá")));
        assertEquals("text:olá", sink.events.get(0));

        assertThrows(IllegalArgumentException.class,
                () -> controller.handle(map("type", "text", "text", "x".repeat(129))));
        assertThrows(IllegalArgumentException.class,
                () -> controller.handle(map("type", "desconhecido")));
        assertThrows(IllegalArgumentException.class,
                () -> controller.handle(map("type", "move", "x", "0")));
    }

    @Test
    void releaseAllIsExplicitAndIdempotent() {
        assertTrue(controller.handle(map("type", "releaseAll")));
        controller.releaseAll();
        assertEquals(2, sink.releaseAllCount);
        assertEquals("sink-de-teste", controller.description());
        assertEquals(100, controller.screenBounds().width);
    }

    private static Map<String, String> map(String... pairs) {
        Map<String, String> result = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            result.put(pairs[i], pairs[i + 1]);
        }
        return result;
    }
}
