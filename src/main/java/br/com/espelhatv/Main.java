package br.com.espelhatv;

import br.com.espelhatv.capture.DemoScreenSource;
import br.com.espelhatv.capture.RobotScreenSource;
import br.com.espelhatv.capture.ScreenSource;
import br.com.espelhatv.capture.ffmpeg.FFmpegProcessManager;
import br.com.espelhatv.config.AppConfig;
import br.com.espelhatv.input.InputSink;
import br.com.espelhatv.input.NoopInputSink;
import br.com.espelhatv.input.RobotInputSink;
import br.com.espelhatv.server.AppServer;
import br.com.espelhatv.util.NetworkInfo;
import java.awt.AWTException;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        configureLogging();
        AppConfig config;
        try {
            config = AppConfig.parse(args);
        } catch (AppConfig.HelpRequested help) {
            return;
        } catch (IllegalArgumentException e) {
            System.err.println("Erro de configuração: " + e.getMessage());
            System.exit(2);
            return;
        }

        boolean wayland = System.getenv("WAYLAND_DISPLAY") != null;
        String screenshotMethod = System.getenv("ESPELHATV_SCREENSHOT_METHOD");
        if (screenshotMethod != null && !screenshotMethod.isBlank()) {
            System.setProperty("awt.robot.screenshotMethod", screenshotMethod.trim());
        } else if (wayland && System.getProperty("awt.robot.screenshotMethod") == null) {
            System.setProperty("awt.robot.screenshotMethod", "x11");
        }

        ScreenSource source;
        InputSink sink;
        FFmpegProcessManager ffmpegManager = null;

        if (config.demo()) {
            source = new DemoScreenSource(config.maxWidth(), config.maxHeight());
            sink = new NoopInputSink(config.maxWidth(), config.maxHeight());
        } else if (config.ffmpegEnabled()) {
            try {
                source = new RobotScreenSource(config.drawCursor());
                sink = new RobotInputSink();

                // O pipeline ffmpeg faz o encode (VAAPI) e publica via WHIP no MediaMTX.
                ffmpegManager = new FFmpegProcessManager(config);
                ffmpegManager.start();
            } catch (Exception e) {
                System.err.println("Não foi possível iniciar pipeline FFmpeg/WebRTC: " + e.getMessage());
                System.err.println("Fallback para modo MJPEG...");
                try {
                    source = new RobotScreenSource(config.drawCursor());
                    sink = new RobotInputSink();
                } catch (AWTException | RuntimeException e2) {
                    System.err.println("Falha no fallback: " + e2.getMessage());
                    System.exit(2);
                    return;
                }
            }
        } else {
            try {
                source = new RobotScreenSource(config.drawCursor());
                sink = new RobotInputSink();
            } catch (AWTException | RuntimeException e) {
                System.err.println("Não foi possível capturar/controlar a tela: " + e.getMessage());
                System.err.println("Dicas:");
                System.err.println("  - Rode em sessão gráfica X11, ou permita captura/injeção no Wayland.");
                System.err.println("  - Use --demo para validar o stream e a interface sem controlar o PC.");
                System.exit(2);
                return;
            }
        }

        if (config.screenIndex() != 0) {
            System.out.println("Aviso: o MVP captura apenas a tela primária; --screen será ignorado.");
        }
        if (wayland) {
            System.out.println("Aviso: Wayland detectado; captura via X11/XWayland. Se a imagem ficar preta, use sessão X11 ou --demo.");
        }

        AppServer server = new AppServer(config, source, sink);
        try {
            server.start();
        } catch (IOException e) {
            System.err.println("Não foi possível abrir o servidor em " + config.bind() + ":" + config.port());
            System.err.println("Detalhe: " + e.getMessage());
            server.close();
            System.exit(1);
            return;
        }

        printBanner(config, server.port());
        final FFmpegProcessManager managerToClose = ffmpegManager;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.close();
            if (managerToClose != null) {
                managerToClose.close();
            }
        }, "espelhatv-shutdown"));

        try {
            new CountDownLatch(1).await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            server.close();
        }
    }

    private static void printBanner(AppConfig config, int port) {
        String host = switch (config.bind()) {
            case "0.0.0.0", "::", "[::]" -> NetworkInfo.localIpv4();
            default -> config.bind();
        };
        String tokenLine = config.tokenGenerated()
                ? config.token()
                : "(definido por --token/ESPELHATV_TOKEN)";

        String mode = config.demo() ? "DEMO (sem captura real)" : 
                      config.ffmpegEnabled() ? "TELA REAL + FFmpeg/WebRTC" : "TELA REAL";
        System.out.println();
        System.out.println("============================================================");
        System.out.println(" " + BuildInfo.NAME + " " + BuildInfo.VERSION);
        System.out.println("============================================================");
        System.out.println(" Modo........: " + mode);
        System.out.println(" URL.........: http://" + host + ":" + port);
        System.out.println(" Token.......: " + tokenLine);
        if (config.ffmpegEnabled()) {
            System.out.println(" FFmpeg......: " + config.ffmpegWidth() + "x" + config.ffmpegHeight() 
                    + " @ " + config.ffmpegFps() + "fps, " + config.ffmpegBitrate() + "kbps"
                    + (config.ffmpegVaapiDevice().isBlank() ? ", libx264/" + config.ffmpegPreset() : ", VAAPI")
                    + ", captura " + config.ffmpegCapture());
            System.out.println(" WHIP........: " + config.ffmpegWhipUrl());
        } else {
            System.out.println(" Stream......: MJPEG " + config.fps() + " fps, até "
                    + config.maxWidth() + "x" + config.maxHeight()
                    + ", JPEG " + String.format("%.2f", config.jpegQuality()));
        }
        System.out.println(" Controle....: 1 por vez, timeout " + config.controlTimeoutSeconds() + "s");
        System.out.println(" Firewall....: libere a porta TCP " + port + " na LAN");
        if (config.ffmpegEnabled()) {
            System.out.println(" WebRTC......: http://" + host + ":8889/stream/whep (WHEP)");
            System.out.println(" Player......: http://" + host + ":" + port + "/test-webrtc.html");
        }
        System.out.println("============================================================");
        System.out.println(" Abra a URL no navegador da TV e informe o token.");
        System.out.println(" Use apenas em rede local confiável: o transporte é HTTP sem TLS.");
        System.out.println(" Ctrl+C encerra o servidor.");
        System.out.println();
    }

    private static void configureLogging() {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "%1$tF %1$tT %4$s %2$s: %5$s%n");
    }
}
