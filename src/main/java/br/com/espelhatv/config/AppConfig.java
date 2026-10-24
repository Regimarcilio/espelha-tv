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
        boolean demo) {

    public static final int DEFAULT_PORT = 8080;
    public static final String DEFAULT_BIND = "0.0.0.0";
    public static final int DEFAULT_FPS = 10;
    public static final int DEFAULT_WIDTH = 1280;
    public static final int DEFAULT_HEIGHT = 720;
    public static final float DEFAULT_QUALITY = 0.60f;
    public static final int DEFAULT_CONTROL_TIMEOUT_SECONDS = 300;
    public static final int DEFAULT_SESSION_TTL_MINUTES = 720;

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
                default -> throw new IllegalArgumentException("Opção desconhecida: --" + key + ". Use --help.");
            }
        }

        boolean generated = token.isBlank();
        if (generated) {
            token = generateToken();
        }

        return new AppConfig(port, bind, token, generated, fps, width, height, quality,
                controlTimeout, sessionTtl, cursor, screen, demo);
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
                + ']';
    }

    /** Exceção de controle de fluxo para --help, sem chamar System.exit. */
    public static final class HelpRequested extends RuntimeException {
        public HelpRequested() {
            super("Ajuda solicitada", null, false, false);
        }
    }
}
