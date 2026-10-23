package br.com.espelhatv.input;

import java.awt.Rectangle;

/** Sink inerte para o modo demo e para testes de servidor. */
public final class NoopInputSink implements InputSink {
    private final Rectangle bounds;

    public NoopInputSink() {
        this(1280, 720);
    }

    public NoopInputSink(int width, int height) {
        this.bounds = new Rectangle(0, 0, width, height);
    }

    @Override
    public void mouseMove(int x, int y) {
        // intencionalmente vazio
    }

    @Override
    public void mouseMoveRelative(int dx, int dy) {
        // intencionalmente vazio
    }

    @Override
    public void mousePress(int button) {
        // intencionalmente vazio
    }

    @Override
    public void mouseRelease(int button) {
        // intencionalmente vazio
    }

    @Override
    public void mouseWheel(int amount) {
        // intencionalmente vazio
    }

    @Override
    public void keyPress(int keyCode) {
        // intencionalmente vazio
    }

    @Override
    public void keyRelease(int keyCode) {
        // intencionalmente vazio
    }

    @Override
    public void typeText(String text) {
        // intencionalmente vazio
    }

    @Override
    public void releaseAll() {
        // intencionalmente vazio
    }

    @Override
    public Rectangle screenBounds() {
        return new Rectangle(bounds);
    }

    @Override
    public String description() {
        return "Demo (sem controle real)";
    }
}
