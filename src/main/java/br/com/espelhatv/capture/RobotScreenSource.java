package br.com.espelhatv.capture;

import java.awt.AWTException;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

/**
 * Captura real da tela primária via java.awt.Robot.
 */
public final class RobotScreenSource implements ScreenSource {
    private final Robot robot;
    private final Rectangle bounds;
    private final boolean drawCursor;

    public RobotScreenSource(boolean drawCursor) throws AWTException {
        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        Rectangle deviceBounds = device.getDefaultConfiguration().getBounds();
        this.bounds = new Rectangle(0, 0, deviceBounds.width, deviceBounds.height);
        this.robot = new Robot(device);
        this.drawCursor = drawCursor;
    }

    @Override
    public BufferedImage capture() {
        BufferedImage image = robot.createScreenCapture(new Rectangle(0, 0, bounds.width, bounds.height));
        if (drawCursor) {
            drawCursor(image);
        }
        return image;
    }

    @Override
    public Rectangle bounds() {
        return new Rectangle(bounds);
    }

    @Override
    public String description() {
        return "Tela primária " + bounds.width + "x" + bounds.height;
    }

    private void drawCursor(BufferedImage image) {
        PointerInfo info = MouseInfo.getPointerInfo();
        if (info == null) {
            return;
        }
        Point point = info.getLocation();
        int x = point.x - bounds.x;
        int y = point.y - bounds.y;
        if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) {
            return;
        }

        Path2D arrow = new Path2D.Double();
        arrow.moveTo(x, y);
        arrow.lineTo(x, y + 20);
        arrow.lineTo(x + 5, y + 15);
        arrow.lineTo(x + 9, y + 24);
        arrow.lineTo(x + 13, y + 22);
        arrow.lineTo(x + 9, y + 13);
        arrow.lineTo(x + 16, y + 13);
        arrow.closePath();

        Graphics2D g = image.createGraphics();
        try {
            g.setStroke(new BasicStroke(2f));
            g.setColor(Color.BLACK);
            g.draw(arrow);
            g.setColor(Color.WHITE);
            g.fill(arrow);
        } finally {
            g.dispose();
        }
    }
}
