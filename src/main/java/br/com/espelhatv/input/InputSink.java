package br.com.espelhatv.input;

import java.awt.Rectangle;

/**
 * Abstração de injeção de input. A implementação Robot é usada em produção e
 * NoopInputSink no modo demo/testes.
 */
public interface InputSink {
    void mouseMove(int x, int y);

    void mouseMoveRelative(int dx, int dy);

    void mousePress(int button);

    void mouseRelease(int button);

    void mouseWheel(int amount);

    void keyPress(int keyCode);

    void keyRelease(int keyCode);

    void typeText(String text);

    /** Solta tudo que estiver pressionado (teclas e botões). */
    void releaseAll();

    Rectangle screenBounds();

    default String description() {
        return getClass().getSimpleName();
    }
}
