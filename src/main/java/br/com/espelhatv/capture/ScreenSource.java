package br.com.espelhatv.capture;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/** Fonte de captura de tela. Implementações podem ser Robot ou demo. */
public interface ScreenSource extends AutoCloseable {
    BufferedImage capture();

    Rectangle bounds();

    default String description() {
        return getClass().getSimpleName();
    }

    @Override
    default void close() {
    }
}
