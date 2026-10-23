package br.com.espelhatv.input;

import java.awt.Rectangle;
import java.util.Map;

/**
 * Valida e traduz o formulário /api/input em chamadas ao InputSink.
 * Não conhece HTTP nem autenticação; isso fica no HttpApi.
 */
public final class InputController {
    private static final int MAX_TEXT = 128;

    private final InputSink sink;

    public InputController(InputSink sink) {
        this.sink = sink;
    }

    public synchronized boolean handle(Map<String, String> form) {
        String type = required(form, "type");
        return switch (type) {
            case "move" -> {
                double x = numberInRange(form, "x", 0.0, 1.0);
                double y = numberInRange(form, "y", 0.0, 1.0);
                Rectangle bounds = sink.screenBounds();
                sink.mouseMove(
                        (int) Math.round(x * Math.max(0, bounds.width - 1)),
                        (int) Math.round(y * Math.max(0, bounds.height - 1)));
                yield true;
            }
            case "moveRelative" -> {
                int dx = integerInRange(form, "dx", -500, 500);
                int dy = integerInRange(form, "dy", -500, 500);
                sink.mouseMoveRelative(dx, dy);
                yield true;
            }
            case "click" -> {
                int button = integerInRange(form, "button", 1, 3);
                if (booleanValue(form, "down")) {
                    sink.mousePress(button);
                } else {
                    sink.mouseRelease(button);
                }
                yield true;
            }
            case "wheel" -> {
                int delta = integerInRange(form, "delta", -10, 10);
                sink.mouseWheel(delta);
                yield true;
            }
            case "key" -> {
                int keyCode = KeyMapper.toKeyCode(required(form, "code"));
                if (keyCode < 0) {
                    yield false;
                }
                if (booleanValue(form, "down")) {
                    sink.keyPress(keyCode);
                } else {
                    sink.keyRelease(keyCode);
                }
                yield true;
            }
            case "text" -> {
                String text = required(form, "text");
                if (text.length() > MAX_TEXT) {
                    throw new IllegalArgumentException("Texto excede " + MAX_TEXT + " caracteres.");
                }
                if (text.isEmpty()) {
                    yield false;
                }
                sink.typeText(text);
                yield true;
            }
            case "releaseAll" -> {
                sink.releaseAll();
                yield true;
            }
            default -> throw new IllegalArgumentException("Tipo de input desconhecido: " + type);
        };
    }

    public synchronized void releaseAll() {
        sink.releaseAll();
    }

    public Rectangle screenBounds() {
        return sink.screenBounds();
    }

    public String description() {
        return sink.description();
    }

    private static String required(Map<String, String> form, String name) {
        String value = form.get(name);
        if (value == null) {
            throw new IllegalArgumentException("Campo ausente: " + name);
        }
        return value;
    }

    private static double number(Map<String, String> form, String name) {
        try {
            return Double.parseDouble(required(form, name).trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Campo " + name + " não é numérico.", e);
        }
    }

    private static int integer(Map<String, String> form, String name) {
        try {
            return Integer.parseInt(required(form, name).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Campo " + name + " não é inteiro.", e);
        }
    }

    private static boolean booleanValue(Map<String, String> form, String name) {
        String value = required(form, name).trim();
        return switch (value) {
            case "true", "1", "yes" -> true;
            case "false", "0", "no" -> false;
            default -> throw new IllegalArgumentException("Campo " + name + " deve ser true/false.");
        };
    }

    private static double numberInRange(Map<String, String> form, String name, double min, double max) {
        double value = number(form, name);
        if (Double.isNaN(value) || Double.isInfinite(value) || value < min || value > max) {
            throw new IllegalArgumentException("Campo " + name + " fora do intervalo.");
        }
        return value;
    }

    private static int integerInRange(Map<String, String> form, String name, int min, int max) {
        int value = integer(form, name);
        if (value < min || value > max) {
            throw new IllegalArgumentException("Campo " + name + " fora do intervalo.");
        }
        return value;
    }
}
