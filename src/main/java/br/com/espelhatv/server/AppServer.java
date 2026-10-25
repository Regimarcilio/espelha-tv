package br.com.espelhatv.server;

import br.com.espelhatv.auth.AuthService;
import br.com.espelhatv.auth.SessionStore;
import br.com.espelhatv.capture.CaptureService;
import br.com.espelhatv.capture.FrameHub;
import br.com.espelhatv.capture.JpegEncoder;
import br.com.espelhatv.capture.ScreenSource;
import br.com.espelhatv.config.AppConfig;
import br.com.espelhatv.control.ControlManager;
import br.com.espelhatv.input.InputController;
import br.com.espelhatv.input.InputSink;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Composição e ciclo de vida de todos os componentes. */
public final class AppServer implements AutoCloseable {
    private final AppConfig config;
    private final FrameHub hub;
    private final CaptureService capture;
    private final InputController input;
    private final ControlManager control;
    private final SessionStore sessions;
    private final AuthService auth;
    private final HttpApi http;
    private final boolean captureEnabled;
    private final ScheduledExecutorService cleanup;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    public AppServer(AppConfig config, ScreenSource source, InputSink sink) {
        this(config, source, sink, Clock.systemUTC(), true);
    }

    public AppServer(AppConfig config, ScreenSource source, InputSink sink, Clock clock) {
        this(config, source, sink, clock, true);
    }

    public AppServer(AppConfig config, ScreenSource source, InputSink sink, Clock clock, boolean captureEnabled) {
        this.config = config;
        this.captureEnabled = captureEnabled;
        this.hub = new FrameHub();
        this.capture = new CaptureService(source, hub, new JpegEncoder(), config.fps(),
                config.maxWidth(), config.maxHeight(), config.jpegQuality());
        this.input = new InputController(sink);
        this.control = new ControlManager(clock,
                Duration.ofSeconds(config.controlTimeoutSeconds()), input::releaseAll);
        this.sessions = new SessionStore(clock, Duration.ofMinutes(config.sessionTtlMinutes()));
        this.auth = new AuthService(config.token(), clock, 10, 60_000L);
        this.http = new HttpApi(config, auth, sessions, control, input, hub, capture);
        this.cleanup = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "espelhatv-cleanup");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() throws IOException {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("Servidor já iniciado.");
        }
        http.start();
        if (captureEnabled) {
            capture.start();
        }
        cleanup.scheduleWithFixedDelay(this::cleanupExpired, 30, 30, TimeUnit.SECONDS);
    }

    public int port() {
        return http.port();
    }

    public AppConfig config() {
        return config;
    }

    public FrameHub hub() {
        return hub;
    }

    public ControlManager control() {
        return control;
    }

    public SessionStore sessions() {
        return sessions;
    }

    public CaptureService capture() {
        return capture;
    }

    public AuthService auth() {
        return auth;
    }

    private void cleanupExpired() {
        try {
            auth.cleanup();
            for (String sessionId : sessions.cleanup()) {
                control.releaseBySession(sessionId);
            }
            control.cleanupIdle();
        } catch (RuntimeException e) {
            // O scheduler não deve morrer por uma falha isolada de limpeza.
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        http.close();
        capture.close();
        control.forceRelease();
        cleanup.shutdownNow();
    }
}
