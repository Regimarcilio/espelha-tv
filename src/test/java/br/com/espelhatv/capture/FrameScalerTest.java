package br.com.espelhatv.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class FrameScalerTest {

    @Test
    void scalesLandscapePreservingAspect() {
        BufferedImage source = new BufferedImage(1920, 1080, BufferedImage.TYPE_3BYTE_BGR);
        BufferedImage scaled = FrameScaler.scale(source, 1280, 720);
        assertEquals(1280, scaled.getWidth());
        assertEquals(720, scaled.getHeight());
    }

    @Test
    void scalesPortraitAndSmallImages() {
        BufferedImage portrait = new BufferedImage(1080, 1920, BufferedImage.TYPE_3BYTE_BGR);
        BufferedImage scaled = FrameScaler.scale(portrait, 1280, 720);
        assertEquals(405, scaled.getWidth());
        assertEquals(720, scaled.getHeight());

        BufferedImage small = new BufferedImage(640, 480, BufferedImage.TYPE_3BYTE_BGR);
        BufferedImage smallScaled = FrameScaler.scale(small, 320, 240);
        assertEquals(320, smallScaled.getWidth());
        assertEquals(240, smallScaled.getHeight());
    }

    @Test
    void doesNotUpscale() {
        BufferedImage source = new BufferedImage(100, 80, BufferedImage.TYPE_3BYTE_BGR);
        assertSame(source, FrameScaler.scale(source, 1280, 720));
    }

    @Test
    void rejectsInvalidInput() {
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_3BYTE_BGR);
        assertThrows(IllegalArgumentException.class, () -> FrameScaler.scale(null, 10, 10));
        assertThrows(IllegalArgumentException.class, () -> FrameScaler.scale(image, 0, 10));
    }
}
