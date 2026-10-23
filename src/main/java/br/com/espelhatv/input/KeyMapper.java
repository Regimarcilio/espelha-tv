package br.com.espelhatv.input;

import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;

/**
 * Traduz os valores de KeyboardEvent.code do navegador para códigos AWT.
 * A digitação de texto é tratada separadamente por RobotInputSink.
 */
public final class KeyMapper {
    private static final Map<String, Integer> KEYS = new HashMap<>();

    static {
        put("Enter", KeyEvent.VK_ENTER);
        put("NumpadEnter", KeyEvent.VK_ENTER);
        put("Escape", KeyEvent.VK_ESCAPE);
        put("Backspace", KeyEvent.VK_BACK_SPACE);
        put("Tab", KeyEvent.VK_TAB);
        put("Space", KeyEvent.VK_SPACE);
        put("Delete", KeyEvent.VK_DELETE);
        put("Insert", KeyEvent.VK_INSERT);
        put("Home", KeyEvent.VK_HOME);
        put("End", KeyEvent.VK_END);
        put("PageUp", KeyEvent.VK_PAGE_UP);
        put("PageDown", KeyEvent.VK_PAGE_DOWN);
        put("ArrowUp", KeyEvent.VK_UP);
        put("ArrowDown", KeyEvent.VK_DOWN);
        put("ArrowLeft", KeyEvent.VK_LEFT);
        put("ArrowRight", KeyEvent.VK_RIGHT);
        put("ShiftLeft", KeyEvent.VK_SHIFT);
        put("ShiftRight", KeyEvent.VK_SHIFT);
        put("ControlLeft", KeyEvent.VK_CONTROL);
        put("ControlRight", KeyEvent.VK_CONTROL);
        put("AltLeft", KeyEvent.VK_ALT);
        put("AltRight", KeyEvent.VK_ALT);
        put("MetaLeft", KeyEvent.VK_META);
        put("MetaRight", KeyEvent.VK_META);
        put("CapsLock", KeyEvent.VK_CAPS_LOCK);
        put("NumLock", KeyEvent.VK_NUM_LOCK);
        put("ScrollLock", KeyEvent.VK_SCROLL_LOCK);
        put("Pause", KeyEvent.VK_PAUSE);
        put("PrintScreen", KeyEvent.VK_PRINTSCREEN);
        put("ContextMenu", KeyEvent.VK_CONTEXT_MENU);
        put("Minus", KeyEvent.VK_MINUS);
        put("Equal", KeyEvent.VK_EQUALS);
        put("BracketLeft", KeyEvent.VK_OPEN_BRACKET);
        put("BracketRight", KeyEvent.VK_CLOSE_BRACKET);
        put("Backslash", KeyEvent.VK_BACK_SLASH);
        put("Semicolon", KeyEvent.VK_SEMICOLON);
        put("Quote", KeyEvent.VK_QUOTE);
        put("Comma", KeyEvent.VK_COMMA);
        put("Period", KeyEvent.VK_PERIOD);
        put("Slash", KeyEvent.VK_SLASH);
        put("Backquote", KeyEvent.VK_BACK_QUOTE);
        put("NumpadAdd", KeyEvent.VK_ADD);
        put("NumpadSubtract", KeyEvent.VK_SUBTRACT);
        put("NumpadMultiply", KeyEvent.VK_MULTIPLY);
        put("NumpadDivide", KeyEvent.VK_DIVIDE);
        put("NumpadDecimal", KeyEvent.VK_DECIMAL);

        for (char c = 'A'; c <= 'Z'; c++) {
            put("Key" + c, KeyEvent.VK_A + (c - 'A'));
        }
        for (int i = 0; i <= 9; i++) {
            put("Digit" + i, KeyEvent.VK_0 + i);
            put("Numpad" + i, KeyEvent.VK_NUMPAD0 + i);
        }
        for (int i = 1; i <= 12; i++) {
            put("F" + i, KeyEvent.VK_F1 + (i - 1));
        }
    }

    private KeyMapper() {
    }

    private static void put(String code, int keyCode) {
        KEYS.put(code, keyCode);
    }

    /** @return código AWT ou -1 se não houver mapeamento. */
    public static int toKeyCode(String code) {
        if (code == null || code.isBlank()) {
            return -1;
        }
        Integer mapped = KEYS.get(code);
        if (mapped != null) {
            return mapped;
        }
        if (code.length() == 1) {
            int keyCode = KeyEvent.getExtendedKeyCodeForChar(code.charAt(0));
            return keyCode == KeyEvent.VK_UNDEFINED ? -1 : keyCode;
        }
        return -1;
    }

    public static int mappedKeyCount() {
        return KEYS.size();
    }
}
