package br.com.espelhatv.capture;

import java.time.Instant;
import java.util.Objects;

/** Frame JPEG imutável publicado no hub. */
public record Frame(byte[] jpeg, long sequence, int width, int height, Instant timestamp) {
    public Frame {
        Objects.requireNonNull(jpeg, "jpeg");
        Objects.requireNonNull(timestamp, "timestamp");
        if (jpeg.length == 0) {
            throw new IllegalArgumentException("Frame JPEG vazio.");
        }
    }

    public int size() {
        return jpeg.length;
    }
}
