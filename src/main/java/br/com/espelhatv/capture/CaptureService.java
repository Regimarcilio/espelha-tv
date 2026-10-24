package br.com.espelhatv.capture;

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pipeline de captura -> escala -> JPEG -> publicação. Captura e codificação
 * rodam em threads separadas para que o tempo de JPEG não bloqueie a próxima
 * captura; se o codificador atrasar, apenas o quadro mais recente é mantido.
 * Só captura quando há pelo menos um espectador, evitando consumo de CPU ocioso.
 */
public final class CaptureService implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger("espelhatv.capture");

    private final ScreenSource source;
    private final FrameHub hub;
    private final JpegEncoder encoder;
    private final int fps;
    private final int maxWidth;
    private final int maxHeight;
    private final float quality;
    private final AtomicLong sequence = new AtomicLong();
    private final AtomicReference<Frame> lastFrame = new AtomicReference<>();
    private final AtomicReference<String> lastError = new AtomicReference<>();
    private final AtomicReference<String> lastLoggedError = new AtomicReference<>();
    private final AtomicBoolean running = new AtomicBoolean();
    private final BlockingQueue<BufferedImage> pendingFrames = new ArrayBlockingQueue<>(1);
    private final ScheduledExecutorService captureExecutor;
    private final ExecutorService encoderExecutor;

    public CaptureService(ScreenSource source, FrameHub hub, JpegEncoder encoder,
                          int fps, int maxWidth, int maxHeight, float quality) {
        this.source = source;
        this.hub = hub;
        this.encoder = encoder;
        this.fps = Math.max(1, fps);
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
        this.quality = quality;
        this.captureExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "espelhatv-capture");
            thread.setDaemon(true);
            return thread;
        });
        this.encoderExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "espelhatv-encoder");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        encoderExecutor.execute(this::encodeLoop);
        long period = Math.max(20L, 1_000L / fps);
        captureExecutor.scheduleAtFixedRate(this::captureTick, 0, period, TimeUnit.MILLISECONDS);
    }

    private void captureTick() {
        if (!running.get() || hub.viewerCount() == 0) {
            pendingFrames.clear();
            return;
        }
        try {
            BufferedImage raw = source.capture();
            if (raw == null) {
                throw new IllegalStateException("Fonte de captura retornou imagem nula.");
            }
            BufferedImage scaled = FrameScaler.scale(raw, maxWidth, maxHeight);
            // Mantém somente o quadro mais recente; o codificador pode estar ocupado.
            pendingFrames.poll();
            pendingFrames.offer(scaled);
        } catch (Exception | LinkageError e) {
            recordError(e);
        }
    }

    private void encodeLoop() {
        while (running.get()) {
            try {
                BufferedImage scaled = pendingFrames.poll(200, TimeUnit.MILLISECONDS);
                if (scaled == null) {
                    continue;
                }
                if (!running.get() || hub.viewerCount() == 0) {
                    pendingFrames.clear();
                    continue;
                }
                byte[] jpeg = encoder.encode(scaled, quality);
                Frame frame = new Frame(jpeg, sequence.incrementAndGet(),
                        scaled.getWidth(), scaled.getHeight(), Instant.now());
                lastFrame.set(frame);
                lastError.set(null);
                lastLoggedError.set(null);
                hub.publish(frame);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception | LinkageError e) {
                recordError(e);
            }
        }
    }

    private void recordError(Throwable error) {
        String message = error.getClass().getSimpleName() + ": " + error.getMessage();
        lastError.set(message);
        if (!message.equals(lastLoggedError.getAndSet(message))) {
            LOG.log(Level.WARNING, "Falha na captura de tela: " + message);
        }
    }

    public Frame lastFrame() {
        return lastFrame.get();
    }

    public String lastError() {
        return lastError.get();
    }

    public String sourceDescription() {
        return source.description();
    }

    public int fps() {
        return fps;
    }

    @Override
    public void close() {
        running.set(false);
        captureExecutor.shutdownNow();
        encoderExecutor.shutdownNow();
        pendingFrames.clear();
        try {
            source.close();
        } catch (RuntimeException e) {
            LOG.log(Level.FINE, "Erro ao fechar a fonte de captura", e);
        }
    }
}
