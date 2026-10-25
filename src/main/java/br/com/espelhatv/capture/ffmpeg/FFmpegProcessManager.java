package br.com.espelhatv.capture.ffmpeg;

import br.com.espelhatv.config.AppConfig;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gerencia o pipeline ffmpeg de codificação nativa de vídeo.
 *
 * <p>Pipeline em dois estágios, ligados por um pipe do shell:</p>
 * <ol>
 *   <li>ffmpeg do sistema: captura (x11grab direto ou rawvideo BGR24 via stdin)
 *       → H.264 via VAAPI (ou libx264 quando não há device VAAPI) → MPEG-TS;</li>
 *   <li>ffmpeg com muxer WHIP (tools/ffmpeg-whip): MPEG-TS → remux sem
 *       recodificar → WHIP (MediaMTX).</li>
 * </ol>
 *
 * <p>No modo x11grab o Java não participa da captura; no modo stdin o Java
 * escreve os frames crus no stdin do pipeline.</p>
 */
public final class FFmpegProcessManager implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger("espelhatv.ffmpeg");

    private final AppConfig config;
    private final BlockingQueue<BufferedImage> frameQueue;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong frameCount = new AtomicLong(0);
    private final AtomicLong droppedFrames = new AtomicLong(0);
    private final AtomicLong encodeErrors = new AtomicLong(0);

    private Process ffmpegProcess;
    private OutputStream ffmpegStdin;
    private Thread encoderThread;
    private Thread monitorThread;
    private Thread drainThread;
    private ScheduledExecutorService captureExecutor;
    private ScheduledExecutorService statsExecutor;
    private br.com.espelhatv.capture.RobotScreenSource screenSource;
    private boolean x11grabMode;

    public FFmpegProcessManager(AppConfig config) {
        this.config = config;
        int queueSize = Math.max(1, config.ffmpegQueueSize());
        this.frameQueue = new ArrayBlockingQueue<>(queueSize);
    }

    public void start() throws IOException, java.awt.AWTException {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        boolean x11grab = useX11grab(config);
        this.x11grabMode = x11grab;
        if (!x11grab) {
            this.screenSource = new br.com.espelhatv.capture.RobotScreenSource(config.drawCursor());
        }

        ProcessBuilder pb = buildFFmpegCommand(x11grab);
        pb.redirectErrorStream(true);

        LOG.info("Iniciando pipeline ffmpeg: " + pb.command());
        ffmpegProcess = pb.start();
        ffmpegStdin = new BufferedOutputStream(ffmpegProcess.getOutputStream(), 64 * 1024);

        drainThread = new Thread(this::drainLoop, "espelhatv-ffmpeg-drain");
        drainThread.setDaemon(true);
        drainThread.start();

        if (!x11grab) {
            encoderThread = new Thread(this::encodeLoop, "espelhatv-ffmpeg-encoder");
            encoderThread.setDaemon(true);
            encoderThread.start();

            long period = Math.max(1_000_000_000L / 60, 1_000_000_000L / config.ffmpegFps());
            captureExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "espelhatv-ffmpeg-capture");
                thread.setDaemon(true);
                return thread;
            });
            captureExecutor.scheduleAtFixedRate(this::captureTick, 0, period, TimeUnit.NANOSECONDS);
        }

        monitorThread = new Thread(this::monitorLoop, "espelhatv-ffmpeg-monitor");
        monitorThread.setDaemon(true);
        monitorThread.start();

        statsExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "espelhatv-ffmpeg-stats");
            t.setDaemon(true);
            return t;
        });
        statsExecutor.scheduleAtFixedRate(this::logStats, 10, 10, TimeUnit.SECONDS);

        LOG.info("FFmpeg process manager iniciado (PID: " + ffmpegProcess.pid() + ")");
    }

    private ProcessBuilder buildFFmpegCommand(boolean x11grab) {
        LOG.info("Encoder: " + (useVaapi(config)
                ? "VAAPI (" + config.ffmpegVaapiDevice() + ")"
                : "libx264/" + config.ffmpegPreset()));
        return new ProcessBuilder("sh", "-c", buildPipelineCommand(config, x11grab));
    }

    /** Monta o comando do pipeline (estágio VAAPI/x264 + estágio WHIP). */
    static String buildPipelineCommand(AppConfig config, boolean x11grab) {
        String width = String.valueOf(config.ffmpegWidth());
        String height = String.valueOf(config.ffmpegHeight());
        String fps = String.valueOf(config.ffmpegFps());
        String bitrate = config.ffmpegBitrate() + "k";
        String bufsize = (config.ffmpegBitrate() * 2) + "k";
        String gop = String.valueOf(config.ffmpegFps() * 2);
        boolean vaapi = useVaapi(config);

        StringBuilder stage1 = new StringBuilder();
        stage1.append(shellQuote(systemFfmpeg()));
        stage1.append(" -hide_banner -nostats -loglevel warning");
        if (vaapi) {
            stage1.append(" -vaapi_device ").append(shellQuote(config.ffmpegVaapiDevice()));
        }

        if (x11grab) {
            java.awt.Dimension screen = screenSize();
            stage1.append(" -f x11grab -draw_mouse ").append(config.drawCursor() ? "1" : "0")
                    .append(" -framerate ").append(fps)
                    .append(" -video_size ").append(screen.width).append('x').append(screen.height)
                    .append(" -i ").append(shellQuote(x11Display() + "+0,0"));
            if (vaapi) {
                stage1.append(" -vf hwupload,scale_vaapi=w=").append(width).append(":h=").append(height)
                        .append(":format=nv12")
                        .append(" -c:v h264_vaapi")
                        .append(" -b:v ").append(bitrate)
                        .append(" -maxrate ").append(bitrate)
                        .append(" -bufsize ").append(bufsize)
                        .append(" -g ").append(gop);
            } else {
                stage1.append(" -vf scale=").append(width).append(':').append(height).append(",format=yuv420p")
                        .append(" -c:v libx264")
                        .append(" -preset ").append(config.ffmpegPreset())
                        .append(" -tune zerolatency -profile:v baseline")
                        .append(" -g ").append(gop)
                        .append(" -b:v ").append(bitrate)
                        .append(" -maxrate ").append(bitrate)
                        .append(" -bufsize ").append(bufsize);
            }
        } else {
            stage1.append(" -f rawvideo -pix_fmt bgr24 -s ").append(width).append('x').append(height)
                    .append(" -r ").append(fps).append(" -i -");
            if (vaapi) {
                stage1.append(" -vf format=nv12,hwupload -c:v h264_vaapi")
                        .append(" -b:v ").append(bitrate)
                        .append(" -maxrate ").append(bitrate)
                        .append(" -bufsize ").append(bufsize)
                        .append(" -g ").append(gop);
            } else {
                stage1.append(" -c:v libx264")
                        .append(" -preset ").append(config.ffmpegPreset())
                        .append(" -tune zerolatency -profile:v baseline -pix_fmt yuv420p")
                        .append(" -g ").append(gop)
                        .append(" -b:v ").append(bitrate)
                        .append(" -maxrate ").append(bitrate)
                        .append(" -bufsize ").append(bufsize);
            }
        }
        stage1.append(" -f mpegts pipe:1");

        String stage2 = shellQuote(config.ffmpegWhipBin())
                + " -hide_banner -nostats -loglevel warning"
                + " -f mpegts -i pipe:0 -c copy -f whip "
                + shellQuote(config.ffmpegWhipUrl());

        return stage1 + " | " + stage2;
    }

    static boolean useVaapi(AppConfig config) {
        String device = config.ffmpegVaapiDevice();
        if (device == null || device.isBlank()) {
            return false;
        }
        boolean exists = Files.exists(Path.of(device));
        if (!exists) {
            LOG.warning("Device VAAPI não encontrado (" + device + "); usando libx264.");
        }
        return exists;
    }

    /** Captura direto no ffmpeg (x11grab) quando há DISPLAY e o modo é x11grab. */
    static boolean useX11grab(AppConfig config) {
        if (!"x11grab".equalsIgnoreCase(config.ffmpegCapture())) {
            return false;
        }
        String display = System.getenv("DISPLAY");
        if (display == null || display.isBlank()) {
            LOG.warning("DISPLAY ausente; captura FFmpeg cai para modo stdin.");
            return false;
        }
        return true;
    }

    private static String x11Display() {
        String display = System.getenv("DISPLAY");
        return display == null || display.isBlank() ? ":0" : display;
    }

    private static java.awt.Dimension screenSize() {
        try {
            var device = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
            var mode = device.getDisplayMode();
            return new java.awt.Dimension(mode.getWidth(), mode.getHeight());
        } catch (RuntimeException e) {
            return new java.awt.Dimension(1920, 1080);
        }
    }

    private static String systemFfmpeg() {
        return "ffmpeg";
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    public boolean offerFrame(BufferedImage frame) {
        if (!running.get()) {
            return false;
        }
        if (frameQueue.remainingCapacity() == 0) {
            frameQueue.poll();
            droppedFrames.incrementAndGet();
        }
        frameQueue.offer(frame);
        return true;
    }

    private void captureTick() {
        if (!running.get()) {
            return;
        }
        try {
            BufferedImage raw = screenSource.capture();
            if (raw == null) {
                return;
            }
            BufferedImage scaled = br.com.espelhatv.capture.FrameScaler.scale(raw, config.ffmpegWidth(), config.ffmpegHeight());
            if (frameQueue.remainingCapacity() == 0) {
                frameQueue.poll();
                droppedFrames.incrementAndGet();
            }
            frameQueue.offer(scaled);
        } catch (Exception | LinkageError e) {
            recordError(e);
        }
    }

    private void encodeLoop() {
        while (running.get()) {
            try {
                BufferedImage scaled = frameQueue.poll(100, TimeUnit.MILLISECONDS);
                if (scaled == null) {
                    continue;
                }
                if (!running.get()) {
                    break;
                }

                int width = scaled.getWidth();
                int height = scaled.getHeight();
                int[] pixels = scaled.getRGB(0, 0, width, height, null, 0, width);

                byte[] encodeBuffer = new byte[width * height * 3];
                int idx = 0;
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int rgb = pixels[y * width + x];
                        encodeBuffer[idx++] = (byte) (rgb & 0xFF);         // B
                        encodeBuffer[idx++] = (byte) ((rgb >> 8) & 0xFF);  // G
                        encodeBuffer[idx++] = (byte) ((rgb >> 16) & 0xFF); // R
                    }
                }

                ffmpegStdin.write(encodeBuffer);
                ffmpegStdin.flush();
                frameCount.incrementAndGet();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (IOException e) {
                if (running.get()) {
                    LOG.log(Level.WARNING, "Erro ao escrever no ffmpeg stdin: " + e.getMessage());
                }
                break;
            } catch (Exception e) {
                encodeErrors.incrementAndGet();
                LOG.log(Level.WARNING, "Erro no encode: " + e.getMessage());
            }
        }
    }

    private void drainLoop() {
        try (var in = ffmpegProcess.getInputStream()) {
            byte[] buffer = new byte[8192];
            while (in.read(buffer) != -1) {
                // descarta; -loglevel warning mantém a saída mínima
            }
        } catch (IOException ignored) {
            // processo encerrado
        }
    }

    private void recordError(Throwable error) {
        String message = error.getClass().getSimpleName() + ": " + error.getMessage();
        LOG.log(Level.WARNING, "Erro na captura FFmpeg: " + message);
    }

    private void monitorLoop() {
        try {
            int exitCode = ffmpegProcess.waitFor();
            if (running.get()) {
                LOG.warning("Pipeline ffmpeg terminou inesperadamente com código: " + exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void logStats() {
        if (x11grabMode) {
            LOG.info("FFmpeg stats - modo x11grab, pipeline vivo: " + isRunning());
            return;
        }
        LOG.info(String.format("FFmpeg stats - Frames: %d, descartados: %d, erros: %d",
                frameCount.get(), droppedFrames.get(), encodeErrors.get()));
    }

    public boolean isRunning() {
        return ffmpegProcess != null && ffmpegProcess.isAlive();
    }

    @Override
    public void close() {
        running.set(false);

        if (ffmpegStdin != null) {
            try {
                ffmpegStdin.close();
            } catch (IOException ignored) {
            }
        }

        if (captureExecutor != null) {
            captureExecutor.shutdownNow();
        }
        if (statsExecutor != null) {
            statsExecutor.shutdownNow();
        }

        if (ffmpegProcess != null) {
            ffmpegProcess.descendants().forEach(ProcessHandle::destroy);
            ffmpegProcess.destroy();
            try {
                if (!ffmpegProcess.waitFor(5, TimeUnit.SECONDS)) {
                    ffmpegProcess.descendants().forEach(ProcessHandle::destroyForcibly);
                    ffmpegProcess.destroyForcibly();
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        LOG.info("FFmpeg process manager encerrado");
    }
}
