package br.com.espelhatv.config;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * Configuração imutável do servidor. A senha/token nunca aparece em
 * {@link #toString()} para evitar vazamento em logs.
 */
public record AppConfig(
        int port,
        String bind,
        String token,
        boolean tokenGenerated,
        int fps,
        int maxWidth,
        int maxHeight,
        float jpegQuality,
        int controlTimeoutSeconds,
        int sessionTtlMinutes,
        boolean drawCursor,
        int screenIndex,
        boolean demo,
        // FFmpeg / WebRTC options
        boolean ffmpegEnabled,
        String ffmpegRtpHost,
        int ffmpegRtpPort,
        int ffmpegWidth,
        int ffmpegHeight,
        int ffmpegFps,
        String ffmpegPreset,
        int ffmpegBitrate,
        int ffmpegQueueSize,
        String ffmpegWhipUrl,
        String ffmpegWhipBin,
        String ffmpegVaapiDevice,
        String ffmpegCapture
) {

    public static final int DEFAULT_PORT = 8080;
    public static final String DEFAULT_BIND = "0.0.0.0";
    public static final int DEFAULT_FPS = 10;
    public static final int DEFAULT_WIDTH = 1280;
    public static final int DEFAULT_HEIGHT = 720;
    public static final float DEFAULT_QUALITY = 0.60f;
    public static final int DEFAULT_CONTROL_TIMEOUT_SECONDS = 300;
    public static final int DEFAULT_SESSION_TTL_MINUTES = 720;
    public static final boolean DEFAULT_FFMPEG_ENABLED = false;
    public static final String DEFAULT_FFMPEG_RTP_HOST = "127.0.0.1";
    public static final int DEFAULT_FFMPEG_RTP_PORT = 5004;
    public static final int DEFAULT_FFMPEG_WIDTH = 1920;
    public static final int DEFAULT_FFMPEG_HEIGHT = 1080;
    public static final int DEFAULT_FFMPEG_FPS = 30;
    public static final String DEFAULT_FFMPEG_PRESET = "ultrafast";
    public static final int DEFAULT_FFMPEG_BITRATE = 8000;
    public static final int DEFAULT_FFMPEG_QUEUE_SIZE = 2;
    public static final String DEFAULT_FFMPEG_WHIP_URL = "http://127.0.0.1:8889/stream/whip";
    public static final String DEFAULT_FFMPEG_WHIP_BIN = "tools/ffmpeg-whip";
    public static final String DEFAULT_FFMPEG_VAAPI_DEVICE = "/dev/dri/renderD128";
    public static final String DEFAULT_FFMPEG_CAPTURE = "x11grab";

    private static final String TOKEN_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public AppConfig {
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("Porta inválida: " + port);
        }
        if (bind == null || bind.isBlank()) {
            throw new IllegalArgumentException("Bind não pode ser vazio.");
        }
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token não pode ser vazio.");
        }
        if (fps < 1 || fps > 30) {
            throw new IllegalArgumentException("FPS deve estar entre 1 e 30.");
        }
        if (maxWidth < 320 || maxWidth > 3_840) {
            throw new IllegalArgumentException("Largura deve estar entre 320 e 3840.");
        }
        if (maxHeight < 240 || maxHeight > 2_160) {
            throw new IllegalArgumentException("Altura deve estar entre 240 e 2160.");
        }
        if (jpegQuality < 0.10f || jpegQuality > 1.0f) {
            throw new IllegalArgumentException("Qualidade JPEG deve estar entre 0.10 e 1.00.");
        }
        if (controlTimeoutSeconds < 30 || controlTimeoutSeconds > 3_600) {
            throw new IllegalArgumentException("Timeout de controle deve estar entre 30 e 3600 segundos.");
        }
        if (sessionTtlMinutes < 1 || sessionTtlMinutes > 10_080) {
            throw new IllegalArgumentException("TTL de sessão deve estar entre 1 e 10080 minutos.");
        }
        if (screenIndex < 0) {
            throw new IllegalArgumentException("Índice de tela não pode ser negativo.");
        }
        if (ffmpegEnabled) {
            if (ffmpegRtpPort < 1 || ffmpegRtpPort > 65535) {
                throw new IllegalArgumentException("Porta RTP FFmpeg inválida: " + ffmpegRtpPort);
            }
            if (ffmpegWidth < 320 || ffmpegWidth > 3840) {
                throw new IllegalArgumentException("Largura FFmpeg inválida: " + ffmpegWidth);
            }
            if (ffmpegHeight < 240 || ffmpegHeight > 2160) {
                throw new IllegalArgumentException("Altura FFmpeg inválida: " + ffmpegHeight);
            }
            if (ffmpegFps < 1 || ffmpegFps > 60) {
                throw new IllegalArgumentException("FPS FFmpeg inválido: " + ffmpegFps);
            }
            if (ffmpegBitrate < 500 || ffmpegBitrate > 50000) {
                throw new IllegalArgumentException("Bitrate FFmpeg inválido: " + ffmpegBitrate);
            }
            if (ffmpegQueueSize < 1 || ffmpegQueueSize > 10) {
                throw new IllegalArgumentException("Queue size FFmpeg inválido: " + ffmpegQueueSize);
            }
            if (ffmpegWhipUrl == null || ffmpegWhipUrl.isBlank()) {
                throw new IllegalArgumentException("URL WHIP do FFmpeg não pode ser vazia.");
            }
            if (ffmpegWhipBin == null || ffmpegWhipBin.isBlank()) {
                throw new IllegalArgumentException("Binário WHIP do FFmpeg não pode ser vazio.");
            }
            if (!"x11grab".equals(ffmpegCapture) && !"stdin".equals(ffmpegCapture)) {
                throw new IllegalArgumentException("Captura FFmpeg deve ser 'x11grab' ou 'stdin': " + ffmpegCapture);
            }
        }
    }

    public static AppConfig parse(String[] args) {
        int port = intEnv("ESPELHATV_PORT", DEFAULT_PORT);
        String bind = stringEnv("ESPELHATV_BIND", DEFAULT_BIND);
        String envToken = System.getenv("ESPELHATV_TOKEN");
        String token = envToken == null ? "" : envToken.trim();
        int fps = DEFAULT_FPS;
        int width = DEFAULT_WIDTH;
        int height = DEFAULT_HEIGHT;
        float quality = DEFAULT_QUALITY;
        int controlTimeout = DEFAULT_CONTROL_TIMEOUT_SECONDS;
        int sessionTtl = DEFAULT_SESSION_TTL_MINUTES;
        boolean cursor = true;
        int screen = 0;
        boolean demo = false;
        boolean ffmpegEnabled = DEFAULT_FFMPEG_ENABLED;
        String ffmpegRtpHost = DEFAULT_FFMPEG_RTP_HOST;
        int ffmpegRtpPort = DEFAULT_FFMPEG_RTP_PORT;
        int ffmpegWidth = DEFAULT_FFMPEG_WIDTH;
        int ffmpegHeight = DEFAULT_FFMPEG_HEIGHT;
        int ffmpegFps = DEFAULT_FFMPEG_FPS;
        String ffmpegPreset = DEFAULT_FFMPEG_PRESET;
        int ffmpegBitrate = DEFAULT_FFMPEG_BITRATE;
        int ffmpegQueueSize = DEFAULT_FFMPEG_QUEUE_SIZE;
        String ffmpegWhipUrl = stringEnv("ESPELHATV_FFMPEG_WHIP_URL", DEFAULT_FFMPEG_WHIP_URL);
        String ffmpegWhipBin = stringEnv("ESPELHATV_FFMPEG_WHIP_BIN", DEFAULT_FFMPEG_WHIP_BIN);
        String ffmpegVaapiDevice = stringEnv("ESPELHATV_FFMPEG_VAAPI_DEVICE", DEFAULT_FFMPEG_VAAPI_DEVICE);
        String ffmpegCapture = stringEnv("ESPELHATV_FFMPEG_CAPTURE", DEFAULT_FFMPEG_CAPTURE).toLowerCase(Locale.ROOT);

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg == null || arg.isBlank()) {
                continue;
            }
            if (arg.equals("--help") || arg.equals("-h")) {
                printHelp();
                throw new HelpRequested();
            }
            if (arg.equals("--demo")) {
                demo = true;
                continue;
            }
            if (arg.equals("--no-cursor")) {
                cursor = false;
                continue;
            }
            if (!arg.startsWith("--")) {
                throw new IllegalArgumentException("Argumento inválido: " + arg + ". Use --help.");
            }

            String key;
            String value;
            int eq = arg.indexOf('=');
            if (eq > 2) {
                key = arg.substring(2, eq);
                value = arg.substring(eq + 1);
            } else {
                key = arg.substring(2);
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Faltou valor para --" + key + ".");
                }
                value = args[++i];
            }

            switch (key) {
                case "port" -> port = intArg(key, value);
                case "bind" -> bind = value;
                case "token" -> token = value == null ? "" : value.trim();
                case "fps" -> fps = intArg(key, value);
                case "width" -> width = intArg(key, value);
                case "height" -> height = intArg(key, value);
                case "quality" -> quality = floatArg(key, value);
                case "control-timeout" -> controlTimeout = intArg(key, value);
                case "session-ttl" -> sessionTtl = intArg(key, value);
                case "cursor" -> cursor = boolArg(key, value);
                case "screen" -> screen = intArg(key, value);
                case "demo" -> demo = boolArg(key, value);
                // FFmpeg options
                case "ffmpeg-enabled" -> ffmpegEnabled = boolArg(key, value);
                case "ffmpeg-rtp-host" -> ffmpegRtpHost = value;
                case "ffmpeg-rtp-port" -> ffmpegRtpPort = intArg(key, value);
                case "ffmpeg-width" -> ffmpegWidth = intArg(key, value);
                case "ffmpeg-height" -> ffmpegHeight = intArg(key, value);
                case "ffmpeg-fps" -> ffmpegFps = intArg(key, value);
                case "ffmpeg-preset" -> ffmpegPreset = value;
                case "ffmpeg-bitrate" -> ffmpegBitrate = intArg(key, value);
                case "ffmpeg-queue-size" -> ffmpegQueueSize = intArg(key, value);
                case "ffmpeg-whip-url" -> ffmpegWhipUrl = value;
                case "ffmpeg-whip-bin" -> ffmpegWhipBin = value;
                case "ffmpeg-vaapi-device" -> ffmpegVaapiDevice = value == null ? "" : value.trim();
                case "ffmpeg-capture" -> ffmpegCapture = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
                default -> throw new IllegalArgumentException("Opção desconhecida: --" + key + ". Use --help.");
            }
        }

        boolean generated = token.isBlank();
        if (generated) {
            token = generateToken();
        }

        return new AppConfig(port, bind, token, generated, fps, width, height, quality,
                controlTimeout, sessionTtl, cursor, screen, demo,
                ffmpegEnabled, ffmpegRtpHost, ffmpegRtpPort, ffmpegWidth, ffmpegHeight,
                ffmpegFps, ffmpegPreset, ffmpegBitrate, DEFAULT_FFMPEG_QUEUE_SIZE,
                ffmpegWhipUrl, ffmpegWhipBin, ffmpegVaapiDevice, ffmpegCapture);
    }

    public static String generateToken() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            sb.append(TOKEN_ALPHABET.charAt(RANDOM.nextInt(TOKEN_ALPHABET.length())));
        }
        return sb.toString();
    }

    private static String stringEnv(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static int intEnv(String name, int fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Variável " + name + " não é um inteiro válido: " + value, e);
        }
    }

    private static int intArg(String key, String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("--" + key + " espera um inteiro, recebeu: " + value, e);
        }
    }

    private static float floatArg(String key, String value) {
        try {
            return Float.parseFloat(value.trim().replace(',', '.'));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("--" + key + " espera um número, recebeu: " + value, e);
        }
    }

    private static boolean boolArg(String key, String value) {
        if (value == null) {
            throw new IllegalArgumentException("--" + key + " espera true ou false.");
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "true", "1", "yes", "sim" -> true;
            case "false", "0", "no", "nao", "não" -> false;
            default -> throw new IllegalArgumentException("--" + key + " espera true ou false, recebeu: " + value);
        };
    }

    public static void printHelp() {
        System.out.print("""
                Espelha-TV — espelhamento de tela para TV via navegador

                Uso:
                  java -jar espelha-tv.jar [opções]

                Opções:
                  --port=8080              Porta HTTP (padrão: 8080)
                  --bind=0.0.0.0           Interface de escuta (padrão: 0.0.0.0)
                  --token=SEGREDO          Token de acesso; se omitido, é gerado
                  --fps=10                 Quadros por segundo (1..30)
                  --width=1280             Largura máxima do frame (320..3840)
                  --height=720             Altura máxima do frame (240..2160)
                  --quality=0.60           Qualidade JPEG (0.10..1.00)
                  --control-timeout=300    Timeout do controle em segundos (30..3600)
                  --session-ttl=720        Validade da sessão em minutos (1..10080)
                  --no-cursor              Não desenhar o cursor na captura
                  --cursor=true|false      Liga/desliga o cursor na captura
                  --screen=0               Tela a capturar (MVP: tela primária)
                  --demo                   Usa tela sintética e não controla o PC
                  
                Opções FFmpeg/WebRTC:
                  --ffmpeg-enabled=true|false    Habilita pipeline FFmpeg nativo (padrão: false)
                  --ffmpeg-rtp-host=IP           Host para RTP (padrão: 127.0.0.1)
                  --ffmpeg-rtp-port=PORT         Porta RTP destino (padrão: 5004)
                  --ffmpeg-width=W               Largura do encode (padrão: 1920)
                  --ffmpeg-height=H              Altura do encode (padrão: 1080)
                  --ffmpeg-fps=FPS               FPS alvo FFmpeg (padrão: 30)
                  --ffmpeg-preset=PRESET         Preset x264 (padrão: ultrafast)
                  --ffmpeg-bitrate=KBPS          Bitrate kbps (padrão: 8000)
                  --ffmpeg-queue-size=N          Tamanho queue frames (padrão: 2)
                  --ffmpeg-whip-url=URL          Endpoint WHIP do MediaMTX
                                                 (padrão: http://127.0.0.1:8889/stream/whip)
                  --ffmpeg-whip-bin=PATH         ffmpeg com muxer WHIP (padrão: tools/ffmpeg-whip)
                  --ffmpeg-vaapi-device=PATH     Device VAAPI (padrão: /dev/dri/renderD128; vazio desliga)
                  --ffmpeg-capture=MODE          Captura: x11grab (direto no ffmpeg) ou stdin (padrão: x11grab)

                Opções gerais:
                  --help                   Mostra esta ajuda

                Variáveis de ambiente:
                  ESPELHATV_PORT, ESPELHATV_BIND, ESPELHATV_TOKEN

                Exemplo:
                  java -jar espelha-tv.jar --port=8080 --fps=8 --quality=0.55

                Aviso: use apenas em rede local confiável. O transporte é HTTP sem TLS.
                """);
    }

    @Override
    public String toString() {
        return "AppConfig[port=" + port
                + ", bind=" + bind
                + ", token=" + (tokenGenerated ? "<gerado>" : "<definido>")
                + ", fps=" + fps
                + ", maxWidth=" + maxWidth
                + ", maxHeight=" + maxHeight
                + ", jpegQuality=" + jpegQuality
                + ", controlTimeoutSeconds=" + controlTimeoutSeconds
                + ", sessionTtlMinutes=" + sessionTtlMinutes
                + ", drawCursor=" + drawCursor
                + ", screenIndex=" + screenIndex
                + ", demo=" + demo
                + ", ffmpegEnabled=" + ffmpegEnabled
                + ", ffmpegRtpHost=" + ffmpegRtpHost
                + ", ffmpegRtpPort=" + ffmpegRtpPort
                + ", ffmpegWidth=" + ffmpegWidth
                + ", ffmpegHeight=" + ffmpegHeight
                + ", ffmpegFps=" + ffmpegFps
                + ", ffmpegPreset=" + ffmpegPreset
                + ", ffmpegBitrate=" + ffmpegBitrate
                + ", ffmpegQueueSize=" + ffmpegQueueSize
                + ", ffmpegWhipUrl=" + ffmpegWhipUrl
                + ", ffmpegWhipBin=" + ffmpegWhipBin
                + ", ffmpegVaapiDevice=" + ffmpegVaapiDevice
                + ", ffmpegCapture=" + ffmpegCapture
                + ']';
    }

    /** Exceção de controle de fluxo para --help, sem chamar System.exit. */
    public static final class HelpRequested extends RuntimeException {
        public HelpRequested() {
            super("Ajuda solicitada", null, false, false);
        }
    }
}
