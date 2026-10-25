package br.com.espelhatv.capture.ffmpeg;

import br.com.espelhatv.capture.Frame;
import br.com.espelhatv.capture.FrameHub;
import br.com.espelhatv.config.AppConfig;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gera MJPEG 30 fps direto no ffmpeg (x11grab → scale → mjpeg) e publica os
 * frames no mesmo {@link FrameHub} usado pelo endpoint /stream. Isso evita o
 * custo de captura + JPEG no Java e mantém a TV no caminho compatível com
 * navegadores antigos.
 */
public final class MjpegTap implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger("espelhatv.mjpeg");
    private static final int JPEG_QUALITY = 10;

    private final AppConfig config;
    private final FrameHub hub;
    private final List<String> commandOverride;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicLong sequence = new AtomicLong();

    private volatile Process process;
    private Thread readerThread;

    public MjpegTap(AppConfig config, FrameHub hub) {
        this(config, hub, null);
    }

    /** Construtor de teste: permite injetar um processo que produz JPEGs. */
    MjpegTap(AppConfig config, FrameHub hub, List<String> commandOverride) {
        this.config = config;
        this.hub = hub;
        this.commandOverride = commandOverride;
    }

    /** Só faz sentido quando o ffmpeg já vai capturar via x11grab. */
    public static boolean supported(AppConfig config) {
        return config.ffmpegEnabled() && FFmpegProcessManager.useX11grab(config);
    }

    public void start() throws IOException {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        process = startProcess();
        readerThread = new Thread(this::readLoop, "espelhatv-mjpeg-tap");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    private Process startProcess() throws IOException {
        List<String> command = commandOverride != null ? commandOverride : buildCommand(config);
        LOG.info("Iniciando MJPEG tap: " + command);
        Process started = new ProcessBuilder(command).start();
        Thread drain = new Thread(() -> drainErrors(started.getErrorStream()), "espelhatv-mjpeg-stderr");
        drain.setDaemon(true);
        drain.start();
        LOG.info("MJPEG tap iniciado (PID: " + started.pid() + ")");
        return started;
    }

    static List<String> buildCommand(AppConfig config) {
        Dimension screen = screenSize();
        List<String> command = new ArrayList<>();
        command.add("ffmpeg");
        command.add("-hide_banner");
        command.add("-nostats");
        command.add("-loglevel");
        command.add("warning");
        command.add("-f");
        command.add("x11grab");
        command.add("-draw_mouse");
        command.add(config.drawCursor() ? "1" : "0");
        command.add("-framerate");
        command.add(String.valueOf(config.fps()));
        command.add("-video_size");
        command.add(screen.width + "x" + screen.height);
        command.add("-i");
        command.add(x11Display() + "+0,0");
        command.add("-vf");
        command.add("scale=" + config.maxWidth() + ":" + config.maxHeight() + ",format=yuvj420p");
        command.add("-c:v");
        command.add("mjpeg");
        command.add("-q:v");
        command.add(String.valueOf(JPEG_QUALITY));
        command.add("-an");
        command.add("-f");
        command.add("image2pipe");
        command.add("pipe:1");
        return command;
    }

    private void readLoop() {
        while (running.get()) {
            Process current = process;
            if (current == null) {
                return;
            }
            MjpegFrameParser parser = new MjpegFrameParser();
            try (InputStream in = current.getInputStream()) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while (running.get() && (read = in.read(buffer)) != -1) {
                    parser.feed(buffer, read, this::publish);
                }
            } catch (IOException e) {
                if (running.get()) {
                    LOG.log(Level.WARNING, "Falha lendo MJPEG do ffmpeg: " + e.getMessage());
                }
            }

            try {
                int exit = current.waitFor();
                if (running.get()) {
                    LOG.warning("MJPEG tap terminou com código " + exit + "; reiniciando em 1s.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            if (!running.get() || !sleep(1_000L)) {
                return;
            }
            try {
                process = startProcess();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Não foi possível reiniciar o MJPEG tap: " + e.getMessage());
                if (!sleep(2_000L)) {
                    return;
                }
            }
        }
    }

    private boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void publish(byte[] jpeg) {
        hub.publish(new Frame(jpeg, sequence.incrementAndGet(),
                config.maxWidth(), config.maxHeight(), Instant.now()));
    }

    private void drainErrors(InputStream errorStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    LOG.warning("ffmpeg mjpeg: " + line);
                }
            }
        } catch (IOException ignored) {
            // processo encerrado
        }
    }

    private static Dimension screenSize() {
        try {
            var device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
            var mode = device.getDisplayMode();
            return new Dimension(mode.getWidth(), mode.getHeight());
        } catch (RuntimeException e) {
            return new Dimension(1920, 1080);
        }
    }

    private static String x11Display() {
        String display = System.getenv("DISPLAY");
        return display == null || display.isBlank() ? ":0" : display;
    }

    @Override
    public void close() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        Process current = process;
        if (current != null) {
            current.descendants().forEach(ProcessHandle::destroy);
            current.destroy();
            try {
                if (!current.waitFor(3, TimeUnit.SECONDS)) {
                    current.descendants().forEach(ProcessHandle::destroyForcibly);
                    current.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (readerThread != null) {
            readerThread.interrupt();
        }
        LOG.info("MJPEG tap encerrado");
    }
}
