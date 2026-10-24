package br.com.espelhatv.capture;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/** Redimensiona preservando proporção e sem fazer upscale. */
public final class FrameScaler {
    private FrameScaler() {
    }

    public static BufferedImage scale(BufferedImage source, int maxWidth, int maxHeight) {
        if (source == null) {
            throw new IllegalArgumentException("Imagem de origem nula.");
        }
        if (maxWidth < 1 || maxHeight < 1) {
            throw new IllegalArgumentException("Dimensões máximas inválidas.");
        }

        double ratio = Math.min((double) maxWidth / source.getWidth(),
                (double) maxHeight / source.getHeight());
        ratio = Math.min(1.0, ratio);

        int width = Math.max(1, (int) Math.round(source.getWidth() * ratio));
        int height = Math.max(1, (int) Math.round(source.getHeight() * ratio));
        if (width == source.getWidth() && height == source.getHeight()) {
            return source;
        }

        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = scaled.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return scaled;
    }
}
