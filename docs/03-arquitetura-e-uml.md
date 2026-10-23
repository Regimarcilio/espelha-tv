# 03 — Arquitetura e UML

## 1. Contexto

```mermaid
flowchart LR
  User[Usuário na TV] -->|HTTP LAN| Server[Servidor Java no PC]
  Admin[Operador do PC] -->|CLI / tray| Server
  Server -->|Robot: captura| Screen[Tela do desktop]
  Server -->|Robot: input| OS[Teclado e mouse do SO]
  Server -.->|sem internet| Cloud((Fora de escopo))
```

O sistema é **local-first**: não há cloud, banco de dados, fila externa ou serviço de terceiros.

## 2. Casos de uso

```mermaid
flowchart LR
  U((Usuário))
  A((Operador))

  U --> UC1[Fazer login]
  U --> UC2[Visualizar tela]
  U --> UC3[Assumir controle]
  U --> UC4[Controlar mouse/teclado]
  U --> UC5[Liberar controle]
  U --> UC6[Alternar fullscreen]
  U --> UC7[Sair]

  A --> UC8[Iniciar servidor]
  A --> UC9[Configurar porta/token/qualidade]
  A --> UC10[Consultar /health]
  A --> UC11[Ativar modo demo]

  UC3 -.->|include| UC2
  UC4 -.->|include| UC3
```

## 3. Componentes

```mermaid
flowchart TB
  subgraph PC["PC — JVM 21"]
    Main[Main] --> Config[AppConfig]
    Main --> Server[AppServer]
    Server --> API[HttpApi]
    Server --> Capture[CaptureService]
    Server --> Hub[FrameHub]
    Server --> Auth[AuthService]
    Server --> Sessions[SessionStore]
    Server --> Control[ControlManager]
    Server --> Input[InputController]

    Capture --> Source[ScreenSource]
    Source --> RobotSrc[RobotScreenSource]
    Source --> DemoSrc[DemoScreenSource]
    Capture --> Encoder[JpegEncoder]
    Capture --> Hub

    Input --> Sink[InputSink]
    Sink --> RobotSink[RobotInputSink]
    Sink --> NoopSink[NoopInputSink]

    API --> Auth
    API --> Sessions
    API --> Control
    API --> Input
    API --> Hub
    API --> Static[StaticFiles]
  end

  subgraph TV["TV — navegador"]
    Login[login.html/login.js]
    App[index.html/app.js]
    Img[&lt;img&gt; MJPEG]
    JS[JS: fetch input]
  end

  App --> Img
  App --> JS
  Img -->|GET /stream| API
  JS -->|POST /api/input| API
  Login -->|POST /api/login| API
```

## 4. Diagrama de classes

```mermaid
classDiagram
  class ScreenSource {
    <<interface>>
    +capture() BufferedImage
    +bounds() Rectangle
    +description() String
  }
  class RobotScreenSource
  class DemoScreenSource

  class JpegEncoder {
    +encode(BufferedImage, float) byte[]
  }

  class Frame {
    +jpeg byte[]
    +sequence long
    +width int
    +height int
    +timestamp Instant
  }

  class FrameHub {
    +subscribe() BlockingQueue~Frame~
    +unsubscribe(BlockingQueue~Frame~)
    +publish(Frame)
    +viewerCount() int
  }

  class CaptureService {
    +start()
    +close()
    +lastFrame() Frame
    +lastError() String
  }

  class InputSink {
    <<interface>>
    +mouseMove(int, int)
    +mouseMoveRelative(int, int)
    +mousePress(int)
    +mouseRelease(int)
    +mouseWheel(int)
    +keyPress(int)
    +keyRelease(int)
    +typeText(String)
    +releaseAll()
    +screenBounds() Rectangle
  }
  class RobotInputSink
  class NoopInputSink

  class InputController {
    +handle(Map~String,String~) boolean
  }

  class KeyMapper {
    +toKeyCode(String) int
  }

  class AuthService {
    +login(String, String) LoginResult
  }

  class SessionStore {
    +create(int) Session
    +get(String) Session
    +remove(String)
    +cleanup()
  }

  class ControlManager {
    +claim(String) ClaimResult
    +release(String, String) boolean
    +touch(String, String) boolean
    +isController(String, String) boolean
    +isControllerSession(String) boolean
    +cleanupIdle()
  }

  ScreenSource <|.. RobotScreenSource
  ScreenSource <|.. DemoScreenSource
  InputSink <|.. RobotInputSink
  InputSink <|.. NoopInputSink
  InputController --> InputSink
  InputController --> KeyMapper
  CaptureService --> ScreenSource
  CaptureService --> JpegEncoder
  CaptureService --> FrameHub
  ControlManager --> InputController
```

## 5. Sequência — login e stream

```mermaid
sequenceDiagram
  participant TV
  participant API as HttpApi
  participant Auth as AuthService
  participant Sess as SessionStore
  participant Hub as FrameHub
  participant Cap as CaptureService

  TV->>API: POST /api/login (token)
  API->>Auth: login(token, ip)
  Auth-->>API: OK
  API->>Sess: create(ttl)
  Sess-->>API: sessionId
  API-->>TV: 200 + Set-Cookie HttpOnly
  TV->>API: GET /stream (cookie)
  API->>Sess: get(sessionId)
  Sess-->>API: Session
  API->>Hub: subscribe()
  loop cada frame
    Cap->>Hub: publish(frame)
    Hub-->>API: frame
    API-->>TV: --frame + JPEG
  end
```

## 6. Sequência — assumir controle e input

```mermaid
sequenceDiagram
  participant TV
  participant API as HttpApi
  participant Sess as SessionStore
  participant Ctl as ControlManager
  participant Inp as InputController
  participant Sink as InputSink

  TV->>API: POST /api/control action=claim
  API->>Sess: get(cookie)
  API->>Ctl: claim(sessionId)
  Ctl-->>API: token de controle
  API-->>TV: 200 {token}

  TV->>API: POST /api/input (token, key ArrowUp)
  API->>Ctl: touch(sessionId, token)
  Ctl-->>API: true
  API->>Inp: handle(...)
  Inp->>Sink: mouseMoveRelative(0,-24)
  API-->>TV: 204

  TV->>API: POST /api/control action=release
  API->>Ctl: release(sessionId, token)
  Ctl->>Inp: releaseAll()
  Inp->>Sink: releaseAll()
  API-->>TV: 200
```

## 7. Sequência — timeout e queda

```mermaid
sequenceDiagram
  participant Sched as Scheduler
  participant Ctl as ControlManager
  participant Inp as InputController
  participant API as HttpApi
  participant TV

  Sched->>Ctl: cleanupIdle()
  Ctl->>Ctl: idle > timeout
  Ctl->>Inp: releaseAll()
  Ctl-->>API: controlador liberado
  TV->>API: GET /api/state
  API-->>TV: controllerActive=false
```

## 8. API HTTP

| Método | Rota | Auth | Descrição |
|---|---|---|---|
| GET | `/` | opcional | `index.html` se sessão válida; senão `login.html` |
| GET | `/login` | não | Página de login |
| GET | `/app.js`, `/login.js`, `/style.css`, `/favicon.svg` | não | Assets estáticos |
| POST | `/api/login` | não | `token` (form) → cookie de sessão |
| POST | `/api/logout` | cookie | Invalida sessão |
| GET | `/api/state` | cookie | Estado da sessão/controle |
| POST | `/api/control` | cookie | `action=claim` ou `action=release`; claim devolve token |
| POST | `/api/input` | cookie + control token | Eventos de mouse/teclado |
| POST | `/api/heartbeat` | cookie + control token | Renova atividade do controle |
| GET | `/stream` | cookie | MJPEG `multipart/x-mixed-replace` |
| GET | `/health` | não | `ok` + versão/uptime |

### 8.1 Formato dos eventos de input
`application/x-www-form-urlencoded`, com `X-EspelhaTV: 1`:

| `type` | Campos | Efeito |
|---|---|---|
| `move` | `x`, `y` (0..1) | mouse absoluto |
| `moveRelative` | `dx`, `dy` | mouse relativo |
| `click` | `button` (1..3), `down` (true/false) | pressiona/solta botão |
| `wheel` | `delta` | roda do mouse |
| `key` | `code`, `down` | tecla |
| `text` | `text` (≤128) | digita ASCII |
| `releaseAll` | — | solta teclas/botões |

## 9. Configuração

| CLI | Env | Padrão | Descrição |
|---|---|---|---|
| `--port` | `ESPELHATV_PORT` | `8080` | Porta HTTP |
| `--bind` | `ESPELHATV_BIND` | `0.0.0.0` | Interface de escuta |
| `--token` | `ESPELHATV_TOKEN` | gerado | Token de acesso |
| `--fps` | — | `10` | FPS de captura (1–30) |
| `--width` | — | `1280` | Largura máxima do frame |
| `--height` | — | `720` | Altura máxima do frame |
| `--quality` | — | `0.60` | Qualidade JPEG (0.1–1.0) |
| `--control-timeout` | — | `300` | Segundos sem input para liberar |
| `--session-ttl` | — | `720` | Minutos de vida da sessão |
| `--cursor` | — | `true` | Desenhar cursor no frame |
| `--screen` | — | `0` | Monitor (MVP: primário) |
| `--demo` | — | `false` | Fonte sintética |
| — | `ESPELHATV_SCREENSHOT_METHOD` | `x11` em Wayland | Backend de screenshot do `Robot` (`x11`, `dbusScreencast`) |
| `--help` | — | — | Ajuda |

## 10. Concorrência

- **Captura:** um `ScheduledExecutorService` de thread única no ritmo de `fps`.
- **HTTP:** `Executors.newVirtualThreadPerTaskExecutor()`; cada stream MJPEG ocupa uma virtual thread.
- **FrameHub:** fila `ArrayBlockingQueue(1)` por cliente; o frame novo substitui o antigo (drop).
- **Controle:** `ControlManager` sincronizado; `InputController` com `synchronized` no `releaseAll`.
- **Sessões:** `ConcurrentHashMap`; cleanup agendado.
- **Queda do stream:** `HttpApi` conta streams por sessão; ao fechar o último stream do controlador, agenda `releaseBySession` com carência de 3 s. Reconexão dentro da carência cancela a liberação.
- **Shutdown:** hook chama `close()` em captura, servidor e `releaseAll()`.

## 11. Segurança

- Token de acesso nunca é logado; comparação por SHA-256 + `MessageDigest.isEqual`.
- Cookie de sessão opaco (32 bytes aleatórios), `HttpOnly`, `SameSite=Strict`, TTL.
- `X-EspelhaTV: 1` obrigatório em POST autenticado; sem CORS.
- CSP restritiva; `X-Frame-Options: DENY`; `nosniff`; `Referrer-Policy: no-referrer`.
- Stream e APIs exigem sessão; não há URL pública de vídeo.
- Rate limit de login por IP.
- Bind padrão em `0.0.0.0` para LAN; documentar firewall e não expor à internet.
- Sem TLS no MVP: usar apenas em LAN confiável/VLAN isolada.

## 12. Compatibilidade e limitações

| Cenário | Situação |
|---|---|
| webOS 3+ / Tizen 2016+ | MJPEG via `<img>`; controle por teclado/fetch |
| webOS 1.x/2.x, NetCast, Orsay | Testar; MJPEG pode funcionar; sem garantia |
| TV sem navegador | Fora de escopo |
| Windows | Captura/input estáveis; UAC/lock screen não capturáveis |
| Linux X11 | Estável |
| Linux Wayland | XWayland/X11 costuma funcionar; portal `dbusScreencast` é experimental e pode bloquear/perder permissão; input pode ser restrito |
| macOS | Permissões de Tela e Acessibilidade |
| Áudio | Fora do MVP |
| Múltiplos monitores | Fora do MVP (primário) |

## 13. Estrutura de pastas

```text
espelha-tv/
├── pom.xml
├── README.md
├── docs/
├── scripts/
│   ├── bootstrap-toolchain.sh
│   ├── env.sh
│   ├── build.sh
│   └── run.sh
└── src/
    ├── main/java/br/com/espelhatv/
    │   ├── Main.java
    │   ├── auth/
    │   ├── capture/
    │   ├── config/
    │   ├── control/
    │   ├── input/
    │   ├── server/
    │   └── util/
    ├── main/resources/web/
    └── test/java/br/com/espelhatv/
```
