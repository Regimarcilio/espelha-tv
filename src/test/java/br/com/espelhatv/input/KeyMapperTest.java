package br.com.espelhatv.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.KeyEvent;
import org.junit.jupiter.api.Test;

class KeyMapperTest {

    @Test
    void mapsCommonBrowserCodes() {
        assertEquals(KeyEvent.VK_ENTER, KeyMapper.toKeyCode("Enter"));
        assertEquals(KeyEvent.VK_ESCAPE, KeyMapper.toKeyCode("Escape"));
        assertEquals(KeyEvent.VK_UP, KeyMapper.toKeyCode("ArrowUp"));
        assertEquals(KeyEvent.VK_A, KeyMapper.toKeyCode("KeyA"));
        assertEquals(KeyEvent.VK_0, KeyMapper.toKeyCode("Digit0"));
        assertEquals(KeyEvent.VK_NUMPAD7, KeyMapper.toKeyCode("Numpad7"));
        assertEquals(KeyEvent.VK_F12, KeyMapper.toKeyCode("F12"));
        assertEquals(KeyEvent.VK_COMMA, KeyMapper.toKeyCode("Comma"));
    }

    @Test
    void acceptsSingleCharacterFallback() {
        assertTrue(KeyMapper.toKeyCode("a") >= 0);
        assertTrue(KeyMapper.toKeyCode("ç") >= 0);
    }

    @Test
    void rejectsUnknownAndBlankCodes() {
        assertEquals(-1, KeyMapper.toKeyCode(null));
        assertEquals(-1, KeyMapper.toKeyCode(""));
        assertEquals(-1, KeyMapper.toKeyCode("UnknownKey"));
        assertEquals(-1, KeyMapper.toKeyCode("MediaPlayPause"));
    }

    @Test
    void hasBroadKeyCoverage() {
        assertTrue(KeyMapper.mappedKeyCount() >= 80);
    }
}
