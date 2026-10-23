package br.com.espelhatv.support;

import br.com.espelhatv.input.InputSink;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/** Sink que registra chamadas para testes do InputController. */
public final class RecordingInputSink implements InputSink {
    public final List<String> events = new ArrayList<>();
    public int releaseAllCount;

    @Override
    public void mouseMove(int x, int y) {
        events.add("move:" + x + "," + y);
    }

    @Override
    public void mouseMoveRelative(int dx, int dy) {
        events.add("moveRelative:" + dx + "," + dy);
    }

    @Override
    public void mousePress(int button) {
        events.add("press:" + button);
    }

    @Override
    public void mouseRelease(int button) {
        events.add("release:" + button);
    }

    @Override
    public void mouseWheel(int amount) {
        events.add("wheel:" + amount);
    }

    @Override
    public void keyPress(int keyCode) {
        events.add("keyPress:" + keyCode);
    }

    @Override
    public void keyRelease(int keyCode) {
        events.add("keyRelease:" + keyCode);
    }

    @Override
    public void typeText(String text) {
        events.add("text:" + text);
    }

    @Override
    public void releaseAll() {
        releaseAllCount++;
        events.add("releaseAll");
    }

    @Override
    public Rectangle screenBounds() {
        return new Rectangle(0, 0, 100, 50);
    }

    @Override
    public String description() {
        return "sink-de-teste";
    }
}
