package br.com.espelhatv.capture;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tela sintética para testar o pipeline sem permissão de captura do sistema.
 * Também serve de smoke test visual em CI/headless (desde que haja fonte).
 */
public final class DemoScreenSource implements ScreenSource {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final int width;
    private final int height;
    private final BufferedImage background;
    private final AtomicLong counter = new AtomicLong();

    public DemoScreenSource(int width, int height) {
        this.width = Math.max(320, width);
        this.height = Math.max(240, height);
        this.background = createBackground(this.width, this.height);
    }

    @Override
    public BufferedImage capture() {
        long frame = counter.incrementAndGet();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = image.createGraphics();
        try {
            g.drawImage(background, 0, 0, null);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            long millis = System.currentTimeMillis();
            double phase = millis / 700.0;
            int circleX = (int) ((millis / 12) % Math.max(1, width - 180)) + 90;
            int circleY = (int) ((Math.sin(phase) + 1.0) / 2.0 * Math.max(1, height - 260)) + 130;

            g.setColor(new Color(34, 197, 94, 210));
            g.fillOval(circleX - 54, circleY - 54, 108, 108);
            g.setColor(new Color(15, 23, 42));
            g.setStroke(new BasicStroke(3f));
            g.drawOval(circleX - 54, circleY - 54, 108, 108);
            g.drawLine(circleX - 78, circleY, circleX + 78, circleY);
            g.drawLine(circleX, circleY - 78, circleX, circleY + 78);

            g.setColor(new Color(226, 232, 240));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(24, width / 28)));
            g.drawString("ESPELHA-TV", 48, 72);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(14, width / 70)));
            g.setColor(new Color(148, 163, 184));
            g.drawString("MODO DEMO - nenhuma tela real esta sendo capturada", 48, 108);

            g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, Math.max(12, width / 80)));
            g.setColor(new Color(203, 213, 225));
            g.drawString("frame " + frame + "  |  " + TIME.format(Instant.now()), 48, height - 84);
            g.drawString("resolucao " + width + "x" + height + "  |  stream MJPEG", 48, height - 58);
            g.drawString("controle: use as setas, Enter, Escape e o botao Teclado", 48, height - 32);

            g.setColor(new Color(15, 23, 42, 180));
            g.fillRoundRect(width - 260, 36, 212, 48, 14, 14);
            g.setColor(new Color(34, 197, 94));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
            g.drawString("STREAM ATIVO", width - 232, 66);
        } finally {
            g.dispose();
        }
        return image;
    }

    @Override
    public Rectangle bounds() {
        return new Rectangle(0, 0, width, height);
    }

    @Override
    public String description() {
        return "Demo " + width + "x" + height;
    }

    private static BufferedImage createBackground(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = image.createGraphics();
        try {
            g.setPaint(new GradientPaint(0, 0, new Color(15, 23, 42),
                    width, height, new Color(30, 41, 59)));
            g.fillRect(0, 0, width, height);

            g.setColor(new Color(51, 65, 85));
            g.setStroke(new BasicStroke(1f));
            int step = 64;
            for (int x = 0; x < width; x += step) {
                g.drawLine(x, 0, x, height);
            }
            for (int y = 0; y < height; y += step) {
                g.drawLine(0, y, width, y);
            }

            g.setColor(new Color(59, 130, 246, 50));
            g.fillRect(0, height - 120, width, 120);
        } finally {
            g.dispose();
        }
        return image;
    }
}
