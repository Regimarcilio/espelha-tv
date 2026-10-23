package br.com.espelhatv.input;

import java.awt.AWTException;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Injeção real de mouse/teclado via java.awt.Robot.
 *
 * Limitação conhecida: em Wayland o Robot pode não ter permissão para capturar
 * ou injetar eventos. O modo --demo existe para validar o fluxo sem isso.
 */
public final class RobotInputSink implements InputSink {
    private final Robot robot;
    private final Rectangle bounds;
    private final Set<Integer> pressedKeys = ConcurrentHashMap.newKeySet();
    private final Set<Integer> pressedButtons = ConcurrentHashMap.newKeySet();

    public RobotInputSink() throws AWTException {
        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        Rectangle deviceBounds = device.getDefaultConfiguration().getBounds();
        this.bounds = new Rectangle(0, 0, deviceBounds.width, deviceBounds.height);
        this.robot = new Robot(device);
    }

    @Override
    public synchronized void mouseMove(int x, int y) {
        robot.mouseMove(clamp(x, 0, bounds.width - 1), clamp(y, 0, bounds.height - 1));
    }

    @Override
    public synchronized void mouseMoveRelative(int dx, int dy) {
        PointerInfo info = MouseInfo.getPointerInfo();
        if (info == null) {
            return;
        }
        Point point = info.getLocation();
        int x = clamp(point.x + dx, 0, bounds.width - 1);
        int y = clamp(point.y + dy, 0, bounds.height - 1);
        robot.mouseMove(x, y);
    }

    @Override
    public synchronized void mousePress(int button) {
        int mask = buttonMask(button);
        if (pressedButtons.add(button)) {
            robot.mousePress(mask);
        }
    }

    @Override
    public synchronized void mouseRelease(int button) {
        int mask = buttonMask(button);
        if (pressedButtons.remove(button)) {
            robot.mouseRelease(mask);
        }
    }

    @Override
    public synchronized void mouseWheel(int amount) {
        robot.mouseWheel(amount);
    }

    @Override
    public synchronized void keyPress(int keyCode) {
        if (pressedKeys.add(keyCode)) {
            robot.keyPress(keyCode);
        }
    }

    @Override
    public synchronized void keyRelease(int keyCode) {
        if (pressedKeys.remove(keyCode)) {
            robot.keyRelease(keyCode);
        }
    }

    @Override
    public synchronized void typeText(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        try {
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '\n' || c == '\r') {
                    tap(KeyEvent.VK_ENTER);
                } else if (c == '\t') {
                    tap(KeyEvent.VK_TAB);
                } else if (c >= 32 && c <= 126) {
                    KeyStroke stroke = asciiStroke(c);
                    if (stroke != null) {
                        if (stroke.shift) {
                            robot.keyPress(KeyEvent.VK_SHIFT);
                        }
                        robot.keyPress(stroke.code);
                        robot.keyRelease(stroke.code);
                        if (stroke.shift) {
                            robot.keyRelease(KeyEvent.VK_SHIFT);
                        }
                    }
                }
            }
        } catch (RuntimeException e) {
            releaseAll();
            throw e;
        }
    }

    @Override
    public synchronized void releaseAll() {
        List<Integer> keys = new ArrayList<>(pressedKeys);
        for (Integer key : keys) {
            try {
                robot.keyRelease(key);
            } catch (RuntimeException ignored) {
                // Melhor esforço: o controle será liberado de qualquer forma.
            }
        }
        pressedKeys.clear();

        List<Integer> buttons = new ArrayList<>(pressedButtons);
        for (Integer button : buttons) {
            try {
                robot.mouseRelease(buttonMask(button));
            } catch (RuntimeException ignored) {
                // idem
            }
        }
        pressedButtons.clear();
    }

    @Override
    public Rectangle screenBounds() {
        return new Rectangle(bounds);
    }

    @Override
    public String description() {
        return "Robot (tela " + bounds.width + "x" + bounds.height + ")";
    }

    private void tap(int keyCode) {
        robot.keyPress(keyCode);
        robot.keyRelease(keyCode);
    }

    private static int buttonMask(int button) {
        return switch (button) {
            case 1 -> InputEvent.BUTTON1_DOWN_MASK;
            case 2 -> InputEvent.BUTTON2_DOWN_MASK;
            case 3 -> InputEvent.BUTTON3_DOWN_MASK;
            default -> throw new IllegalArgumentException("Botão de mouse inválido: " + button);
        };
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static KeyStroke asciiStroke(char c) {
        if (c >= 'a' && c <= 'z') {
            return new KeyStroke(KeyEvent.VK_A + (c - 'a'), false);
        }
        if (c >= 'A' && c <= 'Z') {
            return new KeyStroke(KeyEvent.VK_A + (c - 'A'), true);
        }
        if (c >= '0' && c <= '9') {
            return new KeyStroke(KeyEvent.VK_0 + (c - '0'), false);
        }
        return switch (c) {
            case ' ' -> new KeyStroke(KeyEvent.VK_SPACE, false);
            case '!' -> new KeyStroke(KeyEvent.VK_1, true);
            case '@' -> new KeyStroke(KeyEvent.VK_2, true);
            case '#' -> new KeyStroke(KeyEvent.VK_3, true);
            case '$' -> new KeyStroke(KeyEvent.VK_4, true);
            case '%' -> new KeyStroke(KeyEvent.VK_5, true);
            case '^' -> new KeyStroke(KeyEvent.VK_6, true);
            case '&' -> new KeyStroke(KeyEvent.VK_7, true);
            case '*' -> new KeyStroke(KeyEvent.VK_8, true);
            case '(' -> new KeyStroke(KeyEvent.VK_9, true);
            case ')' -> new KeyStroke(KeyEvent.VK_0, true);
            case '-' -> new KeyStroke(KeyEvent.VK_MINUS, false);
            case '_' -> new KeyStroke(KeyEvent.VK_MINUS, true);
            case '=' -> new KeyStroke(KeyEvent.VK_EQUALS, false);
            case '+' -> new KeyStroke(KeyEvent.VK_EQUALS, true);
            case '[' -> new KeyStroke(KeyEvent.VK_OPEN_BRACKET, false);
            case '{' -> new KeyStroke(KeyEvent.VK_OPEN_BRACKET, true);
            case ']' -> new KeyStroke(KeyEvent.VK_CLOSE_BRACKET, false);
            case '}' -> new KeyStroke(KeyEvent.VK_CLOSE_BRACKET, true);
            case '\\' -> new KeyStroke(KeyEvent.VK_BACK_SLASH, false);
            case '|' -> new KeyStroke(KeyEvent.VK_BACK_SLASH, true);
            case ';' -> new KeyStroke(KeyEvent.VK_SEMICOLON, false);
            case ':' -> new KeyStroke(KeyEvent.VK_SEMICOLON, true);
            case '\'' -> new KeyStroke(KeyEvent.VK_QUOTE, false);
            case '"' -> new KeyStroke(KeyEvent.VK_QUOTE, true);
            case ',' -> new KeyStroke(KeyEvent.VK_COMMA, false);
            case '<' -> new KeyStroke(KeyEvent.VK_COMMA, true);
            case '.' -> new KeyStroke(KeyEvent.VK_PERIOD, false);
            case '>' -> new KeyStroke(KeyEvent.VK_PERIOD, true);
            case '/' -> new KeyStroke(KeyEvent.VK_SLASH, false);
            case '?' -> new KeyStroke(KeyEvent.VK_SLASH, true);
            case '`' -> new KeyStroke(KeyEvent.VK_BACK_QUOTE, false);
            case '~' -> new KeyStroke(KeyEvent.VK_BACK_QUOTE, true);
            default -> null;
        };
    }

    private record KeyStroke(int code, boolean shift) {
    }
}
