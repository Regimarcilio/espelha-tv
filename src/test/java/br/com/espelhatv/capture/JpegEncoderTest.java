package br.com.espelhatv.capture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class JpegEncoderTest {

    @Test
    void encodesDecodableJpeg() throws IOException {
        JpegEncoder encoder = new JpegEncoder();
        byte[] jpeg = encoder.encode(pattern(64, 48), 0.6f);

        assertTrue(jpeg.length > 100);
        assertArrayEquals(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
                new byte[]{jpeg[0], jpeg[1], jpeg[2]});
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(jpeg));
        assertEquals(64, decoded.getWidth());
        assertEquals(48, decoded.getHeight());
    }

    @Test
    void qualityAffectsSize() {
        JpegEncoder encoder = new JpegEncoder();
        byte[] low = encoder.encode(pattern(400, 300), 0.1f);
        byte[] high = encoder.encode(pattern(400, 300), 1.0f);
        assertTrue(low.length < high.length,
                () -> "esperava low < high, mas " + low.length + " >= " + high.length);
    }

    private static BufferedImage pattern(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, (x * 7 + y * 3) & 0xFFFFFF);
            }
        }
        return image;
    }
}
