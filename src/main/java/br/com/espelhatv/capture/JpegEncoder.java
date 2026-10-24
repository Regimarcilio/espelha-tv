package br.com.espelhatv.capture;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/** Codificador JPEG isolado para permitir teste unitário do pipeline. */
public final class JpegEncoder {
    public JpegEncoder() {
        ImageIO.setUseCache(false);
    }

    public byte[] encode(BufferedImage image, float quality) {
        if (image == null) {
            throw new IllegalArgumentException("Imagem nula.");
        }
        float clamped = Math.max(0.10f, Math.min(1.0f, quality));
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IllegalStateException("Nenhum writer JPEG disponível nesta JVM.");
        }

        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream(64 * 1024)) {
            try (ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
                if (imageOutput == null) {
                    throw new IllegalStateException("Não foi possível criar saída de imagem.");
                }
                writer.setOutput(imageOutput);
                ImageWriteParam params = writer.getDefaultWriteParam();
                if (params.canWriteCompressed()) {
                    params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    params.setCompressionQuality(clamped);
                }
                writer.write(null, new IIOImage(image, null, null), params);
            }
            byte[] jpeg = output.toByteArray();
            if (jpeg.length == 0) {
                throw new IllegalStateException("JPEG gerado vazio.");
            }
            return jpeg;
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao codificar JPEG.", e);
        } finally {
            writer.dispose();
        }
    }
}
