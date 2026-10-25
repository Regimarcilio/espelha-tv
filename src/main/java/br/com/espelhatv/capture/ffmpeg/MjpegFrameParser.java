package br.com.espelhatv.capture.ffmpeg;

import java.io.ByteArrayOutputStream;
import java.util.function.Consumer;

/**
 * Extrai imagens JPEG de um fluxo contínuo (image2pipe). O ffmpeg escreve os
 * JPEGs um atrás do outro, sem delimitador; o parser localiza SOI (FF D8) e
 * EOI (FF D9). Em dados entrópicos o 0xFF é byte-stuffed (FF 00), então o
 * marcador EOI não aparece por acidente dentro da imagem.
 */
final class MjpegFrameParser {
    private final ByteArrayOutputStream current = new ByteArrayOutputStream(256 * 1024);
    private boolean inFrame;
    private boolean previousWasFF;

    void feed(byte[] data, int length, Consumer<byte[]> frameConsumer) {
        for (int i = 0; i < length; i++) {
            int value = data[i] & 0xFF;
            if (!inFrame) {
                if (previousWasFF && value == 0xD8) {
                    current.reset();
                    current.write(0xFF);
                    current.write(0xD8);
                    inFrame = true;
                    previousWasFF = false;
                } else {
                    previousWasFF = value == 0xFF;
                }
                continue;
            }

            current.write(value);
            if (previousWasFF && value == 0xD9) {
                frameConsumer.accept(current.toByteArray());
                current.reset();
                inFrame = false;
                previousWasFF = false;
            } else {
                previousWasFF = value == 0xFF;
            }
        }
    }
}
